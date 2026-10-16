# camel-test-dst: deterministic simulation testing for Camel (prototype)

**Status: prototype for discussion, not a pull request.** It lives on the branch `camel-test-dst-prototype` of
github.com/allthingssecurity/camel, on top of apache/camel `main`. This is a test tool only: nothing in it runs in
production, and no Camel component or API is changed.

## The problem

Many Camel bugs only show up under one particular timing:

- a checkpoint timer that is cancelled at the wrong moment, so the last events of a burst are never checkpointed;
- a route suspended between two messages of a poll, so the rest of that poll is skipped;
- a timeout that fires while a task copies its result, so two threads write the same exchange;
- a server restart during a reconnect, so the consumer stays dead while the route reports `Started`.

These bugs usually log nothing, and they are hard to reproduce: today a test for one of them needs latches placed by
hand to force one exact interleaving. A run of such bugs was fixed in Camel recently (azure-eventhubs, slack,
resilience4j, zookeeper, pg-replication-slot, docker, mongodb), each with its own hand-made interleaving.

## The idea

Deterministic simulation testing, the approach FoundationDB and TigerBeetle use for their own code:

1. **One scheduler owns all threads.** Every Camel thread pool and scheduled executor is backed by a single event loop
   on the test thread, plugged in through Camel's existing `ThreadPoolFactory` SPI.
2. **Virtual time.** "Wait 5 seconds" costs nothing: the clock jumps to the next scheduled task.
3. **A seed decides everything.** When several tasks can run, a seeded random number picks the order. The fakes of the
   external system (a checkpoint store, a Slack API, a broker) use the same seed to inject faults: slow or failed
   answers, bursts and quiet periods, a suspend or restart between two messages.
4. **Invariants are checked after every run**, for example "every completed event is checkpointed when the partition
   goes quiet", "every offered message is routed at least once", "the consumer is alive after the fault cleared".
5. **A failing run prints its seed.** Running that seed again replays the exact same step trace (same SHA-256), so the
   failure is reproducible and becomes the regression test.

```java
@DstInstrument("org.apache.camel.component.azure.eventhubs")
class EventHubsCheckpointDstTest {

    @DstTest(seeds = 2000)                 // run 2000 seeds; a failure prints its seed and trace
    void everyCompletedEventIsCheckpointedWhenThePartitionGoesQuiet(Dst dst) throws Exception {
        new EventHubsWorld(dst).run();     // the consumer, a fake Event Hubs, faults and invariants
    }
}
```

Replay one seed with `-Ddst.seed=48`, or `@DstSeed(48)` on the test method.

## Results of the prototype

5000 seeds per run, about 2 ms per seed, on the real consumer code (only the external service is faked):

| Demo | Failing seeds | First failing seed |
|---|---|---|
| azure-eventhubs checkpointing, `main` | 1897 / 5000 (38 %) | 1 |
| azure-eventhubs with apache/camel#27922 (as first submitted) | 33 / 5000 | 48 |
| slack, route suspended mid-poll, `main` | 3018 / 5000 (60 %) | 1 |
| slack with apache/camel#27923 | 0 / 5000 | none |

- Replaying a seed gives a byte-identical trace, also across JVMs and inside a 5000-seed run.
- It found something the review only suspected: with the first version of #27922, 31 of the 33 failing seeds lost
  the last events of a partition through the corner case raised in review (the timer's `compareAndSet` wiping a later
  window). The PR is being updated to close that case too.

## How it works (prototype)

| Class | Role |
|---|---|
| `Dst` | the event loop: virtual clock, seeded random source, step trace with SHA-256 |
| `DstThreadPoolFactory`, `DstExecutor`, `DstTask` | Camel pools and schedulers backed by the loop |
| `DstClock`, `DstInstrumentation` | `System.currentTimeMillis()` / `nanoTime()` in the instrumented package read the virtual clock (byte-code rewrite at test time; sources unchanged) |
| `DstEscapes` | reports what the harness cannot control: sleeps, own threads, third-party schedulers, blocking waits |
| `DstLedger`, `Invariant`, `Invariants` | records what happened to each message; reusable invariants |
| `DstTest`, `DstSeed`, `DstInstrument`, `DstExtension` | JUnit integration |

## Limits

- Interleaving is per task: the harness does not preempt in the middle of a method.
- Code that uses its own threads, `Thread.sleep` or a third-party scheduler is outside its control. The escape report
  lists these places. A small time-source hook in Camel core would remove the need for byte-code rewriting.
- Results are only as good as the fakes of the external system.

## Try it

See [`DEMO.md`](DEMO.md) for the exact commands: the self-tests, the demos on `main`,
replaying a seed and comparing traces, and the demos with the fixes.

## Feedback welcome

Questions this prototype is meant to answer: is a `camel-test-dst` module worth having next to the other
`camel-test` modules, starting with the consumers that have checkpoint, commit or reconnect logic? Comments are
welcome on the Camel Zulip.
