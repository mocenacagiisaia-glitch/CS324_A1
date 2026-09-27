# CS324 A1 — three-person development project

Repository: https://github.com/mocenacagiisaia-glitch/CS324_A1

This is an independent development repository, not the completed reference project.
Java 21, Maven, Java RMI, and JUnit 5. No runtime dependencies beyond the JDK.

## Current state

Areas 1 and 2 now provide membership, worker runtime, election and job coordination.
Calculations, CSV and clients still require Member 3's JobEngine provider.
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

Status initially shows zero counters and `term=null`. The first `elect()` or
`submit()` elects a coordinator. Submission requires Member 3's provider.
Stop demo processes with Ctrl+C; use distinct ports if a prior demo is running.

## Verification and remaining integration

`mvn clean verify -Pintegration` runs membership, worker runtime and coordination
tests, plus a separate-JVM bootstrap/four-worker election test. Maven reports are
in `target/surefire-reports/` and `target/failsafe-reports/`; process logs are in
`target/membership-integration/`. Coordination tests use constant fixture chunks,
not implementations of MAX, PRIMESUM or PRIMECOUNT.

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
