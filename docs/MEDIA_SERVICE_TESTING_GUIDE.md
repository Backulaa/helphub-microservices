# HELPhub Media Service — Testing Guide and Recorded Results

**Project:** HELPhub Microservices  
**Modules covered:** `services/media-service` and related `gateway/api-gateway` HTTP security tests  
**Environment:** Windows PowerShell, Docker Desktop, Java 21/Maven 3.9 via Docker, isolated PostgreSQL and RabbitMQ test containers  
**Recorded verification date:** November 19, 2026  
**Status:** All reported test runs listed below passed. This is a record of the development environment results, **not** a claim that production deployment has been certified.

## 1. Executive summary

| Test execution | Tests | Failed | Errors | Skipped | Recorded outcome |
|---|---:|---:|---:|---:|---|
| Media Service full regression (`mvn clean test`) | 62 | 0 | 0 | 0 | **BUILD SUCCESS** |
| API Gateway JWT, CORS, and header HTTP tests (`GatewaySecurityCorsIT`) | 19 | 0 | 0 | 0 | **BUILD SUCCESS** |
| Live private-access grant tests (`PrivateAccessGrantHttpIT`) | 3 | 0 | 0 | 0 | **BUILD SUCCESS** |
| **Total executed automated tests** | **84** | **0** | **0** | **0** | **All passed in their respective runs** |

**Additional verified behavior (not counted as automated tests):**

- Two cleanup records in the development PostgreSQL database were confirmed as `media.status = DELETED` and `media_cleanup_jobs.status = COMPLETED`, with `completed_at` populated. They completed after one and two attempts, respectively.
- The corresponding deleted assets were independently confirmed absent from the configured Cloudinary environment.
- The live private-grant test confirmed that a signed private URL delivered content before expiry and was rejected by Cloudinary after expiry.
- Earlier manually executed Gateway checks (11 JWT/security, 4 CORS, 4 security-header checks) were subsequently converted into the 19-test JUnit class. **Do not add these 19 manual checks again to the automated total.**

> **Important:** The 62 Media Service regression tests **exclude** `PrivateAccessGrantHttpIT` because that class is named `*IT`, not `*Test`. It must be run explicitly. The 19 Gateway tests are executed in a different Maven module.

## 2. Prerequisites and safety

1. Open PowerShell in the repository root, for example `C:\Users\LENOVO\Desktop\helphub-microservices`.
2. Install and start Docker Desktop; a local Maven installation is **not required**.
3. Have the Maven Docker image `maven:3.9-eclipse-temurin-21` available (Docker will pull it if necessary).
4. For the full Media Service suite, start the **isolated test** PostgreSQL and RabbitMQ containers described below. They must not point to the application's normal `helphub_media` database.
5. For Gateway HTTP tests and live Cloudinary tests, start the HELPhub development stack and use disposable development credentials.
6. Never commit `.env`, real passwords, JWTs, internal service keys, Cloudinary secrets, or signed access URLs. Do not include secrets in CI logs or issue reports.
7. Do not run the live test against production: it creates a real Cloudinary asset and schedules its later deletion.

### 2.1 Create the isolated integration-test network and services (first use only)

```powershell
# Execute once, only if this network does not already exist.
docker network create helphub-media-it-net

# Isolated PostgreSQL database for integration tests.
docker run -d `
    --name helphub-media-it-db `
    --network helphub-media-it-net `
    -e POSTGRES_DB=media_it `
    -e POSTGRES_USER=media_it `
    -e POSTGRES_PASSWORD=media_it_local_only `
    postgres:17-alpine

# Isolated RabbitMQ broker for integration tests.
docker run -d `
    --name helphub-media-it-rabbit `
    --network helphub-media-it-net `
    -e RABBITMQ_DEFAULT_USER=media_it `
    -e RABBITMQ_DEFAULT_PASS=media_it_local_only `
    rabbitmq:4-management
