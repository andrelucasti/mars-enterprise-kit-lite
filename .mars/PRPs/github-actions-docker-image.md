# PRP — GitHub Actions: Build & Push Docker Image to GHCR

## Goal

Create a production-ready GitHub Actions workflow that triggers on **every push to `main`**, runs tests, and always builds and pushes the Spring Boot application as a multi-arch Docker image to GitHub Container Registry (`ghcr.io`). No semantic versioning, no release gates — every commit to `main` produces an image.

Also create the `Dockerfile` that the workflow uses (multi-stage JVM build — this is Spring Boot, NOT Quarkus native).

## Why

- **Automated releases**: Every merge to `main` produces a versioned Docker image without manual steps.
- **Educational value**: The project is used by developers learning; a published Docker image makes it trivially runnable (`docker pull ghcr.io/andrelucasti/mars-enterprise-kit-lite`).
- **Funnel**: Image on GHCR → developers pull and run → see Dual Write problem → convert to PRO.
- **AI-First lab**: Claude Code can orchestrate `docker pull` + `docker-compose up` in smoke tests, no Maven required.

## What

### Deliverables

1. **`Dockerfile`** at repo root — multi-stage build:
   - Stage 1: Build JAR with Maven + Temurin 25 JDK
   - Stage 2: Run JAR with Temurin 25 JRE (minimal image)

2. **`.github/workflows/push-to-main.yaml`** — GitHub Actions workflow:
   - Trigger: `push` to `main`
   - Job 1 (`test`): runs `mvn verify` — TestContainers spins up PostgreSQL + Kafka on the GH Actions runner
   - Job 2 (`build-and-push`): runs only if `test` passes; builds multi-arch image and pushes to `ghcr.io` tagged with `latest` + short git SHA

### Success Criteria

- [ ] `Dockerfile` builds locally: `docker build -t mars-lite .`
- [ ] Workflow YAML is valid (passes `yamllint` / GitHub Actions schema)
- [ ] On **every** push to `main`, both jobs trigger
- [ ] `test` job runs `mvn verify` successfully (all unit + integration tests via TestContainers)
- [ ] `build-and-push` job runs only when `test` passes
- [ ] `build-and-push` produces two tags on every push:
  - `ghcr.io/andrelucasti/mars-enterprise-kit-lite:latest`
  - `ghcr.io/andrelucasti/mars-enterprise-kit-lite:<short-sha>` (e.g., `abc1234`)
- [ ] Image runs successfully: `docker run -p 8082:8082 ghcr.io/andrelucasti/mars-enterprise-kit-lite:latest`
- [ ] Multi-arch: `linux/amd64` AND `linux/arm64` platforms

---

## All Needed Context

### Reference Workflow (Source of Truth)

The reference workflow from `andrelucasti/payments-quarkuzin-rinha-backend-2025`:

