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
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.Endpoint;

/**
 * A rule that was added: what it targets and does (parsed by {@link ChaosRuleParser}, immutable), and its state while
 * it runs (the matches, the faults that fired and the random draws, all thread-safe).
 */
final class ChaosRule {

    /** The kind of target of a rule. */
    enum Target {
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
        DROP("drop");

        final String key;

        Fault(String key) {
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
    final String text;
    final Target target;
    final String pattern;
    final Set<Hook> hooks;
    final Fault fault;
    final Class<? extends Throwable> exceptionType;
    final Constructor<? extends Throwable> exceptionConstructor;
    final boolean exceptionTakesMessage;
    final String message;
    final long duration;
    final double probability;
    final long every;
    final long untilCount;
    final long untilDuration;

    // the state while it runs
    final long endsAt;
    private final AtomicLong matches = new AtomicLong();
    private final LongAdder[] fired = new LongAdder[Hook.values().length];
    private final AtomicLong random;
    private volatile State removedAs;

    ChaosRule(Builder b, String id, long seed, long addedAt, long maxDuration) {
        this.id = id;
        this.text = b.text;
        this.target = b.target;
        this.pattern = b.pattern;
        this.hooks = Set.copyOf(b.hooks);
        this.fault = b.fault;
        this.exceptionType = b.exceptionType;
        this.exceptionConstructor = b.exceptionConstructor;
        this.exceptionTakesMessage = b.exceptionTakesMessage;
        this.message = b.message;
        this.duration = b.duration;
        this.probability = b.probability;
        this.every = b.every;
        this.untilCount = b.untilCount;
        this.untilDuration = b.untilDuration;
        // a rule without an until duration ends after camel.chaos.maxDuration (also one that ends after a count)
        this.endsAt = plus(addedAt, untilDuration > 0 ? untilDuration : maxDuration);
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

    /** Counts that the rule fired at the hook. */
    void fired(Hook hook) {
        fired[hook.ordinal()].increment();
    }

    long matches() {
        return matches.get();
    }

    long firedAt(Hook hook) {
        return fired[hook.ordinal()].sum();
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

    /** The rule was removed by hand, or replaced by a change. */
    void removed(State as) {
        removedAs = as;
    }

    /** The next random number in [0, 1) of the sequence of this rule, which the seed fixes. */
    double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** The next random number of the sequence of this rule (SplitMix64, lock-free, one sequence for all threads). */
    long nextLong() {
        return mix(random.addAndGet(GOLDEN_GAMMA));
    }

    /** Whether the rule targets this endpoint (for {@code endpoint=} and {@code component=}). */
    boolean matchesEndpoint(Endpoint endpoint) {
        String uri = endpoint.getEndpointUri();
        if (target == Target.COMPONENT) {
            return ChaosPatterns.matches(pattern, ChaosPatterns.scheme(uri));
        }
        return ChaosPatterns.matchesUri(pattern, uri);
    }

    /** Whether the rule targets this step id (for {@code step=}). */
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
        return id + ": " + text;
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
        long duration;
        double probability = 1.0d;
        long every;
        long untilCount;
        long untilDuration;
    }
}