```

If the containers already exist, **do not rerun `docker run --name ...`** (it will fail with a name conflict). Start the existing containers instead:

```powershell
docker start helphub-media-it-db
docker start helphub-media-it-rabbit
```

Verify their status and connectivity:

```powershell
docker ps -a --filter "name=helphub-media-it" `
    --format "table {{.Names}}\t{{.Status}}\t{{.Networks}}"

docker exec helphub-media-it-db pg_isready -U media_it -d media_it
docker exec helphub-media-it-rabbit rabbitmq-diagnostics -q ping

docker network inspect helphub-media-it-net `
    --format '{{range .Containers}}{{.Name}} {{end}}'
```

Expected: PostgreSQL reports `accepting connections`; RabbitMQ reports `Ping succeeded`; both containers belong to `helphub-media-it-net`.

**Isolation warning:** The integration tests create and drop test tables in `media_it`. Do not run other tests using the same database concurrently. Never substitute the HELPhub development application's database credentials or database name.

## 3. Run the complete Media Service regression suite (62 tests)

Run from the repository root:

```powershell
$projectDir = (Get-Location).Path

docker run --rm `
    --network helphub-media-it-net `
    --mount "type=bind,source=$projectDir,target=/workspace" `
    -v helphub_maven_cache:/root/.m2 `
    -e MEDIA_IT_JDBC_URL=jdbc:postgresql://helphub-media-it-db:5432/media_it `
    -e MEDIA_IT_JDBC_PASSWORD=media_it_local_only `
    -e MEDIA_IT_RABBIT_HOST=helphub-media-it-rabbit `
    -e MEDIA_IT_RABBIT_PASSWORD=media_it_local_only `
    -w /workspace `
    maven:3.9-eclipse-temurin-21 `
    mvn -B -ntp `
    -pl services/media-service `
    -am `
    clean test
```

**Recorded result (after the final Cleanup Worker changes):**

```text
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

`-pl services/media-service` targets Media Service; `-am` builds its required reactor modules. `clean` removes previous compilation/test artifacts. `test` runs the Maven Surefire default test suite.

### 3.1 Run a specific Media Service test class

For **unit tests** that do not require isolated infrastructure, use the same Maven container without database/broker environment variables. Example:

```powershell
$projectDir = (Get-Location).Path

docker run --rm `
    --mount "type=bind,source=$projectDir,target=/workspace" `
    -v helphub_maven_cache:/root/.m2 `
    -w /workspace `
    maven:3.9-eclipse-temurin-21 `
    mvn -B -ntp `
    -pl services/media-service `
    -am `
    "-Dtest=PrivateAccessGrantServiceTest,CloudinaryTimedDownloadServiceTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    test
```

For **PostgreSQL or RabbitMQ integration tests**, use the Docker network, test JDBC URL and RabbitMQ settings from Section 3, but replace `clean test` with:

```powershell
"-Dtest=MediaCleanupIntegrationTest,MediaOutboxRabbitIntegrationTest" `
"-Dsurefire.failIfNoSpecifiedTests=false" `
test
```

To run an individual class, replace the comma-separated value after `-Dtest=` with exactly one class name. The `surefire.failIfNoSpecifiedTests=false` flag prevents an upstream reactor module without the selected class from failing unnecessarily; it does **not** turn a failing selected test into a pass.

## 4. Media Service test inventory and coverage (62 tests)

The following counts are the results confirmed by Maven. The one original `MediaUploadFlowSimulationTest` accounts for the difference between 61 newly added tests and the 62-test full suite.

| Test class | Count | Level | Recorded result |
|---|---:|---|---|
| `CloudinaryAssetDeletionServiceTest` | 4 | Unit / Mockito | 4/4 PASS |
| `MediaCleanupWorkerTest` | 4 | Unit / Mockito | 4/4 PASS |
| `MediaCleanupIntegrationTest` | 5 | PostgreSQL integration | 5/5 PASS |
| `MediaOutboxRabbitIntegrationTest` | 2 | PostgreSQL + RabbitMQ integration | 2/2 PASS |
| `PrivateUploadFileValidatorTest` | 8 | Unit | 8/8 PASS |
| `MediaUploadSessionServiceTest` | 11 | Unit / Mockito | 11/11 PASS |
| `MediaPrivateUploadServiceTest` | 6 | Unit / Mockito | 6/6 PASS |
| `PrivateAccessGrantServiceTest` | 13 | Unit / Mockito | 13/13 PASS |
| `CloudinaryTimedDownloadServiceTest` | 8 | Unit / Mockito | 8/8 PASS |
| `MediaUploadFlowSimulationTest` | 1 | Existing simulation | 1/1 PASS in full regression |
| **Total** | **62** | | **62/62 PASS** |

