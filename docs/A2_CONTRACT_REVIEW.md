# A2 client contract review — pending agreement

Authorized working remote: https://github.com/mocenacagiisaia-glitch/CS324_A1.git.
Pre-A2 main baseline: `7b339041558adb385a57d92566a908796d701732`.
Feature branch: `feature/a2-jobs-client`.

The user explicitly authorized this existing repository on 14 October 2026. This
does not satisfy the specification's separate A2 repository/initial submitted-A1
commit requirement; the team must resolve that submission requirement. The
baseline preserves the identifiable pre-A2 tree without rewriting any history.

## What is missing

After fetching, main and both existing teammate branches contain only the A1 RMI
contracts. There are no `.proto` files or A2 branches. The current API accepts a
Job and returns BigInteger synchronously; it has no submission key, stable job ID,
state lookup, progress stream or recovery guarantee. A1's explicit pre-admission
RetryException cannot safely substitute for A2 idempotency after a transport timeout.

[jobs_client.proto](proposals/jobs_client.proto) gives concrete proposed field
numbers, message types, service names and enums. It is deliberately outside
`src/main/proto`; it is not compiled or claimed as a teammate implementation.
Use existing team definitions instead if an agreed contract is supplied.

## Contract decisions and server dependencies

- **Isaia:** implement authoritative coordinator discovery; UNAVAILABLE during
  elections/recovery. Preserve and share committed job records using the agreed
  membership configuration. Never form a smaller majority from responders.
- **Maanav:** implement job admission, lookup, streaming, stable task identities,
  increasing attempts, stale-result rejection, coordinator recovery and five-job
  rotation/JAC accounting. Krish must not replace those components.
- **Krish:** retain calculations/CSV semantics, consume generated stubs, persist
  submission receipts, monitor/reconnect, and render authoritative progress in Swing.

Required semantics for the proposed RPCs:

1. SubmitJob acknowledges only after the required majority commits admission.
   Retrying an identical key/input returns the same stable job ID without consuming
   another term slot or allocation. Same key with different input is rejected.
   The mapping and deduplication guarantee survive coordinator replacement.
2. LookupSubmission must read recovered authoritative state. During incomplete
   recovery or insufficient majority, return UNAVAILABLE rather than NOT_FOUND.
   NOT_FOUND can race an in-flight submit: it never permits a fresh submission key.
   Retrying the original key is safe only after durable deduplication is implemented.
3. ACCEPTED, completed tasks, progress and terminal state represent committed
   records. FAILED means permanent job failure; UNAVAILABLE/DEADLINE_EXCEEDED and
   interrupted streams do not imply FAILED. No confirmed result without majority.
4. WatchJob first sends the current full snapshot, then committed changes. This
   closes the GetJob/WatchJob race. EOF without terminal state triggers reconnect
   to the same ID. Revisions are monotonically increasing across leader changes.
   Older/replayed snapshots replace nothing and never add another contribution.
5. Task IDs and sizes survive reassignment. Only current accepted attempts count;
   repeated results and stale attempts are rejected by the coordinator. A snapshot
   contains exactly one current entry per task ID. Fixed total job units equal the
   sum of task totals; completed units equal accepted task credits. This first
   proposal credits whole completed tasks only, avoiding invented partial progress.
6. Worker breakdown groups credited units by the worker of the accepted attempt.
   A retry cannot add another logical task or credit abandoned work twice. Empty
   PRIMECOUNT has zero units: show 100% only on committed COMPLETED, else 0%.
