package com.saibonthala.inventory.service;

import com.saibonthala.inventory.api.dto.AdjustmentRequest;
import com.saibonthala.inventory.api.dto.MovementRequest;
import com.saibonthala.inventory.api.dto.MovementResult;
import com.saibonthala.inventory.api.dto.ReconciliationReport;
import com.saibonthala.inventory.api.dto.StockSummary;
import com.saibonthala.inventory.api.dto.TransactionView;
import com.saibonthala.inventory.api.dto.TransferRequest;
import com.saibonthala.inventory.domain.InsufficientStockException;
import com.saibonthala.inventory.domain.InventoryTransaction;
import com.saibonthala.inventory.domain.Location;
import com.saibonthala.inventory.domain.MovementType;
import com.saibonthala.inventory.domain.Product;
import com.saibonthala.inventory.domain.StockLevel;
import com.saibonthala.inventory.events.InventoryEvent;
import com.saibonthala.inventory.events.InventoryEventPublisher;
import com.saibonthala.inventory.repository.InventoryTransactionRepository;
import com.saibonthala.inventory.repository.LocationBalance;
import com.saibonthala.inventory.repository.LocationRepository;
import com.saibonthala.inventory.repository.ProductRepository;
import com.saibonthala.inventory.repository.StockLevelRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Core inventory logic. Every movement:
 * <ol>
 *   <li>checks the idempotency key, so a retried request is never applied twice;</li>
 *   <li>updates the stock level (optimistic locking, never below zero);</li>
 *   <li>appends an immutable ledger entry;</li>
 *   <li>after the database commit, invalidates the cache and publishes events.</li>
 * </ol>
 */
@Service
public class InventoryService {

    public static final String STOCK_CACHE = "stock-levels";

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockLevelRepository stockLevelRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryEventPublisher eventPublisher;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;

