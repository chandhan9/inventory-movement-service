# Inventory Movement Service

A Spring Boot microservice that tracks retail inventory across stores and distribution
centers. Every stock change is recorded in an append-only **ledger**, published as a
**Kafka event**, and can be **reconciled** against current balances to catch data drift.


## Features

- **REST API (JSON)** for receipts, sales, cycle-count adjustments, and store-to-store / DC-to-store transfers
- **Ledger transaction validation**: stock can never go negative, transfers are atomic, and a reconciliation endpoint rebuilds balances from the ledger
- **Idempotent writes**: every movement carries an idempotency key, so client retries are never applied twice
- **Optimistic locking** (`@Version`) to prevent lost updates under concurrent requests
- **Caching with correct invalidation**: stock lookups are cached in Caffeine and evicted only *after* the database commit
- **Event streaming**: movements are published to Kafka after commit, keyed by SKU to preserve per-SKU ordering
- **Observability**: Spring Boot Actuator health/readiness/liveness probes and Prometheus metrics (movements by type, rejections, publish failures, reconciliation mismatches)
- **Database migrations** with Flyway (PostgreSQL in Docker, H2 for zero-setup local runs and tests)
- **CI/CD** with GitHub Actions: build, test, and Docker image on every push
- **Containerized** with a multi-stage Dockerfile, plus Docker Compose and Kubernetes manifests
- **OpenAPI / Swagger UI** documentation

## Tech stack

Java 21 · Spring Boot 3 · Spring Data JPA / Hibernate · PostgreSQL · H2 · Flyway · Apache Kafka ·
Caffeine · Micrometer / Prometheus · JUnit 5 · MockMvc · Docker · Docker Compose · Kubernetes · GitHub Actions · AWS (EC2)

## Architecture

```
            ┌──────────────────────────────────────────────┐
 HTTP/JSON  │  Controllers  (validation, RFC 7807 errors)  │
 ─────────► ├──────────────────────────────────────────────┤
            │  InventoryService                            │
            │   1. idempotency check                       │
            │   2. update stock_level (optimistic lock)    │──► PostgreSQL
            │   3. append inventory_transaction (ledger)   │    (Flyway schema)
            │   4. after commit: evict cache, publish      │
            ├──────────────────────────────────────────────┤
            │  Caffeine cache      InventoryEventPublisher │──► Kafka topic
            └──────────────────────────────────────────────┘    "inventory-movements"
```

See [docs/WALKTHROUGH.md](docs/WALKTHROUGH.md) for a detailed explanation of the design decisions.

## Run it

**Option 1: zero setup (in-memory H2, events logged instead of sent to Kafka)**

```bash
mvn spring-boot:run
```

**Option 2: full stack with PostgreSQL and Kafka**

```bash
docker compose up --build
```

The API runs at `http://localhost:8080`; Swagger UI is at `http://localhost:8080/swagger-ui.html`.

**Run the tests**

```bash
mvn verify
```

## Try the API

```bash
# Create a product and two locations
curl -X POST localhost:8080/api/v1/products -H 'Content-Type: application/json' \
  -d '{"sku":"BOOT-001","name":"Leather Chelsea Boot"}'
curl -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' \
  -d '{"code":"DC-PDX","name":"Portland DC","type":"DISTRIBUTION_CENTER"}'
curl -X POST localhost:8080/api/v1/locations -H 'Content-Type: application/json' \
  -d '{"code":"ST-SEA","name":"Downtown Seattle","type":"STORE"}'

# Receive 100 units at the DC
curl -X POST localhost:8080/api/v1/inventory/receipts -H 'Content-Type: application/json' \
  -d '{"sku":"BOOT-001","locationCode":"DC-PDX","quantity":100,"idempotencyKey":"rcv-1"}'

# Transfer 25 units to the store
curl -X POST localhost:8080/api/v1/inventory/transfers -H 'Content-Type: application/json' \
  -d '{"sku":"BOOT-001","fromLocationCode":"DC-PDX","toLocationCode":"ST-SEA","quantity":25,"idempotencyKey":"xfer-1"}'

# Sell 3 units in store
curl -X POST localhost:8080/api/v1/inventory/sales -H 'Content-Type: application/json' \
  -d '{"sku":"BOOT-001","locationCode":"ST-SEA","quantity":3,"idempotencyKey":"sale-1"}'

# Check stock, ledger, and reconciliation
curl localhost:8080/api/v1/inventory/BOOT-001
curl localhost:8080/api/v1/inventory/BOOT-001/ledger
curl localhost:8080/api/v1/inventory/BOOT-001/reconciliation

# Metrics
curl localhost:8080/actuator/prometheus | grep inventory_
```

Watch the Kafka events (with Docker Compose running):

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic inventory-movements --from-beginning
```

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/products` | Create a product |
| GET | `/api/v1/products/{sku}` | Get a product |
| POST | `/api/v1/locations` | Create a store / DC / fulfillment center |
| GET | `/api/v1/locations` | List locations |
| POST | `/api/v1/inventory/receipts` | Receive stock |
| POST | `/api/v1/inventory/sales` | Record a sale (rejected with 422 if it would oversell) |
| POST | `/api/v1/inventory/adjustments` | Signed cycle-count adjustment |
| POST | `/api/v1/inventory/transfers` | Atomic transfer between two locations |
| GET | `/api/v1/inventory/{sku}` | Stock by location (cached) |
| GET | `/api/v1/inventory/{sku}/ledger` | Full ledger history |
| GET | `/api/v1/inventory/{sku}/reconciliation` | Validate ledger vs. on-hand balances |

Write endpoints return `201 Created` for a new movement and `200 OK` when an idempotent
retry returns the original result. Errors use RFC 7807 problem responses
(`400` invalid input, `404` unknown SKU/location, `409` conflict, `422` insufficient stock).

## Deploying to AWS

See [docs/DEPLOY_AWS.md](docs/DEPLOY_AWS.md).

## Roadmap

- Transactional outbox so events survive a crash between commit and publish
- Testcontainers-based tests against real PostgreSQL and Kafka
- Cucumber (BDD) acceptance tests
- Kafka consumer for inbound purchase-order receipts
- OAuth2 / JWT security
