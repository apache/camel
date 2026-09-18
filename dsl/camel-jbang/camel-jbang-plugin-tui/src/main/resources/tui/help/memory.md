# Memory

The Memory tab shows JVM memory usage, garbage collection stats, and a
real-time heap usage chart. This helps you monitor memory consumption,
detect memory leaks, and understand GC behavior.

## Heap Memory

The JVM heap is where Java objects live. Three values matter:

- **used**: Memory currently occupied by live objects. This value goes up as objects are created and drops when garbage collection runs
- **committed**: Memory the JVM has reserved from the operating system. This is your working set — the JVM can use this much without requesting more from the OS. The JVM may increase committed memory up to the max limit as needed
- **max**: The absolute upper limit set by `-Xmx`. The JVM will never use more than this. If used reaches max and GC cannot free enough space, you get an `OutOfMemoryError`

Two gauge bars show how full the heap is:

```
committed: 80 MB  ████████████████░░░░░░░░░░░░░░  53%
max:       16 GB  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░░   2%
```

The committed bar shows how much of the reserved memory is in use —
this is the most useful indicator for day-to-day monitoring. The max
bar shows how much headroom remains before the JVM hits its hard limit.

## Example Screen

```
 Heap: used 55 MB / committed 80 MB / max 16 GB
 committed: 80 MB  ████████████████░░░░░░░░░░░░░░  68%
 max:       16 GB  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░░   0%

 Old Gen:   used 19 MB / committed 24 MB / max 16 GB
 Non-Heap:  used 66 MB / committed 68 MB
 Metaspace: used 43 MB / committed 44 MB

 GC: 19 collections, 28 ms total
```

## Old Gen

When objects survive multiple garbage collections, they are promoted to
the Old Generation memory pool. Old Gen stores long-lived objects like
caches, connection pools, and singleton beans.

**Watch for**: Old Gen usage that keeps growing over time without going
back down after GC. This pattern often indicates a **memory leak** —
objects being retained that should have been released.

## Non-Heap and Metaspace

- **Non-Heap**: Memory outside the heap used by the JVM itself — JIT compiled code, thread stacks, internal data structures
- **Metaspace**: A subset of non-heap where Java class metadata is stored (class definitions, method tables, constant pools). Replaced the old PermGen space in Java 8+. Metaspace grows automatically but can be limited with `-XX:MaxMetaspaceSize`

High Metaspace usage is normal for large applications with many classes.
It typically stays stable after startup.

## Garbage Collection

- **Collections**: Total number of GC cycles. The JVM runs GC automatically when it needs to free memory. Modern collectors (G1, ZGC) run frequently with short pauses
- **Time**: Total time spent in GC (milliseconds). High GC time relative to uptime means the JVM is spending too much time cleaning up — consider increasing heap size

**GC key**: The key at the top identifies which GC algorithm is active
(e.g., `G1 Young Generation`, `ZGC`). Different algorithms have
different trade-offs between throughput and pause times.

## Sparkline Chart

The chart shows heap usage over time. Color indicates how full the
heap is relative to committed memory:

- **Green**: below 60% — healthy, plenty of headroom
- **Yellow**: 60-80% — getting full, GC is working harder
- **Red**: above 80% — high pressure, risk of long GC pauses or OOM

A sawtooth pattern (usage rises then drops sharply) is normal — it shows
GC reclaiming memory periodically. A steadily rising baseline that
never drops back suggests a memory leak.

## Keys

- `g` — trigger garbage collection on the running integration (sends a GC request to the JVM — useful for testing if high usage is just uncollected garbage)
- `h` — write a heap dump (.hprof) file for deep analysis with tools like Eclipse MAT or VisualVM
- `Esc` — back
