package com.saibonthala.inventory.api;

import com.saibonthala.inventory.api.dto.AdjustmentRequest;
import com.saibonthala.inventory.api.dto.MovementRequest;
import com.saibonthala.inventory.api.dto.MovementResult;
import com.saibonthala.inventory.api.dto.ReconciliationReport;
import com.saibonthala.inventory.api.dto.StockSummary;
import com.saibonthala.inventory.api.dto.TransactionView;
import com.saibonthala.inventory.api.dto.TransferRequest;
import com.saibonthala.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/receipts")
    public ResponseEntity<MovementResult> receive(@Valid @RequestBody MovementRequest request) {
        return respond(inventoryService.receive(request));
    }

    @PostMapping("/sales")
    public ResponseEntity<MovementResult> sell(@Valid @RequestBody MovementRequest request) {
        return respond(inventoryService.sell(request));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<MovementResult> adjust(@Valid @RequestBody AdjustmentRequest request) {
        return respond(inventoryService.adjust(request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<MovementResult> transfer(@Valid @RequestBody TransferRequest request) {
        return respond(inventoryService.transfer(request));
    }

    @GetMapping("/{sku}")
    public StockSummary getStock(@PathVariable String sku) {
        return inventoryService.getStock(sku);
    }

    @GetMapping("/{sku}/ledger")
    public List<TransactionView> getLedger(@PathVariable String sku) {
        return inventoryService.getLedger(sku);
    }

    @GetMapping("/{sku}/reconciliation")
    public ReconciliationReport reconcile(@PathVariable String sku) {
        return inventoryService.reconcile(sku);
    }

    /** 201 for a new movement, 200 when an idempotent retry returns the original result. */
    private ResponseEntity<MovementResult> respond(MovementResult result) {
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }
}
