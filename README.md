# Claims Management Backend (Java 17 / Spring Boot 3)

Backend for the Chubb APAC claims assessment: claimants report and track claims and answer information requests; claims officers pick up, review and decide claims; managers see workload, performance and liability exposure.

## Design decisions (and why)
- **Modular monolith, one deployable service.** One bounded context, a time-boxed build, and no independent-scaling or team-ownership pressure yet. Domain packages (`domain`, `repository`, `service`, `api`) are separated so claims / staff / reporting can be extracted later.
- **REST for everything user-facing; Kafka deliberately not used yet.** Every workflow here is request/response and needs an immediate answer. Kafka earns its place when there is a real asynchronous consumer (notifications, payments, analytics). Then publish `ClaimSubmitted`, `ClaimAssigned`, `ClaimDecisionRecorded` through a **transactional outbox** (write the event row in the same DB transaction, relay it to Kafka), which avoids the "DB commit succeeded but publish failed" dual-write problem. **Not implemented.**
- **Relational model**: `Claimant` 1—N `Claim` N—1 `ClaimsStaff`; `ClaimEvent` is an append-only history/audit table per claim.
- **State machine in one place** (`ClaimStatus`): `SUBMITTED → UNDER_REVIEW → APPROVED → SETTLED`; `UNDER_REVIEW → REJECTED`; `UNDER_REVIEW ⇄ MORE_INFO_REQUIRED` (officer asks, claimant answers); `MORE_INFO_REQUIRED → REJECTED`. `Claim.moveTo()` refuses anything else with 409.
- **Concurrency**: `@Version` on `Claim` (optimistic locking) → a lost-update attempt returns 409; claimant emails are trimmed and lowercased before lookup and storage; exact repeat submissions (same normalized email, market, claim type, amount, and description) return 409, with a unique fingerprint constraint protecting concurrent submissions.
- **Read/write profile**: writes are small transactional state changes; reads are list/filter (paged, indexed on status and officer+status, entity graph to avoid N+1) and manager aggregates (computed with `GROUP BY`/`SUM` in the database, never by loading all claims).
- **Liability exposure** = claimed amount of open claims (submitted, under review, more info) + *approved* amount of approved-but-unsettled claims; rejected and settled claims are excluded. Also broken down by market. Definition is an assumption to confirm with the business.
- **Workload cap**: an officer cannot hold more than `claims.max-open-per-officer` (default 25) open claims.

## Requirements
JDK 17+, Maven 3.9+.

## Run
```bash
mvn spring-boot:run                                   # MySQL claims_management database
mvn test                                               # isolated in-memory H2 database
```
The API is available at `http://localhost:8080`; health is at `http://localhost:8080/actuator/health`. Tests use an isolated H2 database and do not require MySQL.
The application connects to `jdbc:mysql://localhost:3306/claims_management` by default. Override with `DB_URL`, `DB_USER`, and `DB_PASSWORD` for a different local MySQL setup. Flyway applies the claimant email normalization migration on startup; for an existing database it keeps the lowest-id claimant for each trimmed, case-insensitive email and reassigns related claims before creating the unique index.

## Endpoints
| Method | Endpoint | Who | Purpose |
|---|---|---|---|
| POST | `/api/claims` or `/api/claims/save` | claimant | Submit a claim (201); reuses the claimant for the same email, rejects repeats with matching normalized email, market, claim type, amount and description (409), and allows different claims |
| GET | `/api/claims/by-number/{claimNumber}` | claimant | Track a claim by its number |
| GET | `/api/claimants/{id}/claims` | claimant | List claims associated with a claimant ID |
| GET | `/api/claims/{id}/history` | both | Status history: who, when, why |
| POST | `/api/claims/{id}/information` | claimant | Answer a "more information" request (claim returns to review) |
| GET | `/api/claims?status=&staffId=&unassigned=&page=&size=` | staff | Paged, filters combine. `unassigned=true` is the pick-up queue |
| GET | `/api/claims/{id}` | both | Claim details |
| PUT | `/api/claims/{id}/assignment` | staff | Assign / pick up / reassign (idempotent for the same officer) |
| POST | `/api/claims/{id}/decision` | staff | `APPROVE`, `REJECT`, `REQUEST_MORE_INFO`, `SETTLE`; only the assigned officer |
| GET | `/api/claims/dashboard` | manager | Counts by status, outstanding exposure, approved total, exposure by market |
| GET | `/api/staff/workload` | manager | Per officer: open claims, claims per status, average minutes to decision |
| GET / POST | `/api/staff` | manager | List / create officers |
| PATCH | `/api/staff/{id}/active` | manager | Activate / deactivate an officer |