### 4.1 Cloudinary asset deletion — 4 tests

**Class:** `com.helphub.media.media.cloudinary.CloudinaryAssetDeletionServiceTest`

- Cloudinary returns `result = ok`: deletion succeeds and the expected resource type/delivery type are sent.
- Cloudinary returns `result = not found`: deletion remains idempotent, allowing safe retries after prior deletion.
- Cloudinary returns an unexpected result: the service rejects an unconfirmed deletion.
- Cloudinary throws an `IOException`: the storage error propagates for cleanup retry handling.

Cloudinary is **mocked** in these four tests. The absence of actual assets was checked separately in the development Cloudinary console.

### 4.2 Cleanup Worker unit tests — 4 tests

**Class:** `com.helphub.media.cleanup.worker.MediaCleanupWorkerTest`

- Cloudinary timeout schedules another attempt (`PENDING`, future `next_retry_at`).
- Exhausting the maximum attempts marks the job `FAILED`.
- Successful deletion finalizes the job as `COMPLETED` and media as `DELETED`.
- A stale worker whose job has been reclaimed cannot finalize media based on its old claim.

These tests inspect JDBC calls with Mockito; they do not themselves prove PostgreSQL transaction semantics.

### 4.3 Cleanup Worker PostgreSQL integration — 5 tests

**Class:** `com.helphub.media.cleanup.worker.MediaCleanupIntegrationTest`

- A Cloudinary timeout persists retry state and increases `attempts` in PostgreSQL.
- The maximum attempt count persists terminal `FAILED` state with no future retry time.
- A row held under `FOR UPDATE` is skipped without waiting (`SKIP LOCKED`).
- Two workers do not process the same claimed job concurrently; one deletion completes the job.
- Cloudinary deletion succeeds but database finalization fails: the transaction rolls back; after the failure is removed, retry accepts Cloudinary `not found` and reaches `COMPLETED` / `DELETED`.

Uses the **isolated** `media_it` PostgreSQL container. Cloudinary deletion is mocked. The rollback test simulates a PostgreSQL constraint rejection, not a total loss of database connectivity.

### 4.4 RabbitMQ outbox publisher integration — 2 tests

**Class:** `com.helphub.media.outbox.publisher.MediaOutboxRabbitIntegrationTest`

- **Unroutable message:** exchange exists but no queue is bound; RabbitMQ returns the message and the outbox schedules retry instead of marking the record `PUBLISHED` (`UNROUTABLE_MESSAGE`).
- **Routable message:** matching queue and binding exist; the outbox marks the event `PUBLISHED` and the message arrives in the queue.

Uses the **isolated** PostgreSQL and RabbitMQ test containers. The test queue is declared **durable** for compatibility with RabbitMQ 4; it is deleted during test cleanup.

### 4.5 Private upload file validator — 8 tests

**Class:** `com.helphub.media.media.service.PrivateUploadFileValidatorTest`

- Accepts a PNG signature matching the requested file type.
- Accepts a PDF signature matching the requested file type.
- Rejects a null file.
- Rejects an empty file.
- Rejects a filename that differs from the upload session.
- Rejects a mismatched MIME type.
- Rejects fake PNG content that lacks PNG magic bytes.
- Rejects an oversized image.

**Coverage limit:** A valid magic-byte header does not guarantee that an entire image or PDF is structurally intact or decodable.

### 4.6 Upload session creation — 11 tests

**Class:** `com.helphub.media.media.service.MediaUploadSessionServiceTest`

Covers disallowed executable types; MIME/extension mismatch; path traversal; image above 10 MiB; PDF above 20 MiB; zero-byte declarations; valid `PRIVATE` upload sessions (`SERVER`); valid `PUBLIC` image upload sessions (`DIRECT`); public PDF using Cloudinary `RAW`; exact image-size boundary; and filename/MIME normalization.

