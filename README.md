# CS324 A1 — three-person development project

## A2 work in progress

The user authorized this existing remote for A2 development on 14 October 2026.
Pre-A2 `main` baseline: `7b339041558adb385a57d92566a908796d701732`.
Krish's branch is `feature/a2-jobs-client`. The assignment's separate A2 repository
requirement still needs resolution by the team; retaining this baseline does not
fulfil that requirement.

No shared A2 Protocol Buffer interfaces exist on the fetched branches. Review
[the concrete client contract proposal](docs/A2_CONTRACT_REVIEW.md) and
[proposed proto](docs/proposals/jobs_client.proto) before implementing against it.
It is not generated or wired into the build. Existing calculations, CSV, GUI and
headless client remain the A1 implementation described below. A separate development
GUI now displays progress, worker breakdown and reconnection status through a local
client interface. Actual gRPC streaming, durable job receipts and distributed
recovery are **not yet implemented**.

From the folder containing `pom.xml`, verify Krish's calculation/CSV compatibility:

```powershell
mvn "-Dtest=IntegerJobEngineTest,JobInputTest,CalculationCompatibilityTest" test
mvn clean verify -Pintegration
```

The integration profile exercises the actual legacy RMI cluster with four worker
and two client processes; it is not A2 gRPC/failure-recovery evidence. Java source
targets release 21. A runtime check on Java 21 is required if building with a newer
JDK. The launch commands below run the existing A1 components; there is no working
A2 gRPC launch command until the shared/server integration is available.

See [A2 assistance record](docs/A2_AI_ASSISTANCE.md) for accurate disclosure and
verification limits. Keep this unfinished A2 branch out of `main` until contracts
and integration checks are complete.

### Krish's independent progress GUI

Build and launch the explicitly labeled **development simulation**:

```powershell
mvn clean verify -Pintegration
java -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.client.progress.ProgressGui --simulate
```

Submit MAX/PRIMECOUNT integer lists or PRIMESUM `start,end`, or select a UTF-8 CSV.
Each job has independent progress and connection status. Click its progress bar
or activate **Worker breakdown** with the keyboard to inspect credited units per
simulated worker. For a longer local calculation, try PRIMESUM `1,5000000`, then
**Simulate stream disconnect**; the monitor retains the same `sim-...` job ID and
fetches/subscribes again. This is single-process stream-failure simulation, not
coordinator failover. Progress comes from completed local calculation chunks;
there are no timer-generated percentages. Fast jobs may finish before intermediate
updates are rendered, as with real snapshot streams.

After automatic retries are exhausted, **Resume monitoring** reuses the job ID
or reconciles the original unconfirmed submission key. **Monitor existing job**
attaches by ID without submitting anything. IDs/keys are retained in memory for
this GUI session, and simulator records disappear when its process exits. Durable
receipt storage remains pending agreed idempotency/recovery contracts. Closing
the GUI interrupts monitors and closes the service away from the EDT; accepted
remote work is not claimed to be cancelled.

Focused component tests:

```powershell
mvn "-Dtest=JobMonitorTest,JobSnapshotTest,JobProgressPanelTest,SimulatedJobClientTest" test
```

`client/progress/JobClient` isolates transport operations. A future agreed gRPC
adapter must provide discovery, confirmed submit, lookup by submission key,
lookup by job ID, cancellable full-snapshot streaming, concurrent calls, deadlines
and channel shutdown. See the exact [adapter/server checklist](docs/A2_CONTRACT_REVIEW.md).
The old `ClientGui`/`HeadlessClient` remain unchanged for A1 verification.

Repository: https://github.com/mocenacagiisaia-glitch/CS324_A1

This is an independent development repository, not the completed reference project.
Java 21, Maven, Java RMI, and JUnit 5. No runtime dependencies beyond the JDK.

## Current state

Areas 1 and 2 provide membership, worker runtime, election and job coordination.
Area 3 now supplies the registered JobEngine provider, integer calculations,
balanced splitting/aggregation, numeric CSV input, Swing GUI and checked concurrent client.
See [Member 2 design and review guide](docs/ELECTION_COORDINATION.md) for protocol,
tests, failure semantics and integration limits. Implementation was checked against the supplied `A1 cs324.txt` assignment
specification; see the requirement mapping in the review guide.

```powershell
java -version
mvn -version
mvn clean verify -Pintegration
```

