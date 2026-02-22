---
name: chaos-phantom-event
description: >
  Run the Phantom Event chaos test against Mars Enterprise Kit Lite.
  This skill starts infrastructure, builds and runs the app with the "chaos" Spring profile,
  calls POST /chaos/phantom-event, and validates the Dual Write failure:
  the Kafka event EXISTS but the order does NOT exist in PostgreSQL (DB rolled back by AOP).
  Use this skill to demonstrate the most dangerous Dual Write failure scenario.
argument-hint: "[optional: number of phantom events to generate, default 1]"
---

# Chaos Phantom Event — End-to-End Validation

Demonstrate and validate the **Phantom Event** scenario: an event published to Kafka
for an order that **does not exist** in PostgreSQL.

**Repetitions:** `$ARGUMENTS` (default: 1)

---

## How It Works (Architecture)

```
POST /chaos/phantom-event
    │
    ▼
ChaosController (@Profile("chaos"))
    │
    ▼
ChaosService.attemptPhantomOrder() (@Transactional)
    │
    ▼
ChaosOrderExecutor.execute()
    │                          ┌─────────────────────────────────────┐
    ▼                          │  PhantomEventChaosAspect (@Around)  │
CreateOrderUseCase.execute()   │  Intercepts AFTER execute() returns │
    │                          │  THROWS PhantomEventSimulationException
    ├── orderRepository.save()     ← DB INSERT (not committed yet)
    ├── orderEventPublisher.publish() ← KAFKA SEND (immediate, irreversible)
    └── return orderId
                               │
                               ╳── Exception propagates
                               │
                    @Transactional ROLLS BACK DB
                               │
                               ▼
                    PhantomEventReport {
                        existsInDb: false,      ← DB rolled back
                        eventSentToKafka: true,  ← Kafka has the event
                        dbRolledBack: true
                    }
```

---

## Step 1: Verify Infrastructure

Ensure Docker Compose services are running before starting.

```bash
cd /path/to/mars-enterprise-kit-lite
docker-compose ps
```

**Required services:**

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5432 | Order persistence |
| Redpanda | 9092 | Kafka-compatible broker |
| Redpanda Admin | 9644 | Cluster health API |

If services are not running:

```bash
docker-compose up -d

# Wait for health
docker-compose exec postgres pg_isready -U mars -d orders_db
curl -s http://localhost:9644/v1/brokers | python3 -m json.tool
```

---

## Step 2: Build the Application

```bash
mvn clean install -DskipTests
```

**Expected:** `BUILD SUCCESS`. If it fails, fix before proceeding.

---

## Step 3: Start with Chaos Profile

The chaos endpoint only exists when the `chaos` Spring profile is active.

```bash
cd app
SPRING_PROFILES_ACTIVE=chaos mvn spring-boot:run &
APP_PID=$!

# Wait for startup (up to 30 seconds)
for i in $(seq 1 30); do
    if curl -s http://localhost:8082/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        echo "Application started successfully"
        break
    fi
    sleep 1
done
```

**Verify chaos endpoint is available:**

```bash
# This should NOT return 404 — if it does, the chaos profile is not active
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8082/chaos/phantom-event \
  -H "Content-Type: application/json" \
  -d '{"customerId":"550e8400-e29b-41d4-a716-446655440000","items":[{"productId":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","quantity":1,"unitPrice":1.00}]}'
# Expected: 200 (not 404)
```

---

## Step 4: Record Baseline State

Before creating phantom events, record the current state of DB and Kafka.

```bash
# Count orders in DB
BASELINE_DB_COUNT=$(docker-compose exec -T postgres psql -U mars -d orders_db -t -c \
  "SELECT COUNT(*) FROM orders;" | tr -d ' \n')
echo "Baseline DB order count: $BASELINE_DB_COUNT"

# Count events in Kafka (order.created topic)
BASELINE_KAFKA_COUNT=$(docker-compose exec -T redpanda rpk topic consume order.created \
  --format '%v\n' 2>/dev/null | wc -l | tr -d ' ')
echo "Baseline Kafka event count: $BASELINE_KAFKA_COUNT"
```

---

## Step 5: Execute Phantom Event Scenario

### Single Phantom Event

```bash
PHANTOM_RESPONSE=$(curl -s -X POST http://localhost:8082/chaos/phantom-event \
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

echo "$PHANTOM_RESPONSE" | python3 -m json.tool
```

**Expected response:**

```json
{
    "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "existsInDb": false,
    "eventSentToKafka": true,
    "dbRolledBack": true,
    "explanation": "PHANTOM EVENT: The order.created event was published to Kafka, but the order does NOT exist in PostgreSQL. Any consumer processing this event will reference a non-existent order."
}
```

**Extract orderId for verification:**

