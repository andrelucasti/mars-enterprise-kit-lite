# Mars Enterprise Kit Lite

Mars Enterprise Kit Lite is a free, open-source **Order microservice** built with correct **Onion Architecture**, asynchronous communication via **Kafka (Redpanda)**, and persistence in **PostgreSQL** — but **without the Transactional Outbox Pattern**.

The absence of the Outbox is intentional: it exposes the Dual Write consistency problem in a real codebase, so you can understand it before solving it. This project is also an **AI-First lab** — it is designed from the ground up to be operated by Claude Code. The README is a prompt disguised as documentation.

**Target audience:**
- Software Engineers learning Onion Architecture with a real, functional example.
- Developers exploring AI-First development with Claude Code, Cursor, or Codex.
- Architects who want a well-structured Spring Boot template to extend.

## Tech Stack

[![Java][java-shield]][java-url]
[![Spring Boot][springboot-shield]][springboot-url]
[![Maven][maven-shield]][maven-url]
[![PostgreSQL][postgres-shield]][postgres-url]
[![Redpanda][redpanda-shield]][redpanda-url]
[![Docker][docker-shield]][docker-url]

## High Level Architecture Overview

The diagram below shows the internal structure of the microservice — a single Maven module with package-based Onion Architecture layers, enforced by ArchUnit tests.

![Architecture Overview](mars-enterprise-kit-lite.drawio.png)