Open this folder in VS Code and run the commands in its terminal. Both Java and
Maven must use Java 21. The first build needs access to Maven Central.

## Three assignments

| Person | Area | Deliverables and verification |
|---|---|---|
| You (person 1) | Membership and worker runtime | Bootstrap membership directory, unique-ID registration, random reciprocal neighbours, RMI lookup/status, separate-process startup, concurrent worker execution infrastructure. Test joins, duplicate IDs, active-worker pruning, neighbour symmetry and worker concurrency. |
| Teammate 2 | Election and coordination | ELECTION/COORDINATOR propagation and deduplication, lowest-JAC/highest-ID ranking, consistent local status, five-job admission, JAC accounting, coordinator dispatch and failure/timeout behavior. Test cycles, simultaneous election attempts, concurrent clients, term boundaries and failures. |
| Teammate 3 | Calculations and clients | MAX, PRIMESUM, PRIMECOUNT, balanced splitting and pure aggregation through JobEngine; CSV input, responsive Swing GUI and checked concurrent headless client. Test calculations, boundaries, parsing and split/aggregate correctness. |

Area 1 supplies the runnable membership/transport layer. Area 2 owns the job
coordination methods in WorkerNode and uses area 3's JobEngine. Area 3 can implement
and test its pure engine and client parsing independently; full client integration
requires area 2. Each person owns tests and documentation for their area.

## Shared contracts and integration boundaries

Source root: `src/main/java/edu/usp/cs324/`.

- `api/BootstrapRemote.java`: registration and active membership.
- `api/WorkerRemote.java`: status, neighbour links, election, submission, assignment
  and chunk computation. Keep these signatures compatible across branches.
- `api/Peer.java`, `Candidate.java`, `Term.java`, `Status.java`: serializable endpoint
  and election/status values. `Peer.connect()` is the common RMI lookup helper.
- `api/Job.java`: immutable integer input with basic validation, not calculations.
- `api/JobEngine.java`: pure split, compute and aggregate contract. Teammate 3 must
  register its provider in `src/main/resources/META-INF/services/edu.usp.cs324.api.JobEngine`.
- `api/RetryException.java`: rejection before admission only; never replay an
  accepted job because a nested computation cause happens to have this type.
- `api/JobFailedException.java`: failure with started work known to be finished.
  A transport timeout does not establish this guarantee.
- `network/`: area 1 transport, plus WorkerNode integration points for area 2.
- `election/`: area 2 helpers; `jobs/` and `client/`: area 3 implementation.

Coordinate contract changes before merging. Teammate 2 should implement election
and coordinator behavior in WorkerNode without replacing membership/runtime code.
Teammate 3 should avoid editing WorkerNode; its engine is loaded with ServiceLoader.
The provider is included in the built JAR through its ServiceLoader registration.

## Teammate workflow

Teammate branches start at the same area 1 commit on `main`, without extra commits:

- Teammate 2: `feature/election-coordination`
- Teammate 3: `feature/jobs-client`

Teammate 2:

```powershell
git clone https://github.com/mocenacagiisaia-glitch/CS324_A1.git
cd CS324_A1
git switch --track origin/feature/election-coordination
mvn verify -Pintegration
```

Teammate 3 (on their own computer/folder):

```powershell
git clone https://github.com/mocenacagiisaia-glitch/CS324_A1.git
cd CS324_A1
git switch --track origin/feature/jobs-client
mvn verify -Pintegration
```

Each teammate should configure their own real Git name/email locally, implement
their assigned work, and make meaningful commits describing what they actually
did. Push with `git push -u origin HEAD` and open a pull request into `main`.
Before integration, fetch and merge updated `origin/main` into the feature branch,
resolve conflicts together, and rerun the full verification. Do not force-push or
claim another person's work. An existing local branch uses `git switch <branch>`.

The intended final project must meet the assignment's complete requirements; this
development starting point deliberately does not claim that completion. Run full
multi-client/election/job integration tests when both teammate branches are ready.

## Run the cluster locally

After building, run each command in a separate terminal from this folder, starting
bootstrap first and waiting for each readiness message. Ports and IDs are arguments.

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.network.BootstrapNode 127.0.0.1 1099 1199
```

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.network.WorkerNode 1 127.0.0.1 2001 3001 127.0.0.1 1099
```

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.network.WorkerNode 2 127.0.0.1 2002 3002 127.0.0.1 1099
```

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.network.WorkerNode 3 127.0.0.1 2003 3003 127.0.0.1 1099
```

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.network.WorkerNode 4 127.0.0.1 2004 3004 127.0.0.1 1099
```

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.network.ClusterStatus 127.0.0.1 1099
```

