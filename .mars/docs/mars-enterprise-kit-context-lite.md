# Mars Enterprise Kit Lite — Project Context

**Version:** 1.0.0
**Last Updated:** February 2026
**Status:** Active Development
**Owner:** Andre Silva (@programmingonmars)
---

## 1. Visão Geral

O **Mars Enterprise Kit Lite** é um microserviço open-source do domínio de Order com Onion Architecture correta, comunicação via Kafka e persistência em PostgreSQL — mas sem o padrão Outbox. Essa ausência não é negligência; é intencional para que o desenvolvedor sinta o problema do Dual Write.

O projeto é também um laboratório **AI-First**: toda a operação de infraestrutura — subir o ambiente, criar tópicos, produzir eventos, rodar smoke tests — é orquestrada pelo Claude Code, sem necessidade de intervenção manual. O README é um prompt disfarçado de documentação.

**Público-alvo:**
- Engenheiros de Software que querem aprender Onion Architecture com um exemplo real e funcional.
- Desenvolvedores que querem explorar desenvolvimento AI-First com Claude Code.

---

## 2. O Problema

### O problema que o Lite demonstra (intencionalmente)

Sistemas distribuídos que precisam persistir dados **e** publicar eventos no Kafka enfrentam um desafio fundamental: **não existe atomicidade nativa entre um banco de dados relacional e um message broker**.

A solução ingênua — e a mais comum em codebases reais — é o **Dual Write**: salvar no banco, depois publicar no Kafka dentro de uma `@Transactional`. O Spring gerencia o rollback do banco em caso de falha, mas **não desfaz o evento já publicado no Kafka**. O inverso também é verdade: se o Kafka cair após o commit do banco, o evento se perde silenciosamente.

```
┌─────────────────────────────────────────────────────┐
│  REST POST /orders                                  │
│                                                     │
│  @Transactional                                     │
│  1. INSERT INTO orders (✅ salvo)                   │
│  2. kafkaTemplate.send("order.created") → 💥 falha  │
│                                                     │
│  Resultado: Order existe no banco.                  │
│             Evento nunca chegou ao consumidor.      │
│             Inconsistência silenciosa.              │
└─────────────────────────────────────────────────────┘
```

**Esse é exatamente o cenário que o Lite reproduz.** O smoke test orquestrado pelo Claude Code vai criar e cancelar orders. Em condições normais, tudo funciona. Mas a arquitetura é frágil por design — e isso é o ponto.

### O problema que o Lite resolve (para o usuário)

- **Inexistência de templates educacionais reais:** Spring Initializr gera projetos vazios. Tutoriais do YouTube param no "Hello World". O Lite entrega uma Onion Architecture completa, com separação de camadas real, domínio de negócio implementado e comunicação via Kafka.
- **Curva íngreme para AI-First development:** Sem um projeto bem estruturado como base de contexto, o Claude Code e o Cursor cometem erros arquiteturais. O Lite resolve isso com arquivos `.mars/context/` que guiam o AI assistant.

---

## 3. Objeto Esperado

Um repositório GitHub público contendo um único microserviço Java/Spring Boot para o domínio de **Order**, com:

- Onion Architecture implementada como projeto Maven multi-módulo.
- Persistência em PostgreSQL (via Spring Data JPA + Flyway).
- Comunicação assíncrona via Kafka (Redpanda em local dev).
- Dois fluxos de evento: `OrderCreated` (publicado pelo serviço) e `OrderCancelled` (consumido pelo serviço).
- Docker Compose com PostgreSQL + Redpanda prontos para uso.
- README com instruções completas de uso via Claude Code (AI-First).
- Arquivos de contexto `.mars/context/` para consumo por AI assistants.
**O que o Lite NÃO entrega (por design):**
- Transactional Outbox Pattern (fora do escopo do Lite).
- Helm charts / Kubernetes manifests.
- CI/CD pipelines.
- Autenticação / Autorização.
- Schema Registry / Apache Avro.
- Observabilidade completa (sem Jaeger, sem OpenTelemetry).
- Múltiplos domínios ou múltiplos serviços.
- CLI (`mars-cli`).

---

## 4. Escopo e Principais Funcionalidades

