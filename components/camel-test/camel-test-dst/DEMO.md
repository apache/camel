# DST prototype: commands to reproduce the demos

Branch `camel-test-dst-prototype` on github.com/allthingssecurity/camel (based on apache/camel main 7089992f4a97).
The module is `components/camel-test/camel-test-dst`. JDK 21, Maven through `./mvnw`. The 4.24.0-SNAPSHOT core jars
are not published, so every command builds the needed modules with `-am` (about 80 modules, 3 to 5 minutes the first
time). `-Psource-jar` is needed because camel-spring-xml is in the reactor (it unpacks the camel-api source jar).
`-Dsurefire.rerunFailingTestsCount=0` stops surefire from re-running each failing seed twice.

```bash
git clone https://github.com/allthingssecurity/camel.git && cd camel
git checkout camel-test-dst-prototype
export MAVEN_OPTS=-Xmx2g
MVN="./mvnw -B -Psource-jar -pl components/camel-test/camel-test-dst -am package -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.rerunFailingTestsCount=0"
```

## 1. Harness self-tests (no demo, always on)

```bash
$MVN -Dtest='Dst*Test'
# Tests run: 7, Failures: 0
```

## 2. The demos on main (bugs present): they fail, by design

```bash
# stop at the first failing seed (the default), print its trace
$MVN -Dtest='EventHubsCheckpointDstTest,SlackSuspendDstTest' -Ddst.demo=true -Ddst.seeds=5000

# count every failing seed of 5000
$MVN -Dtest='EventHubsCheckpointDstTest,SlackSuspendDstTest' -Ddst.demo=true -Ddst.seeds=5000 -Ddst.maxFailures=100000
# EventHubsCheckpointDstTest: Tests run: 5001, Failures: 1897   (5000 seeds + the in-JVM replay test)
# SlackSuspendDstTest:        Tests run: 5000, Failures: 3018
```

Reports: `components/camel-test/camel-test-dst/target/dst/`

- `<Test>.summary.txt`: seeds run, failing seeds with the violation, threads left alive, runtime escapes, escape
  points of the loaded classes;
- `<Test>.escapes.txt`: the byte-code scan of the instrumented package;
- `<Test>.<method>/seed-<n>.trace`: the full step trace of each failing seed, first line the result, second line
  the SHA-256 of the trace.

The eventhubs probe counts (in how many seeds each cause was reached) are printed in the Maven console output after
the test (`DST eventhubs probes ...`).

## 3. Replay one seed, twice, and compare

```bash
$MVN -Dtest=EventHubsCheckpointDstTest -Ddst.demo=true -Ddst.seed=1
cp components/camel-test/camel-test-dst/target/dst/EventHubsCheckpointDstTest.*/seed-1.trace /tmp/seed-1.first
$MVN -Dtest=EventHubsCheckpointDstTest -Ddst.demo=true -Ddst.seed=1
diff /tmp/seed-1.first components/camel-test/camel-test-dst/target/dst/EventHubsCheckpointDstTest.*/seed-1.trace \
  && echo identical
```

`-Ddst.seed=1,2,5` replays several seeds. The same seed inside a 5000-seed run writes the same trace (same SHA-256).
In code: `@DstSeed(1)` on the test method.

## 4. The demos with the fixes (#27922, #27923)

On a local branch only (do not push it):

```bash
git checkout -b dst-with-fixes-local camel-test-dst-prototype
git fetch origin pull/27922/head:pr-27922 pull/27923/head:pr-27923    # origin = apache/camel
git cherry-pick 4161147f6759                 # #27922, camel-azure-eventhubs (one commit)
git cherry-pick 1cc8a7ac4c76 4961eec8c745    # #27923, camel-slack (two commits)
$MVN -Dtest='EventHubsCheckpointDstTest,SlackSuspendDstTest' -Ddst.demo=true -Ddst.seeds=5000 -Ddst.maxFailures=100000
# EventHubsCheckpointDstTest: 33 failing of 5000 (the review corner case of #27922 and a same-millisecond residual)
# SlackSuspendDstTest:        0 failing of 5000
$MVN -Dtest=EventHubsCheckpointDstTest -Ddst.demo=true -Ddst.seed=48     # the compareAndSet corner case
$MVN -Dtest=EventHubsCheckpointDstTest -Ddst.demo=true -Ddst.seed=2507   # the same-millisecond residual
```

## 5. Options

| Property | Meaning | Default |
|---|---|---|
| `dst.demo` | enables the two demo classes | off |
| `dst.seeds` | number of seeds | `@DstTest(seeds)`, 2000 in the demos |
| `dst.firstSeed` | first seed | 1 |
| `dst.maxFailures` | stop after this many failing seeds | 1 |
| `dst.seed` | comma separated seeds to replay (writes the full traces) | - |
| `dst.traceDir` | where reports and traces go | `target/dst` |
| `dst.replayCheckSeed` | seed of the in-JVM replay test of the eventhubs demo | 1 |