7. Numbers/endpoints are sint32, ranges inclusive, results canonical decimal
   BigInteger strings. Reject unspecified states/types, conflicting input fields,
   empty MAX and reversed ranges. Numeric progress values must fit supported Java
   arithmetic (the input domain's work size fits long).
8. Agree stream liveness (for example bounded stream lifetime with reconnection),
   cancellation behavior, and RPC status handling before client integration.

## Client behavior after agreement

One Java 21 project retains shared protocol, bootstrap, workers and client/GUI
packages. Generate shared Java messages/stubs in the agreed build. Keep pure
JobEngine integration and teammates' ownership boundaries.

Persist a local receipt (submission key, immutable input, then confirmed job ID)
before/after submission. On uncertainty reconcile the same key; never silently
create another job. Offer Resume for existing IDs. Fetch and watch that same ID
after disconnect or coordinator rotation; retain it on retry exhaustion.

Perform file/network/reconnect work outside the EDT; marshal Swing updates onto
the EDT. Render genuine work percentages and a clickable/keyboard-accessible
worker breakdown. Display connection availability separately from committed job
state. Proposed defaults: 5-second unary deadlines; six cancellable retries with
250ms, 500ms, 1s, 2s, 4s, 4s delays, then unavailable with explicit Resume.
Closing the GUI cancels monitors and closes channels/executors; it does not cancel
accepted remote jobs unless the team agrees a separate cancellation contract.

## Verification limits

Calculation and CSV regression checks can run now. gRPC components cannot be
integrated against agreed interfaces until contract agreement. Real recovery,
majority acknowledgement, stale attempt acceptance and coordinator replacement
checks require Isaia's and Maanav's actual A2 implementations. Mock/fixture checks
must be reported as component evidence only. Do not merge incomplete A2 work into
main solely because the legacy RMI suite passes.

## Independent client implementation (contracts remain proposed)

At the user's request, `client/progress` now provides a local Java interface,
snapshot model, background monitor, EDT-only progress view and a separate GUI.
These are development components, not approved Protocol Buffer definitions.
`SimulatedJobClient` computes real chunks locally but has no network, majority,
replication, elections or coordinator replacement. Its stream disconnect action
is explicitly simulated. Tests use scripted fixtures for uncertain admission,
reconnect, stale/replayed snapshots and temporary unavailability; those fixtures
are not evidence that the teammates' server implements recovery correctly.

### Exact adapter methods and required server behavior

| Local `JobClient` method | Proposed RPC dependency | Behavior required from owner |
|---|---|---|
| `locateCoordinator()` | Isaia: `CoordinatorDirectory.LocateCoordinator` | Refresh authoritative host/port/worker/term identity before each reconnect; unavailable during election or incomplete recovery |
| `submit(key, input)` | Maanav: `JobService.SubmitJob` | Return stable job ID in committed snapshot only after agreed majority confirmation; validate input; durable deduplication by identical key/input; allocate once |
| `lookupSubmission(key)` | Maanav: `JobService.LookupSubmission`, backed by Isaia's shared records | Resolve ambiguous admission using the same original key; authoritative NOT_FOUND only after recovery; UNAVAILABLE when quorum/recovery cannot establish absence |
| `getJob(id)` | Maanav: `JobService.GetJob` | Return latest committed state/result/tasks for the same job ID from recovered records; no replacement job on missing records |
| `watchJob(id, updates)` | Maanav: `JobService.WatchJob` | Send current full snapshot first, then committed changes; stable IDs/attempts and deduplicated credits; monotonically recovered revisions; defined stream liveness and interruptible cancellation |
| `close()` | gRPC adapter owned by Krish after agreement | Cancel calls/streams and release channels with bounded shutdown; not a remote job cancellation RPC |

The local model requires every snapshot to include one current entry per stable
task ID, with fixed work totals. Accepted snapshots must already describe that
task set. Equal revisions must have identical content. A higher revision may
increase an attempt but may not regress it or change the stable job/task IDs or
task sizes. Map task state/attempt/result fields from the agreed proto and reject
invalid snapshots before rendering. Task-result acceptance/rejection remains
Maanav's responsibility, not a client-side calculation of server progress.

The monitor sends submit at most once, then only reconciles its key on uncertainty.
Key-only Resume does not submit again even after NOT_FOUND. Reconnect attempts
are bounded across each monitoring run. A gRPC adapter must bound unary/discovery
RPCs (proposed 5-second deadline), implement stream liveness, and translate
transport unavailable/deadline errors to local Unavailable. Other RPC/input errors
need truthful error details. Permanent job failure comes from committed FAILED
state; temporary transport errors never manufacture a permanent failed job.

The GUI keeps IDs/keys in memory only. Durable cross-process receipts, actual
gRPC wiring, server admission/attempt handling, quorum behavior, and real
coordinator/worker recovery remain blocked on agreed contracts and teammate
implementations. The proposal files remain ready for Isaia/Maanav review.
