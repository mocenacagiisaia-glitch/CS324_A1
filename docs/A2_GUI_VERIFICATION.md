# Progress GUI verification — development simulation only

Date: 14 October 2026, Pacific/Fiji. Branch: `feature/a2-jobs-client`.
Starting commit: `8c210c974f8bd32f953e8410e7ecc6ed36cf180d`.

## Actual runtime/desktop findings

Checked system Java directories, common Adoptium/Microsoft/Zulu/user JDK
locations, executable paths and JavaSoft registry entries. Found Oracle
JDK 24.0.2 and an older Java 8 runtime; no Java 21 runtime was available in those
locations. The build still targets Java 21 (`maven.compiler.release=21`).

Launched the actual `ProgressGui.main(new String[]{"--simulate"})` entry point
through a temporary Java Robot desktop probe. Swing created a simulation window
and its title identified development simulation. Robot typing failed to reach
the text area; a screen capture showed only the desktop background rather than
the application. The probe timed out on keyboard input and was stopped. The
temporary probe is not shipped as a passing test. No visual, mouse/keyboard,
worker-dialog or end-to-end GUI interaction check is claimed to have passed.

The headless Swing/component tests check updates, worker table contents,
reconnection status and EDT safety. Scripted monitor tests check same-ID
reconnect, same-key reconciliation and no submit replay. These do not replace
the manual checklist below and are not A2 gRPC/recovery evidence.

`mvn clean verify -Pintegration` passed 51 unit/component/RMI tests and one
legacy separate-process RMI integration test, with zero failures/errors/skips,
on JDK 24.0.2 targeting release 21. This includes the one-shot lost-reply
simulation regression. It does not establish desktop interaction or A2 recovery.

## Short manual checklist

From the checkout containing `pom.xml`, build and launch:

```powershell
mvn clean verify -Pintegration
java -cp target/distrilab-team-1.0.0.jar edu.usp.cs324.client.progress.ProgressGui --simulate
```

1. Confirm the title/banner says **DEVELOPMENT SIMULATION** and states there is no
   gRPC, quorum or real recovery. Select MAX, enter `-9,42,7`, submit, and confirm
   COMPLETED, result `42`, and 100% (`3/3` work units).
2. Click that job's progress bar or activate **Worker breakdown**. Confirm the
   dialog identifies that same `sim-...` job and shows three workers with one
   credited unit each. Close it and open another job's breakdown to check selection.
3. Select PRIMESUM, enter `1,8000000`, and submit. Confirm progress advances only
   when local chunks finish (four chunks, 25% steps). If your machine finishes too
   quickly to inspect it, use a larger modest range. While it runs, type in the
   input or existing-ID field and open a completed job's breakdown to check responsiveness.
4. Record its job ID/key and click **Simulate stream disconnect** while running.
   Confirm RECONNECTING appears separately from the last job state/progress, then
   monitoring resumes with exactly the same ID/key and eventually completes.
   Reconnection may be brief (the first backoff is 250 ms). This is simulated
   stream interruption, not coordinator replacement.
5. Click **Simulate next lost submission reply**, then submit a new PRIMESUM job.
   Confirm the row starts unconfirmed and temporarily shows RECONNECTING, then
   resolves the original submission key to one `sim-...` job. It must not create
   a second GUI row automatically. The simulation admits locally and deliberately
   loses the reply; this does not test a real majority or network failure.
6. Paste a completed ID into **Existing job ID** and activate **Monitor existing
   job**. Confirm it attaches to the same ID/result. With an unknown ID, expect
   bounded reconnect attempts followed by UNAVAILABLE; **Resume monitoring**
   repeats lookup without submitting a replacement. Close the GUI and confirm
   the process exits. Simulator records/GUI receipts are in-memory only.

To verify on an installed Java 21 JDK, set its actual path in that terminal before
the build/launch above (do not use a Java 8 runtime):

```powershell
$env:JAVA_HOME = 'C:\path\to\your\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
mvn clean verify -Pintegration
```

Both version commands must report Java 21. Current verification on JDK 24 with
release 21 must not be described as an actual Java 21 runtime run.

## Outstanding teammate dependencies (all contracts still proposed)

**Isaia:** authoritative coordinator endpoint/worker/term discovery via proposed
`CoordinatorDirectory.LocateCoordinator`; active agreed membership configuration;
storage/sharing of committed admission mappings, job/task/attempt/progress/result
records; majority confirmation based on that configuration. Discovery must report
UNAVAILABLE during elections/incomplete recovery, not invent a coordinator or
smaller responding-worker majority.

**Maanav:** proposed `JobService.SubmitJob`, `LookupSubmission`, `GetJob`,
`WatchJob`. Admission must yield a stable ID only after required majority commit;
same key/input must survive failover and allocate only once. Lookup must reconcile
uncertainty against recovered records. Watch must emit the current complete
snapshot first, then committed updates with monotonic recovered revisions.
Stable task IDs, increasing attempts, fixed work totals and server rejection of
stale/repeated results must prevent duplicate credit. Elections, reassignment,
coordinator recovery and five-job rotation/JAC remain Maanav's scope.

**Krish after agreement:** actual gRPC adapter with deadlines/liveness/cancellation
and channel shutdown; durable local receipts; actual worker/coordinator failure,
majority-unavailable and cross-process reconnect integration checks. See
[full method-by-method contract review](A2_CONTRACT_REVIEW.md) and
[proposed proto](proposals/jobs_client.proto). Neither is team-approved.