### 4.1 Domínio: Order

O domínio de Order é o único contexto do sistema. Ele implementa dois casos de uso:

**Caso de Uso 1 — Criar Order (`CreateOrderUseCase`)**
- Entrada: REST `POST /orders` com payload contendo `customerId` e `items`.
- Processamento: persiste a order no PostgreSQL com status `CREATED`.
- Saída: publica evento `OrderCreated` no tópico `order.created` via Kafka (Dual Write — **sem garantia atômica**).
- Retorno: `201 Created` com o `orderId` gerado.

**Caso de Uso 2 — Cancelar Order (`CancelOrderUseCase`)**
- Entrada: evento consumido do tópico `order.cancelled` (publicado manualmente ou por outro sistema).
- Processamento: atualiza o status da order para `CANCELLED` no PostgreSQL.
- Saída: nenhuma (o cancelamento é o estado final do fluxo).

### 4.2 Fluxo de Eventos

```
┌─────────────────────────────────────────────────────────────────┐
│  FLUXO 1 — CREATE ORDER (Publicador)                            │
│                                                                 │
│  Client ──POST /orders──► OrderController                       │
│                               │                                 │
│                           CreateOrderUseCase                    │
│                               │                                 │
│                    ┌──────────┴──────────┐                      │
│                    ▼                     ▼                      │
│             PostgreSQL            Kafka Producer                │
│          INSERT orders         send("order.created")            │
│                                                                 │
│  ⚠️  Dual Write: nenhuma garantia atômica entre os dois         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  FLUXO 2 — CANCEL ORDER (Consumidor)                            │
│                                                                 │
│  Kafka Producer          order-service                          │
│  (manual / externo)                                             │
│       │                      │                                  │
│  publish("order.cancelled") ─► OrderCancelledConsumer           │
│                                      │                          │
│                               CancelOrderUseCase                │
│                                      │                          │
│                                 PostgreSQL                      │
│                           UPDATE orders SET status='CANCELLED'  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Tópicos Kafka

| Tópico | Papel do order-service | Publicado por | Consumido por |
|--------|------------------------|---------------|---------------|
| `order.created` | Publicador | order-service | Consumidores externos (simulado no smoke test) |
| `order.cancelled` | Consumidor | Produção manual / sistema externo | order-service |

### 4.4 Endpoints REST

| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/orders` | Cria uma nova order |
| `GET` | `/orders/{id}` | Consulta uma order por ID |
| `GET` | `/actuator/health` | Health check |
| `POST` | `/chaos/phantom-event` | Simula evento fantasma (requer profile `chaos`) |

Sem autenticação. Sem rate limiting. Endpoints abertos por design (projeto educacional local).

> **Chaos endpoint:** Requer `SPRING_PROFILES_ACTIVE=chaos`. Demonstra o problema do Phantom Event — evento vai pro Kafka, mas a order NÃO existe no banco (DB faz rollback via AOP interceptor).

---

## 5. Arquitetura e Abordagem

### 5.1 Onion Architecture (Multi-Module Maven)

A estrutura interna segue Onion Architecture. A **lei da cebola** é inviolável: dependências apontam sempre para dentro.

