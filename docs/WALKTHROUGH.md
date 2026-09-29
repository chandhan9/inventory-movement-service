# Walkthrough: how this service works and why

Read this until you can explain every section in your own words. Interviewers will ask
"walk me through a project you built", and the answers below are the talking points.

---

## 1. The problem

A retailer holds the same product (SKU) in many places: distribution centers (DCs),
fulfillment centers, and stores. Stock moves constantly: it's received from vendors,
transferred DC → store, sold, and corrected after cycle counts. The system of record has to:

- never lose or double-count a unit,
- never oversell (go below zero),
- stay correct when many requests hit the same SKU at the same time,
- tell other systems (e-commerce availability, replenishment, finance) what changed.

## 2. Code structure (layered architecture)

```
api/          Controllers + request/response DTOs (records) + global error handling
service/      Business logic (InventoryService, CatalogService)
domain/       JPA entities: Product, Location, StockLevel, InventoryTransaction
repository/   Spring Data JPA repositories
events/       InventoryEvent + publisher interface + Kafka / logging implementations
config/       Kafka topic configuration
resources/db/migration   Flyway SQL migrations
```

Each layer only talks to the layer below it. Controllers never touch repositories directly.

## 3. What happens on a request: `POST /api/v1/inventory/transfers`

1. **Validation**: `@Valid` + Bean Validation annotations on the `TransferRequest` record
   reject bad input (missing SKU, quantity ≤ 0) with a `400` before any logic runs.
2. **Idempotency check**: the service looks up the `idempotencyKey`. If it was already
   processed, it returns the original result (`200`, `replayed: true`) and changes nothing.
   Reusing a key for a *different* request returns `409`.
3. **Stock update**: inside one `@Transactional` method it subtracts from the source
   `StockLevel` and adds to the destination. `StockLevel.apply()` throws
   `InsufficientStockException` if stock would go negative → `422`, and the whole
   transaction rolls back, so a transfer is never half-applied.
4. **Ledger**: two immutable `InventoryTransaction` rows (TRANSFER_OUT, TRANSFER_IN) are
   appended, sharing one `transactionGroup` id and recording `balanceAfter`.
5. **After commit**: the cache entry for that SKU is evicted and two `InventoryEvent`s are
   published to Kafka.

## 4. Key design decisions (the interview gold)

### Ledger + balance (event-sourcing lite)
`stock_level` holds the current number for fast reads; `inventory_transaction` is the
append-only history. The `/reconciliation` endpoint sums the ledger per location and
compares it with `stock_level`. If they ever differ, something wrote to the table outside
the service, or there's a bug. This is **transaction validation**: the ledger is the
source of truth that proves the balance is right.

### Idempotency
Networks fail; clients retry. Without idempotency a retried "receive 100 units" becomes
200 units. The client sends a unique key per logical operation. The database has a unique
constraint on `(idempotency_key, type)`, so even two *concurrent* retries can't both
insert. The second one fails the constraint and gets a `409`.

### Optimistic locking (`@Version`)
Two requests read the same `StockLevel` (on_hand = 10). Both subtract 3 and save.
Without locking the result is 7 (a **lost update**); it should be 4. With `@Version`,
Hibernate adds `WHERE version = ?` to the UPDATE; the second writer updates 0 rows and gets
`OptimisticLockingFailureException` → `409`, and the client retries with the same
idempotency key.
*Why optimistic instead of pessimistic (`SELECT ... FOR UPDATE`)?* Conflicts on a single
SKU-location are relatively rare, so optimistic locking avoids holding row locks and scales
better. For a very hot SKU (a flash sale), pessimistic locking or a queue per SKU might be better.

### Cache invalidation after commit
`GET /inventory/{sku}` is `@Cacheable` (Caffeine, in-memory). The tricky part is
**when** to evict. If you evict *before* the transaction commits, a concurrent reader can
load the *old* value from the database and put it back in the cache, where it stays stale
until the TTL. So eviction is registered with `TransactionSynchronization.afterCommit()`.
There's also a 10-minute TTL as a safety net.
*Follow-up question: "What if you run 3 instances?"* Each has its own local cache, so
instance B doesn't know instance A changed the stock. Fixes: a shared cache (Redis), or
have every instance consume the Kafka events and evict its own cache.

### Events after commit, keyed by SKU
Publishing *before* commit could announce a change that then rolls back. So events are
published after commit. The Kafka message key is the SKU, so all events for one SKU go to
the same partition and consumers see them **in order**.
*Known gap (say it before they do):* if the app crashes between commit and publish, the
event is lost. The production fix is the **transactional outbox pattern**: write the event
into an `outbox` table in the *same* DB transaction, and a separate relay publishes it to
Kafka. That's the first item on the roadmap.

