---
name: chaos-testing
description: >
  Run chaos tests to demonstrate the Dual Write anti-pattern in Mars Enterprise Kit Lite.
  This skill deliberately breaks infrastructure (stops Redpanda/Kafka) to prove that
  DB and Kafka operations are NOT atomic. It creates orders while Kafka is down, then
  verifies: the order EXISTS in PostgreSQL but the event is LOST in Kafka.
  Use this skill to educate developers on why the Transactional Outbox pattern matters.
argument-hint: "[scenario: lost-event | phantom-event]"
---

# Chaos Testing — Dual Write Failure Demonstration

Demonstrate the real-world consequences of the Dual Write anti-pattern by intentionally
breaking infrastructure and observing inconsistency between PostgreSQL and Kafka.

**Target:** `$ARGUMENTS`

---

## Prerequisite Check

Before running any chaos scenario, verify the environment is healthy:

```bash
# All containers running
docker-compose ps

# PostgreSQL healthy
docker-compose exec postgres pg_isready -U mars -d orders_db

# Redpanda healthy
curl -s http://localhost:9644/v1/brokers | python3 -m json.tool

# Application running on port 8082
curl -s http://localhost:8082/actuator/health
```

If the application is NOT running, start it:

```bash
cd /path/to/mars-enterprise-kit-lite
mvn clean install -DskipTests
cd app && mvn spring-boot:run &
# Wait for startup
sleep 10
curl -s http://localhost:8082/actuator/health
```

---

## Scenario: Lost Event (DB saves, Kafka loses)

**What happens:** The order is persisted in PostgreSQL, but the `order.created` event
never reaches Kafka. Any downstream consumer (analytics, notifications, inventory)
will never know the order exists.

**Root cause:** `CreateOrderUseCaseImpl.execute()` calls `orderRepository.save()` then
`orderEventPublisher.publish()` sequentially. If Kafka is down at publish time, the
exception occurs AFTER the `@Transactional` has already committed the DB write.

### Step 1 — Baseline: Create a healthy order

First, prove the system works correctly when everything is up:

```bash
BASELINE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "quantity": 1, "unitPrice": 50.00}
    ]
  }')

BASELINE_HTTP_CODE=$(echo "$BASELINE_RESPONSE" | tail -1)
BASELINE_BODY=$(echo "$BASELINE_RESPONSE" | head -1)
BASELINE_ORDER_ID=$(echo "$BASELINE_BODY" | python3 -c "import sys, json; print(json.load(sys.stdin)['orderId'])")

echo "Baseline order: $BASELINE_ORDER_ID (HTTP $BASELINE_HTTP_CODE)"
```

**Expected:** `201 Created` with an `orderId`.

Verify the event reached Kafka:

```bash
docker-compose exec redpanda rpk topic consume order.created --num 1 --offset end
```

**Expected:** Event payload with matching `orderId`.

### Step 2 — Kill Kafka (Redpanda)

```bash
docker-compose stop redpanda
```

Verify Kafka is truly down:

```bash
# This should fail or timeout
docker-compose exec redpanda rpk cluster health 2>&1 || echo "Redpanda is DOWN"
```

### Step 3 — Create an order while Kafka is down

```bash
CHAOS_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    "items": [
      {"productId": "11111111-2222-3333-4444-555555555555", "quantity": 3, "unitPrice": 99.99}
    ]
  }')

CHAOS_HTTP_CODE=$(echo "$CHAOS_RESPONSE" | tail -1)
CHAOS_BODY=$(echo "$CHAOS_RESPONSE" | head -1)

echo "Chaos order response: HTTP $CHAOS_HTTP_CODE"
echo "Body: $CHAOS_BODY"
```

**Observe the behavior.** Depending on the Kafka producer configuration:
- The request may **hang** waiting for Kafka (if `max.block.ms` is high)
- The request may **fail with 500** (if the exception propagates)
- The request may **succeed with 201** but the event is silently lost (if send is async/fire-and-forget)