```yaml
# URL: https://raw.githubusercontent.com/andrelucasti/payments-quarkuzin-rinha-backend-2025/main/.github/workflows/push-to-main.yaml

name: push-to-main
on:
  push:
    branches: [main]
permissions:
  contents: write
  packages: write

jobs:
  tag:
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.semantic.outputs.version }}
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: go-semantic-release/action@v1
        id: semantic
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          changelog-file: CHANGELOG.md
          changelog-api: true

  build-and-push:
    needs: tag
    if: needs.tag.outputs.version != ''
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '21'            # ← WE USE '25'
          distribution: 'temurin'
          cache: maven
      - name: Build native             # ← WE DO NOT USE NATIVE (Spring Boot JVM)
        run: mvn clean package -Pnative -DskipTests
      - uses: docker/setup-buildx-action@v3
      - uses: docker/setup-qemu-action@v3
      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Get latest tag
        id: tag
        run: |
          TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "${{ needs.tag.outputs.version }}")
          echo "tag=${TAG}" >> $GITHUB_OUTPUT
      - uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          platforms: linux/amd64,linux/arm64
          tags: |
            ghcr.io/${{ github.repository }}:${{ steps.tag.outputs.tag }}
            ghcr.io/${{ github.repository }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Critical Adaptations (Reference → This Project)

| Aspect | Reference (Quarkus) | This Project (Spring Boot) |
|--------|--------------------|-----------------------------|
| Java version | `21` | `25` |
| Semantic versioning | `go-semantic-release/action@v1` | **Removed** — no semantic release |
| Test step | None (tests skipped) | Dedicated `test` job: `mvn verify` before build |
| Build command | `mvn clean package -Pnative -DskipTests` | `mvn clean package -DskipTests` (tests already ran in `test` job) |
| Docker base image | Binary (no JVM needed) | `eclipse-temurin:25-jre-alpine` |
| Dockerfile | Single-stage (copy native binary) | Multi-stage (build JAR → copy to JRE image) |
| JAR location | N/A | `target/mars-enterprise-kit-lite-*.jar` |
| Image tags | semver + latest | `latest` + short git SHA (`${{ github.sha }}` sliced to 7 chars) |
| Job dependency | `build-and-push` needs `tag` | `build-and-push` needs `test` only |
| Trigger condition | Every push (semantic gate controls Docker push) | Every push to `main` always produces an image |
| `contents: write` permission | Required (creates git tags) | **Not needed** — removed |

### Project Structure Relevant to This PRP

```
mars-enterprise-kit-lite/
├── pom.xml                             # artifactId: mars-enterprise-kit-lite, version: 1.0.0
├── src/main/java/io/mars/lite/
│   └── Application.java               # Main class: io.mars.lite.Application
├── src/main/resources/
│   └── application.yaml               # spring.application.name, port 8082
├── docker-compose.yml                 # PostgreSQL + Redpanda (for local dev only)
├── .devcontainer/Dockerfile           # Dev container (NOT the app Dockerfile)
└── .github/                           # DOES NOT EXIST YET — we create it
```

**pom.xml key facts:**
- `artifactId`: `mars-enterprise-kit-lite`
- `version`: `1.0.0`
- `packaging`: `jar` (default, single-module)
- Spring Boot Maven plugin configured with `mainClass: io.mars.lite.Application`
- Build output: `target/mars-enterprise-kit-lite-1.0.0.jar`

### Docker Base Image for Java 25

Eclipse Temurin 25 is available since September 2025 (LTS release):
- Build stage: `eclipse-temurin:25-jdk-alpine`
- Run stage: `eclipse-temurin:25-jre-alpine`

Alpine variants minimize image size. Use Alpine for both stages.

### Documentation & References

```yaml
- url: https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions
  why: GitHub Actions workflow syntax reference

- url: https://github.com/docker/build-push-action
  why: docker/build-push-action@v5 options (context, platforms, tags, cache)

- url: https://github.com/go-semantic-release/action
  why: Semantic release action — outputs `version` and `release-created`

- url: https://hub.docker.com/_/eclipse-temurin/tags?name=25
  why: Temurin 25 image tags — use eclipse-temurin:25-jdk-alpine and eclipse-temurin:25-jre-alpine

- url: https://docs.spring.io/spring-boot/maven-plugin/build-image.html
  why: Spring Boot Maven plugin build-image (alternative — NOT used here, we use plain Dockerfile)

- url: https://github.com/andrelucasti/payments-quarkuzin-rinha-backend-2025/blob/main/.github/workflows/push-to-main.yaml
  why: Reference workflow — the source of truth for this PRP
