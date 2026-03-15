# PRP — Helm Chart: Kubernetes Deployment with ServiceMonitor & Externalized Config

## Goal

Create a production-ready Helm chart that deploys the Mars Enterprise Kit Lite Spring Boot application to Kubernetes. The chart connects to **external** PostgreSQL and Kafka instances (not managed by the chart — no subcharts). It includes a Prometheus `ServiceMonitor` for scraping `/actuator/prometheus` metrics and externalizes database credentials via ConfigMap/Secret patterns.

Also add the `micrometer-registry-prometheus` Maven dependency and update `application.yaml` to expose the Prometheus metrics endpoint — required for the ServiceMonitor to function.

## Why

- **Kubernetes-native deployment**: Helm chart makes the project deployable to any K8s cluster with a single `helm install` command.
- **External infrastructure**: PostgreSQL and Kafka are managed outside the cluster (e.g., AWS RDS, Confluent Cloud, self-hosted) — the chart only needs connection details.
- **Observability**: ServiceMonitor enables Prometheus to auto-discover and scrape Spring Boot metrics (JVM, HTTP, Kafka, HikariCP).
- **Security**: Database credentials are injected via Kubernetes Secrets, not hardcoded in config files.
- **Educational value**: Developers learning Kubernetes can study the chart as a reference for Spring Boot microservice deployment.

## What

### Deliverables

1. **`pom.xml` update** — add `micrometer-registry-prometheus` dependency
2. **`application.yaml` update** — expose `prometheus` actuator endpoint + enable health probes
3. **`helm/mars-enterprise-kit-lite/Chart.yaml`** — chart metadata (no subchart dependencies)
4. **`helm/mars-enterprise-kit-lite/values.yaml`** — default configuration (image, replicas, resources, external DB/Kafka connection, monitoring)
5. **`helm/mars-enterprise-kit-lite/templates/`** — all Kubernetes manifests:
   - `_helpers.tpl` — reusable template helpers (labels, naming, selectors)
   - `deployment.yaml` — app Deployment with health probes, envFrom ConfigMap, env from Secret
   - `service.yaml` — ClusterIP Service with named `http` port
   - `configmap.yaml` — non-sensitive Spring Boot config (DB URL, Kafka brokers, actuator)
   - `secret.yaml` — sensitive config (DB password) — conditional, skipped if `existingSecret` is set
   - `servicemonitor.yaml` — Prometheus ServiceMonitor (conditional on `serviceMonitor.enabled`)
   - `serviceaccount.yaml` — ServiceAccount (conditional)
   - `ingress.yaml` — Ingress (conditional on `ingress.enabled`)
   - `NOTES.txt` — post-install instructions
   - `tests/test-connection.yaml` — Helm test

### Success Criteria

- [ ] `micrometer-registry-prometheus` dependency added to `pom.xml`
- [ ] `application.yaml` exposes `health,info,prometheus` endpoints + health probes enabled
- [ ] `mvn clean verify` passes (all existing tests still green)
- [ ] `/actuator/prometheus` endpoint returns Prometheus metrics when app is running
- [ ] `helm lint helm/mars-enterprise-kit-lite/` passes
- [ ] `helm template mars helm/mars-enterprise-kit-lite/` renders valid YAML for all templates
- [ ] Deployment template includes liveness, readiness, and startup probes
- [ ] ConfigMap injects `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, actuator config
- [ ] Secret injects `SPRING_DATASOURCE_PASSWORD` (or references `existingSecret`)
- [ ] ServiceMonitor targets port `http` and path `/actuator/prometheus`
- [ ] External PostgreSQL and Kafka connection details are configurable via `values.yaml` (no subcharts)
- [ ] Image defaults to `ghcr.io/andrelucasti/mars-enterprise-kit-lite` with tag from `Chart.appVersion`

---

## All Needed Context

### Documentation & References

```yaml
- file: CLAUDE.md
  why: Project conventions, code style, architecture rules

- file: .mars/docs/mars-enterprise-kit-context-lite.md
  why: Full project context

- file: pom.xml
  why: Current dependencies — add micrometer-registry-prometheus here

- file: src/main/resources/application.yaml
  why: Current actuator config — update to expose prometheus endpoint

- file: Dockerfile
  why: Container config — port 8082, health check path, base image

- file: docker-compose.yml
  why: Infrastructure services — reference for default connection details (ports, credentials)