### Step 4 — Verify the inconsistency

**Check PostgreSQL — does the order exist?**

```bash
# Check if order was saved in DB
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT id, customer_id, status, total FROM orders ORDER BY created_at DESC LIMIT 5;"
```

**Check Kafka — was the event published?**

```bash
# Bring Kafka back first
docker-compose start redpanda

# Wait for it to be healthy
sleep 10

# Count total messages in order.created
docker-compose exec redpanda rpk topic consume order.created --format '%v\n' 2>/dev/null | wc -l
```

### Step 5 — Analyze the result

Compare the DB state vs Kafka state:

```bash
# Total orders in DB
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT COUNT(*) as db_orders FROM orders;"

# Total events in Kafka
KAFKA_EVENTS=$(docker-compose exec redpanda rpk topic consume order.created --format '%v\n' 2>/dev/null | wc -l)
echo "Kafka events: $KAFKA_EVENTS"
```

**The inconsistency:**
- If DB has N orders but Kafka has N-1 events → **EVENT LOST** (Dual Write failure proven)
- If DB has N orders and Kafka has N events → the producer handled it gracefully (retry, async, etc.)

### Step 6 — Document the finding

```
## Chaos Test Report — Lost Event

**Scenario:** Lost Event (Kafka down during order creation)
**Date:** [auto-fill]
**Infrastructure:** PostgreSQL UP, Redpanda DOWN (manually stopped)

### Timeline
1. Baseline order created successfully (DB + Kafka both received)
2. Redpanda stopped via `docker-compose stop redpanda`
3. New order creation attempted while Kafka is down
4. Redpanda restarted

### Results
| Check | Expected | Actual |
|-------|----------|--------|
| Baseline order in DB | YES | [YES/NO] |
| Baseline event in Kafka | YES | [YES/NO] |
| Chaos order in DB | DEPENDS | [YES/NO] |
| Chaos event in Kafka | NO | [YES/NO] |
| DB order count | N | [count] |
| Kafka event count | N-1 | [count] |

### Inconsistency Detected?
[YES — order exists in DB but event missing from Kafka]
[NO — the system handled the failure by rolling back / timing out]

### Root Cause
CreateOrderUseCaseImpl calls orderRepository.save() then orderEventPublisher.publish()
inside a @Transactional boundary. However, KafkaTemplate.send() is NOT part of the
DB transaction. If Kafka is unreachable, the DB write may already be committed.

### Fix
Implement the Transactional Outbox pattern: instead of publishing directly to Kafka,
write the event to an `outbox` table within the SAME DB transaction. A separate
process (poller or CDC) reads the outbox and publishes to Kafka, guaranteeing
at-least-once delivery.
```

---

## Cleanup

After chaos testing, restore the system to a healthy state:

```bash
# Ensure Redpanda is running
docker-compose start redpanda
sleep 5

# Verify health
curl -s http://localhost:8082/actuator/health
docker-compose exec redpanda rpk cluster health

# Optional: clean up test data
docker-compose exec postgres psql -U mars -d orders_db -c \
  "TRUNCATE order_items, orders CASCADE;"

# Optional: reset Kafka topics
docker-compose exec redpanda rpk topic delete order.created
docker-compose exec redpanda rpk topic delete order.cancelled
# Topics will be auto-created on next publish
```

---

## Key Takeaways

After running this chaos test, the developer should understand:

1. **Dual Write is fragile** — any infrastructure failure between the two writes creates inconsistency
2. **@Transactional only protects the DB** — Kafka operations are outside the transaction boundary
3. **The failure window is real** — it's not theoretical; even a brief Kafka outage causes data loss
4. **The fix is the Transactional Outbox** — write events to an outbox table in the same DB transaction, then relay to Kafka asynchronously
5. **Event-driven systems need delivery guarantees** — fire-and-forget is never acceptable for business events