The tests mock Cloudinary signing and database persistence. They do not establish that a real Cloudinary direct-upload signature succeeds in production.

### 4.7 Private upload service — 6 tests

**Class:** `com.helphub.media.media.service.MediaPrivateUploadServiceTest`

Verifies that the service rejects fake PNG bytes, mismatched MIME, mismatched filename, non-owner upload, use of the private-upload endpoint for public media, and attempts to upload media already in the uploading state. Rejected requests must not call Cloudinary or begin the upload transaction.

### 4.8 Private access-grant authorization — 13 tests

**Class:** `com.helphub.media.media.access.service.PrivateAccessGrantServiceTest`

Tests successful exact Community reference binding; missing reference; wrong resource ID; unknown media; media in `PENDING`, `UPLOADING`, `DELETING`, or `DELETED`; public media; Community trying to grant a Support resource; Support trying to grant a Community post; unknown internal service; and valid exact Support request binding.

The service uses a locked media lookup (`findByIdForUpdate`) and rejects media that is no longer active. The authorization test mocks references and timed URL generation.

### 4.9 Timed Cloudinary download URL generation — 8 tests

**Class:** `com.helphub.media.media.access.service.CloudinaryTimedDownloadServiceTest`

Covers signed authenticated image URLs with a 120-second expiry, authenticated raw/PDF URLs, invalid TTL outside 15–300 seconds, missing asset ID, missing format, insecure URL, null URL, and Cloudinary signing failure with a safe API error.

These tests mock Cloudinary URL generation. Actual URL delivery/expiry is verified in the separate live HTTP test.

### 4.10 Existing upload simulation — 1 test

**Class:** `com.helphub.media.media.MediaUploadFlowSimulationTest`

Exercises the existing public upload/completion simulation. Included in the full 62-test Media Service run.

## 5. API Gateway security and CORS HTTP regression (19 tests)

**Class:** `com.helphub.gateway.GatewaySecurityCorsIT`  
**File:** `gateway/api-gateway/src/test/java/com/helphub/gateway/GatewaySecurityCorsIT.java`

These are end-to-end HTTP tests against a **running development stack** (API Gateway, Identity Service, Media Service). They fetch a CSRF token, log in with a disposable test account, and obtain a **fresh JWT** rather than reusing an expired token.

### 5.1 Start or verify the development stack

```powershell
docker compose -f compose.yaml -f compose.dev.yaml ps

# If services are not already running, start the development stack:
# docker compose -f compose.yaml -f compose.dev.yaml up -d
```

Set the credentials in the **current PowerShell session**, without printing the password:

```powershell
$env:HELPHUB_TEST_EMAIL = "requester.demo@helphub.local"
$securePassword = Read-Host "DEV test account password" -AsSecureString
$env:HELPHUB_TEST_PASSWORD = `
    [System.Net.NetworkCredential]::new("", $securePassword).Password
Remove-Variable securePassword
```

### 5.2 Run all 19 Gateway HTTP tests

```powershell
$projectDir = (Get-Location).Path

docker run --rm `
    --mount "type=bind,source=$projectDir,target=/workspace" `
    -v helphub_maven_cache:/root/.m2 `
    -e HELPHUB_TEST_EMAIL `
    -e HELPHUB_TEST_PASSWORD `
    -e HELPHUB_GATEWAY_URL=http://host.docker.internal:8080 `
    -e HELPHUB_MEDIA_URL=http://host.docker.internal:8082 `
    -e HELPHUB_FRONTEND_ORIGIN=http://localhost:5173 `
    -w /workspace `
    maven:3.9-eclipse-temurin-21 `
    mvn -B -ntp `
    -pl gateway/api-gateway `
    -am `
    "-Dtest=GatewaySecurityCorsIT" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    test
```