- url: https://helm.sh/docs/chart_best_practices/
  why: Helm chart best practices (naming, labels, templates)

- url: https://prometheus-operator.dev/docs/api-reference/api/
  why: ServiceMonitor CRD spec — apiVersion monitoring.coreos.com/v1

- url: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
  why: Spring Boot metrics + Prometheus auto-configuration

- url: https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html
  why: Micrometer Prometheus registry setup
```

### Current Application Config

```yaml
# src/main/resources/application.yaml (current)
spring:
  application:
    name: mars-enterprise-kit-lite
  datasource:
    url: jdbc:postgresql://localhost:5432/orders_db
    username: mars
    password: mars
  kafka:
    bootstrap-servers: localhost:9092
server:
  port: 8082
management:
  endpoints:
    web:
      exposure:
        include: health          # ← Currently only health. Must add prometheus.
```

### Current pom.xml Dependencies (relevant section)

```xml
<!-- Already present -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- MISSING — must add for Prometheus metrics -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Desired Codebase Changes

```
mars-enterprise-kit-lite/
├── pom.xml                                          # MODIFIED: add micrometer-registry-prometheus
├── src/main/resources/
│   └── application.yaml                             # MODIFIED: expose prometheus endpoint + health probes
├── helm/
│   └── mars-enterprise-kit-lite/
│       ├── Chart.yaml                               # NEW: chart metadata (no subchart dependencies)
│       ├── values.yaml                              # NEW: default configuration
│       ├── .helmignore                              # NEW: exclude files from chart packaging
│       └── templates/
│           ├── _helpers.tpl                         # NEW: template helpers
│           ├── deployment.yaml                      # NEW: app Deployment
│           ├── service.yaml                         # NEW: ClusterIP Service
│           ├── configmap.yaml                       # NEW: Spring Boot config
│           ├── secret.yaml                          # NEW: DB credentials (conditional)
│           ├── servicemonitor.yaml                  # NEW: Prometheus ServiceMonitor (conditional)
│           ├── serviceaccount.yaml                  # NEW: ServiceAccount (conditional)
│           ├── ingress.yaml                         # NEW: Ingress (conditional)
│           ├── NOTES.txt                            # NEW: post-install message
│           └── tests/
│               └── test-connection.yaml             # NEW: Helm test
```

### Known Gotchas

```yaml
# CRITICAL: The app runs on port 8082 (not 8080) — Redpanda Schema Registry uses 8081
#           All K8s manifests must use containerPort: 8082
# CRITICAL: Spring Boot relaxed binding maps env vars to properties:
#           SPRING_DATASOURCE_URL → spring.datasource.url
#           SPRING_DATASOURCE_PASSWORD → spring.datasource.password
#           SPRING_KAFKA_BOOTSTRAP_SERVERS → spring.kafka.bootstrap-servers
# CRITICAL: ServiceMonitor requires Prometheus Operator CRD installed in the cluster
#           Make it conditional: only created when serviceMonitor.enabled=true
# CRITICAL: ServiceMonitor labels MUST match Prometheus's serviceMonitorSelector
#           Common label: release: kube-prometheus-stack (configurable in values.yaml)
# CRITICAL: ServiceMonitor port name must match the Service port name (use "http")
# CRITICAL: micrometer-registry-prometheus version is managed by Spring Boot parent POM
#           Do NOT specify a version — let spring-boot-starter-parent manage it
# CRITICAL: PostgreSQL and Kafka are EXTERNAL — no Bitnami subcharts
#           The ConfigMap uses values from externalDatabase and externalKafka in values.yaml
#           Operators must provide the correct host/port for their external services
# CRITICAL: Never store real passwords in values.yaml — support existingSecret pattern
# CRITICAL: Health probes must use the actuator group endpoints:
#           Liveness:  /actuator/health/liveness
#           Readiness: /actuator/health/readiness
#           Requires: management.endpoint.health.probes.enabled=true in Spring config
# NOTE: No subchart dependencies — no Chart.lock, no charts/ directory needed
#       The chart is self-contained with only templates and values
```

---

## Implementation Blueprint

### Task 1: Add Micrometer Prometheus Dependency

```yaml
Task 1: Add micrometer-registry-prometheus to pom.xml
  file: pom.xml
  action: Add dependency in the dependencies section (after spring-boot-starter-actuator)
  note: No version needed — managed by Spring Boot parent POM
```

**pom.xml change — add after the `spring-boot-starter-actuator` dependency:**

