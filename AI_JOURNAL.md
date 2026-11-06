# AI working journal

This log records the review and changes made in this session. The initial implementation was already present when this session began; I have not reconstructed or invented the earlier AI conversation. Add the actual prompts, decisions, and corrections from the original build before submitting.

## Initial implementation (complete from actual build history)

| What I asked the AI | What it produced | What I accepted, challenged, or overrode | Why |
|---|---|---|---|
| _Fill in from the original build conversation; do not guess._ | | | |

## Review and implementation — 6 November 2026

| What I asked the AI | What it identified or changed | Decision | Reason |
|---|---|---|---|
| Compare the implementation with the take-home brief and identify omissions. | Identified the missing journal, a hardcoded database credential, lack of currency modeling across markets, an assignment-cap concurrency race, and the lack of authentication. | Accepted the journal, credential, and concurrency fixes. Currency and authentication choices need an explicit design decision before implementation. | These are concrete deliverable/integrity issues; currency conversion and identity provider choices change domain behavior and security boundaries. |
| Implement the missing items. | Restored the journal as an honest record of this session; changed DB credentials to required environment variables; added a Compose MySQL setup; serialize assignments per officer with a database row lock. | Accepted. | Keeps credentials out of configuration source, makes local setup reproducible, and prevents two concurrent assignments from both consuming the same remaining capacity. |
| Run the clean Maven build and fix any failures. | The first run exposed one stale unit-test mock: it stubbed the old staff lookup, so the inactive-officer case returned 404 instead of 400 after assignment began using the locked lookup. | Changed the test to stub `findByIdForUpdate`; reran the full build successfully. | The production behavior was correct; the test setup needed to follow the new repository call. |

## Decisions still to record

- Fill in the actual initial AI prompts, accepted suggestions, challenged outputs, and overrides from the original build.
- Decide and document the currency model and authentication approach before implementing either.
- Verify local MySQL/Compose startup separately; the Maven test suite uses H2 and does not exercise the MySQL container or Flyway migrations.