```
mars-enterprise-kit-lite/
├── pom.xml                          # Parent POM (multi-module)
│
├── business/                        # ← Núcleo (sem dependências externas)
│   └── src/main/java/io/mars/lite/
│       └── order/
│           ├── Order.java                    # Aggregate Root
│           ├── OrderStatus.java              # Value Object (enum)
│           ├── OrderItem.java                # Value Object
│           ├── OrderRepository.java          # Porta (interface)
│           ├── BusinessException.java        # Domain Exception
│           └── usecase/
│               ├── CreateOrderUseCase.java   # Interface
│               ├── CreateOrderUseCaseImpl.java
│               ├── CancelOrderUseCase.java   # Interface
│               └── CancelOrderUseCaseImpl.java
│
├── data-provider/                   # ← Persistência (implementa business)
│   └── src/main/java/io/mars/lite/
│       ├── configuration/
│       │   ├── DataSourceConfiguration.java
│       │   └── KafkaConfiguration.java
│       └── order/
│           ├── OrderEntity.java              # JPA Entity
│           ├── OrderJpaRepository.java       # Spring Data JPA
│           └── OrderRepositoryImpl.java      # Adapter (implementa OrderRepository)
│
└── app/                             # ← Entry Point (orquestra tudo)
    └── src/main/java/io/mars/lite/
        ├── Application.java
        ├── configuration/
        │   └── UseCaseConfiguration.java     # Wiring de dependências
        └── api/
            ├── order/
            │   ├── OrderController.java
            │   ├── CreateOrderRequest.java
            │   └── OrderResponse.java
            ├── event/
            │   ├── OrderCreatedPublisher.java  # Kafka Producer (Dual Write)
            │   └── OrderCancelledConsumer.java # Kafka Consumer
            ├── chaos/                            # @Profile("chaos") — Chaos Testing
            │   ├── ChaosController.java          # POST /chaos/phantom-event
            │   ├── ChaosService.java             # @Transactional orchestrator
            │   ├── ChaosOrderExecutor.java       # AOP target (wraps UseCase)
            │   ├── PhantomEventChaosAspect.java  # @Aspect forces rollback after publish
            │   ├── PhantomEventSimulationException.java
            │   └── PhantomEventReport.java       # Response DTO
            └── GlobalExceptionHandler.java
```

**Regra de dependência Maven:**
- `business` → sem dependências em outros módulos.
- `data-provider` → depende de `business`.
- `app` → depende de `business` e `data-provider`.

### 5.2 O Anti-Padrão Intencional: Dual Write

O `CreateOrderUseCaseImpl` opera dentro de um `@Transactional` do Spring. Isso garante atomicidade **apenas para o banco de dados**. A publicação no Kafka acontece após o commit da transação — o que significa:

```java
@Transactional
public OrderCreatedEvent execute(CreateOrderCommand command) {
    // 1. Persiste no PostgreSQL (dentro da transação)
    Order order = Order.create(command.customerId(), command.items());
    orderRepository.save(order);

    // 2. Publica no Kafka (FORA da garantia transacional)
    // Se falhar aqui: order existe no banco, evento não existe no Kafka.
    // Se o Kafka estiver indisponível: falha silenciosa ou exception não tratada.
    eventPublisher.publish(new OrderCreatedEvent(order.getId(), ...));

    return new OrderCreatedEvent(...);
}
```

**Por que isso importa:** em produção com alta concorrência, essa janela de inconsistência é suficiente para gerar ordens fantasma — salvas no banco, invisíveis para outros serviços. O Transactional Outbox Pattern resolve isso. O Lite expõe o problema intencionalmente para que o desenvolvedor o sinta.

### 5.3 Stack Tecnológico

| Componente | Tecnologia | Versão | Racional |
|------------|-----------|--------|----------|
| Linguagem | Java | 25 | Última versão, virtual threads disponíveis |
| Framework | Spring Boot | 4.0.3 | Spring Framework 7, ecossistema maduro |
| Build | Maven (multi-module) | 3.9.x | Enforça separação de módulos |
| Persistência | Spring Data JPA + Hibernate | 3.x | Padrão Spring, simples para o escopo do Lite |
| Migrations | Flyway | 10.x | Versionamento de schema |
| Banco de Dados (local) | PostgreSQL | 16-alpine | Mesmo da versão completa |
| Mensageria (local) | Redpanda | latest | Kafka-compatible, single binary, zero config |
| Serialização de eventos | JSON (Jackson) | - | Simples para o escopo do Lite |
| Containerização | Docker Compose | 2.23+ | Único arquivo para subir toda a infra local |

### 5.4 Modelo de Dados

```sql
-- Migration: V1__create_orders_table.sql
CREATE TABLE orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total       NUMERIC(10,2) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders(id),
    product_id  UUID        NOT NULL,
    quantity    INT         NOT NULL,
    unit_price  NUMERIC(10,2) NOT NULL
);
```

### 5.5 Eventos Kafka

**Tópico `order.created` — Payload (JSON):**
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

**Tópico `order.cancelled` — Payload (JSON):**
```json
{
  "eventId": "uuid-v4",
  "orderId": "uuid-v4",
  "reason": "Customer requested cancellation",
  "occurredAt": "2026-02-21T10:05:00Z"
}
```