```xml
<!-- Prometheus metrics (auto-configured by Spring Boot when on classpath) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Task 2: Update application.yaml for Prometheus & Health Probes

```yaml
Task 2: Update application.yaml
  file: src/main/resources/application.yaml
  action: Update management section to expose prometheus endpoint and enable health probes
```

**Updated management section:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
    prometheus:
      enabled: true
```

This enables:
- `/actuator/prometheus` — Prometheus metrics scrape endpoint
- `/actuator/health/liveness` — Kubernetes liveness probe
- `/actuator/health/readiness` — Kubernetes readiness probe

### Task 3: Run Tests to Verify No Regressions

```yaml
Task 3: Verify existing tests pass
  command: mvn clean verify
  expected: BUILD SUCCESS — all tests still green
  note: micrometer-registry-prometheus is auto-configured, should not break anything
```

### Task 4: Create Helm Chart Scaffold

```yaml
Task 4: Create Helm chart directory and core files
  files:
    - helm/mars-enterprise-kit-lite/Chart.yaml
    - helm/mars-enterprise-kit-lite/values.yaml
    - helm/mars-enterprise-kit-lite/.helmignore
```

**Chart.yaml:**

```yaml
apiVersion: v2
name: mars-enterprise-kit-lite
description: Order microservice with Onion Architecture and Dual Write pattern — educational Helm chart
type: application
version: 0.1.0
appVersion: "1.0.0"
# No subchart dependencies — PostgreSQL and Kafka are external services
```

**values.yaml:**

```yaml
# =============================================================================
# Application
# =============================================================================
replicaCount: 1

image:
  repository: ghcr.io/andrelucasti/mars-enterprise-kit-lite
  tag: ""                                    # Defaults to Chart.appVersion
  pullPolicy: IfNotPresent

nameOverride: ""
fullnameOverride: ""

# =============================================================================
# Service
# =============================================================================
service:
  type: ClusterIP
  port: 8082
  targetPort: 8082

# =============================================================================
# Spring Boot Configuration
# =============================================================================
spring:
  profiles:
    active: "default"
  datasource:
    password: "mars"                         # Override for dev. Use existingSecret in prod.

# External Secret reference (pre-created K8s Secret with DB password)
# Set this to use a pre-existing secret instead of the chart-managed one
existingSecret: ""
existingSecretKey: "SPRING_DATASOURCE_PASSWORD"

# =============================================================================
# External Database (PostgreSQL) — NOT managed by this chart
# =============================================================================
# Provide connection details for your external PostgreSQL instance.
# Examples: AWS RDS, Cloud SQL, self-hosted PostgreSQL, etc.
externalDatabase:
  host: "localhost"                          # PostgreSQL hostname or IP
  port: 5432                                 # PostgreSQL port
  database: "orders_db"                      # Database name
  username: "mars"                           # Database username

# =============================================================================
# External Kafka — NOT managed by this chart
# =============================================================================
# Provide the bootstrap servers for your external Kafka/Redpanda cluster.
# Examples: Confluent Cloud, Amazon MSK, self-hosted Kafka, Redpanda, etc.
externalKafka:
  bootstrapServers: "localhost:9092"         # Comma-separated list of broker addresses

# =============================================================================
# ServiceAccount
# =============================================================================
serviceAccount:
  create: true
  annotations: {}
  name: ""

# =============================================================================
# Health Probes
# =============================================================================
probes:
  liveness:
    path: /actuator/health/liveness
    initialDelaySeconds: 30
    periodSeconds: 10
    failureThreshold: 3
  readiness:
    path: /actuator/health/readiness
    initialDelaySeconds: 20
    periodSeconds: 5
    failureThreshold: 3
  startup:
    path: /actuator/health/liveness
    initialDelaySeconds: 10
    periodSeconds: 5
    failureThreshold: 30

# =============================================================================
# Resources
# =============================================================================
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1Gi

# =============================================================================
# Monitoring — Prometheus ServiceMonitor
# =============================================================================
serviceMonitor:
  enabled: false                             # Set to true if Prometheus Operator is installed
  interval: 30s
  scrapeTimeout: 10s
  path: /actuator/prometheus
  labels: {}                                 # e.g., release: kube-prometheus-stack

# =============================================================================
# Ingress
# =============================================================================
ingress:
  enabled: false
  className: ""
  annotations: {}
  hosts:
    - host: mars-orders.local
      paths:
        - path: /
          pathType: Prefix
  tls: []
```