```

### Known Gotchas

```yaml
# CRITICAL: This is a Spring Boot JVM app — do NOT use -Pnative or native-image build
# CRITICAL: JAR name includes version: mars-enterprise-kit-lite-1.0.0.jar
#           Use wildcard in COPY: COPY target/*.jar app.jar
# CRITICAL: The app runs on port 8082 (not 8080 — Redpanda's Schema Registry uses 8081)
#           Set EXPOSE 8082 in Dockerfile
# CRITICAL: The app requires PostgreSQL and Redpanda to start correctly in production
#           The Docker image alone will fail to start without those services
#           Add a note in the Dockerfile/README that docker-compose is needed for full stack
# NOTE: No semantic release, no conventional commit requirements — any commit message triggers the pipeline
# CRITICAL: Multi-arch builds with Buildx require QEMU setup (docker/setup-qemu-action@v3)
# CRITICAL: permissions: packages: write is required to push to ghcr.io
# CRITICAL: The .jar is fat (Spring Boot executable jar) — no need to set CLASSPATH
#           Just: java -jar app.jar
# WARNING: Java 25 requires --enable-preview flags if preview features are used
#          The project pom.xml does NOT use --enable-preview, so no flag needed in ENTRYPOINT
# TESTCONTAINERS: GitHub Actions ubuntu-latest runners have Docker pre-installed
#                 TestContainers works without any extra setup — just run `mvn verify`
#                 No need for `services:` block or docker-compose in the test job
# TESTCONTAINERS: The test job runs in parallel with the tag job (no `needs:` dependency)
#                 This is intentional — tests run on EVERY push, version gate only affects Docker push
# SKIP TESTS RATIONALE: build-and-push uses -DskipTests because the test job already ran them
#                        Running tests twice wastes runner minutes and adds no safety value
```

---

## Implementation Blueprint

### File 1: `Dockerfile` (repo root)

**Strategy**: Multi-stage build — Maven builds the fat JAR in stage 1, stage 2 copies only the JAR into a minimal JRE image.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper and POM first (layer caching)
COPY pom.xml .
COPY .mvn/ .mvn/           # If mvnw exists
COPY mvnw .                # If mvnw exists

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -q  # OR: ./mvnw dependency:go-offline

# Copy source
COPY src/ src/

# Build fat JAR, skip tests (tests run in CI separately)
RUN mvn clean package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# App runs on port 8082
EXPOSE 8082

# Health check (Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Check if `mvnw` exists**: The project was set up with Spring Initializr. If `mvnw` exists, use it. If not (likely since pom.xml shows standard Maven), use `mvn` — but `mvn` isn't in the Temurin image by default. We need to install Maven OR copy it. **Better approach**: Use Maven wrapper (`mvnw`) or install Maven in the builder stage.

**Decision**: Since the project uses `mvn` commands (CLAUDE.md shows `mvn clean install`), check if `mvnw` exists. If not, install Maven in the builder image. Use the Maven official image as builder instead:

```dockerfile
# Stage 1: Build — use maven:3.9-eclipse-temurin-25 to get both Maven + Java 25
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
```

> Check if `maven:3.9-eclipse-temurin-25-alpine` exists. If not, use `maven:3.9-eclipse-temurin-25`.
> Alternative if that image is not available: use `eclipse-temurin:25-jdk-alpine` + install Maven manually.

### File 2: `.github/workflows/push-to-main.yaml`

**Job execution order:**
```
test ──────────────────────────────────► build-and-push
     (mvn verify, runs on every push)   (every push, always, if test passed)
```

```yaml
name: push-to-main

on:
  push:
    branches:
      - main

permissions:
  packages: write    # Required to push to ghcr.io (contents:write NOT needed — no git tags)