Errors share one shape: `{timestamp, status, error}` (or `errors[]` for validation): 400 invalid input, 403 wrong officer/claimant, 404 not found, 409 state conflict / concurrent update / capacity.

## Walkthrough
```bash
curl http://localhost:8080/api/staff                                   # seeded officers (ids 1, 2)

curl -X POST http://localhost:8080/api/claims \
  -H 'Content-Type: application/json' -d '{
  "claimantName":"Priya Sharma","claimantEmail":"priya@example.com","claimantPhone":"+91-9000000000",
  "market":"India","claimType":"Motor","claimedAmount":125000.00,"description":"Vehicle damage after an accident"}'

curl 'http://localhost:8080/api/claims?unassigned=true'                # pick-up queue
curl -X PUT http://localhost:8080/api/claims/1/assignment -H 'Content-Type: application/json' -d '{"staffId":1}'

curl -X POST http://localhost:8080/api/claims/1/decision -H 'Content-Type: application/json' \
  -d '{"staffId":1,"decision":"REQUEST_MORE_INFO","notes":"Please send repair estimate"}'
curl -X POST http://localhost:8080/api/claims/1/information -H 'Content-Type: application/json' \
  -d '{"claimantEmail":"priya@example.com","message":"Estimate attached"}'

curl -X POST http://localhost:8080/api/claims/1/decision -H 'Content-Type: application/json' \
  -d '{"staffId":1,"decision":"APPROVE","approvedAmount":100000.00,"notes":"Approved"}'
curl -X POST http://localhost:8080/api/claims/1/decision -H 'Content-Type: application/json' \
  -d '{"staffId":1,"decision":"SETTLE","notes":"Paid"}'

curl http://localhost:8080/api/claims/1/history
curl http://localhost:8080/api/claims/dashboard
curl http://localhost:8080/api/staff/workload
```

## Prioritisation and shortcuts (what I chose not to build)
Built: the end-to-end claimant and officer flow, the state machine, manager reporting, optimistic locking, tests.
Left out on purpose, with the reason:
- **Authentication / authorization.** `staffId` and `claimantEmail` in request bodies stand in for the authenticated user. Production: OIDC/JWT, roles (claimant, officer, manager), per-claim access checks. Until then do not expose this to the internet.
- **Kafka / outbox, notifications** (see decisions above).
- `ddl-auto: update` remains enabled for the demo; Flyway manages the normalized claimant email uniqueness migration.
- **Documents/attachments**, payment integration (`SETTLE` records a status; it does not move money; in production settlement is its own endpoint driven by a verified payment result).
- **Multi-currency**: amounts are single-currency decimals; six markets would need a currency per claim.
- **PII**: claimant email/phone should be masked in logs and access-controlled per market data-protection rules.
- Claim numbers are random 48-bit identifiers with a unique constraint (a collision returns 409 and the client retries); production would use a sequence-backed format.

## What I'd do with more time
Security and per-market data access; outbox + Kafka events and a notification consumer; document upload; a read model / reporting store if dashboard load grows; Testcontainers MySQL integration tests; observability (metrics, structured logs, correlation ids); load test of the list and dashboard queries.