### 5.6 Infraestrutura Local (Docker Compose)

```
┌──────────────────────────────────────────────────────────┐
│  docker-compose.yml                                      │
│                                                          │
│  ┌──────────────┐   ┌─────────────────────────────────┐ │
│  │  PostgreSQL  │   │  Redpanda (Kafka-compatible)    │ │
│  │  16-alpine   │   │  Porta: 9092 (Kafka)            │ │
│  │  Porta: 5432 │   │  Porta: 9644 (Admin API)        │ │
│  │  DB: orders_db│  │  Console: 8080 (opcional)       │ │
│  └──────────────┘   └─────────────────────────────────┘ │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  order-service (Spring Boot)                     │   │
│  │  Porta: 8081                                     │   │
│  │  Depende de: postgres (healthcheck), redpanda    │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

**Tópicos criados automaticamente pelo Redpanda na inicialização:**
- `order.created` (partitions: 1, replication-factor: 1)
- `order.cancelled` (partitions: 1, replication-factor: 1)

---

## 6. AI-First: Operação via Claude Code

### 6.1 Filosofia

O Lite não é apenas um template. É um experimento de **desenvolvimento e operação AI-First**. O Claude Code é o operador principal — ele lê o README, sobe a infra, executa o smoke test e valida os resultados. O desenvolvedor observa.

Os arquivos `.mars/context/` são o sistema nervoso dessa integração: eles descrevem a arquitetura, os fluxos de evento, as convenções de código e o propósito de cada camada com precisão suficiente para que o AI assistant tome decisões corretas sem alucinações arquiteturais.

### 6.2 Estrutura dos Arquivos de Contexto

```
.mars/
└── context/
    ├── architecture.md       # Onion Architecture: regras, módulos, dependências
    ├── domain.md             # Domínio Order: agregados, casos de uso, eventos
    ├── flows.md              # Fluxo CreateOrder e CancelOrder passo a passo
    ├── infrastructure.md     # Docker Compose: serviços, portas, healthchecks
    ├── dual-write-warning.md # Explica o anti-padrão intencional e seus riscos
    └── smoke-test.md         # Roteiro do smoke test que o Claude Code executa
```

### 6.3 Roteiro do Smoke Test (AI-Orquestrado)

O Claude Code executa os seguintes passos em sequência ao receber o comando `smoke test` no README:

```
1. docker compose up -d
   └── Aguarda healthcheck do PostgreSQL e do Redpanda

2. Verifica se os tópicos Kafka existem:
   └── order.created
   └── order.cancelled

3. Cria uma Order via REST:
   POST /orders
   { "customerId": "...", "items": [...] }
   └── Valida 201 Created + orderId no response

4. Consome o evento order.created do Kafka:
   └── Valida que o evento foi publicado com o orderId correto

5. Publica manualmente um evento order.cancelled no Kafka:
   └── Payload: { "orderId": "...", "reason": "smoke-test" }

6. Consulta a order via REST:
   GET /orders/{orderId}
   └── Valida que status == "CANCELLED"

7. docker compose down
   └── Limpa o ambiente

