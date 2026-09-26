# CS324 A1 — three-person development project

Repository: https://github.com/mocenacagiisaia-glitch/CS324_A1

This is an independent development repository, not the completed reference project.
Java 21, Maven, Java RMI, and JUnit 5. No runtime dependencies beyond the JDK.

## Current state

Area 1 is implemented: bootstrap membership, registration, random reciprocal
neighbours, worker processes, RMI status and provider-based concurrent chunk
execution. Election, leadership/JAC, job admission/dispatch, actual calculations,
CSV and clients remain unimplemented for teammates 2 and 3. This is not a complete
assignment solution. A successful build verifies only the included area 1 scope.

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
The baseline contains no engine provider, calculation algorithms or GUI.

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

## Run area 1 locally

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

Status shows neighbours, zero counters and `term=null`; these are honest initial
values, not an implemented leader election. Calling election/submission/assignment
returns an explicit unimplemented error. Compute requires teammate 3's provider;
only test fixtures supply a constant-returning provider at this milestone.
Stop demo processes with Ctrl+C. Use distinct ports if a prior demo is running.

## Area 1 tests and remaining integration work

`mvn test` runs MembershipTest and WorkerRuntimeTest. They check connected reciprocal
joins, concurrent duplicate-ID registration, active-worker pruning, neighbour
deduplication/self exclusion, honest unfinished-feature errors, overlapping worker
tasks, interrupted-task draining/interrupt restoration, and computation errors.
`mvn verify -Pintegration` also starts bootstrap, four workers and a status command
in separate JVMs, verifies membership over RMI, then stops those child processes.
Logs are in `target/membership-integration/`; Maven reports are in
`target/surefire-reports/` and `target/failsafe-reports/`.

Teammate 2 must update the tests that currently assert null terms or unimplemented
election/submission behavior when implementing those features. Preserve membership
and runtime assertions. Teammate 3 supplies real algorithm/provider tests; runtime
fixture outputs are not tests of MAX/PRIMESUM/PRIMECOUNT.

Membership probes remove unreachable workers from the bootstrap directory, but
neighbour repair and transactional rollback of failed reciprocal joins are not
implemented. Bootstrap holds a monitor while probing RMI endpoints; a slow peer
can delay registration. Test with stable membership on a trusted lab network.
Future coordinator code must account for RMI timeouts: a returned/failed call is
not proof that remote work stopped. Interruption of compute waits for the started
task before reporting failure. This is not durable recovery or partition consensus.

## Reference and contribution policy

The local Safe101 working reference and the original project remain outside this
repository. Never copy their folders, Git directories, generated artifacts or full
implementations into a commit. `.gitignore` excludes Safe101, target and local IDE
files; exclusions alone do not replace a history review.

The skeleton's shared contracts were adapted from the reference. Setup and area 1
are AI-assisted work performed by Codex for person 1; they are not three people's
independent contributions. See [contribution record](docs/CONTRIBUTIONS.md).