```bash
PHANTOM_ORDER_ID=$(echo "$PHANTOM_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['orderId'])")
echo "Phantom Order ID: $PHANTOM_ORDER_ID"
```

### Multiple Phantom Events (if requested)

If `$ARGUMENTS` > 1, run multiple iterations:

```bash
ITERATIONS=${ARGUMENTS:-1}
PHANTOM_IDS=()

for i in $(seq 1 $ITERATIONS); do
    RESPONSE=$(curl -s -X POST http://localhost:8082/chaos/phantom-event \
      -H "Content-Type: application/json" \
      -d "{
        \"customerId\": \"550e8400-e29b-41d4-a716-446655440000\",
        \"items\": [
          {\"productId\": \"6ba7b810-9dad-11d1-80b4-00c04fd430c8\", \"quantity\": $i, \"unitPrice\": 10.00}
        ]
      }")

    ORDER_ID=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['orderId'])")
    EXISTS=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['existsInDb'])")
    PHANTOM_IDS+=("$ORDER_ID")

    echo "Iteration $i: orderId=$ORDER_ID, existsInDb=$EXISTS"
done
```

---

## Step 6: Verify Inconsistency — PostgreSQL

Confirm the phantom order does NOT exist in the database.

```bash
# Check specific phantom order
docker-compose exec -T postgres psql -U mars -d orders_db -c \
  "SELECT id, customer_id, status, total FROM orders WHERE id = '$PHANTOM_ORDER_ID';"
# Expected: (0 rows)

# Count total orders after chaos
AFTER_DB_COUNT=$(docker-compose exec -T postgres psql -U mars -d orders_db -t -c \
  "SELECT COUNT(*) FROM orders;" | tr -d ' \n')
echo "DB order count after chaos: $AFTER_DB_COUNT (was $BASELINE_DB_COUNT)"
```

**Expected:** DB count should be UNCHANGED — the rollback prevented the INSERT.

---

## Step 7: Verify Inconsistency — Kafka

Confirm the phantom event WAS published to Kafka despite the DB rollback.

```bash
# Count events after chaos
AFTER_KAFKA_COUNT=$(docker-compose exec -T redpanda rpk topic consume order.created \
  --format '%v\n' 2>/dev/null | wc -l | tr -d ' ')
echo "Kafka event count after chaos: $AFTER_KAFKA_COUNT (was $BASELINE_KAFKA_COUNT)"

# Consume the latest event and check if it matches the phantom orderId
docker-compose exec -T redpanda rpk topic consume order.created \
  --format '%v\n' --offset end --num 1 2>/dev/null | python3 -m json.tool
```

**Expected:** Kafka count INCREASED by the number of phantom events generated.
The latest event's `orderId` matches `$PHANTOM_ORDER_ID`.

---

## Step 8: The Proof — Side-by-Side Comparison

```bash
echo ""
echo "=========================================="
echo "  DUAL WRITE FAILURE: PHANTOM EVENT PROOF"
echo "=========================================="
echo ""
echo "DB orders before:     $BASELINE_DB_COUNT"
echo "DB orders after:      $AFTER_DB_COUNT"
echo "DB delta:             $(($AFTER_DB_COUNT - $BASELINE_DB_COUNT)) (expected: 0)"
echo ""
echo "Kafka events before:  $BASELINE_KAFKA_COUNT"
echo "Kafka events after:   $AFTER_KAFKA_COUNT"
echo "Kafka delta:          $(($AFTER_KAFKA_COUNT - $BASELINE_KAFKA_COUNT)) (expected: $ITERATIONS)"
echo ""
echo "Phantom Order ID:     $PHANTOM_ORDER_ID"
echo "Exists in DB:         NO (rolled back by AOP)"
echo "Exists in Kafka:      YES (published before rollback)"
echo ""

if [ "$AFTER_DB_COUNT" -eq "$BASELINE_DB_COUNT" ] && [ "$AFTER_KAFKA_COUNT" -gt "$BASELINE_KAFKA_COUNT" ]; then
    echo "RESULT: PHANTOM EVENT CONFIRMED"
    echo ""
    echo "The order.created event in Kafka references order $PHANTOM_ORDER_ID"
    echo "but this order does NOT exist in PostgreSQL."
    echo "Any consumer processing this event will fail or create"
    echo "downstream inconsistencies."
else
    echo "RESULT: UNEXPECTED — check logs for details"
fi

echo ""
echo "=========================================="
```

---

## Step 9: Verify Normal Flow Still Works

After chaos testing, confirm the normal `POST /orders` endpoint is unaffected.

