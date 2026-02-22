---
name: exploratory-testing
description: Run exploratory tests after implementing a feature in Mars Enterprise Kit Lite. Starts local infrastructure (Docker Compose: PostgreSQL + Redpanda), builds and runs the order-service, executes HTTP requests against real endpoints, verifies data persistence in PostgreSQL, and validates Kafka event publishing/consuming. Use after completing a feature implementation to validate the full end-to-end flow works in a real environment.
disable-model-invocation: true
argument-hint: "[feature-description]"
---

# Exploratory Testing - Mars Enterprise Kit Lite

Run end-to-end exploratory validation for `$ARGUMENTS` after a feature implementation.

**Purpose:** Beyond unit and integration tests (which run in isolation with TestContainers), exploratory testing validates the REAL application running locally against REAL infrastructure. This catches issues that automated tests miss: configuration problems, migration errors, serialization bugs, and Kafka connectivity gaps.

**Scope:** Single microservice — `order-service` — running on port `8081`. Two use cases: Create Order (publishes to Kafka) and Cancel Order (consumes from Kafka).

> ⚠️ **Dual Write Reminder:** The Lite version uses Dual Write intentionally. PostgreSQL and Kafka operations are NOT atomic. The Outbox pattern solves this but is out of scope for Lite. Exploratory testing validates both sides independently.

## When to Run

Execute exploratory testing **after every feature implementation** as the final validation step:

```
TDD Cycle Complete → Unit Tests PASS → Integration Tests PASS → EXPLORATORY TESTING
```

---

## Workflow Overview

```
1. Start Infrastructure    (Docker Compose: PostgreSQL + Redpanda)
2. Build the Application   (mvn clean install)
3. Start the Application   (mvn spring-boot:run in app/)
4. Wait for Health Check   (GET /actuator/health)
5. Execute Test Scenarios  (curl requests to real endpoints)
6. Verify Database State   (psql queries to check orders + order_items)
7. Verify Kafka Events     (consume from order.created topic)
8. Test Cancel Flow        (publish to order.cancelled, verify DB update)
9. Cleanup                 (stop app, optionally stop containers)
```

---

## Step 1: Start Infrastructure

```bash
cd /path/to/mars-enterprise-kit-lite

docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

**Expected services:**

| Service | Port | Notes |
|---------|------|-------|
| PostgreSQL 16 | 5432 | DB: `orders_db`, User: `mars` |
| Redpanda (Kafka-compatible) | 9092 | Kafka API |
| Redpanda Admin | 9644 | Admin REST API |

**Wait for healthy state:**
```bash
# PostgreSQL health
docker-compose exec postgres pg_isready -U mars -d orders_db

# Redpanda health
curl -s http://localhost:9644/v1/brokers | python3 -m json.tool
```

---

## Step 2: Build the Application

```bash
# From the project root
mvn clean install

# Faster rebuild if tests already passed:
mvn clean install -DskipTests
```

**Expected output:** `BUILD SUCCESS`

If build fails, fix the issue before proceeding. Never run exploratory testing with a broken build.

---

## Step 3: Start the Application

```bash
cd app
mvn spring-boot:run
```

**Wait for startup log:**
```
Started Application in X.XXX seconds
```

**Application port:** `8081`

---

## Step 4: Health Check

```bash
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
```

**Expected response:**
```json
{
  "status": "UP"
}
```

If status is `DOWN`, check:
1. Docker containers: `docker-compose ps`
2. Application logs for DB or Kafka connection errors
3. Credentials in `app/src/main/resources/application.yaml`

---

## Step 5: Execute Test Scenarios

### Scenario Template

```
SCENARIO: [Description of what you're testing]
GIVEN:    [Preconditions]
WHEN:     [HTTP request or Kafka event]
THEN:     [Expected HTTP response + DB/Kafka state]
```

---

### Scenario 1: Create Order (Happy Path)

```bash
# WHEN: POST /orders
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "quantity": 2,
        "unitPrice": 149.95
      }
    ]
  }')