8. Reporta resultado: ✅ PASS ou ❌ FAIL com logs
```

---

## 7. Posicionamento de Produto e Narrativa de Conversão

### 7.1 A Ferida Exposta

O Lite existe para que o desenvolvedor **sinta o problema**. A ausência do Outbox Pattern não é uma limitação técnica — é uma escolha pedagógica deliberada.

O desenvolvedor que usa o Lite em produção vai, eventualmente, ter um incidente de inconsistência de dados. Orders no banco sem evento no Kafka. Eventos no Kafka para orders que não existem no banco. Dados duplicados. Suporte de madrugada.

### 7.2 O que o Lite entrega

| Aspecto | Lite |
|--|-----------------|
| **O que entrega** | Onion Architecture + Kafka + PostgreSQL |
| **Garantia de dados** | ❌ Dual Write (sem atomicidade) |
| **Pronto para produção** | ❌ |
| **AI-Native context files** | ✅ |

---

## 8. Dependências e Restrições

### 8.1 Restrições Técnicas

- **Java 25 obrigatório.** Não há suporte para versões anteriores.
- **Docker e Docker Compose** são pré-requisitos para rodar o ambiente local. Sem alternativa de `java -jar` standalone.
- **Redpanda** é usado como broker Kafka-compatible. Não há configuração para Kafka "puro" fora do Docker Compose — o Lite não é para cloud, é para local dev e aprendizado.
- **JSON puro para eventos.** Sem Schema Registry, sem Avro. Breaking changes em schemas de eventos são descobertos em runtime — intencional para manter simplicidade.
- **Módulo `third-party` ausente.** O Lite não tem integrações com serviços externos.

### 8.2 Restrições de Escopo

- **Um único domínio.** Adicionar novos domínios ao Lite desfoca o propósito educacional. Qualquer extensão deve ser feita como fork ou como serviço separado.
- **Sem multi-tenancy, sem RBAC, sem autenticação.** Endpoints abertos por design.
- **Sem observabilidade avançada.** Spring Boot Actuator (`/actuator/health`) é suficiente. Jaeger e Prometheus estão fora do escopo.

### 8.3 Riscos Identificados

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Desenvolvedor usa o Lite em produção sem entender o Dual Write | Alta | Alto | Avisos explícitos no README e nos arquivos `.mars/context/dual-write-warning.md` |
| Redpanda com comportamento diferente do Kafka em edge cases | Baixa | Médio | Documentar as diferenças conhecidas; o Lite não usa features avançadas do Kafka |
| Claude Code gerar código que viola a Onion Architecture | Média | Alto | Arquivos `.mars/context/architecture.md` com regras explícitas e exemplos do que é permitido e proibido |
| Scope creep (pressão para adicionar Outbox ao Lite) | Alta | Alto | Manter a decisão documentada aqui como ADR: o Dual Write é o produto, não um bug |

### 8.4 ADR-001: Dual Write Intencional no Lite

**Contexto:** O Lite poderia implementar um Outbox simplificado. Tecnicamente, seria mais correto.

**Decisão:** O Lite **não** implementa Outbox. Implementa Dual Write sem garantia atômica.

**Racional:** O Lite deve ser funcionalmente atraente e estruturalmente frágil — exatamente como a maioria dos codebases reais de microserviços. O desenvolvedor precisa entender o problema para buscar a solução.

**Consequências:** O Lite não é adequado para produção. Isso está documentado em múltiplos lugares (README, dual-write-warning.md, CTA de conversão). A responsabilidade do uso indevido é do desenvolvedor, não do produto.

---

## 9. Estrutura do Repositório

```
mars-enterprise-kit-lite/
│
├── .mars/                           # AI Context Files
│   └── context/
│       ├── architecture.md
│       ├── domain.md
│       ├── flows.md
│       ├── infrastructure.md
│       ├── dual-write-warning.md
│       └── smoke-test.md
│
├── business/                        # Módulo de Domínio
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/mars/lite/order/
│       └── test/java/io/mars/lite/order/
│
├── data-provider/                   # Módulo de Persistência
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/mars/lite/
│       └── main/resources/db/migration/
│
├── app/                             # Módulo de Entry Point
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/mars/lite/
│       └── main/resources/
│           ├── application.yaml
│           └── application-local.yaml
│
├── docker-compose.yml               # PostgreSQL + Redpanda + order-service
├── .env.example                     # Variáveis de ambiente necessárias
├── pom.xml                          # Parent POM
└── README.md                        # Documentação AI-First com CTA
```

---

## 10. Referências

- **Padrão Outbox:** https://microservices.io/patterns/data/transactional-outbox.html
- **Dual Write Problem:** https://thorben-janssen.com/dual-writes/
- **Onion Architecture:** https://jeffreypalermo.com/2008/07/the-onion-architecture-part-1/
- **Redpanda:** https://docs.redpanda.com/current/home/
- **Claude Code:** https://claude.ai/code

---

**Document Version:** 1.0.0
**Last Updated:** February 2026
**Status:** Ready for Development
**Owner:** Andre Silva — programmingonmars.io

---

**END OF CONTEXT DOCUMENT**