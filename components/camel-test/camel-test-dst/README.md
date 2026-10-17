# camel-test-dst: deterministic simulation testing for Camel (preview)

CAMEL-25738, stage 1. This module holds the harness only. The worlds that use it live in the tests of their
components. It is a test tool: nothing in it runs in production, and no Camel component or API is changed.

The user guide, including the guide for writing a fake world (for AI agents and humans), is the module doc page
[`src/main/docs/test-dst.adoc`](src/main/docs/test-dst.adoc).

## The problem

Many Camel bugs only show up under one particular timing:

- a checkpoint timer that is cancelled at the wrong moment, so the last events of a burst are never checkpointed;
- a route suspended between two messages of a poll, so the rest of that poll is skipped;
- a visibility timeout that runs out while a message waits its turn in a batch, so SQS delivers it again;
- a server restart during a reconnect, so the consumer stays dead while the route reports `Started`.

These bugs usually log nothing, and a test for one of them needs latches placed by hand to force one interleaving.

## The idea

Deterministic simulation testing, the approach FoundationDB and TigerBeetle use for their own code:

1. **One scheduler owns all threads.** Every Camel thread pool and scheduled executor is backed by a single event loop
   on the test thread, plugged in through Camel's `ThreadPoolFactory` SPI.
2. **Virtual time.** "Wait 30 seconds" costs nothing: the clock jumps to the next due task. Time reads, sleeps and
   blocking waits of the component under test are rewritten at test time to use it.
3. **A seed decides everything**: the order of runnable tasks, the endpoint configuration, and the faults the fake of
   the external system injects.
4. **Invariants are checked after every run.**
5. **A failing run prints its seed**, and the same seed replays the same step trace (same SHA-256).

## The worlds and their results

Each world is a regression test in its component, 1000 seeds by default, a few milliseconds per seed. The "before the
fix" rows revert the main code of the fix locally (see [`DEMO.md`](DEMO.md)); all rows use the seed scramble and
strict mode of stage 1.

| World (test) | Code | Seeds | Failing |
|---|---|---|---|
| camel-azure-eventhubs checkpointing (`EventHubsCheckpointDstTest`) | `main` | 5000 | 1 (seed 3042, see below) |
| | before CAMEL-25683 (#27922, 83aaa80754f9) | 5000 | 2064 |
| camel-slack suspend mid-poll (`SlackSuspendDstTest`) | `main` | 5000 | 0 |
| | before CAMEL-25684 (#27923, 68329f0919bc) | 5000 | 2652 |
| camel-aws2-sqs visibility extender (`SqsVisibilityDstTest`, by Claus Ibsen) | `main` | 5000 | 0 |
| | before CAMEL-25700 (#27985, d09593b4a584) | 5000 | 1397, all with `maxMessagesPerPoll >= 2` and `concurrentConsumers >= 2` |
| | `main`, `visibilityTimeout=1` only (CAMEL-25737, open) | 2000 | 843 |

The one failing seed of the Event Hubs world on `main` (seed 3042, outside the default 1000) is an event that
completes in the same millisecond in which the timer of its window ran: `EventHubsCheckpointUpdaterTask.isExpired()`
compares with `>`, so the window whose timer already ran is reused and the event is checkpointed only with the next
event of the partition. In real time the window is one millisecond (low severity, not filed). Replaying seed 3042 in
two JVMs gives the same trace (SHA-256 1137160c...).

History: the first prototype run found that the first version of #27922 still lost the last events of a partition
through the corner case raised in review (the timer's `compareAndSet` wiping a later window); the PR was updated
before it was merged. The SQS world found CAMEL-25737 because the seed also picks the endpoint configuration.

## How it works

| Class | Role |
|---|---|
| `Dst` | the event loop: virtual clock, seeded random source (scrambled seed), step trace with SHA-256, timed and untimed waits, strict mode |
| `DstThreadPoolFactory`, `DstExecutor`, `DstTask` | Camel pools and schedulers backed by the loop; `get(timeout)` and `awaitTermination` in virtual time |
| `DstInstrumentation`, `DstClock`, `DstWaits` | byte code rewrite of the code under test: time reads, sleeps and blocking waits go to the loop |
| `DstEscapes`, `DstWatchdog` | the escape scan and runtime escapes (strict mode); a watchdog for waits in code that is not rewritten |
| `DstLedger`, `Invariant`, `Invariants` | records what happened to each message; reusable invariants |
| `DstTest`, `DstSeed`, `DstInstrument`, `DstExtension` | JUnit integration |
| `DstCamel` | a `CamelContext` on the loop, and an asynchronous route step with a seeded latency |

## Stage 1 (this branch)

0. The seed is scrambled (SplitMix64): `new Random(seed)` drew the same first values for consecutive seeds.
1. `java.time` reads and `new Date()` are rewritten too; `@DstInstrument(alsoRewrite)` rewrites listed core classes
   (`StopWatch`, `MonotonicClock`, ...) as a stop-gap until Camel core has a time source the harness can replace.
2. Sleeps on the loop thread are virtual waits; with `alsoRewrite` this covers `RedeliveryPolicy.sleep`, the
   synchronous redelivery loop, `ForegroundTask` and the wait loop of `DefaultShutdownStrategy`.
3. `DstTask.get(timeout)` and `awaitTermination` time out in virtual time.
4. Blocking waits on something that is not a DST task run the loop until they can end, or fail the seed at once; a
   watchdog fails a seed whose loop thread waits in code that is not rewritten.
5. Strict mode (the default): reaching an uncontrolled escape at runtime fails the seed.
6. The worlds moved to the tests of their components; this module is the harness.
7. The guide for writing a fake world is on the doc page.

## Limits

- Interleaving is per task: the harness does not preempt in the middle of a method.
- One stack: a blocked task stays on the loop thread's stack while the others run nested above it, so it resumes only
  after the tasks that blocked after it. A timed wait notices its timeout only when the tasks above it return. A route
  stop cannot see an exchange that is blocked in a synchronous call below it finish (the Slack world copies the suspend
  sequence for that reason). A real fix may need virtual threads with a custom scheduler (a later stage).
- Code on its own threads or a third-party scheduler (Reactor in the Azure SDKs) is outside the harness; strict mode
  makes reaching it fail the seed.
- Camel core reads `System` directly; `alsoRewrite` is a stop-gap (see the dev@ proposal of a time source in
  camel-util).
- One run per JVM at a time (the active run is a static field).
- Results are only as good as the fakes of the external system.

## Try it

See [`DEMO.md`](DEMO.md) for the commands.