    public InventoryService(ProductRepository productRepository,
                            LocationRepository locationRepository,
                            StockLevelRepository stockLevelRepository,
                            InventoryTransactionRepository transactionRepository,
                            InventoryEventPublisher eventPublisher,
                            CacheManager cacheManager,
                            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public MovementResult receive(MovementRequest request) {
        return applySingle(request.sku(), request.locationCode(), request.quantity(),
                request.idempotencyKey(), MovementType.RECEIPT);
    }

    @Transactional
    public MovementResult sell(MovementRequest request) {
        return applySingle(request.sku(), request.locationCode(), -request.quantity(),
                request.idempotencyKey(), MovementType.SALE);
    }

    @Transactional
    public MovementResult adjust(AdjustmentRequest request) {
        if (request.quantity() == 0) {
            throw new InvalidMovementException("Adjustment quantity must not be zero");
        }
        return applySingle(request.sku(), request.locationCode(), request.quantity(),
                request.idempotencyKey(), MovementType.ADJUSTMENT);
    }

    /** Moves stock between two locations atomically: both legs commit together or not at all. */
    @Transactional
    public MovementResult transfer(TransferRequest request) {
        if (request.fromLocationCode().equals(request.toLocationCode())) {
            throw new InvalidMovementException("Source and destination locations must be different");
        }
        Optional<MovementResult> replay = replay(request.idempotencyKey(), request.sku(),
                EnumSet.of(MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN));
        if (replay.isPresent()) {
            return replay.get();
        }

        Product product = findProduct(request.sku());
        Location from = findLocation(request.fromLocationCode());
        Location to = findLocation(request.toLocationCode());
        String group = UUID.randomUUID().toString();

        InventoryTransaction out = post(group, request.idempotencyKey(), MovementType.TRANSFER_OUT,
                product, from, -request.quantity());
        InventoryTransaction in = post(group, request.idempotencyKey(), MovementType.TRANSFER_IN,
                product, to, request.quantity());

        List<InventoryTransaction> entries = List.of(out, in);
        onCommit(product.getSku(), entries);
        return new MovementResult(group, product.getSku(), false,
                entries.stream().map(TransactionView::from).toList());
    }

    /** Read-through cache: served from Caffeine until a movement for this SKU commits. */
    @Cacheable(cacheNames = STOCK_CACHE, key = "#sku")
    @Transactional(readOnly = true)
    public StockSummary getStock(String sku) {
        Product product = findProduct(sku);
        List<StockSummary.LocationStock> locations = stockLevelRepository.findAllForProduct(product).stream()
                .map(s -> new StockSummary.LocationStock(
                        s.getLocation().getCode(), s.getLocation().getName(), s.getOnHand()))
                .toList();
        int total = locations.stream().mapToInt(StockSummary.LocationStock::onHand).sum();
        return new StockSummary(product.getSku(), total, locations);
    }

    @Transactional(readOnly = true)
    public List<TransactionView> getLedger(String sku) {
        Product product = findProduct(sku);
        return transactionRepository.findLedger(product).stream().map(TransactionView::from).toList();
    }

    /**
     * Ledger validation: rebuilds each location's balance from the ledger and compares it
     * with the stored on-hand quantity. Any mismatch means data drift that needs investigation.
     */
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(String sku) {
        Product product = findProduct(sku);

        Map<String, Integer> onHandByLocation = new TreeMap<>();
        for (StockLevel level : stockLevelRepository.findAllForProduct(product)) {
            onHandByLocation.put(level.getLocation().getCode(), level.getOnHand());
        }
        Map<String, Long> ledgerByLocation = new TreeMap<>();
        for (LocationBalance balance : transactionRepository.sumByLocation(product)) {
            ledgerByLocation.put(balance.getLocationCode(), balance.getBalance());
        }

        Set<String> codes = new TreeSet<>(onHandByLocation.keySet());
        codes.addAll(ledgerByLocation.keySet());

        List<ReconciliationReport.LocationCheck> checks = codes.stream()
                .map(code -> {
                    int onHand = onHandByLocation.getOrDefault(code, 0);
                    long ledger = ledgerByLocation.getOrDefault(code, 0L);
                    return new ReconciliationReport.LocationCheck(code, onHand, ledger, onHand == ledger);
                })
                .toList();
        boolean consistent = checks.stream().allMatch(ReconciliationReport.LocationCheck::matches);
        if (!consistent) {
            meterRegistry.counter("inventory.reconciliation.mismatch").increment();
        }
        return new ReconciliationReport(product.getSku(), consistent, checks);
    }

    // ---------------------------------------------------------------------------------------

    private MovementResult applySingle(String sku, String locationCode, int delta,
                                       String idempotencyKey, MovementType type) {
        Optional<MovementResult> replay = replay(idempotencyKey, sku, EnumSet.of(type));
        if (replay.isPresent()) {
            return replay.get();
        }
        Product product = findProduct(sku);
        Location location = findLocation(locationCode);
        String group = UUID.randomUUID().toString();

        InventoryTransaction entry = post(group, idempotencyKey, type, product, location, delta);
        onCommit(product.getSku(), List.of(entry));
        return new MovementResult(group, product.getSku(), false, List.of(TransactionView.from(entry)));
    }

    private InventoryTransaction post(String group, String idempotencyKey, MovementType type,
                                      Product product, Location location, int delta) {
        StockLevel level = stockLevelRepository.findByProductAndLocation(product, location)
                .orElseGet(() -> stockLevelRepository.save(new StockLevel(product, location)));
        int balanceAfter;
        try {
            balanceAfter = level.apply(delta);
        } catch (InsufficientStockException e) {
            meterRegistry.counter("inventory.movements.rejected", "reason", "insufficient_stock").increment();
            throw e;
        }
        InventoryTransaction entry = transactionRepository.save(
                new InventoryTransaction(group, idempotencyKey, type, product, location, delta, balanceAfter));
        meterRegistry.counter("inventory.movements", "type", type.name()).increment();
        return entry;
    }

    /**
     * Returns the original result if this idempotency key was already processed.
     * Reusing a key for a different kind of movement or SKU is rejected.
     */
    private Optional<MovementResult> replay(String idempotencyKey, String sku, Set<MovementType> allowedTypes) {
        List<InventoryTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        boolean sameRequest = existing.stream().allMatch(tx ->
                allowedTypes.contains(tx.getType()) && tx.getProduct().getSku().equals(sku));
        if (!sameRequest) {
            throw new ConflictException("Idempotency key " + idempotencyKey + " was already used for a different request");
        }
        meterRegistry.counter("inventory.movements.replayed").increment();
        return Optional.of(new MovementResult(existing.get(0).getTransactionGroup(), sku, true,
                existing.stream().map(TransactionView::from).toList()));
    }

    /**
     * Cache invalidation and event publishing run only AFTER the transaction commits.
     * Evicting before commit could let a concurrent reader re-cache the old value, and
     * publishing before commit could announce a change that is later rolled back.
     */
    private void onCommit(String sku, List<InventoryTransaction> entries) {
        List<InventoryEvent> events = entries.stream()
                .map(tx -> new InventoryEvent(UUID.randomUUID().toString(), tx.getType(), sku,
                        tx.getLocation().getCode(), tx.getQuantityDelta(), tx.getBalanceAfter(),
                        tx.getTransactionGroup(), tx.getCreatedAt()))
                .toList();

        Runnable afterCommit = () -> {
            Cache cache = cacheManager.getCache(STOCK_CACHE);
            if (cache != null) {
                cache.evict(sku);
            }
            events.forEach(eventPublisher::publish);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else {
            afterCommit.run();
        }
    }

    private Product findProduct(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Product " + sku + " not found"));
    }

    private Location findLocation(String code) {
        return locationRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Location " + code + " not found"));
    }
}