echo $ORDER_RESPONSE | python3 -m json.tool

# Extract orderId for subsequent steps
ORDER_ID=$(echo $ORDER_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['orderId'])")
echo "Order ID: $ORDER_ID"
```

**THEN:** Should return `201 Created` with:
```json
{
  "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

---

### Scenario 2: Get Order by ID

```bash
# WHEN: GET /orders/{id}
curl -s http://localhost:8081/orders/$ORDER_ID | python3 -m json.tool
```

**THEN:** Should return `200 OK` with:
```json
{
  "orderId": "...",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "total": 299.90,
  "items": [...]
}
```

---

### Scenario 3: Verify Database Persistence

```bash
# Check orders table
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT id, customer_id, status, total, created_at, updated_at
   FROM orders
   WHERE id = '$ORDER_ID';"

# Check order_items table
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT id, order_id, product_id, quantity, unit_price
   FROM order_items
   WHERE order_id = '$ORDER_ID';"
```

**Expected database state:**

| Table | Expected |
|-------|----------|
| `orders` | 1 row, `status = 'CREATED'`, correct `total`, `created_at` populated |
| `order_items` | 1 row per item, correct `quantity` and `unit_price` |

---

### Scenario 4: Verify Kafka Event Published (order.created)

```bash
# Consume from order.created topic — Redpanda CLI via Docker
docker-compose exec redpanda rpk topic consume order.created --num 1
```

**Expected event payload:**
```json
{
  "eventId": "uuid-v4",
  "orderId": "<matches ORDER_ID>",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "totalAmount": 299.90,
  "items": [
    { "productId": "...", "quantity": 2, "unitPrice": 149.95 }
  ],
  "occurredAt": "2026-..."
}
```

> ⚠️ **Dual Write note:** If Kafka is unavailable at publish time, the event will be lost — the DB will have the order but the topic will be empty. This is the intentional fragility.

---

### Scenario 5: Cancel Order (Kafka Consumer Flow)

```bash
# WHEN: Publish an order.cancelled event to Kafka
docker-compose exec redpanda rpk topic produce order.cancelled <<EOF
{"eventId":"$(uuidgen)","orderId":"$ORDER_ID","reason":"smoke-test cancellation","occurredAt":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}
EOF

# Wait for the consumer to process (~1-2 seconds)
sleep 2

# THEN: Verify DB was updated
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT id, status, updated_at FROM orders WHERE id = '$ORDER_ID';"
```

**Expected:** `status = 'CANCELLED'`, `updated_at` refreshed.

---

### Scenario 6: Verify Cancel via REST

```bash
# WHEN: GET /orders/{id} after cancellation
curl -s http://localhost:8081/orders/$ORDER_ID | python3 -m json.tool
```

**THEN:** `status` should be `"CANCELLED"`.

---

### Scenario 7: Invalid Input — Missing customerId

```bash
# WHEN: POST with missing customerId
curl -s -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"items": [{"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "quantity": 1, "unitPrice": 10.00}]}' \
  | python3 -m json.tool

# THEN: Should return 400 Bad Request
```

---

### Scenario 8: Not Found

```bash
# WHEN: GET non-existent order
curl -s http://localhost:8081/orders/00000000-0000-0000-0000-000000000000 | python3 -m json.tool

# THEN: Should return 404 Not Found
```

---

## Step 6: Database Verification Queries

### Summary Count

```bash
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT
    (SELECT COUNT(*) FROM orders) as total_orders,
    (SELECT COUNT(*) FROM orders WHERE status = 'CREATED') as created,
    (SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED') as cancelled,
    (SELECT COUNT(*) FROM order_items) as total_items;"
```

### Full Order Inspection

```bash
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT o.id, o.customer_id, o.status, o.total, o.created_at,
          i.product_id, i.quantity, i.unit_price
   FROM orders o
   JOIN order_items i ON i.order_id = o.id
   ORDER BY o.created_at DESC
   LIMIT 20;"
```

---

## Step 7: Kafka Topic Verification

```bash
# List topics
docker-compose exec redpanda rpk topic list

# Check offset for order.created (how many messages published)
docker-compose exec redpanda rpk topic describe order.created

# Check offset for order.cancelled (how many messages consumed)
docker-compose exec redpanda rpk topic describe order.cancelled
```

---

## Step 8: Cleanup

### Reset Database (for next test run)

```bash
docker-compose exec postgres psql -U mars -d orders_db -c \
  "TRUNCATE order_items, orders CASCADE;"
```

### Stop Application

```
Press Ctrl+C in the terminal running mvn spring-boot:run
```

### Stop Infrastructure

```bash
# Keep volumes (data persists)
docker-compose down

# Full reset (fresh state)
docker-compose down -v
```

---

## Exploratory Testing Checklist

After each feature implementation, verify:

### Infrastructure
- [ ] PostgreSQL is healthy (`pg_isready`)
- [ ] Redpanda is healthy (`/v1/brokers` returns broker list)
- [ ] Topics `order.created` and `order.cancelled` exist
- [ ] Application starts without errors
- [ ] Health endpoint returns `UP`

### Create Order Flow
- [ ] `POST /orders` returns `201 Created` with `orderId`
- [ ] `orders` table has 1 row with `status = 'CREATED'`
- [ ] `order_items` table has correct rows with right `unit_price`
- [ ] `order.created` topic has 1 message with matching `orderId`
- [ ] Event payload contains `eventId`, `orderId`, `customerId`, `totalAmount`, `occurredAt`

### Get Order Flow
- [ ] `GET /orders/{id}` returns `200 OK`
- [ ] Response `status` matches DB state
- [ ] Response `total` matches DB `total`

### Cancel Order Flow
- [ ] Publish to `order.cancelled` triggers consumer
- [ ] DB `status` updates to `CANCELLED`
- [ ] `updated_at` is refreshed
- [ ] `GET /orders/{id}` reflects `CANCELLED` status

### Error Handling
- [ ] Missing `customerId` returns `400 Bad Request`
- [ ] Empty `items` returns `400 Bad Request`
- [ ] Non-existent `orderId` returns `404 Not Found`

### Dual Write Awareness
- [ ] Confirmed event was published to Kafka (not silently lost)
- [ ] No false assumption that DB + Kafka are in sync atomically

---

## Reporting Results

```
## Exploratory Test Report

**Feature:** [Feature name]
**Service:** order-service (mars-enterprise-kit-lite)
**Date:** [YYYY-MM-DD]
**Tester:** [Name/AI]

### Infrastructure
- PostgreSQL: UP (5432)
- Redpanda: UP (9092, 9644)
- Application: UP (8081)
- Health Check: UP

### Scenarios Executed
| # | Scenario | Expected | Actual | Status |
|---|----------|----------|--------|--------|
| 1 | POST /orders (happy path) | 201 + orderId | 201 + UUID | PASS |
| 2 | GET /orders/{id} | 200 + CREATED status | 200 + CREATED | PASS |
| 3 | DB: orders table | 1 row, status=CREATED | Found | PASS |
| 4 | DB: order_items table | 1 row, correct price | Found | PASS |
| 5 | Kafka: order.created event | Event with orderId | Found | PASS |
| 6 | Cancel via Kafka event | status=CANCELLED in DB | CANCELLED | PASS |
| 7 | GET /orders/{id} after cancel | status=CANCELLED | CANCELLED | PASS |
| 8 | Invalid input | 400 Bad Request | 400 | PASS |
| 9 | Not found | 404 Not Found | 404 | PASS |

### Dual Write Observation
- DB committed: YES
- Kafka event published: YES / NO (expected YES; NO means Dual Write failure)

### Issues Found
- [None | List issues]

### Conclusion
- [PASS: Feature validated end-to-end]
- [FAIL: Issues found — describe and fix before merging]
```