**Recorded result:** `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.

### 5.3 What the 19 tests verify

| Group | Count | Checks |
|---|---:|---|
| JWT / Gateway / Media security | 11 | Public health endpoints (2); GET without JWT; malformed JWT; unauthenticated upload-session POST; unauthenticated DELETE; internal API without key; wrong internal key; authenticated `/auth/me`; authenticated request for missing media (404); Gateway does not expose `/internal/media/references` |
| CORS | 4 | Allowed GET preflight; allowed POST preflight; disallowed origin rejected; authenticated GET includes expected CORS headers |
| Security / request headers | 4 | Forged identity headers without JWT still rejected; forged identity headers with valid JWT do not override authentication; client-provided `X-Request-ID` is replaced; independent requests receive different IDs |
| **Total** | **19** | |

**Limit:** These HTTP tests do not directly inspect the headers received by the downstream service, and therefore do not alone prove the Gateway stripped every forged header on the forwarded request. Use dedicated filter/downstream tests for that guarantee.

## 6. Live Cloudinary private-grant HTTP integration (3 tests)

**Class:** `com.helphub.media.media.access.PrivateAccessGrantHttpIT`  
**File:** `services/media-service/src/test/java/com/helphub/media/media/access/PrivateAccessGrantHttpIT.java`

> **DEV ONLY — state-changing test.** It creates a real private PNG in Cloudinary, registers a reference, obtains a signed URL, checks access before and after expiry, detaches the reference, and schedules soft deletion. Physical asset cleanup is asynchronous (normally after the 24-hour grace period). Do not use production accounts, production datasets, or production Cloudinary credentials.

### 6.1 Required environment variables

Reuse `HELPHUB_TEST_EMAIL` and `HELPHUB_TEST_PASSWORD` from Section 5, then configure:

```powershell
$secureKey = Read-Host "MEDIA_INTERNAL_COMMUNITY_KEY (DEV)" -AsSecureString
$env:HELPHUB_COMMUNITY_KEY = `
    [System.Net.NetworkCredential]::new("", $secureKey).Password
$env:HELPHUB_RUN_LIVE_GRANT_IT = "true"
Remove-Variable secureKey
```

The internal key must match the running Media Service DEV configuration (at least 32 characters). The development stack and Cloudinary integration must be operating normally.

### 6.2 Run the live test explicitly

```powershell
$projectDir = (Get-Location).Path

docker run --rm `
    --mount "type=bind,source=$projectDir,target=/workspace" `
    -v helphub_maven_cache:/root/.m2 `
    -e HELPHUB_TEST_EMAIL `
    -e HELPHUB_TEST_PASSWORD `
    -e HELPHUB_COMMUNITY_KEY `
    -e HELPHUB_RUN_LIVE_GRANT_IT `
    -e HELPHUB_GATEWAY_URL=http://host.docker.internal:8080 `
    -e HELPHUB_MEDIA_URL=http://host.docker.internal:8082 `
    -w /workspace `
    maven:3.9-eclipse-temurin-21 `
    mvn -B -ntp `
    -pl services/media-service `
    -am `
    "-Dtest=PrivateAccessGrantHttpIT" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    test
```

**Recorded result:** `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.

**Three test cases:**

1. `grant01_missingInternalKeyIsUnauthorized`: internal grant endpoint returns HTTP **401** when the internal key is absent.
2. `grant02_wrongResourceBindingIsNotFound`: incorrect resource binding returns HTTP **404**.
3. `grant03_realSignedUrlWorksThenExpires`: a legitimate grant is issued; Cloudinary returns **200** while the signed URL is valid; after the issued expiry time, Cloudinary rejects it with **401 or 403**.

This test waits internally until the grant expires. It is intentionally not part of the normal fast `mvn test` cycle. If execution fails partway through, inspect the disposable asset's cleanup status in DEV before rerunning.

### 6.3 Clear sensitive values when finished

```powershell
Remove-Item Env:HELPHUB_TEST_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:HELPHUB_COMMUNITY_KEY -ErrorAction SilentlyContinue
Remove-Item Env:HELPHUB_RUN_LIVE_GRANT_IT -ErrorAction SilentlyContinue
```

## 7. Additional database and Cloudinary checks (manual, previously verified)

### 7.1 Check completed cleanup jobs in the development database

The application Docker Compose service is named `postgres`; its actual container name may differ. Use `docker compose exec`, not `docker exec postgres`:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml exec -T postgres `
    psql -U helphub_media_app -d helphub_media `
    -c "SELECT m.id, m.public_id, m.status AS media_status, j.status AS cleanup_status, j.attempts, j.completed_at FROM media m JOIN media_cleanup_jobs j ON j.media_id = m.id WHERE j.status = 'COMPLETED' ORDER BY j.completed_at DESC LIMIT 5;"
```