| Package | Description |
|---------|-------------|
| **domain/** | Domain and application layer. NO JPA, NO Kafka, NO Spring Web. Contains `Order`, ports (interfaces `OrderRepository`, `OrderEventPublisher`), `@Service` use cases, and domain events. ArchUnit enforces that nothing here depends on infrastructure or api. |
| **infrastructure/** | Infrastructure layer. Implements domain ports (JPA adapter, Kafka publisher/consumer). Manages Flyway migrations. Depends on domain/ only. |
| **api/** | HTTP entry point. Exposes REST endpoints, maps DTOs to domain objects. `OrderController` injects `CreateOrderUseCase` + `OrderRepository` directly — no service wrapper. Depends on domain/ ports only. |

## Infrastructure Architecture — Cloud-Native Lab

The **application** is intentionally Lite (single microservice, Dual Write anti-pattern, no Outbox). The **infrastructure around it**, however, is a full cloud-native lab demonstrating enterprise patterns: API gateway, identity provider, service mesh, observability stack, and zero-trust networking — all declarative, all versioned under `infraestrucuture/`.

> Goal: let you experiment with the operational patterns of a real enterprise platform without needing a real enterprise to do it in.

### Cluster topology

```mermaid
flowchart TB
    User([User / Bruno via Tailscale])

    subgraph TS["Tailscale tailnet (zero-trust edge)"]
        TSGW["Kong proxy: kong-gateway.ts.net<br/>Keycloak: keycloak-lab.ts.net<br/>Grafana: grafana-lab.ts.net<br/>Kiali: kiali.ts.net"]
    end

    User --> TSGW

    subgraph K8S["Kubernetes cluster (k3s, 4 nodes)"]
        direction TB

        subgraph KONG["namespace: kong"]
            KongDP["Kong DataPlane<br/>(Gateway Operator)<br/>OpenResty/nginx"]
            KongMgr["Kong Manager UI"]
        end

        subgraph ISTIO["namespace: istio-system"]
            Istiod["istiod control plane"]
            Kiali["Kiali"]
        end

        subgraph KC["namespace: keycloak"]
            Keycloak["Keycloak<br/>realm: mars"]
        end

        subgraph MARS["namespace: mars (istio-injection=enabled)"]
            App["mars-enterprise-kit-lite<br/>+ istio-proxy sidecar"]
            PG[(PostgreSQL)]
            Kafka["Redpanda (Kafka)"]
        end

        subgraph MON["namespace: monitoring-lab"]
            Prom["Prometheus<br/>(kube-prometheus-stack)"]
            Graf["Grafana"]
            AM["Alertmanager"]
        end

        TSGW -->|north-south| KongDP
        KongDP -->|HTTP/HTTPS| App
        KongDP -->|OIDC introspect| Keycloak
        App -->|JDBC| PG
        App -->|produce/consume| Kafka

        Prom -.->|scrape /metrics| KongDP
        Prom -.->|scrape /stats/prometheus| App
        Prom -.->|scrape :15014| Istiod
        Prom -.->|scrape /actuator/prometheus| App
        Kiali -->|PromQL| Prom
        Graf -->|PromQL| Prom

        Istiod -.->|inject sidecar + push xDS| App
    end

    classDef edge fill:#1f6feb,color:#fff,stroke:#0b3d91
    classDef gw fill:#22863a,color:#fff,stroke:#0e4d1f
    classDef mesh fill:#6f42c1,color:#fff,stroke:#3d1d80
    classDef idp fill:#d29922,color:#000,stroke:#7a5800
    classDef app fill:#cf222e,color:#fff,stroke:#7a131c
    classDef obs fill:#0969da,color:#fff,stroke:#053b85
    class TSGW edge
    class KongDP,KongMgr gw
    class Istiod,Kiali mesh
    class Keycloak idp
    class App,PG,Kafka app
    class Prom,Graf,AM obs
```

### Components

| Component | Namespace | Role | Manifests |
|---|---|---|---|
| **Kubernetes (k3s)** | — | Multi-node lab cluster (1 master + 3 workers) | — |
| **Kong Gateway** | `kong` | North-south API gateway (auth, rate-limit, CORS, observability) | `infraestrucuture/kong/operator/` |
| **Keycloak** | `keycloak` | Identity provider; realm `mars` issues bearer tokens for OIDC | `infraestrucuture/keycloak/` |
| **Istio** | `istio-system` | East-west service mesh (mTLS, traffic policy, telemetry to Kiali) | `infraestrucuture/istio/` |
| **Kiali** | `istio-system` | Mesh topology + traffic graph UI | `infraestrucuture/istio/02-kiali-tailscale-svc.yaml` |
| **kube-prometheus-stack** | `monitoring-lab` | Prometheus Operator + Prometheus + Grafana + Alertmanager | `infraestrucuture/monitoring/kube-prometheus-stack-values.yaml` |
| **Tailscale** | `tailscale` | Zero-trust edge — `LoadBalancer` services exposed only inside the tailnet | `*-tailscale-svc.yaml` files |
| **App** | `mars` | The Order microservice itself, deployed via Helm chart | `helm/mars-enterprise-kit-lite/` |

### North–South vs East–West

The boundary is intentional: **Kong on the edge, Istio inside**. They do not overlap.

```mermaid
flowchart LR
    Ext([External client]) -->|"1. HTTPS via Tailscale"| Kong
    Kong -->|"2. OIDC introspect (Bearer)"| KC[Keycloak]
    Kong -->|"3. plain HTTP to ClusterIP<br/>(north→south boundary)"| Sidecar

    subgraph mars["mars namespace (mesh)"]
        Sidecar["istio-proxy sidecar"] -->|"4. localhost → 8082"| App[Spring Boot app]
        App -->|"5. east-west, mTLS via mesh"| Sidecar2["istio-proxy"]
        Sidecar2 --> Other[other mesh service]
    end

    style Kong fill:#22863a,color:#fff
    style KC fill:#d29922,color:#000
    style Sidecar fill:#6f42c1,color:#fff
    style Sidecar2 fill:#6f42c1,color:#fff
    style App fill:#cf222e,color:#fff
    style Other fill:#cf222e,color:#fff
```

| Direction | Tech | Why |
|---|---|---|
| **North-south** (client → cluster) | Kong Gateway | Plugin ecosystem (OIDC, rate-limit, CORS, ACL, key-auth), TLS termination, single entry point for external traffic |
| **East-west** (service → service inside cluster) | Istio mesh | Automatic mTLS, fine-grained `AuthorizationPolicy`, retries/circuit-break per service, telemetry to Kiali |

**Why Kong is NOT inside the mesh:** Kong is already a proxy (OpenResty/nginx). Adding `istio-proxy` sidecar would create **two user-space proxies on the same hop** — doubling latency and CPU while making two control planes fight over TLS, retries and observability. Standard pattern: **the mesh begins after the gateway**.

### Kong Plugins

All plugins are versioned as Kubernetes CRDs under `infraestrucuture/kong/plugins/` (cluster-wide) and `infraestrucuture/kong/oidc/` (per-route).

| Plugin | Scope | Manifest | Purpose |
|---|---|---|---|
| `prometheus` | global (KongClusterPlugin) | `kong/plugins/prometheus.yaml` | Exposes `kong_http_requests_total`, `kong_request_latency_ms`, `kong_bandwidth_bytes`, `kong_upstream_target_health` on `:8100/metrics`. Kong 3.x requires per-category flags (`status_code_metrics`, `latency_metrics`, etc.) — all enabled in our config |
| `oidc` | route `orders` (KongPlugin) | `kong/oidc/01-kongplugin-oidc-bearer.yaml` | Validates `Authorization: Bearer <jwt>` against Keycloak realm `mars` via token introspection. `bearer_only: yes` — stateless, no session cookie |
| `cors` | route `orders` (KongPlugin) | `kong/plugins/cors.yaml` | Per-route CORS with `credentials: true` and explicit origin list (no wildcard) |

**Upstream health** — `KongUpstreamPolicy` (`infraestrucuture/kong/mars/upstream-policy.yaml`) attached to the Service via `konghq.com/upstream-policy` annotation:
- **Active probe**: GET `/actuator/health` every 10s; 3 failures → mark unhealthy
- **Passive circuit-breaker**: 5 consecutive 5xx/timeouts → eject target
- Status `healthy` reported in Grafana dashboard 7424

### Observability stack

```mermaid
flowchart LR
    subgraph SRC["Metric sources"]
        AppMetrics["App<br/>/actuator/prometheus"]
        Sidecar["istio-proxy<br/>:15020/stats/prometheus"]
        Istiod[":15014/metrics"]
        KongMet["Kong dataplane<br/>:8100/metrics"]
        KSM["kube-state-metrics"]
        Node["node-exporter"]
    end

    subgraph SCRAPE["Prometheus Operator (monitoring-lab)"]
        ServMon["ServiceMonitor /<br/>PodMonitor CRDs"]
        Prom[("Prometheus<br/>TSDB, 7d retention")]
        ServMon --> Prom
    end

    subgraph UI["Visualization"]
        Grafana
        Kiali
    end

    AppMetrics --> ServMon
    Sidecar --> ServMon
    Istiod --> ServMon
    KongMet --> ServMon
    KSM --> ServMon
    Node --> ServMon

    Prom --> Grafana
    Prom --> Kiali

    Alert["Alertmanager"]
    Prom --> Alert
```

| Source | Scraped via | Dashboard suggestion |
|---|---|---|
| **Spring Boot app** (`/actuator/prometheus`) | `ServiceMonitor` rendered by Helm chart (`serviceMonitor.enabled=true`) | JVM Micrometer, Spring HTTP, Kafka client |
| **Istio sidecars** (`:15020/stats/prometheus`) | `PodMonitor envoy-stats` in `istio-system` | Istio Mesh Dashboard (7636), Kiali |
| **Istio control plane** (`istiod :15014`) | `ServiceMonitor istiod` in `istio-system` | Istio Control Plane Dashboard |
| **Kong dataplane** (`:8100/metrics`) | `PodMonitor kong-dataplane` in `kong` | Kong Official Dashboard (7424) |
| **Kubernetes** (kube-state-metrics, node-exporter) | Bundled in kube-prometheus-stack | Kubernetes Cluster Dashboards |

**Prometheus discovery is open:** all monitor selectors are `{}` (match-all) so any `ServiceMonitor`/`PodMonitor` in any namespace is picked up. The `release: kps` label is added by convention, not enforcement.

### External access via Tailscale

Instead of `Ingress` + `cert-manager` + DNS for a lab, every UI is exposed as a `LoadBalancer` with `loadBalancerClass: tailscale`. The Tailscale operator provisions a per-service hostname inside the tailnet:

| Service | Hostname pattern | File |
|---|---|---|
| Kong proxy | `kong-gateway.<tailnet>.ts.net` | `kong/operator/` |
| Keycloak | `keycloak-lab.<tailnet>.ts.net` | `keycloak/keycloak-tailscale-svc.yaml` |
| Grafana | `grafana-lab.<tailnet>.ts.net` | `grafana-tailscale-svc.yaml` |
| Kiali | `kiali.<tailnet>.ts.net` | `istio/02-kiali-tailscale-svc.yaml` |
| Kong Manager | `kong-manager.<tailnet>.ts.net` | `kong/operator/kong-manager-svc.yaml` |

Only devices logged into the tailnet reach them — no public exposure, no public DNS, no certificates to manage.

### Trade-offs and known caveats

- **Anti-pattern preserved:** the **app** still does Dual Write (DB → Kafka with no atomicity). The surrounding infra does not fix this — by design. The Transactional Outbox is the Pro version.
- **`adminPassword: admin`** for Grafana, and OIDC `client_secret` hardcoded in `kong/oidc/`: lab-only. For production: Kong Vault (`{vault://env/...}`) or `configFrom.secretKeyRef`.
- **No HPA, no PodDisruptionBudget, no NetworkPolicy** beyond the Kong Manager one. Lab simplicity.
- **Prometheus storage is `emptyDir`** with 7-day retention — metrics evaporate on pod restart. Acceptable for a lab.
- **Istio mesh has a single workload** (`mars`). The mesh is overkill for one service, but the wiring is realistic so you can add more services later.

---

## Prerequisites

Local execution requirements:

* [![Java][java-shield]][java-url] — Java 25 (LTS)
* [![Maven][maven-shield]][maven-url] — Maven 3.9+
* [![Docker][docker-shield]][docker-url] — Docker + Docker Compose

## Quick Start

```bash
# 1. Start infrastructure (PostgreSQL + Redpanda)
docker-compose up -d

# 2. Build the project
mvn clean install

# 3. Run the application (port 8082)
mvn spring-boot:run
```

The application starts at **http://localhost:8082**.

Flyway runs migrations automatically on startup.

---

## AI-First Development

This project is designed to be operated by an AI assistant. The infrastructure, context files, and custom commands exist so that Claude Code (or any capable AI agent) can spin up the environment, run the smoke test, and implement new features — with no manual intervention required.

### Philosophy

> The README is a prompt. The context files are the memory. The commands are the interface.

The files under `.mars/docs/` and `CLAUDE.md` form the AI's knowledge base: architecture rules, domain model, Onion Architecture law, Dual Write constraints, TDD workflow. When Claude Code reads those files before acting, it avoids architectural mistakes and follows the project's conventions without needing to ask.

### AI Context Files

| File | Purpose |
|------|---------|
| `CLAUDE.md` | Primary instruction file: architecture rules, conventions, TDD workflow, what to do and what never to do |
| `.mars/docs/mars-enterprise-kit-context-lite.md` | Full project context: domain model, Kafka topics, event payloads, ADRs |
| `.mars/PRPs/templates/prp_base.md` | Template used by `/generate-prp` to structure feature implementation prompts |

### Custom Claude Code Commands

Two slash commands are available in this project. Run them from inside Claude Code:

#### `/generate-prp <feature-description>`

Generates a **PRP (Product Requirements Prompt)** for a new feature. A PRP is a structured, self-contained prompt designed to give an AI agent all the context it needs to implement a feature correctly in one pass — without hallucinating architecture, violating module boundaries, or skipping TDD.

**What it does:**
1. Reads `CLAUDE.md` and `.mars/docs/` to understand the codebase
2. Searches for existing patterns to reference (entities, use cases, DTOs)
3. Researches external docs if needed (Spring Boot, JPA)
4. Writes a complete PRP to `.mars/PRPs/{feature-name}.md` — including module structure, implementation blueprint, validation gates, and a confidence score

**Example:**
```
/generate-prp add GET /orders endpoint with pagination
```

**Output:** `.mars/PRPs/add-pagination-to-orders.md` — ready for execution.

---

#### `/execute-prp <path-to-prp>`

Executes a PRP file. The agent reads the PRP, plans the implementation layer by layer (Domain → Infrastructure → API), and writes code following strict TDD: tests first, then implementation, then refactor.

**What it does:**
1. Loads the PRP and re-reads `CLAUDE.md` for conventions
2. Thinks hard about the full implementation plan (ULTRATHINK phase)
3. Executes layer by layer, inside out:
   - **Phase 1** — `business/`: domain objects, ports, use cases (pure Java, no frameworks)
   - **Phase 2** — `data-provider/`: JPA entities, adapters, Flyway migrations
   - **Phase 3** — `app/`: services, controllers, DTOs, Kafka wiring
4. Runs the validation gates from the PRP (`mvn test`, `mvn verify`)
5. Reports completion or surfaces failing tests to fix

**Example:**
```
/execute-prp .mars/PRPs/add-pagination-to-orders.md
```

---

### Smoke Test (AI-Orchestrated)

Ask Claude Code to run the smoke test and it will execute the full flow autonomously:

```
Run the smoke test for this project
```

Claude Code will:
1. Start Docker Compose (PostgreSQL + Redpanda)
2. Verify Kafka topics exist
3. Create an order via `POST /orders` and validate the response
4. Consume the `order.created` event and verify the payload
5. Publish an `order.cancelled` event to Kafka
6. Query the order via `GET /orders/{id}` and verify `status == "CANCELLED"`
7. Tear down Docker Compose
8. Report: `✅ PASS` or `❌ FAIL` with logs

---

### Chaos Testing Skills

Beyond PRPs and smoke tests, the project includes **chaos testing skills** that Claude Code executes autonomously to demonstrate Dual Write failures in real time. See [Chaos Testing](#chaos-testing--proving-the-dual-write-problem) for the full details.

| Skill | Scenario | What it does |
|-------|----------|--------------|
| `chaos-phantom-event` | Phantom Event | Starts the app with profile `chaos`, calls `POST /chaos/phantom-event`, verifies the event exists in Kafka but the order does NOT exist in PostgreSQL |
| `chaos-testing` | Lost Event | Stops Redpanda, creates an order, verifies the order exists in PostgreSQL but the event is LOST in Kafka |

**Run them from Claude Code:**

```
Run the chaos-phantom-event skill
Run the chaos-testing skill with scenario lost-event
```

---

### Using with Cursor, Codex, or Other AI Assistants

`CLAUDE.md` is the instruction file for Claude Code. Other AI tools use different file names for the same concept:

| Tool | Instruction file |
|------|-----------------|
| Claude Code | `CLAUDE.md` |
| Cursor | `AGENTS.md` (or `.cursorrules`) |
| OpenAI Codex | `AGENTS.md` |
| GitHub Copilot Workspace | `AGENTS.md` |

**To use this project with Cursor or Codex**, copy or rename `CLAUDE.md` to `AGENTS.md` at the project root. The content works as-is — it describes the architecture, conventions, and rules in plain language that any AI agent can follow.

```bash
cp CLAUDE.md AGENTS.md
```

The `.mars/docs/` context files and `.mars/PRPs/` are tool-agnostic and work with any agent that can read files.

---

## API Endpoints

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/orders` | Create a new order | `201 Created` + `{ "orderId": "uuid" }` |
| `GET` | `/orders/{id}` | Get order by ID | `200 OK` + order payload |
| `GET` | `/actuator/health` | Health check | `200 OK` |
| `POST` | `/chaos/phantom-event` | Simulate phantom event (chaos testing) | `200 OK` + report |

No authentication. No rate limiting. Endpoints are open by design — this is an educational project.

> **Note:** The chaos endpoint requires starting the app with `SPRING_PROFILES_ACTIVE=chaos`. See [Chaos Testing](#chaos-testing--proving-the-dual-write-problem) below.

### POST /orders — Request Body

```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "quantity": 2,
      "unitPrice": 149.95
    }
  ]
}
```

### GET /orders/{id} — Response Body

```json
{
  "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "total": 299.9,
  "items": [
    { "productId": "...", "quantity": 2, "unitPrice": 149.95 }
  ],
  "createdAt": "2026-02-21T10:00:00Z",
  "updatedAt": "2026-02-21T10:00:00Z"
}
```

## Communication Overview

The Order microservice communicates with external systems via Kafka events (Redpanda, JSON format). No Schema Registry. No Avro.

### Topics Published

Events that the order-service **produces**:

| Event | Topic | Description |
|-------|-------|-------------|
| Order Created | `order.created` | Published after a new order is persisted in PostgreSQL. ⚠️ Dual Write — no atomic guarantee. |

### Queues Consumed

Events that the order-service **consumes**:

| Event | Topic | Description | Type |
|-------|-------|-------------|------|
| Order Cancelled | `order.cancelled` | Triggers cancellation of an existing order, updating status to `CANCELLED` in PostgreSQL. | PERSISTENT |

### Event Payloads

**`order.created` — Published payload (JSON):**
```json
{
  "eventId": "uuid-v4",
  "orderId": "uuid-v4",
  "customerId": "uuid-v4",
  "totalAmount": 299.90,
  "items": [
    { "productId": "uuid-v4", "quantity": 2, "unitPrice": 149.95 }
  ],
  "occurredAt": "2026-02-21T10:00:00Z"
}
```

**`order.cancelled` — Consumed payload (JSON):**
```json
{
  "eventId": "uuid-v4",
  "orderId": "uuid-v4",
  "reason": "Customer requested cancellation",
  "occurredAt": "2026-02-21T10:05:00Z"
}
```

## UML Diagrams — Main Flows

### Flow 1: Create Order (Publisher)

```mermaid
sequenceDiagram
    participant Client as Client
    participant API as OrderController
    participant UC as "CreateOrderUseCase (@Transactional)"
    participant DB as PostgreSQL
    participant K as "Kafka (order.created)"

    Client->>API: POST /orders { customerId, items }
    API->>UC: execute(input)
    UC->>DB: INSERT orders + order_items
    UC->>K: send("order.created")
    Note over DB,K: ⚠️ Dual Write — if Kafka fails, catch swallows the exception
    Note over DB,K: DB stays committed. HTTP 201 returned. Event silently lost.
    UC-->>API: orderId
    API-->>Client: 201 Created { orderId }
```

### Flow 2: Cancel Order (Consumer)

```mermaid
sequenceDiagram
    participant EXT as External System
    participant K as "Kafka (order.cancelled)"
    participant C as OrderCancelledConsumer
    participant UC as CancelOrderUseCase
    participant DB as PostgreSQL

    EXT->>K: publish { orderId, reason }
    K->>C: consume event
    C->>UC: execute(CancelOrderCommand)
    UC->>DB: UPDATE orders SET status = 'CANCELLED'
    Note over UC,DB: Order is updated atomically in PostgreSQL
```

### Flow 3: AI-First Smoke Test (Claude Code Orchestrated)

```mermaid
sequenceDiagram
    participant CC as Claude Code
    participant DC as Docker Compose
    participant API as "order-service (8082)"
    participant K as "Redpanda (Kafka)"
    participant DB as PostgreSQL

    CC->>DC: docker compose up -d
    DC-->>CC: PostgreSQL + Redpanda healthy
    CC->>K: Verify topics order.created + order.cancelled exist
    CC->>API: POST /orders { customerId, items }
    API-->>CC: 201 Created { orderId }
    CC->>K: Consume order.created - validate orderId matches
    CC->>K: Publish order.cancelled { orderId, reason: "smoke-test" }
    CC->>API: GET /orders/{orderId}
    API-->>CC: 200 OK { status: "CANCELLED" }
    CC->>DC: docker compose down
    CC-->>CC: PASS or FAIL with logs
```

## The Dual Write Anti-Pattern (Intentional)

This is the most important section of this README.

Distributed systems that need to **persist data AND publish events** face a fundamental challenge: there is no native atomicity between a relational database and a message broker.

The naive — and most common — solution is **Dual Write**: save to the database, then publish to Kafka inside a `@Transactional`. Spring manages the DB rollback on failure, but **does not undo an already-published Kafka event**. The reverse is also true: if Kafka goes down after the DB commit, the event is silently lost.

![Happy Path](dual-write-problem-happy-path.drawio.png)

This project makes the failure mode explicit — here is the actual code running in `CreateOrderUseCase`:

```java
@Transactional
public UUID execute(final Input input) {
    var result = Order.create(input.customerId(), input.items());
    orderRepository.save(result.domain());   // ← DB commit guaranteed

    try {
        orderEventPublisher.publish(result.event());  // ← Kafka, outside transaction
    } catch (Exception e) {
        // ⚠️ Exception swallowed. DB already committed.
        // HTTP 201 will be returned. Event is silently lost.
        log.warn("DUAL WRITE FAILURE — EVENT LOST for orderId={}", result.domain().id());
    }

    return result.domain().id();
}
```

This pattern is extremely common in production codebases. It looks defensive — it is actually **hiding a data consistency failure**. The DB committed, the client received 201, and downstream consumers never got the event. No error. No alert. Silent inconsistency.

**This is exactly the scenario this project reproduces — by design.**

In production with high concurrency, this inconsistency window is enough to generate lost events at scale — orders saved in the database, invisible to downstream services. The Transactional Outbox Pattern solves this. The Lite version exposes the problem so that you feel it.

## Chaos Testing — Proving the Dual Write Problem

The Lite version doesn't just describe the Dual Write problem — it **proves** it with two reproducible chaos scenarios. Each scenario demonstrates a different failure mode, and each has a dedicated Claude Code skill that orchestrates the test end-to-end.

### Scenario 1: Phantom Event (Ghost Event in Kafka)

**The problem:** A Kafka event references an order that **does not exist** in PostgreSQL. Any consumer processing this event will fail or create downstream inconsistencies.

**How to reproduce:** The project includes a built-in chaos endpoint (`POST /chaos/phantom-event`) that uses an AOP interceptor to force a DB rollback after the Kafka event has already been published.

```bash
# Start the app with the chaos profile
SPRING_PROFILES_ACTIVE=chaos mvn spring-boot:run

# Trigger the phantom event
curl -s -X POST http://localhost:8082/chaos/phantom-event \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {"productId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "quantity": 2, "unitPrice": 149.95}
    ]
  }'
```

**Response:**

```json
{
  "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "existsInDb": false,
  "eventSentToKafka": true,
  "dbRolledBack": true,
  "explanation": "PHANTOM EVENT: The order.created event was published to Kafka, but the order does NOT exist in PostgreSQL. Any consumer processing this event will reference a non-existent order."
}
```

**Verification:**

```bash
# Order does NOT exist in PostgreSQL (rolled back)
docker-compose exec postgres psql -U mars -d orders_db -c \
  "SELECT * FROM orders WHERE id = '<orderId>';"
# → (0 rows)

# Event DOES exist in Kafka
docker-compose exec redpanda rpk topic consume order.created --num 1 --offset end
# → Event payload with the phantom orderId
```

```mermaid
sequenceDiagram
    participant Client as Client
    participant CC as ChaosController
    participant SVC as "ChaosService (@Transactional)"
    participant AOP as "PhantomEventChaosAspect (@Around)"
    participant UC as CreateOrderUseCase
    participant DB as PostgreSQL
    participant K as "Kafka (order.created)"

    Client->>CC: POST /chaos/phantom-event
    CC->>SVC: attemptPhantomOrder(input)
    Note over SVC: @Transactional BEGINS
    SVC->>AOP: execute(input)
    AOP->>UC: proceed() → execute(input)
    UC->>DB: INSERT orders + order_items
    UC->>K: send("order.created")
    Note over K: Event sent immediately (irreversible)
    UC-->>AOP: return orderId
    AOP--xSVC: throw PhantomEventSimulationException
    Note over SVC: @Transactional ROLLBACK
    Note over DB: INSERT rolled back (order gone)
    SVC--xCC: exception propagates
    CC->>SVC: orderExists(orderId)
    SVC->>DB: SELECT ... WHERE id = orderId
    DB-->>SVC: empty (not found)
    CC-->>Client: 200 OK { existsInDb: false, eventSentToKafka: true }
```

![Failure Case 2 - Phantom Event](dual-write-problem-failure-case-2.drawio.png)

> **How it works internally:** `PhantomEventChaosAspect` is an AOP `@Around` advice that intercepts `ChaosOrderExecutor.execute()`. It lets the use case run completely (DB INSERT + Kafka publish), then throws a `PhantomEventSimulationException`. Since the exception occurs inside the `@Transactional` boundary, Spring rolls back the DB — but `KafkaTemplate.send()` already dispatched the event. All chaos beans use `@Profile("chaos")` and don't exist in the default profile.

---

### Scenario 2: Lost Event (Kafka Down)

**The problem:** An order is persisted in PostgreSQL, but the corresponding Kafka event is **never published**. Downstream consumers never learn the order was created.

**How to reproduce:** Stop Redpanda before creating an order. The `try-catch` in `CreateOrderUseCase` swallows the Kafka exception — the DB commit succeeds, and the client receives **HTTP 201** with no indication that the event was lost.

```bash
# 1. Create a baseline order (everything healthy)
curl -s -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"550e8400-e29b-41d4-a716-446655440000","items":[{"productId":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","quantity":1,"unitPrice":50.00}]}'
# → 201 Created — order in DB, event in Kafka ✅

# 2. Kill Kafka
docker-compose stop redpanda

# 3. Create another order — Kafka is down
curl -s -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","items":[{"productId":"11111111-2222-3333-4444-555555555555","quantity":1,"unitPrice":99.99}]}'
# → 201 Created — event silently lost ⚠️

# Check the application log — only a WARN, not an error:
# WARN DUAL WRITE FAILURE — EVENT LOST for orderId=<uuid>. Order saved in DB but event NOT published to Kafka.

# 4. Bring Kafka back
docker-compose start redpanda
sleep 10

# 5. Compare: DB has 2 orders, Kafka has only 1 event
docker-compose exec postgres psql -U mars -d orders_db -c "SELECT COUNT(*) FROM orders;"
docker-compose exec redpanda rpk topic consume order.created --format '%v\n' | wc -l
```

![Failure Case 1 - Lost Event](dual-write-problem-failure-case-1.drawio.png)

---

### Side-by-Side: Two Faces of the Same Problem

Both scenarios are caused by the same root issue: **no atomicity between PostgreSQL and Kafka**.

| | Scenario 1: Phantom Event | Scenario 2: Lost Event |
|---|---|---|
| **Trigger** | AOP forces DB rollback after publish | Kafka is down, `catch` swallows exception |
| **HTTP Response** | 500 (DB rolled back by AOP) | **201** — client sees success, event is gone |
| **PostgreSQL** | Order does NOT exist (rolled back) | Order EXISTS (committed) |
| **Kafka** | Event EXISTS (already sent) | Event does NOT exist (publish failed) |
| **Impact** | Consumers process a non-existent order | Consumers never learn the order was created |
| **Reproduction** | `POST /chaos/phantom-event` (requires `chaos` profile) | `docker-compose stop redpanda` + `POST /orders` |
| **Claude Code Skill** | `chaos-phantom-event` | `chaos-testing` |
| **Fix** | Transactional Outbox Pattern | Transactional Outbox Pattern |

> Both failures are silent in production. There are no errors in the logs, no alerts, no retries. The system continues operating with inconsistent state between the database and the message broker. **The Transactional Outbox Pattern prevents both scenarios** by writing events to an outbox table within the same DB transaction, then relaying to Kafka asynchronously with at-least-once delivery.

## Monitoring

| Dashboard | URL |
|-----------|-----|
| Redpanda Console | http://localhost:8080 |
| Spring Boot Actuator | http://localhost:8082/actuator/health |
| PostgreSQL | `docker exec -it <postgres-container> psql -U mars -d orders_db` |

## Troubleshooting

```bash
# View service logs
docker-compose logs -f postgres
docker-compose logs -f redpanda

# Restart everything (clean slate)
docker-compose down -v && docker-compose up -d --force-recreate

# Port already in use (app port is 8082, NOT 8081 — Redpanda uses 8081)
lsof -i :8082 && kill -9 <PID>

# Force rebuild Maven dependencies
mvn clean install -U

# Flyway migration issues
mvn flyway:info
mvn flyway:repair
```

## Running Tests

```bash
mvn test                                          # All tests (50 tests total)
mvn test -Dtest=ArchitectureTest                  # Architecture rules (12 tests, fast)
mvn test -Dtest=CreateOrderUseCaseTest            # Single test class
mvn test -Dtest=CreateOrderUseCaseTest#shouldSaveOrderAndPublishEvent  # Single method
mvn clean verify                                  # Full build + all tests
```

> TestContainers requires Docker running locally.

## Project Structure

```
mars-enterprise-kit-lite/
├── .mars/
│   ├── docs/
│   │   └── mars-enterprise-kit-context-lite.md   # Full project context (AI reads this)
│   └── PRPs/
│       ├── templates/
│       │   └── prp_base.md                       # PRP template for /generate-prp
│       └── {feature-name}.md                     # Generated PRPs live here
├── .claude/
│   ├── commands/
│   │   ├── generate-prp.md                       # /generate-prp command definition
│   │   └── execute-prp.md                        # /execute-prp command definition
│   └── skills/
│       ├── chaos-phantom-event/                   # Skill: phantom event chaos test
│       ├── chaos-testing/                         # Skill: lost event chaos test (infra-based)
│       ├── exploratory-testing/                   # Skill: end-to-end smoke test
│       └── git-worktree-prp/                      # Skill: worktree lifecycle for PRPs
├── src/
│   ├── main/
│   │   ├── java/io/mars/lite/
│   │   │   ├── Application.java
│   │   │   ├── domain/                           # Domain layer (no JPA/Kafka/Web)
│   │   │   │   └── usecase/                      # @Service use cases
│   │   │   ├── infrastructure/                   # Adapters (JPA, Kafka, config)
│   │   │   │   ├── persistence/
│   │   │   │   ├── messaging/
│   │   │   │   └── configuration/
│   │   │   └── api/                              # HTTP endpoints + chaos testing
│   │   │       └── chaos/                        # @Profile("chaos") only
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── db/migration/V1__create_orders_table.sql
│   └── test/
│       └── java/io/mars/lite/
│           ├── ArchitectureTest.java             # 12 ArchUnit rules
│           ├── AbstractIntegrationTest.java
│           ├── domain/ · infrastructure/ · api/  # Tests by layer
├── docker-compose.yml                            # PostgreSQL + Redpanda
├── pom.xml                                       # Single POM (packaging=jar)
├── CLAUDE.md                                     # AI instruction file (copy → AGENTS.md for Cursor/Codex)
└── README.md                                     # This file
```

---

## ⚠️ Ready for Production?

This project intentionally demonstrates the **Dual Write problem**.
In production, you need atomic guarantees between your database and Kafka.

When Kafka goes down after the DB commit, your event is silently lost. When the Kafka publish succeeds but the DB rolls back, you have a ghost event with no matching record. The Lite version exposes this so you feel the problem before you need to solve it.

The solution is the **Transactional Outbox Pattern**.

👉 **[Follow the progress at programmingonmars.io](https://programmingonmars.io)**

---

**Owner:** Andre Lucas — [Andre Lucas](https://www.linkedin.com/in/andre-lucastic/)
**License:** MIT
**Version:** 1.0.0

<!-- MARKDOWN LINKS & IMAGES -->
[java-shield]: https://img.shields.io/badge/Java%2025-ED8B00?logo=openjdk&logoColor=white&style=for-the-badge
[java-url]: https://openjdk.org/projects/jdk/25/
[springboot-shield]: https://img.shields.io/badge/Spring%20Boot%204.0-6DB33F?logo=springboot&logoColor=white&style=for-the-badge
[springboot-url]: https://spring.io/projects/spring-boot
[maven-shield]: https://img.shields.io/badge/Maven%203.9-C71A36?logo=apachemaven&logoColor=white&style=for-the-badge
[maven-url]: https://maven.apache.org/
[postgres-shield]: https://img.shields.io/badge/PostgreSQL%2016-4169E1?logo=postgresql&logoColor=white&style=for-the-badge
[postgres-url]: https://www.postgresql.org/
[redpanda-shield]: https://img.shields.io/badge/Redpanda-E5484D?logo=apachekafka&logoColor=white&style=for-the-badge
[redpanda-url]: https://docs.redpanda.com/
[docker-shield]: https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white&style=for-the-badge
[docker-url]: https://www.docker.com/
