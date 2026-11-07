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

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.StreamCache;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        // the reply of a send, after the producer succeeded, which a rule reaches only with at=reply
        REPLY("reply"),
        // the send of the dead letter channel, which a rule reaches only with at=dlq
        DLQ("dlq"),
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
        CORRUPT("corrupt"),
        // a send that happens twice
        DUPLICATE("duplicate"),
        // stall and lifecycle: they act once on the route, at no hook
        LIFECYCLE("lifecycle");

        final String key;

        Fault(String key) {
            this.key = key;
        }
    }

    /** What a {@code lifecycle} rule (or {@code stall}) does to its route, and how it brings it back. */
    enum Action {
        SUSPEND("suspend", "suspended", "resume", "resumed"),
        STOP("stop", "stopped", "start", "started"),
        REMOVE("remove", "removed", "add back", "added back");

        final String key;
        final String done;
        final String comeback;
        final String back;

        Action(String key, String done, String comeback, String back) {
            this.key = key;
            this.done = done;
            this.comeback = comeback;
            this.back = back;
        }
    }

    /** How a {@code corrupt} rule changes the message. */
    enum CorruptMode {
        HEADERS("headers"),
        CHARSET("charset"),
        TRUNCATE("truncate"),
        EMPTY("empty"),
        REMOVE_HEADER("remove-header");

        final String key;

        CorruptMode(String key) {
            this.key = key;
        }
    }

    /** The state of a rule in the report. */
    enum State {
        ACTIVE("active"),
        // before its start (from=)
        WAITING("waiting"),
        ENDED("ended"),
        REMOVED("removed"),
        REPLACED("replaced");

        final String key;

        State(String key) {
            this.key = key;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ChaosRule.class);

    // the reason of a skip when the condition (when=) threw, one of the fixed texts of the skipped reasons
    static final String CONDITION_FAILED = "the condition (when=) failed with an exception";

    // the golden ratio increment of SplitMix64
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;

    // the seconds of the faults per second the rule keeps: the last minute
    static final int MINUTE = 60;

    // the definition (immutable)
    final String id;
    // 1 when the rule is added, one more each time it is changed (the report has an entry per version of an id)
    final int version;
    final String text;
    final Target target;
    final String pattern;
    // the routes the rule is narrowed to (in=, a route id or a pattern), else null
    final String in;
    // the condition on the message (when=, in the simple language), else null; the predicate is made when the rule is
    // added, so it is null in a rule made without a context
    final String when;
    private final Predicate condition;
    private final boolean conditionReadsBody;
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
    // the longest delay of a jittered delay (duration=100ms..2s), else 0
    final long durationMax;
    // how long an error waits before it fails (after=), else 0
    final long after;
    final CorruptMode mode;
    // the headers mode=remove-header removes (header=), or null for every header that is not one of Camel's own
    final String header;
    final double probability;
    final long every;
    // rate=burst:<on>/<period>: the rule fires at every match during the first <on> of each <period>, else 0
    final long burstOn;
    final long burstPeriod;
    // how long after it is added the rule starts (from=), else 0
    final long from;
    final long untilCount;
    final long untilDuration;
    final String expectRoute;
    // the next route of a lost expectation (orders->shipping:lost<=0), else null
    final String expectToRoute;
    final long expectCount;
    final long within;
    // the action of a lifecycle rule (stall is suspend), else null
    final Action action;
    // the rule says stall, not lifecycle action=suspend
    final boolean stall;
    // how often a lifecycle rule acts (repeat=, 1 without it), and the time from one action to the next (every=)
    final int repeat;
    final long repeatEvery;
    // when the rule was added, and its clock started: a rule added before the context started (camel.chaos.rules)
    // starts its clock when the context has started (see startClock)
    volatile long addedAt;
    // how long the rule runs: its until duration, else camel.chaos.maxDuration
    private final long runFor;

    // the state while it runs
    volatile long endsAt;
    // when the rule starts: when it was added, plus from=
    volatile long startsAt;
    final Expectation expectation;
    private final AtomicLong matches = new AtomicLong();
    private final LongAdder[] fired = new LongAdder[Hook.values().length];
    private final LongAdder skipped = new LongAdder();
    // why the rule did nothing when it fired: a reason per kind of message it could not change (a few fixed texts)
    private final ConcurrentMap<String, LongAdder> skipReasons = new ConcurrentHashMap<>();
    // the messages lost because the rule failed or dropped the send of the dead letter channel, which then handled them
    private final LongAdder lostAtDlq = new LongAdder();
    private final AtomicLong random;
    private volatile State removedAs;
    // the clock of the rule started: false for a rule added before the context started, until it has (guarded by the
    // lock of the manager)
    boolean clockStarted;
    // what a lifecycle rule did to its route, else null
    final ChaosLifecycle.Fault lifecycle;
    // the live state, for the chaos screen: when the rule last fired, its faults per second over the last minute (a
    // ring of seconds, guarded by itself), the effect of its faults on the exchanges, and the recent faults of the
    // manager (null in the tests of a rule alone; set by the manager before the rule is published to the hooks)
    private volatile long lastFiredAt = -1;
    private final long[] faultsPerSecond = new long[MINUTE];
    private final long[] secondOf = new long[MINUTE];
    final Effect effect = new Effect();
    // the faults of the rule per route, for what has been tried (CAMEL-26728); bounded, as the routes are
    final ConcurrentMap<String, LongAdder> firedInRoute = new ConcurrentHashMap<>();
    static final int MAX_ROUTES = 200;
    ChaosRecentFaults recentFaults;

    ChaosRule(Builder b, String id, long seed, long addedAt, long maxDuration) {
        this(b, id, 1, seed, addedAt, maxDuration);
    }

    ChaosRule(Builder b, String id, int version, long seed, long addedAt, long maxDuration) {
        this.id = id;
        this.version = version;
        this.text = b.text;
        this.target = b.target;
        this.pattern = b.pattern;
        this.in = b.in;
        this.when = b.when;
        this.condition = b.condition;
        this.conditionReadsBody = b.whenReadsBody;
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
        this.durationMax = b.durationMax;
        this.after = b.after;
        this.mode = b.mode;
        this.header = b.header;
        this.probability = b.probability;
        this.every = b.every;
        this.burstOn = b.burstOn;
        this.burstPeriod = b.burstPeriod;
        this.from = b.from;
        this.untilCount = b.untilCount;
        this.untilDuration = b.untilDuration;
        this.expectRoute = b.expectRoute;
        this.expectToRoute = b.expectToRoute;
        this.expectCount = b.expectCount;
        this.action = b.action;
        this.stall = b.stall;
        this.repeat = Math.max(1, b.repeat);
        this.repeatEvery = b.repeatEvery;
        this.addedAt = addedAt;
        // a rule without an until duration ends after camel.chaos.maxDuration (also one that ends after a count); the
        // time counts from its start
        this.runFor = untilDuration > 0 ? untilDuration : maxDuration;
        this.startsAt = plus(addedAt, from);
        this.endsAt = plus(startsAt, runFor);
        this.within = b.within > 0 ? b.within : plus(from, untilDuration > 0 ? untilDuration : maxDuration);
        this.expectation = expectRoute != null
                ? new Expectation(expectRoute, expectToRoute, b.expectKind, b.expectOp, expectCount, plus(addedAt, within))
                : null;
        for (int i = 0; i < fired.length; i++) {
            fired[i] = new LongAdder();
        }
        this.random = new AtomicLong(mix(seed + GOLDEN_GAMMA * mix(id.hashCode())));
        this.lifecycle = action != null ? new ChaosLifecycle.Fault(this) : null;
    }

    /**
     * Whether the rule fires for this match, and counts the match. A match after the rule ended (after its until count
     * or its end time) does not count and does not fire.
     */
    boolean fires(long now) {
        if (now >= endsAt || now < startsAt) {
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
        if (burstPeriod > 0) {
            return inBurst(now);
        }
        if (probability < 1.0d) {
            return nextDouble() < probability;
        }
        return true;
    }

    /**
     * Whether the rule can fire now: after its start, before its end time and its until count. The hooks ask it before
     * they match the pattern, so a rule that ended costs two reads until the next change of the rules drops it from the
     * snapshot.
     */
    boolean live(long now) {
        return now >= startsAt && !ended(now);
    }

    /** Whether the rule ended: after its end time or its until count (a rule that did not start yet has not). */
    boolean ended(long now) {
        return now >= endsAt || untilCount > 0 && matches.get() >= untilCount;
    }

    /** Whether a {@code rate=burst:} rule is in a burst now: the first part of each period, from its start. */
    boolean inBurst(long now) {
        return now >= startsAt && (now - startsAt) % burstPeriod < burstOn;
    }

    /**
     * How long until a {@code rate=burst:} rule changes: the end of the burst it is in, or the start of the next one.
     */
    long burstChangesIn(long now) {
        long into = (Math.max(now, startsAt) - startsAt) % burstPeriod;
        long wait = now < startsAt ? startsAt - now : 0;
        return wait + (into < burstOn ? burstOn - into : burstPeriod - into);
    }

    /** How long a {@code delay} waits: its duration, or a draw between the two of a jittered delay. */
    long delayFor() {
        if (durationMax <= duration) {
            return duration;
        }
        return duration + (long) (nextDouble() * (durationMax - duration + 1));
    }

    /** Counts that the rule fired at the hook, where there is no exchange (a poll). */
    void fired(Hook hook) {
        fired(hook, null, null, null);
    }

    /**
     * Counts that the rule fired at the hook, with the live state of the rule: the time, the faults per second, the
     * recent faults, and the exchange it faulted (marked with {@link ChaosManager#CHAOS_RULE}, and counted in the
     * effect of the rule).
     *
     * @param exchange the exchange, or null at a poll
     * @param routeId  the route, or null to take the route of the exchange
     * @param where    the endpoint URI of a send or poll, or the id of a step, or null at the entry of a route
     */
    void fired(Hook hook, Exchange exchange, String routeId, String where) {
        fired[hook.ordinal()].increment();
        long now = TimeSource.currentTimeMillis();
        countFault(now);
        if (exchange != null) {
            ChaosExchangeState.faulted(this, exchange);
        }
        String route = routeId != null || exchange == null ? routeId : ChaosExchangeState.routeId(exchange);
        countInRoute(route);
        ChaosRecentFaults recent = recentFaults;
        if (recent != null) {
            recent.add(now, this, hook, route, where, exchange != null ? exchange.getExchangeId() : null);
        }
    }

    private void countInRoute(String route) {
        if (route != null && (firedInRoute.size() < MAX_ROUTES || firedInRoute.containsKey(route))) {
            firedInRoute.computeIfAbsent(route, k -> new LongAdder()).increment();
        }
    }

    /** A lifecycle rule (or stall) took its route down: counted in its live state, as the faults at a hook are. */
    void firedLifecycle(String routeId, long now) {
        countFault(now);
        countInRoute(routeId);
        ChaosRecentFaults recent = recentFaults;
        if (recent != null) {
            recent.add(now, this, null, routeId, null, null);
        }
    }

    private void countFault(long now) {
        lastFiredAt = now;
        long second = now / 1000;
        int i = (int) (second % MINUTE);
        synchronized (faultsPerSecond) {
            if (secondOf[i] != second) {
                secondOf[i] = second;
                faultsPerSecond[i] = 0;
            }
            faultsPerSecond[i]++;
        }
    }

    /** When the rule last fired, or -1 when it has not. */
    long lastFiredAt() {
        return lastFiredAt;
    }

    /** The faults per second over the last minute, the oldest second first: the last one is the current second. */
    long[] faultsPerSecond(long now) {
        long second = now / 1000;
        long[] answer = new long[MINUTE];
        synchronized (faultsPerSecond) {
            for (int k = 0; k < MINUTE; k++) {
                long s = second - (MINUTE - 1) + k;
                int i = (int) Math.floorMod(s, MINUTE);
                answer[k] = secondOf[i] == s ? faultsPerSecond[i] : 0;
            }
        }
        return answer;
    }

    /**
     * The share of the matches the rule decides to fire at: the probability of {@code rate=}, one in N for
     * {@code rate=every:N}, else all of them.
     */
    double targetRate() {
        if (burstPeriod > 0) {
            return (double) burstOn / burstPeriod;
        }
        return every > 0 ? 1.0d / every : probability;
    }

    /** What the rule does when it fires, in a few words, such as {@code error status 503} or {@code delay 2s}. */
    String faultText() {
        return switch (fault) {
            case ERROR -> "error " + (status > 0
                    ? "status " + status
                    : exceptionType != null ? exceptionType.getSimpleName() : ChaosException.class.getSimpleName())
                          + (after > 0 ? " after " + ChaosRuleParser.printTime(after) : "");
            case DELAY -> "delay " + ChaosRuleParser.printTime(duration)
                          + (durationMax > duration ? ".." + ChaosRuleParser.printTime(durationMax) : "");
            case DROP -> "drop";
            case CORRUPT -> "corrupt " + mode.key + (header != null ? " " + header : "");
            case DUPLICATE -> "duplicate";
            case LIFECYCLE -> (stall ? "stall" : action.key) + " " + ChaosRuleParser.printTime(duration);
        };
    }

    /**
     * The rule in plain words, at all its hooks, such as {@code fail half of the sends to http:stock* with HTTP status
     * 503, for 5 minutes}.
     */
    String describe() {
        return describe(hooks);
    }

    /**
     * The rule in plain words, such as {@code fail half of the sends to http:stock* with HTTP status 503, for 5
     * minutes}: what it does, to which share of what, with the parameters of its fault, then when it starts and ends
     * and what it expects. It starts with a lower case verb, so a tool can put it after a colon.
     *
     * @param where the hooks to name, such as only those that can fire in the context now (the hooks of the rule when
     *              it is empty)
     */
    String describe(Set<Hook> where) {
        Set<Hook> at = where == null || where.isEmpty() ? hooks : where;
        StringBuilder sb = new StringBuilder();
        if (fault == Fault.LIFECYCLE) {
            Action a = action != null ? action : Action.SUSPEND;
            sb.append(a.key).append(' ').append(routeWords()).append(" for ").append(ChaosWords.time(duration));
            if (a == Action.REMOVE) {
                sb.append(", then add it back");
            }
            if (repeat > 1) {
                sb.append(", ").append(repeat).append(" times, every ").append(ChaosWords.time(repeatEvery));
            }
        } else {
            sb.append(verb()).append(' ').append(share(at));
            sb.append(faultWords());
            if (when != null) {
                sb.append(", when ").append(when);
            }
            if (burstPeriod > 0) {
                sb.append(", during the first ").append(ChaosWords.time(burstOn)).append(" of every ")
                        .append(ChaosWords.time(burstPeriod));
            }
        }
        if (from > 0) {
            sb.append(", starting after ").append(ChaosWords.time(from));
        }
        if (untilCount > 0) {
            sb.append(", for the next ")
                    .append(untilCount == 1 ? kind(at, false) : ChaosWords.count(untilCount, kind(at, false), kind(at, true)));
        }
        if (untilDuration > 0) {
            sb.append(", for ").append(ChaosWords.time(untilDuration));
        }
        if (expectation != null) {
            Expectation e = expectation;
            sb.append("; expects ");
            if (e.kind == Expectation.Kind.MEAN_TIME) {
                sb.append("a mean time of ").append(e.op.words).append(' ').append(ChaosWords.time(e.count))
                        .append(" in route ").append(e.route);
            } else if (e.kind == Expectation.Kind.MAX_TIME) {
                sb.append("every exchange in route ").append(e.route).append(" to take ").append(e.op.words).append(' ')
                        .append(ChaosWords.time(e.count));
            } else if (e.kind == Expectation.Kind.LOST) {
                sb.append(e.count == 0 ? "no message" : e.op.words + " " + e.count + " " + e.kind.words(e.count))
                        .append(e.count == 0 ? " lost" : "").append(" between route ").append(e.route).append(" and route ")
                        .append(e.toRoute);
            } else {
                if (e.op == Expectation.Op.AT_MOST && e.count == 0) {
                    sb.append("no ").append(e.kind.words(0));
                } else {
                    sb.append(e.op.words).append(' ').append(e.count).append(' ').append(e.kind.words(e.count));
                }
                sb.append(" in route ").append(e.route);
            }
            if (within != plus(from, runFor)) {
                // a within= of its own (the default is until the rule ends)
                sb.append(" within ").append(ChaosWords.time(within));
            }
        }
        return sb.toString();
    }

    // the verb of the fault
    private String verb() {
        return switch (fault) {
            case ERROR -> "fail";
            case DELAY -> "delay";
            case DROP -> "drop";
            case CORRUPT -> "corrupt";
            case DUPLICATE -> "duplicate";
            case LIFECYCLE -> action != null ? action.key : "suspend";
        };
    }

    // the share of the matches the rule fires at, with what it matches: every send to x, half of the sends to x
    private String share(Set<Hook> at) {
        if (every > 1) {
            return "1 in " + every + " " + noun(at, true);
        }
        if (every == 1 || burstPeriod > 0 || probability >= 1.0d) {
            return "every " + noun(at, false);
        }
        String of = probability == 0.5d ? "half" : ChaosWords.percent(probability);
        return of + " of the " + noun(at, true);
    }

    // what the rule matches at the hooks, such as "send to http:stock*" or "polls of file:inbox*"
    private String noun(Set<Hook> at, boolean plural) {
        return switch (target) {
            case ROUTE -> (plural ? "messages" : "message") + " entering " + routeWords();
            case STEP -> (plural ? "runs" : "run") + " of step " + pattern + inWords();
            case ENDPOINT, COMPONENT -> endpointNoun(at, plural) + inWords();
        };
    }

    // the routes of in=, after what the rule matches
    private String inWords() {
        if (in == null) {
            return "";
        }
        return (in.indexOf('*') >= 0 ? " in routes " : " in route ") + in;
    }

    private String endpointNoun(Set<Hook> at, boolean plural) {
        String what = target == Target.COMPONENT ? "the " + pattern + " endpoints" : pattern;
        List<String> kinds = new ArrayList<>();
        if (at.contains(Hook.SEND)) {
            kinds.add(plural ? "sends" : "send");
        }
        if (at.contains(Hook.REPLY)) {
            kinds.add(plural ? "replies" : "reply");
        }
        if (at.contains(Hook.DLQ)) {
            kinds.add(plural ? "dead letter sends" : "dead letter send");
        }
        if (at.contains(Hook.POLL)) {
            kinds.add(plural ? "polls" : "poll");
        }
        if (kinds.size() == 1) {
            String kind = kinds.get(0);
            String prep = at.contains(Hook.POLL) ? " of " : at.contains(Hook.REPLY) ? " from " : " to ";
            return kind + prep + what;
        }
        return String.join(" and ", kinds) + " of " + what;
    }

    // what the rule matches, without the target: sends, polls, messages, runs (matches when it is more than one kind)
    private String kind(Set<Hook> at, boolean plural) {
        String one = switch (target) {
            case ROUTE -> "message";
            case STEP -> "run";
            case ENDPOINT, COMPONENT -> at.size() == 1
                    ? switch (at.iterator().next()) {
                        case POLL -> "poll";
                        case REPLY -> "reply";
                        case DLQ -> "dead letter send";
                        default -> "send";
                    }
                    : "match";
        };
        if (!plural) {
            return one;
        }
        return switch (one) {
            case "reply" -> "replies";
            case "match" -> "matches";
            default -> one + "s";
        };
    }

    private String routeWords() {
        return (pattern.indexOf('*') >= 0 ? "routes " : "route ") + pattern;
    }

    // the parameters of the fault, after what it matches
    private String faultWords() {
        return switch (fault) {
            case ERROR -> {
                StringBuilder sb = new StringBuilder();
                if (status > 0) {
                    sb.append(" with HTTP status ").append(status);
                } else if (exceptionType != null) {
                    sb.append(" with ").append(exceptionType.getSimpleName());
                    if (message != null) {
                        sb.append(" \"").append(message).append('"');
                    }
                } else if (message != null) {
                    sb.append(" with \"").append(message).append('"');
                }
                if (after > 0) {
                    sb.append(" after waiting ").append(ChaosWords.time(after));
                }
                yield sb.toString();
            }
            case DELAY -> " by " + (durationMax > duration
                    ? ChaosWords.time(duration) + " to " + ChaosWords.time(durationMax) : ChaosWords.time(duration));
            case CORRUPT -> switch (mode) {
                case HEADERS -> " with random header values";
                case CHARSET -> " by encoding the text body in UTF-16";
                case TRUNCATE -> " by cutting the body in half";
                case EMPTY -> " by emptying the body";
                case REMOVE_HEADER -> header == null
                        ? " by removing its headers"
                        : header.indexOf('*') >= 0 ? " by removing the headers " + header
                        : " by removing the header " + header;
            };
            default -> "";
        };
    }

    /**
     * Starts the clock of the rule again: its end time and the deadline of its expectation count from now. For the
     * rules added before the context started, so their times do not include the startup.
     */
    void startClock(long now) {
        addedAt = now;
        startsAt = plus(now, from);
        endsAt = plus(startsAt, runFor);
        if (expectation != null) {
            expectation.startClock(plus(now, within));
        }
    }

    /** Counts a message lost because the rule failed or dropped the send of the dead letter channel. */
    void lostAtDlq() {
        lostAtDlq.increment();
    }

    long lostAtDlqCount() {
        return lostAtDlq.sum();
    }

    /**
     * Counts that the rule fired at a message it could not change, so it did nothing.
     *
     * @param reason why, such as {@code a stream body (charset changes a text body)}: one of a few fixed texts, so the
     *               report can count them
     */
    void skipped(String reason) {
        skipped.increment();
        skipReasons.computeIfAbsent(reason, k -> new LongAdder()).increment();
    }

    /** How often the rule did nothing, per reason, in the order of the reasons. */
    Map<String, Long> skipReasons() {
        Map<String, Long> answer = new TreeMap<>();
        skipReasons.forEach((reason, count) -> answer.put(reason, count.sum()));
        return answer;
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
        if (lifecycle != null) {
            // a lifecycle rule ends when it is done with its route, not by its end time
            if (lifecycle.isDone()) {
                return State.ENDED;
            }
            return lifecycle.isPending() && now < startsAt ? State.WAITING : State.ACTIVE;
        }
        if (ended(now)) {
            return State.ENDED;
        }
        return now < startsAt ? State.WAITING : State.ACTIVE;
    }

    /** The rule was removed by hand (or replaced by a change): its expectation is not decided any more. */
    void removed(State as, long now) {
        removedAs = as;
        if (expectation != null) {
            expectation.freeze(now);
        }
        if (lifecycle != null) {
            // the fault ends with the rule: a route it took down comes back now
            lifecycle.ruleRemoved();
        }
    }

    /** Counts the match of a lifecycle rule: it found its route when it acted. */
    void matched() {
        matches.incrementAndGet();
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

    /**
     * Whether the exchange is in a route of {@code in=}: the route of its unit of work, which is the route a
     * {@code direct} call is in, not the route that called it. True for a rule without {@code in=}.
     */
    boolean matchesIn(Exchange exchange) {
        return in == null || matchesIn(ChaosExchangeState.routeId(exchange));
    }

    /**
     * Whether the message meets the condition of {@code when=} (true for a rule without one). A condition that throws
     * is not met, and counts as skipped. A condition on the body reads a stream body from its cache: a stream that is
     * not cached yet (such as the reply of a send) is cached first, and the cache is reset after the check, so the
     * route reads the body as before.
     */
    boolean matchesWhen(Exchange exchange) {
        Predicate c = condition;
        if (c == null) {
            return true;
        }
        try {
            if (conditionReadsBody) {
                cacheStreamBody(exchange);
            }
            return c.matches(exchange);
        } catch (Exception e) {
            LOG.debug("The condition of chaos rule {} (when={}) failed on exchange {}: {}", id, when,
                    exchange.getExchangeId(), e.getMessage(), e);
            skipped(CONDITION_FAILED);
            return false;
        } finally {
            if (conditionReadsBody) {
                MessageHelper.resetStreamCache(exchange.getMessage());
            }
        }
    }

    private static void cacheStreamBody(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        if ((body instanceof InputStream || body instanceof Reader) && !(body instanceof StreamCache)) {
            StreamCache cache = exchange.getContext().getStreamCachingStrategy().cache(exchange);
            if (cache != null) {
                exchange.getMessage().setBody(cache);
            }
        }
    }

    /** Whether the route is one of {@code in=} (true for a rule without it). */
    boolean matchesIn(String routeId) {
        return in == null || (routeId != null && ChaosPatterns.matches(in, routeId));
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
     * An expectation of a rule: a count of the route after the rule is added, before the time is up. Its kind is what
     * it counts (the exchanges that completed, were handled by the error handler or failed, or the redeliveries), and
     * its op how the count compares: at least ({@code >=}, as {@code expect=orders:100} is), at most ({@code <=}) or
     * exactly ({@code =}) that many.
     * <p/>
     * At least is met as soon as the count is reached in time, and unmet when the time is up without it. At most and
     * exactly are unmet as soon as the count goes over, and decided when the time is up otherwise. Before that it is
     * pending. It is cancelled when the rule is removed (or replaced), or the context stops, first: the messages may
     * still arrive, so it is neither met nor unmet, and does not keep the report from being final.
     */
    static final class Expectation {

        /** The state of an expectation. */
        enum Outcome {
            MET("met"),
            UNMET("unmet"),
            PENDING("pending"),
            CANCELLED("cancelled");

            final String key;

            Outcome(String key) {
                this.key = key;
            }
        }

        /** What an expectation counts in its route. */
        enum Kind {
            COMPLETED("completed", "exchange completed", "exchanges completed", "The exchanges completed in the route"),
            HANDLED("handled", "exchange handled by the error handler", "exchanges handled by the error handler",
                    "The exchanges handled by the error handler in the route"),
            FAILED("failed", "exchange failed", "exchanges failed", "The exchanges failed in the route"),
            REDELIVERIES("redeliveries", "redelivery", "redeliveries", "The redeliveries in the route"),
            // the time an exchange is in the route, from when it enters until the route is done with it (CAMEL-26726)
            MEAN_TIME("meanTime", "mean time", "mean time",
                      "The mean time of the exchanges in the route, from entering it until done, such as meanTime<=2s"),
            MAX_TIME("maxTime", "max time", "max time",
                     "The longest time of an exchange in the route, from entering it until done, such as maxTime<=5s"),
            // the messages one route completed that the next one did not: <route>-><next route>:lost<=0
            LOST("lost", "message lost", "messages lost",
                 "The messages completed in the route and not in the next one, such as orders->shipping:lost<=0");

            final String key;
            // for the reason of an unmet expectation
            private final String one;
            private final String many;
            final String description;

            Kind(String key, String one, String many, String description) {
                this.key = key;
                this.one = one;
                this.many = many;
                this.description = description;
            }

            String words(long n) {
                return n == 1 ? one : many;
            }

            /** Whether the count of the kind is a time (in milliseconds), not a number of exchanges. */
            boolean time() {
                return this == MEAN_TIME || this == MAX_TIME;
            }
        }

        /** How the count of an expectation compares. */
        enum Op {
            AT_LEAST(">=", "at least"),
            AT_MOST("<=", "at most"),
            EXACTLY("=", "exactly");

            final String key;
            final String words;

            Op(String key, String words) {
                this.key = key;
                this.words = words;
            }
        }

        final String route;
        // the next route of a lost expectation, else null
        final String toRoute;
        final Kind kind;
        final Op op;
        // a number, or a time in milliseconds for a time kind
        final long count;
        private long deadline;
        private long value;
        // a time kind: the exchanges done, their time together and the longest; lost: the completed of both routes
        private long samples;
        private long sum;
        private long max;
        private long toValue;
        // when it was decided before the time was up (met for at least, unmet for at most and exactly), and how
        private long decidedAt = -1;
        private Outcome decided;
        private long frozenAt = -1;

        Expectation(String route, long count, long deadline) {
            this(route, Kind.COMPLETED, Op.AT_LEAST, count, deadline);
        }

        Expectation(String route, Kind kind, Op op, long count, long deadline) {
            this(route, null, kind, op, count, deadline);
        }

        Expectation(String route, String toRoute, Kind kind, Op op, long count, long deadline) {
            this.route = route;
            this.toRoute = toRoute;
            this.kind = kind;
            this.op = op;
            this.count = count;
            this.deadline = deadline;
        }

        /**
         * The route of this expectation counted one more of its kind. The time is read under the lock, so once a report
         * saw the expectation decided, a count that comes later cannot change it. The count goes on after that, until
         * the time is up, so the report says how far it went over: "6 exchanges handled, at most 0 expected".
         */
        synchronized void record() {
            recordAt(TimeSource.currentTimeMillis());
        }

        /** The route of this expectation counted one more of its kind at the given time (the tests give the time). */
        synchronized void recordAt(long now) {
            if (frozenAt >= 0 || now > deadline) {
                return;
            }
            value++;
            if (decided == null && (op == Op.AT_LEAST ? value >= count : value > count)) {
                decided = op == Op.AT_LEAST ? Outcome.MET : Outcome.UNMET;
                decidedAt = now;
            }
        }

        /** An exchange was done in the route, after it was so long in it (a time kind). */
        synchronized void recordTime(long millis) {
            recordTimeAt(millis, TimeSource.currentTimeMillis());
        }

        synchronized void recordTimeAt(long millis, long now) {
            if (frozenAt >= 0 || now > deadline) {
                return;
            }
            samples++;
            sum += millis;
            max = Math.max(max, millis);
            if (decided == null && kind == Kind.MAX_TIME && op == Op.AT_MOST && millis > count) {
                // one exchange took too long: it cannot become met
                decided = Outcome.UNMET;
                decidedAt = now;
            }
        }

        /**
         * An exchange completed in the route ({@code next} false) or in the next route (true) of a lost expectation.
         */
        synchronized void recordLost(boolean next) {
            if (frozenAt >= 0 || TimeSource.currentTimeMillis() > deadline) {
                return;
            }
            if (next) {
                toValue++;
            } else {
                samples++;
            }
        }

        synchronized long deadline() {
            return deadline;
        }

        /** The clock of the rule starts again (see {@link ChaosRule#startClock(long)}). */
        synchronized void startClock(long deadline) {
            if (decided == null && frozenAt < 0) {
                this.deadline = deadline;
            }
        }

        synchronized void freeze(long now) {
            if (frozenAt < 0) {
                frozenAt = now;
            }
        }

        synchronized long value() {
            return switch (kind) {
                case MEAN_TIME -> samples > 0 ? sum / samples : 0;
                case MAX_TIME -> max;
                case LOST -> Math.max(0, samples - toValue);
                default -> value;
            };
        }

        synchronized Outcome outcome(long now) {
            if (decided != null) {
                return decided;
            }
            if (frozenAt >= 0 && frozenAt <= deadline) {
                // removed before it was decided: the messages may still arrive, so it is neither met nor unmet
                return Outcome.CANCELLED;
            }
            if (now <= deadline) {
                return Outcome.PENDING;
            }
            // the time is up: a time of no exchange says nothing, so it is not met
            if (kind.time() && samples == 0) {
                return Outcome.UNMET;
            }
            long v = value();
            boolean met = switch (op) {
                case AT_LEAST -> v >= count;
                case AT_MOST -> v <= count;
                case EXACTLY -> v == count;
            };
            return met ? Outcome.MET : Outcome.UNMET;
        }

        synchronized boolean frozen() {
            return frozenAt >= 0;
        }

        /** The outcome, the counted value and whether it is frozen, read together (for the report). */
        synchronized Reading read(long now) {
            return new Reading(outcome(now), value(), frozenAt >= 0, samples, toValue);
        }

        /**
         * The messages an unmet expectation counts as lost: only one of completed exchanges, at least or exactly that
         * many, misses the ones that did not complete.
         */
        long lost(Reading reading) {
            if (reading.outcome() != Outcome.UNMET) {
                return 0;
            }
            if (kind == Kind.LOST) {
                // the messages the next route did not complete
                return reading.value();
            }
            return kind == Kind.COMPLETED && op != Op.AT_MOST ? Math.max(0, count - reading.value()) : 0;
        }

        /** The expectation as the rule writes it, such as orders:100 or orders:handled<=0. */
        String spec() {
            if (kind == Kind.COMPLETED && op == Op.AT_LEAST) {
                return route + ":" + count;
            }
            String routes = toRoute != null ? route + "->" + toRoute : route;
            return routes + ":" + kind.key + op.key + (kind.time() ? ChaosRuleParser.printTime(count) : count);
        }

        /** The value as the report says it, such as {@code 3 completed} or {@code mean 1s200ms of 12 exchanges}. */
        String valueText(Reading reading) {
            return switch (kind) {
                case MEAN_TIME, MAX_TIME -> reading.samples() == 0
                        ? "no exchange done yet"
                        : (kind == Kind.MEAN_TIME ? "mean " : "max ") + ChaosRuleParser.printTime(reading.value()) + " of "
                          + reading.samples() + (reading.samples() == 1 ? " exchange" : " exchanges");
                case LOST -> reading.value() + " lost (" + reading.samples() + " completed in " + route + ", "
                             + reading.next() + " in " + toRoute + ")";
                default -> reading.value() + " " + kind.key;
            };
        }

        /** What an expectation reads at one time. */
        record Reading(Outcome outcome, long value, boolean frozen, long samples, long next) {
        }
    }

    /**
     * The effect of the faults of a rule on the exchanges it faulted, each counted once when it is done: completed
     * (without a redelivery after the fault, such as after a delay), recovered by a redelivery of the error handler,
     * handled by the error handler (such as the dead letter channel), or failed. The others are still in flight. Only
     * the exchanges with a unit of work count, as it tells when the exchange is done.
     */
    static final class Effect {

        /** How an exchange was done, after the rule faulted it. */
        enum Outcome {
            COMPLETED,
            RECOVERED,
            HANDLED,
            FAILED
        }

        private final LongAdder faulted = new LongAdder();
        private final LongAdder[] done = new LongAdder[Outcome.values().length];

        Effect() {
            for (int i = 0; i < done.length; i++) {
                done[i] = new LongAdder();
            }
        }

        void faulted() {
            faulted.increment();
        }

        /** A fault of another copy of an exchange that was counted already (CAMEL-26701). */
        void unfaulted() {
            faulted.decrement();
        }

        void done(Outcome outcome) {
            done[outcome.ordinal()].increment();
        }

        long faultedCount() {
            return faulted.sum();
        }

        long count(Outcome outcome) {
            return done[outcome.ordinal()].sum();
        }
    }

    /** The definition of a rule, as the parser builds it. */
    static final class Builder {
        String text;
        Target target;
        String pattern;
        String in;
        String when;
        boolean whenReadsBody;
        Predicate condition;
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
        long durationMax;
        long after;
        CorruptMode mode;
        String header;
        double probability = 1.0d;
        long every;
        long burstOn;
        long burstPeriod;
        long from;
        long untilCount;
        long untilDuration;
        String expectRoute;
        String expectToRoute;
        Expectation.Kind expectKind = Expectation.Kind.COMPLETED;
        Expectation.Op expectOp = Expectation.Op.AT_LEAST;
        long expectCount;
        long within;
        Action action;
        // the rule says stall (lifecycle action=suspend)
        boolean stall;
        int repeat;
        long repeatEvery;
    }
}