Status initially shows zero counters and `term=null`. The first `elect()` or
`submit()` elects a coordinator. The included provider handles all three job types.
Stop demo processes with Ctrl+C; use distinct ports if a prior demo is running.

## Run clients

After starting bootstrap and workers above, launch the GUI in another terminal:

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.client.ClientGui 127.0.0.1 1099
```

Select MAX or PRIMECOUNT and enter comma/newline-separated signed integers, or
select PRIMESUM and enter exactly `start,end` (both inclusive). Click **Submit
entered data**, or **Submit CSV file...** to choose a UTF-8 file. CSV is headerless
numeric data: commas separate cells, newlines separate rows, quoted integers and
a UTF-8 BOM are accepted. Blank lines are ignored; empty cells, headers, decimals,
malformed quotes and values outside the signed 32-bit range are rejected with an
input error. MAX needs at least one value; empty PRIMECOUNT returns zero.
For PRIMESUM, CSV must contain exactly two integers with start <= end.

Each click creates an independent task row; submit more tasks while previous
ones run. File reads and RMI calls run off the Swing event thread. Launch this
command again in a separate terminal to demonstrate a second client process.
The result table reports each task's completion or error independently.
Enter an explicit bootstrap host (for example, `127.0.0.1` for a local cluster).
A blank host reports `Bootstrap host is required` instead of connecting locally.

For a checked concurrent demo, run this command in two terminals:

```powershell
java -Dsun.rmi.transport.tcp.responseTimeout=10000 -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.client.HeadlessClient 127.0.0.1 1099
```

Each process submits 12 concurrent jobs, checks MAX=42, PRIMESUM(1,1000)=76127
and PRIMECOUNT=4, and exits unsuccessfully if any result is wrong. Clients retry
only explicit pre-admission rejections during term rotation. Transport failures
are reported without replay because accepted work may still be running.

For multiple computers, replace loopback addresses with reachable host addresses
in the bootstrap, worker and client commands. Allow the configured registry and
export ports through the lab firewall. Each worker advertises its supplied host.

Chunks contain equal numbers of elements (or range integers), differing by at
most one, and retain list duplicates and inclusive range boundaries. Fewer items
than workers produce fewer nonempty chunks. Empty PRIMECOUNT uses one empty
chunk. Results use BigInteger; primality uses integer trial division. Equal-sized
chunks do not guarantee equal CPU time, and very large ranges can take a long time.

## Verification and remaining integration

`mvn clean verify -Pintegration` runs 26 unit/RMI tests for calculations, parsing,
retry classification, membership, worker runtime and coordination, plus one
separate-JVM bootstrap/four-worker/two-client integration test. The latter checks
24 results across all job types, leader rotation/agreement and 72 allocations to
other workers. Maven reports are
in `target/surefire-reports/` and `target/failsafe-reports/`; process logs are in
`target/membership-integration/`. Coordination tests use constant fixture chunks,
not implementations of MAX, PRIMESUM or PRIMECOUNT; the separate-process test uses
the actual provider and checked clients.

The automated suite does not exercise Swing mouse/keyboard interaction. Manually
demonstrate file selection, invalid input feedback, concurrent task rows and two
GUI processes. Cross-machine firewall/RMI configuration also requires a lab demo.
Closing a GUI does not cancel already accepted remote work. RMI has no durable
job receipt/resume protocol; a timeout leaves completion uncertain.

Membership probes prune unreachable workers, but neighbour repair and transactional
join rollback remain outside this change. Use stable membership on a trusted lab
network. Election propagation skips failed neighbour links and checks that all active
members supplied candidates before choosing a winner.
This implementation is not durable recovery or network-partition consensus.

## Reference and contribution policy

The local Safe101 working reference and the original project remain outside this
repository. Never copy their folders, Git directories, generated artifacts or full
implementations into a commit. `.gitignore` excludes Safe101, target and local IDE
files; exclusions alone do not replace a history review.

The skeleton's shared contracts were adapted from the reference. Setup and area 1
are AI-assisted work performed by Codex for person 1; they are not three people's
independent contributions. See [contribution record](docs/CONTRIBUTIONS.md).
