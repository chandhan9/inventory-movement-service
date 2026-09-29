package com.saibonthala.inventory.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the REST layer, service, JPA, Flyway schema and cache,
 * against an in-memory H2 database. Each test uses its own SKU and locations so tests
 * stay independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability   // enables the Prometheus registry, which Spring Boot turns off in tests by default
class InventoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String sku;
    private String store;
    private String dc;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        sku = "SKU-" + suffix;
        store = "ST-" + suffix;
        dc = "DC-" + suffix;

        postJson("/api/v1/products", """
                {"sku": "%s", "name": "Leather Chelsea Boot"}
                """.formatted(sku)).andExpect(status().isCreated());
        postJson("/api/v1/locations", """
                {"code": "%s", "name": "Downtown Seattle", "type": "STORE"}
                """.formatted(store)).andExpect(status().isCreated());
        postJson("/api/v1/locations", """
                {"code": "%s", "name": "Portland DC", "type": "DISTRIBUTION_CENTER"}
                """.formatted(dc)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("receiving stock creates a ledger entry and updates on-hand quantity")
    void receiveUpdatesStock() throws Exception {
        receive(dc, 50, key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.transactions[0].type").value("RECEIPT"))
                .andExpect(jsonPath("$.transactions[0].balanceAfter").value(50));

        mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOnHand").value(50))
                .andExpect(jsonPath("$.locations[0].locationCode").value(dc));
    }

    @Test
    @DisplayName("a retried request with the same idempotency key is not applied twice")
    void idempotentRetryIsNotDoubleCounted() throws Exception {
        String key = key();
        receive(dc, 20, key).andExpect(status().isCreated());

        receive(dc, 20, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(jsonPath("$.totalOnHand").value(20));
    }

    @Test
    @DisplayName("reusing an idempotency key for a different kind of movement is rejected")
    void idempotencyKeyReuseForDifferentMovementIsRejected() throws Exception {
        String key = key();
        receive(dc, 10, key).andExpect(status().isCreated());

        postJson("/api/v1/inventory/sales", """
                {"sku": "%s", "locationCode": "%s", "quantity": 1, "idempotencyKey": "%s"}
                """.formatted(sku, dc, key))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a sale larger than available stock is rejected with 422 and nothing changes")
    void saleCannotOversell() throws Exception {
        receive(store, 2, key()).andExpect(status().isCreated());

        postJson("/api/v1/inventory/sales", """
                {"sku": "%s", "locationCode": "%s", "quantity": 5, "idempotencyKey": "%s"}
                """.formatted(sku, store, key()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.requested").value(5));

        mockMvc.perform(get("/api/v1/inventory/{sku}/ledger", sku))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("a transfer moves stock atomically and the ledger reconciles with on-hand balances")
    void transferMovesStockAndReconciles() throws Exception {
        receive(dc, 30, key()).andExpect(status().isCreated());

        postJson("/api/v1/inventory/transfers", """
                {"sku": "%s", "fromLocationCode": "%s", "toLocationCode": "%s",
                 "quantity": 12, "idempotencyKey": "%s"}
                """.formatted(sku, dc, store, key()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.transactions[1].type").value("TRANSFER_IN"));

        mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(jsonPath("$.totalOnHand").value(30));

        mockMvc.perform(get("/api/v1/inventory/{sku}/reconciliation", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true))
                .andExpect(jsonPath("$.locations", hasSize(2)));
    }

    @Test
    @DisplayName("a failed transfer leg rolls back the whole transfer")
    void failedTransferRollsBack() throws Exception {
        receive(dc, 5, key()).andExpect(status().isCreated());

        postJson("/api/v1/inventory/transfers", """
                {"sku": "%s", "fromLocationCode": "%s", "toLocationCode": "%s",
                 "quantity": 8, "idempotencyKey": "%s"}
                """.formatted(sku, dc, store, key()))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(jsonPath("$.totalOnHand").value(5))
                .andExpect(jsonPath("$.locations", hasSize(1)));
    }

    @Test
    @DisplayName("the stock cache is invalidated when a movement commits")
    void cacheIsInvalidatedAfterMovement() throws Exception {
        receive(store, 4, key()).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(jsonPath("$.totalOnHand").value(4));   // now cached

        receive(store, 6, key()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(jsonPath("$.totalOnHand").value(10)); // not the stale cached 4
    }

    @Test
    @DisplayName("invalid requests return 400 and unknown SKUs return 404")
    void validationAndNotFound() throws Exception {
        postJson("/api/v1/inventory/receipts", """
                {"sku": "%s", "locationCode": "%s", "quantity": -3, "idempotencyKey": "%s"}
                """.formatted(sku, dc, key()))
                .andExpect(status().isBadRequest());

        postJson("/api/v1/inventory/transfers", """
                {"sku": "%s", "fromLocationCode": "%s", "toLocationCode": "%s",
                 "quantity": 1, "idempotencyKey": "%s"}
                """.formatted(sku, dc, dc, key()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/inventory/{sku}", "DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("health and Prometheus metrics endpoints are exposed")
    void observabilityEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    private ResultActions receive(String location, int quantity, String key) throws Exception {
        return postJson("/api/v1/inventory/receipts", """
                {"sku": "%s", "locationCode": "%s", "quantity": %d, "idempotencyKey": "%s"}
                """.formatted(sku, location, quantity, key));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
