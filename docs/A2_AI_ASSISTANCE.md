# A2 AI assistance record

Date: 14 October 2026, Pacific/Fiji.
Tool: OpenAI Codex, with the Ponytail coding skill.

Codex read the user's pasted request and both assignment specifications, inspected
the nearby extracted A1 code, and prepared the initial workspace contract proposal
and disclosure record. After the user explicitly authorized the existing
`mocenacagiisaia-glitch/CS324_A1` remote for A2, Codex cloned it into `CS324_A2`,
fetched branches, recorded pre-A2 baseline
`7b339041558adb385a57d92566a908796d701732`, and created
`feature/a2-jobs-client` from `origin/main` without altering prior files/history.

Inspected README, contribution/election documentation, Maven configuration,
existing client/calculation/shared RMI APIs and remote branch trees. No AGENTS.md,
agreed A2 proto or A2 server implementation was present. Prepared the concrete
pending proto under `docs/proposals`, documented teammate dependencies, and added
CalculationCompatibilityTest with three input-to-split/compute/aggregate regression
tests. Updated README with actual verification commands and explicit A2 limits.

The calculations and A1 clients were already implemented in the baseline; they
must not be reported as new A2 implementation. No new gRPC client, GUI progress,
receipt/reconnect, coordinator, membership or replication implementation was
introduced without agreed contracts. The proto is a review artifact only.

Initial baseline verification: `mvn clean verify -Pintegration` passed 26 unit/RMI
tests and one real multi-process integration test. Maven ran on Oracle JDK 24.0.2
with `--release 21`; runtime verification on Java 21 remains outstanding.
After adding the three compatibility tests, `mvn clean verify -Pintegration`
passed 29 unit/RMI tests and one separate-process integration test (zero failures,
errors or skips). The real integration checks 24 calculations from two client
JVMs, coordinator rotation/agreement and 72 remote allocations. This is A1 RMI
evidence only. The proposal has not been compiled with protoc, and Swing interaction,
A2 streaming, majority unavailability and coordinator recovery were not verified.
`git diff --check` passed. A fresh fetch left origin/main at the recorded baseline.

The feature branch is being committed/pushed for review as authorized; main will
not be merged because A2 contracts and server integration are missing. Commit IDs
can be obtained with `git log origin/main..feature/a2-jobs-client --oneline`.

The A2 specification requires a separate report appendix page identifying AI tools
and explaining their use by section/task, plus Git log and member contributions.
Use this factual record for that page; do not attribute AI-generated work as
independent student implementation or mock checks as real recovery evidence.

## Independent GUI development continuation

The user explicitly requested independent GUI/progress/monitoring development
behind a local interface, while keeping the shared proto proposed and main
unchanged. Codex added the `client/progress` package: JobClient transport boundary,
validated full JobSnapshot UI model, background JobMonitor, EDT-only
JobProgressPanel, separate ProgressGui and clearly labeled SimulatedJobClient.

The simulator uses real IntegerJobEngine calculations and credits completed local
chunks, but runs within one JVM with in-memory records. It does not implement
gRPC, worker/coordinator failure tolerance, majority replication or durable
recovery. Its disconnect button only interrupts simulated monitoring streams.
GUI identities/keys survive interruptions in the session but not process restart.

Added component tests for weighted progress/worker credits, duplicate/older
snapshots, stale attempts, changed job IDs/conflicting revisions, uncertain
submission reconciliation without replay, retry exhaustion, key-only resume,
missing records, permanent failure, stream EOF/cancellation, reconnect status,
headless EDT view updates and simulation calculations/concurrent jobs/closure.
These are fixtures/local simulation evidence, not end-to-end A2 recovery evidence.

Updated README with exact simulation launch and focused test commands, and
A2_CONTRACT_REVIEW.md with method-by-method adapter/server dependencies.
`docs/proposals/jobs_client.proto` remains unchanged, proposed, outside the
generated source tree, and unapproved. Teammate production code is unchanged.

Java 21 runtime was not found in checked system/user Java installation locations;
Maven uses Oracle JDK 24.0.2 targeting release 21. Manual visual/mouse/keyboard
demonstration, a Java 21 runtime run, actual gRPC wiring, durable receipts and
distributed A2 integration remain outstanding. This work stays on the feature
branch until contracts are agreed and actual integration checks pass.

Final verification for this continuation: `mvn clean verify -Pintegration` passed
49 unit/component/RMI tests and one legacy real-process integration test. After
adding a regression check for restoring connection status when a reconnect
returns unchanged progress, `mvn verify -Pintegration` passed 50
unit/component/RMI tests and one legacy real-process integration test, with zero
failures/errors/skips. Of the unit tests, 21 cover the new progress components.
`git diff --check` passed. A fresh fetch still showed main at the pre-A2 baseline.
