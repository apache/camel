# camel-test-dst: commands

JDK 21, Maven through `./mvnw`. The harness is `components/camel-test/camel-test-dst`; the worlds are tests of their
components, in a `dst` package. Build the harness in the same reactor as the component (`-pl` both), so the
component's tests see it. `-Dsurefire.rerunFailingTestsCount=0` stops surefire from re-running each failing seed.

```bash
export MAVEN_OPTS=-Xmx2g
DST=components/camel-test/camel-test-dst
SQS=components/camel-aws/camel-aws2-sqs
EH=components/camel-azure/camel-azure-eventhubs
SLACK=components/camel-slack
MVN="./mvnw -B -Papache-snapshots test -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.rerunFailingTestsCount=0"
```

## 1. Harness self-tests

```bash
$MVN -pl $DST -Dtest='Dst*Test'
```

## 2. The worlds on main (regression tests, they pass)

```bash
$MVN -pl $DST,$SQS,$EH,$SLACK -Dtest='*DstTest'                                       # 1000 seeds each
$MVN -pl $DST,$SQS,$EH,$SLACK -Dtest='*DstTest' -Ddst.seeds=5000 -Ddst.maxFailures=100000
```

Reports in `<component>/target/dst/`:

- `<Test>.summary.txt`: seeds run, failing seeds with the violation, threads left alive, runtime escapes, escape
  points of the loaded classes;
- `<Test>.escapes.txt`: the byte code scan of the instrumented package;
- `<Test>.<method>/seed-<n>.trace`: the step trace of each failing or replayed seed; first line the result, second
  line the SHA-256 of the trace.

## 3. The self-check: fail on the code before the fix

Revert the main code of a fix locally (do not commit), run the world, restore:

```bash
git show d09593b4a584 -- $SQS/src/main | git apply -R          # CAMEL-25700
$MVN -pl $DST,$SQS -Dtest=SqsVisibilityDstTest -Ddst.seeds=5000 -Ddst.maxFailures=100000
git checkout -- $SQS/src/main

git show 83aaa80754f9 -- $EH/src/main | git apply -R           # CAMEL-25683
git show 68329f0919bc -- $SLACK/src/main | git apply -R        # CAMEL-25684
```

The SQS world fails only with `maxMessagesPerPoll >= 2` and `concurrentConsumers >= 2` before CAMEL-25700, as that
fix describes. `-Ddst.sqs.visibilityTimeouts=1` shows CAMEL-25737 on main.

## 4. Replay one seed, twice, and compare

```bash
$MVN -pl $DST,$SQS -Dtest=SqsVisibilityDstTest -Ddst.seed=8
cp $SQS/target/dst/SqsVisibilityDstTest.*/seed-8.trace /tmp/seed-8.first
$MVN -pl $DST,$SQS -Dtest=SqsVisibilityDstTest -Ddst.seed=8
diff /tmp/seed-8.first $SQS/target/dst/SqsVisibilityDstTest.*/seed-8.trace && echo identical
```

`-Ddst.seed=1,2,5` replays several seeds; `@DstSeed(8)` on the test method does the same in code. The same seed inside
a 5000-seed run writes the same trace.

## 5. Options

| Property | Meaning | Default |
|---|---|---|
| `dst.seeds` | number of seeds | `@DstTest(seeds)`, 1000 in the worlds |
| `dst.firstSeed` | first seed | 1 |
| `dst.maxFailures` | stop after this many failing seeds | 1 |
| `dst.seed` | comma separated seeds to replay (writes the full traces) | - |
| `dst.traceDir` | where reports and traces go | `target/dst` |
| `dst.strict` | strict mode: an escape reached at runtime fails the seed | `@DstInstrument(strict)`, true |
| `dst.alsoRewrite` | classes or packages outside the component to rewrite too (stop-gap) | - |
| `dst.watchdogMillis` | real time without progress before a waiting loop thread fails the seed | 10000 |
| `dst.sqs.visibilityTimeouts` | visibility timeouts the SQS world picks from | `2,3,5,10` |
| `dst.replayCheckSeed` | seed of the in-JVM replay test of the Event Hubs world | 1 |
