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
package org.apache.camel.component.chaos;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.util.TimeSource;

/**
 * A rule that was added: what it targets and does (parsed by {@link ChaosRuleParser}, immutable), and its state while
 * it runs (the matches, the faults that fired, the random draws and the expectation, all thread-safe).
 */
final class ChaosRule {

    /** The kind of target of a rule. */
    enum Target {
        ROUTE("route"),
        ENDPOINT("endpoint"),
        COMPONENT("component"),
        STEP("step");

        final String key;

        Target(String key) {
            this.key = key;
        }
    }

    /** A place where a rule can fire. */
    enum Hook {
        SEND("send"),
        POLL("poll"),
        ENTRY("entry"),
        STEP("step");

        final String key;

        Hook(String key) {
            this.key = key;
        }
    }

    /** What a rule does when it fires. */
    enum Fault {
        ERROR("error"),
        DELAY("delay"),
        DROP("drop"),
        CORRUPT("corrupt");

        final String key;

        Fault(String key) {
            this.key = key;
        }
    }

    /** How a {@code corrupt} rule changes the message. */
    enum CorruptMode {
        HEADERS("headers"),
        CHARSET("charset"),
        TRUNCATE("truncate");

        final String key;

        CorruptMode(String key) {
            this.key = key;
        }
    }

    /** The state of a rule in the report. */
    enum State {
        ACTIVE("active"),
        ENDED("ended"),
        REMOVED("removed"),
        REPLACED("replaced");

        final String key;

        State(String key) {
            this.key = key;
        }
    }

    // the golden ratio increment of SplitMix64
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;

    // the definition (immutable)
    final String id;
    // 1 when the rule is added, one more each time it is changed (the report has an entry per version of an id)
    final int version;
    final String text;
    final Target target;
    final String pattern;
    // the endpoint= pattern without the // after its scheme, made once (the hooks match it on every send and poll)
    private final String uriPattern;
    final Set<Hook> hooks;
    final Fault fault;
    final Class<? extends Throwable> exceptionType;
    final Constructor<? extends Throwable> exceptionConstructor;
    final boolean exceptionTakesMessage;
    final String message;
    final int status;
    final Map<String, String> responseHeaders;
    final String responseBody;
    final long duration;
    final CorruptMode mode;
    final double probability;
    final long every;
    final long untilCount;
    final long untilDuration;
    final String expectRoute;
    final long expectCount;
    final long within;

    // the state while it runs
    final long endsAt;
    final Expectation expectation;
    private final AtomicLong matches = new AtomicLong();
    private final LongAdder[] fired = new LongAdder[Hook.values().length];
    private final LongAdder skipped = new LongAdder();
    private final AtomicLong random;
    private volatile State removedAs;

    ChaosRule(Builder b, String id, long seed, long addedAt, long maxDuration) {
        this(b, id, 1, seed, addedAt, maxDuration);
    }

    ChaosRule(Builder b, String id, int version, long seed, long addedAt, long maxDuration) {
        this.id = id;
        this.version = version;
        this.text = b.text;
        this.target = b.target;
        this.pattern = b.pattern;
        this.uriPattern = target == Target.ENDPOINT ? ChaosPatterns.withoutSlashes(pattern) : null;
        this.hooks = Set.copyOf(b.hooks);
        this.fault = b.fault;
        this.exceptionType = b.exceptionType;
        this.exceptionConstructor = b.exceptionConstructor;
        this.exceptionTakesMessage = b.exceptionTakesMessage;
        this.message = b.message;
        this.status = b.status;
        this.responseHeaders = Map.copyOf(b.responseHeaders);
        this.responseBody = b.responseBody;
        this.duration = b.duration;
        this.mode = b.mode;
        this.probability = b.probability;
        this.every = b.every;
        this.untilCount = b.untilCount;
        this.untilDuration = b.untilDuration;
        this.expectRoute = b.expectRoute;
        this.expectCount = b.expectCount;
        // a rule without an until duration ends after camel.chaos.maxDuration (also one that ends after a count)
        this.endsAt = plus(addedAt, untilDuration > 0 ? untilDuration : maxDuration);
        this.within = b.within > 0 ? b.within : (untilDuration > 0 ? untilDuration : maxDuration);
        this.expectation = expectRoute != null ? new Expectation(expectRoute, expectCount, plus(addedAt, within)) : null;
        for (int i = 0; i < fired.length; i++) {
            fired[i] = new LongAdder();
        }
        this.random = new AtomicLong(mix(seed + GOLDEN_GAMMA * mix(id.hashCode())));
    }

    /**
     * Whether the rule fires for this match, and counts the match. A match after the rule ended (after its until count
     * or its end time) does not count and does not fire.
     */
    boolean fires(long now) {
        if (now >= endsAt) {
            return false;
        }
        long n;
        do {
            n = matches.get();
            if (untilCount > 0 && n >= untilCount) {
                return false;
            }
        } while (!matches.compareAndSet(n, n + 1));
        long match = n + 1;
        if (every > 0) {
            return match % every == 0;
        }
        if (probability < 1.0d) {
            return nextDouble() < probability;
        }
        return true;
    }

