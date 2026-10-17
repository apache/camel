/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.test.dst;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * One deterministic simulation run: a single event loop that runs every task of every executor created through
 * {@link DstThreadPoolFactory}, a virtual clock, a seeded random source for the scheduling choices and the faults, and
 * a step trace.
 * <p>
 * Rules:
 * <ul>
 * <li>Only the thread that created the run (the loop thread, the JUnit test thread) runs tasks. Tasks run one at a time
 * and are never preempted: the unit of interleaving is a task.</li>
 * <li>Virtual time only moves when nothing is runnable at the current time; it then jumps to the next due task.</li>
 * <li>When several tasks are runnable, the seeded random source picks one. A single-thread executor runs its tasks in
 * FIFO order, an executor with n threads offers its first n runnable tasks.</li>
 * <li>A blocking call of a fake external system is a yield point ({@link #block(long, String)}): the calling task's
 * thread stays busy and the loop runs other tasks (nested) until the call returns.</li>
 * </ul>
 * The same seed gives the same sequence of steps, so a failing seed is replayed exactly.
 */
public final class Dst {

    /** Virtual time 0 is this wall clock instant, so code that prints times sees plausible values. */
    public static final long EPOCH_MILLIS = 1_700_000_000_000L;

    private static final int MAX_NESTING = 64;
    private static final int MAX_TRACE_LINES = 200_000;

    private static volatile Dst active;

    private final long seed;
    private final Random random;
    private final Thread loopThread;
    private final long maxSteps;
    private final List<DstTask<?>> pending = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private final List<DstTask<?>> running = new ArrayList<>();
    private final DstExecutor wakeups;
    private long now;
    private long sequence;
    private long steps;
    private int nesting;
    private boolean traceTruncated;
    private boolean closed;

    public Dst(long seed) {
        this(seed, 1_000_000);
    }

    public Dst(long seed, long maxSteps) {
        this.seed = seed;
        this.random = new Random(scramble(seed));
        this.loopThread = Thread.currentThread();
        this.maxSteps = maxSteps;
        this.wakeups = new DstExecutor(this, "wakeup", Integer.MAX_VALUE);
        active = this;
    }

    /**
     * The seed of the random source of a run: the given seed scrambled with the SplitMix64 finalizer.
     * <p>
     * {@code new Random(seed)} draws nearly the same first values for consecutive seeds (for seeds 1 to 1000 the first
     * {@code nextInt(2)} is always 1 and the first {@code nextDouble()} is always above 0.5), so the first choices of a
     * world, often its configuration, would hardly change over thousands of seeds. The scramble is a bijection, so
     * different seeds stay different and a seed still replays exactly.
     */
    static long scramble(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The run of the calling thread, or null. */
    static Dst active() {
        return active;
    }

    public long seed() {
        return seed;
    }

    /** Virtual milliseconds since the start of the run. */
    public long now() {
        return now;
    }

    /** Virtual wall clock time in milliseconds. */
    public long currentTimeMillis() {
        return EPOCH_MILLIS + now;
    }

    public long steps() {
        return steps;
    }

    public boolean isLoopThread() {
        return Thread.currentThread() == loopThread;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the seeded random source (only to be used from the loop thread, in a deterministic order)

    public int nextInt(int bound) {
        checkLoopThread("random");
        return random.nextInt(bound);
    }

    /** A value in [min, max]. */
    public long between(long min, long max) {
        checkLoopThread("random");
        if (max <= min) {
            return min;
        }
        return min + (long) (random.nextDouble() * (max - min + 1));
    }

    public boolean chance(double probability) {
        checkLoopThread("random");
        return random.nextDouble() < probability;
    }

    public <T> T pick(List<T> values) {
        return values.get(nextInt(values.size()));
    }

    // ------------------------------------------------------------------------------------------------------------
    // trace

    /** Adds a line to the step trace, prefixed with the step number, the virtual time and the current executor. */
    public void trace(String message) {
        if (trace.size() >= MAX_TRACE_LINES) {
            traceTruncated = true;
            return;
        }
        DstTask<?> current = running.isEmpty() ? null : running.get(running.size() - 1);
        String who = current != null ? current.executor().label() : "main";
        trace.add(String.format("#%d t=%d [%s] %s", steps, now, who, message));
    }

    public List<String> traceLines() {
        return Collections.unmodifiableList(trace);
    }

    public boolean isTraceTruncated() {
        return traceTruncated;
    }

    /** SHA-256 of the trace, to compare two runs of the same seed. */
    public String traceHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String line : trace) {
                md.update(line.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The label of the executor of the task that is running now, or "main". */
    public String currentExecutor() {
        return running.isEmpty() ? "main" : running.get(running.size() - 1).executor().label();
    }

    // ------------------------------------------------------------------------------------------------------------
    // tasks

    /**
     * An executor of this run that is not created by Camel, for a fake external system (for example its I/O thread).
     */
    public DstExecutor executor(String label, int threads) {
        return new DstExecutor(this, label, threads);
    }

    synchronized <V> DstTask<V> schedule(
            DstExecutor executor, long delayMillis, long period, Callable<V> callable, String description) {
        if (closed) {
            throw new IllegalStateException("The DST run of seed " + seed + " is closed");
        }
        if (!isLoopThread()) {
            DstEscapes.recordRuntime("task submitted from a thread outside the harness: " + Thread.currentThread().getName()
                                     + " -> " + executor.label() + " " + description);
        }
        DstTask<V> task = new DstTask<>(
                this, executor, now + Math.max(0, delayMillis), sequence++, period, callable, description);
        insert(task);
        return task;
    }

    private void insert(DstTask<?> task) {
        int i = pending.size();
        while (i > 0 && compare(pending.get(i - 1), task) > 0) {
            i--;
        }
        pending.add(i, task);
    }

    private static int compare(DstTask<?> a, DstTask<?> b) {
        int c = Long.compare(a.due(), b.due());
        return c != 0 ? c : Long.compare(a.sequence(), b.sequence());
    }

    synchronized void dropTasksOf(DstExecutor executor) {
        pending.removeIf(t -> t.executor() == executor);
    }

    synchronized void dropPeriodicTasksOf(DstExecutor executor) {
        pending.removeIf(t -> t.executor() == executor && t.isPeriodic());
    }

    synchronized boolean hasPendingTasksOf(DstExecutor executor) {
        for (DstTask<?> t : pending) {
            if (t.executor() == executor) {
                return true;
            }
        }
        return false;
    }

    synchronized boolean remove(DstTask<?> task) {
        return pending.remove(task);
    }

    /** Runs one step, or moves the clock to the next due task. Returns false when no task can make progress. */
    private boolean step(long timeLimit) {
        if (steps >= maxSteps) {
            throw new DstViolation(
                    "step budget", "the run did not finish within " + maxSteps + " steps (livelock or endless polling?)");
        }
        List<DstTask<?>> candidates = new ArrayList<>();
        Map<DstExecutor, Integer> offered = new IdentityHashMap<>();
        DstTask<?> nextLater = null;
        synchronized (this) {
            for (DstTask<?> t : pending) {
                DstExecutor ex = t.executor();
                int taken = offered.getOrDefault(ex, 0);
                if (ex.busy() + taken >= ex.threads()) {
                    continue;
                }
                if (t.due() <= now) {
                    candidates.add(t);
                    offered.put(ex, taken + 1);
                } else if (nextLater == null) {
                    nextLater = t;
                    // FIFO per executor: a later task of this executor must not overtake
                    offered.put(ex, taken + 1);
                }
            }
        }
        if (candidates.isEmpty()) {
            if (nextLater == null || nextLater.due() > timeLimit) {
                return false;
            }
            now = nextLater.due();
            return true;
        }
        DstTask<?> chosen = candidates.size() == 1 ? candidates.get(0) : candidates.get(random.nextInt(candidates.size()));
        run(chosen);
        return true;
    }

    private void run(DstTask<?> task) {
        synchronized (this) {
            pending.remove(task);
        }
        steps++;
        DstExecutor ex = task.executor();
        ex.enter();
        running.add(task);
        trace("run " + task.description() + (task.isPeriodic() ? " (periodic)" : ""));
        try {
            task.runOnce();
        } finally {
            running.remove(running.size() - 1);
            ex.exit();
        }
        if (task.isPeriodic() && !task.isDone() && !ex.isShutdown()) {
            synchronized (this) {
                task.reschedule(now, sequence++);
                insert(task);
            }
        }
    }

    /** Runs until no task is pending (periodic tasks prevent this: use {@link #runFor(long)}). */
    public void runUntilQuiet() {
        checkLoopThread("runUntilQuiet");
        while (step(Long.MAX_VALUE)) {
            // loop
        }
    }

    /** Runs all tasks due within the next {@code millis} virtual milliseconds, then sets the clock to that time. */
    public void runFor(long millis) {
        checkLoopThread("runFor");
        long target = now + millis;
        while (step(target)) {
            // loop
        }
        if (now < target) {
            now = target;
        }
    }

    /** Runs until the condition holds. Fails when no task can make progress before. */
    public void runUntil(BooleanSupplier condition, String what) {
        checkLoopThread("runUntil");
        while (!condition.getAsBoolean()) {
            if (!step(Long.MAX_VALUE)) {
                throw new DstViolation("progress", "nothing can run any more while waiting for: " + what);
            }
        }
    }

    /**
     * A blocking call of a fake external system that takes {@code millis} virtual milliseconds. The calling task's
     * thread stays busy; the loop runs the other tasks meanwhile.
     */
    public void block(long millis, String what) {
        checkLoopThread("block");
        if (running.isEmpty()) {
            // called from the test body: just let the time pass
            runFor(millis);
            return;
        }
        if (nesting >= MAX_NESTING) {
            throw new DstViolation("nesting", "more than " + MAX_NESTING + " nested blocking calls");
        }
        trace("block " + millis + "ms " + what);
        DstTask<Object> wake = schedule(wakeups, millis, 0, () -> null, "wake " + what);
        nesting++;
        try {
            runUntil(wake::isDone, what);
        } finally {
            nesting--;
        }
    }

    /**
     * A timed wait on the loop thread: runs the other tasks until the condition holds or {@code timeoutMillis} virtual
     * milliseconds have passed, whichever comes first, and returns whether the condition holds. The timeout is a task
     * of the run, so when it is due at the same time as other tasks the seed picks the order.
     * <p>
     * One-stack limit: the condition is checked between tasks of this wait. A task that blocks (nested) above this wait
     * on the stack returns before the wait sees its timeout, so a timeout can be noticed late (see
     * {@link DstTask#get(long, java.util.concurrent.TimeUnit)}).
     */
    public boolean awaitUntil(BooleanSupplier condition, long timeoutMillis, String what) {
        checkLoopThread("awaitUntil");
        if (condition.getAsBoolean()) {
            return true;
        }
        if (timeoutMillis <= 0) {
            return false;
        }
        if (nesting >= MAX_NESTING) {
            throw new DstViolation("nesting", "more than " + MAX_NESTING + " nested blocking calls");
        }
        trace("wait up to " + timeoutMillis + "ms for " + what);
        DstTask<Object> timeout = schedule(wakeups, timeoutMillis, 0, () -> null, "timeout of " + what);
        nesting++;
        try {
            runUntil(() -> condition.getAsBoolean() || timeout.isDone(), what);
        } finally {
            nesting--;
            remove(timeout);
        }
        boolean holds = condition.getAsBoolean();
        if (!holds) {
            trace("timed out after " + timeoutMillis + "ms waiting for " + what);
        }
        return holds;
    }

    /** Waits on the loop thread for a future of this run (used by {@link DstTask#get()}). */
    void await(DstTask<?> task) {
        if (!isLoopThread()) {
            DstEscapes.recordRuntime("a thread outside the harness waits for a DST task: " + Thread.currentThread().getName()
                                     + " -> " + task.description());
            throw new IllegalStateException("DST tasks can only be awaited on the loop thread");
        }
        nesting++;
        try {
            runUntil(task::isDone, "result of " + task.description());
        } finally {
            nesting--;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // invariants

    public void check(Invariant... invariants) {
        for (Invariant invariant : invariants) {
            String violation = invariant.violation();
            trace("check " + invariant.name() + (violation == null ? ": ok" : ": VIOLATED " + violation));
            if (violation != null) {
                throw new DstViolation(invariant.name(), violation);
            }
        }
    }

    void checkLoopThread(String what) {
        if (!isLoopThread()) {
            DstEscapes.recordRuntime(what + " called from a thread outside the harness: " + Thread.currentThread().getName());
            throw new IllegalStateException(what + " must be called on the DST loop thread");
        }
    }

    /**
     * Drops every pending task (the world stops), for example before the CamelContext is stopped at the end of a run:
     * the shutdown then does not interleave with polls or answers of the fakes.
     */
    public synchronized void dropPending() {
        trace("drop " + pending.size() + " pending tasks");
        pending.clear();
    }

    /** Drops every pending task and detaches the virtual clock. */
    public void close() {
        synchronized (this) {
            closed = true;
            pending.clear();
        }
        if (active == this) {
            active = null;
        }
    }

    public synchronized int pendingTasks() {
        return pending.size();
    }
}
