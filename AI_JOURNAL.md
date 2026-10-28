# AI working journal

> The brief asks for a running log of what I asked the AI, what I accepted, what I challenged and what I overrode, with brief reasoning. It does not need to be polished.
>
> **TEMPLATE: I must replace the "Round 1" placeholders with what actually happened in my own build. Round 2 below is accurate for the review session and should be checked and edited by me before I submit. Delete this banner when done.**

## Round 1: initial build (FILL IN from my real chat history)
| # | What I asked the AI | What it produced | Accepted / challenged / overrode | Why |
|---|---|---|---|---|
| 1 | _e.g. decompose the brief into entities and service boundaries_ | _..._ | _..._ | _..._ |
| 2 | _e.g. REST vs Kafka for each interaction_ | _..._ | _..._ | _..._ |
| 3 | _e.g. generate entities, repositories, service, controllers_ | _..._ | _..._ | _..._ |
| 4 | _..._ | | | |

Things the AI suggested that I rejected or changed (be specific, the panel will ask):
- _..._

## Round 2: review against the brief and hardening
I reviewed the submission against the brief and a list of likely panel questions, then asked the AI to close the gaps. What I asked for and what I decided:

| Gap found | Change made | Decision / reasoning |
|---|---|---|
| Claimants could not "provide additional information when asked": `MORE_INFO_REQUIRED` was a dead end | `POST /api/claims/{id}/information`, moves the claim back to `UNDER_REVIEW`; email must match the claimant | Accepted. Email match is a stand-in for authentication; real auth is listed as a deliberate omission |
| Claimants could not track by claim number | `GET /api/claims/by-number/{n}`, `GET /api/claims/{id}/history` | Accepted. History table doubles as the audit trail |
| No way for officers to pick up work | `unassigned=true` filter (queue); assignment by the officer's own id is the pick-up | Chose a filter over a separate queue entity: simpler, same result |
| No team workload / performance view | `GET /api/staff/workload` (open claims, per-status counts, average minutes to decision); capacity cap on assignment | Accepted. Cap default 25 is arbitrary and configurable; I would confirm with the business |
| Lost updates possible | `@Version` on `Claim`; 409 on conflict | Accepted. Chose optimistic over pessimistic locking since conflicts on one claim are rare |
| Unique-constraint races returned 500 | Catch-all handler; integrity violation and optimistic-lock failures return 409 | Accepted |
| Status transitions only partly enforced | Single transition table in `ClaimStatus`; `Claim.moveTo()` is the only way to change status | Accepted. Changed behaviour on purpose: an officer can no longer approve straight from `MORE_INFO_REQUIRED`; the claimant must answer first |
| `list()` unpaged, `status` overrode `staffId`; N+1 from eager fetches | Paging, combinable filters via Specifications, lazy associations plus entity graph, composite index | Accepted |
| Dashboard loaded every claim into memory | `GROUP BY` / `SUM` queries; exposure by market | Accepted. **Overrode the original exposure definition**: approved claims now count at the approved amount, not the claimed amount |
| Decisions had no actor and any officer could decide any claim | `staffId` required; only the assigned active officer may decide (403 otherwise) | Accepted as a stand-in for the authenticated user |
| Only a `contextLoads` test | State-machine, service (Mockito) and end-to-end MockMvc tests | Accepted; I still need to run `mvn test` locally and fix anything that fails |
| Amounts could overflow the column and return 500 | `@Digits(integer=12, fraction=2)`; `POST /api/claims` returns 201 | Accepted |

Left as-is on purpose: `SETTLE` still goes through the decision endpoint (documented in the README as a shortcut; production would have a payment-driven settlement endpoint). No auth, no Kafka, H2 by default (MySQL profile added).

## Things the AI got wrong or I had to correct
- _Add real examples from my own sessions, e.g. a bug it introduced, an assumption I corrected, a suggestion I declined._