```bash
NORMAL_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "quantity": 1, "unitPrice": 25.00}
    ]
  }')

NORMAL_HTTP_CODE=$(echo "$NORMAL_RESPONSE" | tail -1)
NORMAL_BODY=$(echo "$NORMAL_RESPONSE" | head -1)
NORMAL_ORDER_ID=$(echo "$NORMAL_BODY" | python3 -c "import sys, json; print(json.load(sys.stdin)['orderId'])")

echo "Normal order: HTTP $NORMAL_HTTP_CODE, orderId=$NORMAL_ORDER_ID"

# Verify it EXISTS in DB (unlike the phantom)
docker-compose exec -T postgres psql -U mars -d orders_db -c \
  "SELECT id, status, total FROM orders WHERE id = '$NORMAL_ORDER_ID';"
# Expected: 1 row with status=CREATED
```

**Expected:** Normal orders work fine — the chaos AOP only targets `ChaosOrderExecutor`, not `OrderService`.

---

## Step 10: Cleanup

```bash
# Stop the application
kill $APP_PID 2>/dev/null

# Optional: clean test data
docker-compose exec -T postgres psql -U mars -d orders_db -c \
  "TRUNCATE order_items, orders CASCADE;"

# Optional: reset Kafka topics
docker-compose exec -T redpanda rpk topic delete order.created
docker-compose exec -T redpanda rpk topic delete order.cancelled
```

---

## Chaos Test Report Template

```
## Phantom Event Chaos Test Report

**Scenario:** Phantom Event (AOP forces DB rollback after Kafka publish)
**Date:** [auto-fill]
**Profile:** chaos
**Infrastructure:** PostgreSQL UP, Redpanda UP, App on port 8082

### Timeline
1. Infrastructure verified healthy
2. Application started with SPRING_PROFILES_ACTIVE=chaos
3. Baseline recorded: DB=[count] orders, Kafka=[count] events
4. POST /chaos/phantom-event executed [N] time(s)
5. Inconsistency verified: DB unchanged, Kafka increased

### Results
| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| Chaos endpoint returns 200 | 200 | [code] | [PASS/FAIL] |
| Report: existsInDb | false | [value] | [PASS/FAIL] |
| Report: eventSentToKafka | true | [value] | [PASS/FAIL] |
| Report: dbRolledBack | true | [value] | [PASS/FAIL] |
| DB: phantom order NOT found | 0 rows | [rows] | [PASS/FAIL] |
| Kafka: phantom event found | present | [present/absent] | [PASS/FAIL] |
| DB count unchanged | [baseline] | [after] | [PASS/FAIL] |
| Kafka count increased | +[N] | +[delta] | [PASS/FAIL] |
| Normal POST /orders works | 201 | [code] | [PASS/FAIL] |
| Normal order exists in DB | present | [present/absent] | [PASS/FAIL] |

### Inconsistency Detected?
[YES — Kafka has event for order that does NOT exist in PostgreSQL]

### Root Cause
PhantomEventChaosAspect (@Around) intercepts ChaosOrderExecutor.execute()
and throws PhantomEventSimulationException AFTER orderEventPublisher.publish()
but BEFORE the @Transactional on ChaosService commits. The DB rolls back,
but KafkaTemplate.send() is NOT part of the DB transaction — the event is
already dispatched to the broker.

### What This Proves
1. KafkaTemplate.send() is fire-and-forget from the DB transaction perspective
2. @Transactional only protects PostgreSQL — Kafka is outside the boundary
3. Any failure between publish() and commit creates a phantom event
4. The Transactional Outbox pattern prevents this by writing the event to
   an outbox TABLE (same DB transaction) and relaying to Kafka asynchronously

### Conclusion
[PHANTOM EVENT CONFIRMED — Dual Write failure demonstrated successfully]
```

---

## Troubleshooting

### Endpoint returns 404
The chaos profile is not active. Ensure you started the app with:
```bash
SPRING_PROFILES_ACTIVE=chaos mvn spring-boot:run
```

### Endpoint returns 500
Check application logs for the root cause. Common issues:
- PostgreSQL connection failure (check `docker-compose ps`)
- Kafka connection timeout (check Redpanda health)
- AOP aspect not loaded (verify `spring-aspects` is on classpath)

### DB count increased (phantom order exists!)
The AOP rollback did not work. Possible causes:
- `PhantomEventSimulationException` is not a `RuntimeException` (checked exceptions don't trigger rollback)
- Self-invocation bypassed the `@Transactional` proxy
- The `@Transactional` annotation is not on the correct method

### Kafka count did NOT increase
The event was not published. Possible causes:
- `OrderEventPublisher.publish()` threw an exception before the AOP aspect ran
- Redpanda is down or unreachable
- KafkaTemplate is misconfigured

### Application won't start
```bash
# Check port 8082 is free
lsof -i :8082
# Kill stale process if needed
kill -9 <PID>
```