jobs:
  test:
    runs-on: ubuntu-latest     # Docker is pre-installed — TestContainers works out of the box
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Run tests
        run: mvn verify          # Runs unit tests + integration tests (TestContainers: PostgreSQL + Kafka)

  build-and-push:
    needs: test                  # Only runs when tests pass
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Build Spring Boot JAR
        run: mvn clean package -DskipTests      # Tests already passed in the 'test' job — no double work

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Set up QEMU (for multi-arch)
        uses: docker/setup-qemu-action@v3

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          platforms: linux/amd64,linux/arm64
          tags: |
            ghcr.io/${{ github.repository }}:latest
            ghcr.io/${{ github.repository }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

**Image tag strategy (no semver):**
- `latest` — always points to the most recent push on `main`; used by developers to pull and run
- `${{ github.sha }}` — full 40-char commit SHA; provides traceability (which commit produced which image) without requiring semver or git tags

---

## Tasks (Execution Order)

```yaml
Task 1: Check for mvnw (Maven Wrapper)
  action: Run `ls -la mvnw 2>/dev/null` in project root
  outcome: If present, use `./mvnw` in Dockerfile; if not, use Maven official image as builder

Task 2: Create Dockerfile
  file: Dockerfile (repo root)
  content: Multi-stage build as described above
  note: Use `maven:3.9-eclipse-temurin-25` for builder if available; else `eclipse-temurin:25-jdk-alpine` + install Maven
  note: Dockerfile uses -DskipTests — tests run in CI 'test' job, NOT inside Docker build
  validate: `docker build -t mars-lite:test .` succeeds locally

Task 3: Verify Dockerfile runs (optional local test)
  command: `docker run --rm mars-lite:test --help`  # Should fail gracefully (no DB) but show Spring context error
  note: Full test requires docker-compose; just verify the image builds and Java starts

Task 4: Create GitHub Actions workflow directory and file
  files:
    - .github/workflows/push-to-main.yaml
  content: Two-job workflow (test → build-and-push) as described above
  critical: NO semantic release, NO tag job, NO if condition — build-and-push runs on every push that passes tests
  critical: permissions only needs packages:write — contents:write is NOT needed

Task 5: Verify YAML syntax
  command: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/push-to-main.yaml'))" && echo "YAML valid"`
  OR install yamllint: `pip install yamllint && yamllint .github/workflows/push-to-main.yaml`

Task 6: Commit and push to main
  note: This triggers the workflow — verify in GitHub Actions tab that both jobs run
  note: No conventional commit format required — any commit message works
    example: git commit -m "ci: add GitHub Actions workflow to build and push Docker image to GHCR"
```

---

## Dockerfile Decision Tree

The executor must check if `mvnw` exists in the project root before writing the Dockerfile:

```
IF mvnw exists in project root:
    Builder: eclipse-temurin:25-jdk-alpine
    Build: COPY .mvn/ .mvn/ + COPY mvnw . + RUN chmod +x mvnw && ./mvnw clean package -DskipTests
ELSE:
    Builder: maven:3.9-eclipse-temurin-25  (or maven:3.9-eclipse-temurin-25-alpine if available)
    Build: RUN mvn clean package -DskipTests

NOTE: -DskipTests in Dockerfile is CORRECT — tests are the CI 'test' job's responsibility.
      The Dockerfile only produces the artifact; the CI pipeline enforces quality gates.
```

Check Docker Hub for valid tags:
- `maven:3.9-eclipse-temurin-25` — most likely available
- `maven:3.9-eclipse-temurin-25-alpine` — may not exist, fall back to non-alpine

---

## Validation Gates

### Level 1: Files Created
```bash
# Verify files exist
ls -la Dockerfile .github/workflows/push-to-main.yaml
# Expected: both files present
```

### Level 2: YAML Syntax
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/push-to-main.yaml')); print('YAML OK')"
# Expected: YAML OK
```

### Level 3: Docker Build (local)
```bash
docker build -t mars-lite:local .
# Expected: Successfully built <image-id>
# Note: This requires Docker daemon running locally
```

### Level 4: Maven Build Still Works
```bash
mvn clean package -DskipTests
# Expected: BUILD SUCCESS
# Expected output: target/mars-enterprise-kit-lite-1.0.0.jar
```

### Level 5: Full Project Build (no regressions)
```bash
mvn clean verify
# Expected: BUILD SUCCESS — all tests pass
```

### Level 6: GitHub Actions (post-push)
```bash
# After committing with conventional commit message and pushing:
git push origin main
# Then verify in GitHub: https://github.com/andrelucasti/mars-enterprise-kit-lite/actions
# Expected: Both 'tag' and 'build-and-push' jobs succeed
# Expected: Image visible at https://github.com/andrelucasti/mars-enterprise-kit-lite/pkgs/container/mars-enterprise-kit-lite
```

---

## Integration Points

```yaml
GITHUB_ACTIONS:
  trigger: every push to main (no conditions, no semver gate)
  permissions: packages:write only (ghcr.io push — no contents:write needed)
  secrets: GITHUB_TOKEN (automatically available, no manual secret needed)

GHCR:
  registry: ghcr.io
  repository: ghcr.io/andrelucasti/mars-enterprise-kit-lite
  tags:
    - latest         (always updated — points to most recent main commit)
    - <github.sha>   (full commit SHA — e.g., a1b2c3d4e5f6...)
  visibility: public (set in GitHub Package settings after first push)
```

---

## Anti-Patterns to Avoid

- **DO NOT** use `-Pnative` — this is Spring Boot JVM, not Quarkus
- **DO NOT** use `java-version: '21'` — project requires Java 25
- **DO NOT** skip `fetch-depth: 0` in the tag job — semantic-release needs full history
- **DO NOT** hardcode the image tag — use `${{ steps.tag.outputs.tag }}` from git describe
- **DO NOT** expose port 8080 or 8081 — app runs on 8082
- **DO NOT** run as root in the final Docker image — use a non-root user (`spring:spring`)
- **DO NOT** include docker-compose services in the Dockerfile — it's the app image only
- **DO NOT** add a `tag` job or `go-semantic-release` — semantic versioning is explicitly out of scope
- **DO NOT** add an `if:` condition to `build-and-push` — every push to `main` must produce an image
- **DO NOT** add `contents: write` permission — no git tags are created
- **DO NOT** run `mvn verify` inside the Dockerfile — tests run in the CI `test` job; Dockerfile only builds the artifact
- **DO NOT** add `services:` block to the `test` job for Postgres/Kafka — TestContainers manages its own containers automatically on the GH Actions runner

---

## Confidence Score: 9/10

- **Context completeness**: 9/10 — reference workflow included verbatim, all adaptations documented
- **Pattern availability**: 9/10 — reference workflow is the direct source; Docker multi-stage is standard
- **Validation gate coverage**: 9/10 — local Docker build + Maven + GitHub Actions post-push
- **One-pass implementation likelihood**: 9/10 — only unknown is exact Maven Docker image tag availability for Java 25 (executor must verify before writing Dockerfile)