**Recorded observation:** two rows had `media_status = DELETED`, `cleanup_status = COMPLETED`, non-null `completed_at`, and `attempts` of **1** and **2**.

### 7.2 Confirm Cloudinary deletion

In the correct Cloudinary environment, search Media Library for the `public_id` of each completed cleanup record. Check the appropriate resource type and private `authenticated` delivery type. **Recorded observation:** both assets were confirmed deleted.

The database status alone does not prove physical Cloudinary deletion; both checks were performed.

## 8. Test reports and troubleshooting

- **Surefire reports (Media Service):** `services/media-service/target/surefire-reports/`
- **Surefire reports (Gateway):** `gateway/api-gateway/target/surefire-reports/`
- **A Maven run is successful only when:** test failures = 0, errors = 0, and the reactor ends with `BUILD SUCCESS`.
- **`UnknownHostException: helphub-media-it-db`:** PostgreSQL test container is stopped or the Maven container is not attached to `helphub-media-it-net`. Check `docker ps -a`, `docker start` and Docker network membership.
- **Docker container-name conflict:** The container already exists. Use `docker start`; do not create another container with the same name.
- **RabbitMQ `transient_nonexcl_queues` error:** RabbitMQ 4 rejects transient non-exclusive queues. The RabbitMQ integration test uses a durable test queue.
- **JWT-based HTTP test returns 401:** Confirm the test credentials and whether the account requires MFA. The JUnit HTTP tests log in for a fresh JWT; manual reused tokens can expire.
- **Mockito Java-agent warnings:** These were present in successful runs and were **not** the cause of test failures. A future JDK/Mockito configuration may require an explicit Java agent.
- **Tests show `Media not found` unexpectedly:** The production grant service loads media using `findByIdForUpdate`, not `findById`. Unit-test mocks must match the actual repository method.
- **Concurrent integration test runs:** Avoid them when they share the same `media_it` database because setup methods may drop/recreate tables.
- **Cloudinary request costs:** Only the explicit live test uploads a real asset; unit and database integration tests use mocks for asset operations.

## 9. Recommended repeatable verification workflow

1. Start isolated PostgreSQL and RabbitMQ test containers.
2. Run the **62-test Media Service full regression** (Section 3).
3. Start/verify the HELPhub DEV stack; export disposable DEV login credentials.
4. Run the **19-test Gateway HTTP regression** (Section 5).
5. Only when testing live Cloudinary behavior or preparing a release candidate, deliberately opt in to the **3-test private-grant HTTP suite** (Section 6).
6. Check relevant PostgreSQL cleanup jobs and Cloudinary assets when validating deletion behavior (Section 7).
7. Store Surefire test results in the CI/job artifacts and clear secret environment variables.

### Remaining limitations / areas for follow-up

- Upload validation currently checks MIME/filename/magic bytes, but does not prove a file with a valid header is fully decodable.
- A Cleanup Worker claiming multiple jobs in one batch may encounter lease expiry on later jobs under prolonged external-service delays; consider smaller batches, bounded Cloudinary call timeouts, or lease renewal for higher throughput.
- HTTP header tests do not directly verify every downstream forwarded header value.
- The normal regression suite relies on isolated PostgreSQL/RabbitMQ infrastructure; the live Cloudinary test additionally relies on a configured DEV stack and external availability.
- Passing these tests supports development/MVP readiness; it is not a substitute for production configuration review, load testing, operational observability, and secret-management review.

---

**Recorded final results:** **62/62 Media Service**, **19/19 Gateway HTTP**, **3/3 live grant HTTP**; **84 automated tests passed in separate recorded executions**, plus successful manual PostgreSQL/Cloudinary deletion verification.