    /**
     * Whether the rule can still fire: before its end time and its until count. The hooks ask it before they match the
     * pattern, so a rule that ended costs two reads until the next change of the rules drops it from the snapshot.
     */
    boolean live(long now) {
        return now < endsAt && (untilCount == 0 || matches.get() < untilCount);
    }

    /** Counts that the rule fired at the hook. */
    void fired(Hook hook) {
        fired[hook.ordinal()].increment();
    }

    /** Counts that the rule fired at a message it could not change, so it did nothing. */
    void skipped() {
        skipped.increment();
    }

    long matches() {
        return matches.get();
    }

    long firedAt(Hook hook) {
        return fired[hook.ordinal()].sum();
    }

    long skippedCount() {
        return skipped.sum();
    }

    /** The state of the rule at the given time. */
    State state(long now) {
        State removed = removedAs;
        if (removed != null) {
            return removed;
        }
        if (now >= endsAt || untilCount > 0 && matches.get() >= untilCount) {
            return State.ENDED;
        }
        return State.ACTIVE;
    }

    /** The rule was removed by hand (or replaced by a change): its expectation is not decided any more. */
    void removed(State as, long now) {
        removedAs = as;
        if (expectation != null) {
            expectation.freeze(now);
        }
    }

    /** The next random number in [0, 1) of the sequence of this rule, which the seed fixes. */
    double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** The next random number of the sequence of this rule (SplitMix64, lock-free, one sequence for all threads). */
    long nextLong() {
        return mix(random.addAndGet(GOLDEN_GAMMA));
    }

    /**
     * Whether the rule targets the endpoint with this URI, as {@code ChaosPatterns.endpointUri} gives it (for
     * {@code endpoint=} and {@code component=}).
     */
    boolean matchesUri(String uri) {
        if (target == Target.COMPONENT) {
            return ChaosPatterns.matchesScheme(pattern, uri);
        }
        return ChaosPatterns.matchesUriPattern(uriPattern, uri);
    }

    /** Whether the rule targets this route id or step id (for {@code route=} and {@code step=}). */
    boolean matchesId(String id) {
        return id != null && ChaosPatterns.matches(pattern, id);
    }

    // a time plus a duration, at most Long.MAX_VALUE (a very long camel.chaos.maxDuration means "no end")
    private static long plus(long time, long duration) {
        long sum = time + duration;
        return sum < time ? Long.MAX_VALUE : sum;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    @Override
    public String toString() {
        return id + " v" + version + ": " + text;
    }

    /**
     * An expectation of a rule: the route completes at least that many more exchanges after the rule is added, before
     * the time is up. It is met when the count is reached in time, unmet when the time is up without it, and pending
     * before that, or for good when the rule is removed (or replaced) first.
     */
    static final class Expectation {

        /** The state of an expectation. */
        enum Outcome {
            MET("met"),
            UNMET("unmet"),
            PENDING("pending");

            final String key;

            Outcome(String key) {
                this.key = key;
            }
        }

        final String route;
        final long count;
        final long deadline;
        private long completed;
        private long metAt = -1;
        private long frozenAt = -1;

        Expectation(String route, long count, long deadline) {
            this.route = route;
            this.count = count;
            this.deadline = deadline;
        }

        /**
         * The route of this expectation completed an exchange. The time is read under the lock, so once a report saw
         * the expectation unmet, a completion that comes later cannot make it met.
         */
        synchronized void complete() {
            completeAt(TimeSource.currentTimeMillis());
        }

        /** The route of this expectation completed an exchange at the given time (the tests give the time). */
        synchronized void completeAt(long now) {
            if (metAt >= 0 || frozenAt >= 0 || now > deadline) {
                return;
            }
            completed++;
            if (completed >= count) {
                metAt = now;
            }
        }

        synchronized void freeze(long now) {
            if (frozenAt < 0) {
                frozenAt = now;
            }
        }

        synchronized long completed() {
            return completed;
        }

        synchronized Outcome outcome(long now) {
            if (metAt >= 0) {
                return Outcome.MET;
            }
            if (frozenAt >= 0 && frozenAt <= deadline) {
                // removed before it was decided: the messages may still arrive, so they are not called lost
                return Outcome.PENDING;
            }
            return now > deadline ? Outcome.UNMET : Outcome.PENDING;
        }

        synchronized boolean frozen() {
            return frozenAt >= 0;
        }

        /** The outcome, the completed count and whether it is frozen, read together (for the report). */
        synchronized Reading read(long now) {
            return new Reading(outcome(now), completed, frozenAt >= 0);
        }

        /** What the report reads of an expectation at one time. */
        record Reading(Outcome outcome, long completed, boolean frozen) {
        }
    }

    /** The definition of a rule, as the parser builds it. */
    static final class Builder {
        String text;
        Target target;
        String pattern;
        Hook at;
        Set<Hook> hooks;
        Fault fault;
        Class<? extends Throwable> exceptionType;
        Constructor<? extends Throwable> exceptionConstructor;
        boolean exceptionTakesMessage;
        String message;
        int status;
        Map<String, String> responseHeaders = Map.of();
        String responseBody;
        long duration;
        CorruptMode mode;
        double probability = 1.0d;
        long every;
        long untilCount;
        long untilDuration;
        String expectRoute;
        long expectCount;
        long within;
    }
}