**.helmignore:**

```
.DS_Store
.git
.gitignore
.idea
*.swp
*.bak
*.tmp
```

### Task 5: Create Helm Templates

```yaml
Task 5: Create all Kubernetes template files
  files:
    - helm/mars-enterprise-kit-lite/templates/_helpers.tpl
    - helm/mars-enterprise-kit-lite/templates/deployment.yaml
    - helm/mars-enterprise-kit-lite/templates/service.yaml
    - helm/mars-enterprise-kit-lite/templates/configmap.yaml
    - helm/mars-enterprise-kit-lite/templates/secret.yaml
    - helm/mars-enterprise-kit-lite/templates/servicemonitor.yaml
    - helm/mars-enterprise-kit-lite/templates/serviceaccount.yaml
    - helm/mars-enterprise-kit-lite/templates/ingress.yaml
    - helm/mars-enterprise-kit-lite/templates/NOTES.txt
    - helm/mars-enterprise-kit-lite/templates/tests/test-connection.yaml
```

#### _helpers.tpl

```gotemplate
{{/*
Expand the name of the chart.
*/}}
{{- define "mars.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "mars.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "mars.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "mars.labels" -}}
helm.sh/chart: {{ include "mars.chart" . }}
{{ include "mars.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "mars.selectorLabels" -}}
app.kubernetes.io/name: {{ include "mars.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "mars.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "mars.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
```

#### deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "mars.fullname" . }}
  labels:
    {{- include "mars.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "mars.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "mars.selectorLabels" . | nindent 8 }}
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
    spec:
      serviceAccountName: {{ include "mars.serviceAccountName" . }}
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.service.targetPort }}
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ include "mars.fullname" . }}-config
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ .Values.existingSecret | default (printf "%s-secret" (include "mars.fullname" .)) }}
                  key: {{ .Values.existingSecretKey | default "SPRING_DATASOURCE_PASSWORD" }}
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
            failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
            failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
          startupProbe:
            httpGet:
              path: {{ .Values.probes.startup.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.startup.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.startup.periodSeconds }}
            failureThreshold: {{ .Values.probes.startup.failureThreshold }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

#### service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "mars.fullname" . }}
  labels:
    {{- include "mars.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - name: http
      port: {{ .Values.service.port }}
      targetPort: {{ .Values.service.targetPort }}
      protocol: TCP
  selector:
    {{- include "mars.selectorLabels" . | nindent 4 }}
```

#### configmap.yaml

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "mars.fullname" . }}-config
  labels:
    {{- include "mars.labels" . | nindent 4 }}
data:
  SPRING_PROFILES_ACTIVE: {{ .Values.spring.profiles.active | quote }}
  SPRING_DATASOURCE_URL: {{ printf "jdbc:postgresql://%s:%v/%s" .Values.externalDatabase.host (.Values.externalDatabase.port | int) .Values.externalDatabase.database | quote }}
  SPRING_DATASOURCE_USERNAME: {{ .Values.externalDatabase.username | quote }}
  SPRING_KAFKA_BOOTSTRAP_SERVERS: {{ .Values.externalKafka.bootstrapServers | quote }}
  SERVER_PORT: {{ .Values.service.targetPort | quote }}
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,prometheus"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
  MANAGEMENT_ENDPOINT_PROMETHEUS_ENABLED: "true"
```

#### secret.yaml

```yaml
{{- if not .Values.existingSecret }}
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "mars.fullname" . }}-secret
  labels:
    {{- include "mars.labels" . | nindent 4 }}
type: Opaque
data:
  SPRING_DATASOURCE_PASSWORD: {{ .Values.spring.datasource.password | b64enc | quote }}
{{- end }}
```

#### servicemonitor.yaml

```yaml
{{- if .Values.serviceMonitor.enabled }}
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "mars.fullname" . }}
  labels:
    {{- include "mars.labels" . | nindent 4 }}
    {{- with .Values.serviceMonitor.labels }}
    {{- toYaml . | nindent 4 }}
    {{- end }}
spec:
  selector:
    matchLabels:
      {{- include "mars.selectorLabels" . | nindent 6 }}
  namespaceSelector:
    matchNames:
      - {{ .Release.Namespace }}
  endpoints:
    - port: http
      path: {{ .Values.serviceMonitor.path }}
      interval: {{ .Values.serviceMonitor.interval }}
      scrapeTimeout: {{ .Values.serviceMonitor.scrapeTimeout }}
{{- end }}
```

#### serviceaccount.yaml

```yaml
{{- if .Values.serviceAccount.create }}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "mars.serviceAccountName" . }}
  labels:
    {{- include "mars.labels" . | nindent 4 }}
  {{- with .Values.serviceAccount.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
{{- end }}
```

#### ingress.yaml

```yaml
{{- if .Values.ingress.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "mars.fullname" . }}
  labels:
    {{- include "mars.labels" . | nindent 4 }}
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  {{- if .Values.ingress.className }}
  ingressClassName: {{ .Values.ingress.className }}
  {{- end }}
  {{- if .Values.ingress.tls }}
  tls:
    {{- range .Values.ingress.tls }}
    - hosts:
        {{- range .hosts }}
        - {{ . | quote }}
        {{- end }}
      secretName: {{ .secretName }}
    {{- end }}
  {{- end }}
  rules:
    {{- range .Values.ingress.hosts }}
    - host: {{ .host | quote }}
      http:
        paths:
          {{- range .paths }}
          - path: {{ .path }}
            pathType: {{ .pathType }}
            backend:
              service:
                name: {{ include "mars.fullname" $ }}
                port:
                  number: {{ $.Values.service.port }}
          {{- end }}
    {{- end }}
{{- end }}
```

#### NOTES.txt

```
Mars Enterprise Kit Lite has been deployed!

Application URL:
{{- if .Values.ingress.enabled }}
  {{- range .Values.ingress.hosts }}
  http{{ if $.Values.ingress.tls }}s{{ end }}://{{ .host }}
  {{- end }}
{{- else }}
  kubectl port-forward svc/{{ include "mars.fullname" . }} 8082:{{ .Values.service.port }} --namespace {{ .Release.Namespace }}
  Then visit: http://localhost:8082
{{- end }}

API Endpoints:
  POST /orders          — Create a new order
  GET  /orders/{id}     — Get order by ID
  GET  /actuator/health — Health check

{{- if .Values.serviceMonitor.enabled }}
Prometheus Metrics:
  GET /actuator/prometheus — scraped by ServiceMonitor every {{ .Values.serviceMonitor.interval }}
{{- end }}

Prerequisites:
  - External PostgreSQL must be reachable at: {{ .Values.externalDatabase.host }}:{{ .Values.externalDatabase.port }}
  - External Kafka must be reachable at: {{ .Values.externalKafka.bootstrapServers }}

NOTE: This is an educational project demonstrating the Dual Write anti-pattern.
      The order service writes to PostgreSQL and publishes to Kafka WITHOUT atomic guarantees.
```

#### tests/test-connection.yaml

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: "{{ include "mars.fullname" . }}-test-connection"
  labels:
    {{- include "mars.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": test
spec:
  containers:
    - name: wget
      image: busybox
      command: ['wget']
      args: ['{{ include "mars.fullname" . }}:{{ .Values.service.port }}/actuator/health']
  restartPolicy: Never
```

### Task 6: Validate Helm Chart

```yaml
Task 6: Lint and template validation
  commands:
    - helm lint helm/mars-enterprise-kit-lite/
    - helm template mars helm/mars-enterprise-kit-lite/
  expected: No errors or warnings
  note: No helm dependency update needed — no subchart dependencies
```

### Task 7: Update Documentation

```yaml
Task 7: Update CLAUDE.md and context docs
  files:
    - CLAUDE.md (update project structure, add Helm section)
    - .mars/docs/mars-enterprise-kit-context-lite.md (note Helm chart exists)
```

---

## Tasks (Execution Order)

```yaml
Task 1: Add micrometer-registry-prometheus dependency
  file: pom.xml
  action: Add dependency after spring-boot-starter-actuator (no version — managed by parent POM)

Task 2: Update application.yaml
  file: src/main/resources/application.yaml
  action: Expand management section to expose prometheus endpoint and enable health probes

Task 3: Run mvn clean verify
  command: mvn clean verify
  expected: BUILD SUCCESS — all tests pass with new dependency

Task 4: Create Helm chart scaffold
  files:
    - helm/mars-enterprise-kit-lite/Chart.yaml (no dependencies — external infra)
    - helm/mars-enterprise-kit-lite/values.yaml
    - helm/mars-enterprise-kit-lite/.helmignore

Task 5: Create all Helm templates
  files:
    - helm/mars-enterprise-kit-lite/templates/_helpers.tpl
    - helm/mars-enterprise-kit-lite/templates/deployment.yaml
    - helm/mars-enterprise-kit-lite/templates/service.yaml
    - helm/mars-enterprise-kit-lite/templates/configmap.yaml
    - helm/mars-enterprise-kit-lite/templates/secret.yaml
    - helm/mars-enterprise-kit-lite/templates/servicemonitor.yaml
    - helm/mars-enterprise-kit-lite/templates/serviceaccount.yaml
    - helm/mars-enterprise-kit-lite/templates/ingress.yaml
    - helm/mars-enterprise-kit-lite/templates/NOTES.txt
    - helm/mars-enterprise-kit-lite/templates/tests/test-connection.yaml

Task 6: Validate Helm chart
  commands:
    - helm lint helm/mars-enterprise-kit-lite/
    - helm template mars helm/mars-enterprise-kit-lite/

Task 7: Update documentation
  files: CLAUDE.md, .mars/docs/mars-enterprise-kit-context-lite.md
```

---

## Validation Gates

### Level 1: Maven Build (application changes)
```bash
mvn clean verify
# Expected: BUILD SUCCESS — all existing tests pass, micrometer dependency resolves
```

### Level 2: Prometheus Endpoint (manual, if app is running)
```bash
# Start app locally (requires docker-compose up first):
# mvn spring-boot:run
# curl http://localhost:8082/actuator/prometheus
# Expected: Prometheus-formatted metrics (jvm_*, http_*, kafka_*, hikaricp_*)
```

### Level 3: Helm Lint
```bash
helm lint helm/mars-enterprise-kit-lite/
# Expected: 0 errors, 0 warnings (or only informational)
```

### Level 4: Helm Template Render
```bash
helm template mars helm/mars-enterprise-kit-lite/
# Expected: Valid YAML for all templates — Deployment, Service, ConfigMap, Secret, ServiceAccount
# ServiceMonitor and Ingress should NOT render (disabled by default)
```

### Level 5: Helm Template with ServiceMonitor Enabled
```bash
helm template mars helm/mars-enterprise-kit-lite/ --set serviceMonitor.enabled=true
# Expected: ServiceMonitor resource is rendered with correct apiVersion and spec
```

### Level 6: Helm Template with Ingress Enabled
```bash
helm template mars helm/mars-enterprise-kit-lite/ --set ingress.enabled=true
# Expected: Ingress resource is rendered
```

### Level 7: Full Project Build Still Works
```bash
mvn clean verify
# Expected: BUILD SUCCESS — Helm files don't affect Maven build
```

---

## Anti-Patterns to Avoid

- **DO NOT** hardcode database passwords in ConfigMap — use Secret or existingSecret
- **DO NOT** use port 8080 or 8081 — the app runs on 8082
- **DO NOT** use `/actuator/health` for liveness/readiness — use the group endpoints `/actuator/health/liveness` and `/actuator/health/readiness`
- **DO NOT** specify a version for `micrometer-registry-prometheus` — Spring Boot parent manages it
- **DO NOT** make ServiceMonitor enabled by default — it requires Prometheus Operator CRD
- **DO NOT** make Ingress enabled by default — not all clusters have an Ingress controller
- **DO NOT** use `latest` as the default image tag — use Chart.appVersion
- **DO NOT** forget the `checksum/config` annotation on the Deployment — it ensures pods restart when ConfigMap changes
- **DO NOT** add Spring Boot management config to `application.yaml` that conflicts with ConfigMap env vars — env vars take precedence, but having both is confusing
- **DO NOT** add `micrometer-registry-prometheus` with a `<scope>` — it must be runtime scope (default)
- **DO NOT** add Bitnami PostgreSQL or Kafka as subchart dependencies — infrastructure is external to the cluster
- **DO NOT** use subchart-derived service names (e.g., `{{ .Release.Name }}-postgresql`) in the ConfigMap — use `externalDatabase.host` and `externalKafka.bootstrapServers` from values.yaml

---

## Confidence Score: 9/10

- **Context completeness**: 9/10 — all app config, external infrastructure patterns, and Helm templates documented with URLs
- **Pattern availability**: 9/10 — standard Helm chart patterns, no subchart complexity
- **Validation gate coverage**: 9/10 — Maven build, Helm lint, template render, conditional resource validation
- **One-pass implementation likelihood**: 9/10 — simpler chart (no subchart dependencies), all templates provided verbatim