### Dependency inversion (the "D" in SOLID)
`InventoryService` depends on the `InventoryEventPublisher` **interface**, not Kafka.
Kafka or logging is chosen by configuration (`@ConditionalOnProperty`). Swapping Kafka for
AWS SQS means writing one new class; the service doesn't change. It also keeps tests fast
(no Kafka needed).

### SOLID quick reference
- **S**ingle responsibility: controller = HTTP, service = business rules, repository = data access, entity = its own invariants (`StockLevel.apply`).
- **O**pen/closed: add a new publisher without modifying the service.
- **L**iskov: any `InventoryEventPublisher` implementation can replace another.
- **I**nterface segregation: the publisher interface has one method.
- **D**ependency inversion: see above; all dependencies are injected via constructors.

### Errors (RFC 7807)
`ApiExceptionHandler` turns exceptions into consistent problem-detail JSON with the right
status: 400 / 404 / 409 / 422.

### Schema migrations (Flyway)
The schema lives in versioned SQL files (`V1__...sql`), so every environment's database is
built the same way. Hibernate is set to `ddl-auto: none` so it never changes the schema
by itself. Database constraints (`CHECK on_hand >= 0`, unique keys) back up the Java rules.

### Observability
Actuator exposes `/actuator/health/readiness` and `/liveness` (used by the Kubernetes
probes) and `/actuator/prometheus`. Custom Micrometer counters:
`inventory_movements_total{type=...}`, `inventory_movements_rejected_total`,
`inventory_movements_replayed_total`, `inventory_events_publish_failed_total`,
`inventory_reconciliation_mismatch_total`. In production you'd alert on publish failures
and reconciliation mismatches.

## 5. Testing

- `StockLevelTest`: plain JUnit unit tests for the domain rule (no Spring, runs in milliseconds).
- `InventoryApiIntegrationTest`: `@SpringBootTest` + `MockMvc` end-to-end tests through the
  real controller, service, JPA, Flyway schema, and cache against in-memory H2. It covers
  idempotent retries, overselling, atomic transfer rollback, reconciliation, cache
  invalidation, validation errors, and the metrics endpoints.

**TDD vs BDD** (the job posting mentions this):
- **TDD** (Test-Driven Development) is a *developer* practice: write a failing unit test,
  write the minimum code to pass, refactor. Tests describe *how the code behaves*.
- **BDD** (Behavior-Driven Development) starts from *business behavior* written in plain
  language that product owners can read (Given / When / Then, e.g. with Cucumber):
  *Given 5 units at the DC, when I transfer 8 to the store, then the transfer is rejected and
  the DC still has 5.* It's about shared understanding of requirements; TDD is about code design.
The test names in this project (`@DisplayName`) are written in that behavior style.

## 6. CI/CD and deployment

- **GitHub Actions** (`.github/workflows/ci.yml`): on every push → set up Java 21 → `mvn verify`
  (compile + all tests) → build the Docker image. A red build blocks bad code.
- **Dockerfile**: multi-stage build. Maven builds the JAR in a big image; only the JRE and
  the JAR go into the small runtime image, running as a non-root user.
- **Docker Compose**: runs the app with PostgreSQL and Kafka (KRaft mode, no ZooKeeper).
- **Kubernetes** (`k8s/`): Deployment with 2 replicas, resource limits, readiness/liveness probes.
- **AWS**: deployed on EC2 with Docker Compose (see `DEPLOY_AWS.md`). Next step: ECR + ECS/EKS, RDS, MSK.

## 7. Questions you should be ready for

1. *Why store both balances and a ledger?* Fast reads + auditability and validation.
2. *What happens if two people buy the last unit at the same time?* One succeeds; the other
   hits the optimistic lock (409) and on retry sees 0 stock (422). No oversell.
3. *How would you scale this?* Stateless app → more replicas behind a load balancer;
   DB read replicas for queries; shared cache (Redis) or event-driven cache eviction;
   partition Kafka by SKU.
4. *What would you improve?* Transactional outbox, Testcontainers, Cucumber BDD tests,
   security (OAuth2/JWT), and a consumer that processes inbound receipts from Kafka.
5. *How did you use AI tools?* Be honest about how you used them (e.g. to scaffold code,
   generate tests, and explain concepts) and how you verified the output (tests, CI, reading
   and running every endpoint yourself).
