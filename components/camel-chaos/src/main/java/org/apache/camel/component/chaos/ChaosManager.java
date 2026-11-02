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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.State;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rules of camel-chaos in a {@link CamelContext}, and the report of what they did.
 * <p/>
 * There is a manager only when chaos is switched on (see {@link ChaosContextServicePlugin}); {@link #get(CamelContext)}
 * returns it. Rules are added, changed and removed while the routes run, from any thread: the hooks read an immutable
 * snapshot of the rules, so a change applies to the next send, poll, exchange or step, and one that is running keeps
 * the rules it started with.
 *
 * @since 4.24
 */
public final class ChaosManager {

    /**
     * The most rules at a time. When there are this many, adding a rule first moves the rules that ended (and whose
     * expectation is decided) into the report history; remove some to add more when that does not make room.
     */
    static final int MAX_RULES = 100;

    // the most removed, replaced or ended rules the report keeps
    static final int MAX_HISTORY = 100;

    private static final Logger LOG = LoggerFactory.getLogger(ChaosManager.class);

    private final CamelContext camelContext;
    private final long seed;
    private final boolean seedGiven;
    private final long maxDuration;

    private final Object lock = new Object();
    // guarded by lock: the rules in the order they were added, the removed ones, and the number of the next id
    private final List<ChaosRule> rules = new ArrayList<>();
    private final Deque<ChaosRule> history = new ArrayDeque<>();
    private int nextId = 1;
    // the unmet expectations of the rules that left the history, and the messages they count as lost
    private int evictedUnmet;
    private long evictedLost;
    private int evictedPending;

    // the snapshot the hooks read
    private volatile RuleSet ruleSet = RuleSet.EMPTY;
    // whether the event notifier counts the exchanges (while there are rules)
    private volatile boolean counting;
    private volatile boolean used;
    private final ConcurrentMap<String, RouteCounts> routes = new ConcurrentHashMap<>();

    ChaosManager(CamelContext camelContext, long seed, boolean seedGiven, long maxDuration) {
        this.camelContext = camelContext;
        this.seed = seed;
        this.seedGiven = seedGiven;
        this.maxDuration = maxDuration;
    }

    /**
     * The manager of the context, or null when chaos is not switched on in it.
     *
     * @param  camelContext the context
     * @return              the manager, or null
     */
    public static ChaosManager get(CamelContext camelContext) {
        return camelContext.getCamelContextExtension().getContextPlugin(ChaosManager.class);
    }

    /**
     * The seed of the random draws of the rules: {@code camel.chaos.seed}, or one that was picked at startup.
     */
    long getSeed() {
        return seed;
    }

    /**
     * Adds a rule.
     *
     * @param  rule                     the rule in its one-line text form, such as
     *                                  {@code endpoint=kafka:orders* error rate=0.3 until=1m}
     * @return                          the id of the rule, for {@link #changeRule(String, String)} and
     *                                  {@link #removeRule(String)}
     * @throws IllegalArgumentException when the rule is not valid, with the reason
     * @throws IllegalStateException    when there are {@link #MAX_RULES} rules that have not ended (or whose
     *                                  expectation is still pending)
     */
    public String addRule(String rule) {
        ChaosRule.Builder definition = ChaosRuleParser.parse(rule, camelContext.getClassResolver(), maxDuration);
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            if (rules.size() >= MAX_RULES) {
                retireEnded(now);
            }
            if (rules.size() >= MAX_RULES) {
                throw new IllegalStateException(
                        "There are " + MAX_RULES + " chaos rules already; remove some before adding more");
            }
            String id = "r" + nextId++;
            ChaosRule added = new ChaosRule(definition, id, seed, now, maxDuration);
            rules.add(added);
            update();
            LOG.info("Chaos rule {} added: {}", id, added.text);
            if (camelContext.isStarted()) {
                warnIfNoRoute(added);
            }
            return id;
        }
    }

    /**
     * Changes a rule: the rule with that id is replaced by the new one, which starts with new counts, a new end time
     * and a new expectation, and has the next version of the id (the rule that is added is version 1). The replaced
     * rule stays in the report with its own version, so the report has an entry per version of the id, and the entries
     * of an id are told apart by their version.
     *
     * @param  id                       the id of the rule
     * @param  rule                     the new rule in its one-line text form
     * @return                          the version of the new rule
     * @throws IllegalArgumentException when there is no rule with that id, or the new rule is not valid
     */
    public int changeRule(String id, String rule) {
        ChaosRule.Builder definition = ChaosRuleParser.parse(rule, camelContext.getClassResolver(), maxDuration);
        synchronized (lock) {
            int index = indexOf(id);
            if (index < 0) {
                throw new IllegalArgumentException("There is no chaos rule " + id);
            }
            long now = TimeSource.currentTimeMillis();
            ChaosRule changed = new ChaosRule(definition, id, rules.get(index).version + 1, seed, now, maxDuration);
            ChaosRule old = rules.set(index, changed);
            old.removed(State.REPLACED, now);
            remember(old);
            update();
            LOG.info("Chaos rule {} changed (version {}): {}", id, changed.version, changed.text);
            if (camelContext.isStarted()) {
                warnIfNoRoute(changed);
            }
            return changed.version;
        }
    }

    /**
     * Whether there is a rule with that id that can be changed or removed: one that was added and is not removed, nor
     * moved to the report history.
     *
     * @param  id the id of the rule
     * @return    whether there is such a rule
     */
    public boolean hasRule(String id) {
        synchronized (lock) {
            return indexOf(id) >= 0;
        }
    }

    /**
     * Removes a rule. It stays in the report, and its expectation, if not decided yet, stays pending.
     *
     * @param  id the id of the rule
     * @return    whether there was a rule with that id
     */
    public boolean removeRule(String id) {
        synchronized (lock) {
            int index = indexOf(id);
            if (index < 0) {
                return false;
            }
            ChaosRule removed = rules.remove(index);
            removed.removed(State.REMOVED, TimeSource.currentTimeMillis());
            remember(removed);
            update();
            LOG.info("Chaos rule {} removed", id);
            return true;
        }
    }

    /**
     * Removes all rules.
     *
     * @return the number of rules removed
     */
    public int clearRules() {
        synchronized (lock) {
            int count = rules.size();
            long now = TimeSource.currentTimeMillis();
            for (ChaosRule rule : rules) {
                rule.removed(State.REMOVED, now);
                remember(rule);
            }
            rules.clear();
            update();
            if (count > 0) {
                LOG.info("Chaos rules removed: {}", count);
            }
            return count;
        }
    }

    /**
     * The report: the rules and what they did, the counts per route, the expectations and the verdict.
     */
    public ChaosReport report() {
        List<ChaosRule> current;
        List<ChaosRule> removed;
        int unmet;
        long lost;
        int pending;
        synchronized (lock) {
            current = new ArrayList<>(rules);
            removed = new ArrayList<>(history);
            unmet = evictedUnmet;
            lost = evictedLost;
            pending = evictedPending;
        }
        Map<String, RouteCounts> counts = new TreeMap<>(routes);
        return new ChaosReport(
                seed, seedGiven, TimeSource.currentTimeMillis(), current, removed, counts, unmet, lost, pending);
    }

    // -- for the hooks, the event notifier and the plugin

    RuleSet ruleSet() {
        return ruleSet;
    }

    boolean isCounting() {
        return counting;
    }

    /** Whether a rule was ever added (for the report when the context stops). */
    boolean isUsed() {
        return used;
    }

    RouteCounts counts(String routeId) {
        return routes.computeIfAbsent(routeId, k -> new RouteCounts());
    }

    /**
     * A route is done with an exchange: it completed (also after the error handler handled a failure, as with the dead
     * letter channel or {@code onException} with {@code handled(true)}) or failed (also when it was marked rollback
     * only). An exchange counts in each route it goes through: a route called with {@code direct} counts it too, and a
     * copy of a wire tap or a seda queue counts in the route that consumes it.
     */
    void routeDone(Route route, Exchange exchange) {
        String routeId = route.getRouteId();
        RouteCounts counts = counts(routeId);
        // an exchange marked rollback only has no exception, but its message is not processed either
        boolean failed = exchange.isFailed();
        if (failed || exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
            counts.failed.increment();
            // the JMS listener rolls back for isRollbackOnly, not for isRollbackOnlyLast (which a transaction policy
            // of the route handles)
            if (failed
                    ? ChaosConsumers.keepsFailed(route.getEndpoint())
                    : exchange.isRollbackOnly() && ChaosConsumers.keepsRollbackOnly(route.getEndpoint())) {
                counts.failedKept.increment();
            }
            return;
        }
        for (ChaosRule.Expectation expectation : ruleSet.expectations) {
            if (expectation.route.equals(routeId)) {
                expectation.complete();
            }
        }
        ExchangeExtension ee = exchange.getExchangeExtension();
        if (ee.isErrorHandlerHandledSet() && ee.isErrorHandlerHandled()) {
            counts.handled.increment();
        }
        counts.completed.increment();
    }

    /**
     * The context stops: camel-core does not call the route policies while it stops, so the exchanges that finish while
     * the context drains them are not counted. The expectations that are not decided yet stay pending for good, as for
     * a removed rule, so they are not called unmet for messages that were not counted.
     */
    void contextStopping() {
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            for (ChaosRule rule : rules) {
                if (rule.expectation != null) {
                    rule.expectation.freeze(now);
                }
            }
        }
    }

    /**
     * Puts the chaos poll strategy around the poll strategy of the scheduled poll consumers of the routes. It is done
     * once per consumer, before it starts (a route that is started again gets a new consumer), so the poll thread sees
     * it without a race; it stays idle while no rule fires at polls.
     */
    void wrapPollStrategies(Collection<Route> added) {
        for (Route route : added) {
            Consumer consumer = route.getConsumer();
            if (consumer instanceof ScheduledPollConsumer spc && !(spc.getPollStrategy() instanceof ChaosPollStrategy)) {
                spc.setPollStrategy(new ChaosPollStrategy(this, spc.getPollStrategy()));
            }
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The context started: warns for the expectations of the current rules (such as those of camel.chaos.rules, added
     * before the routes) whose route does not exist. It runs at every start of the context.
     *
     * @return the ids of the rules it warned for
     */
    List<String> contextStarted() {
        List<String> warned = new ArrayList<>();
        for (ChaosRule rule : ruleSet.expectationRules) {
            if (warnIfNoRoute(rule)) {
                warned.add(rule.id);
            }
        }
        return warned;
    }

    // the WARNs for an expectation of a route that does not exist, for the tests
    final AtomicInteger unknownRouteWarnings = new AtomicInteger();

    private boolean warnIfNoRoute(ChaosRule rule) {
        if (rule.expectation != null && camelContext.getRoute(rule.expectation.route) == null) {
            unknownRouteWarnings.incrementAndGet();
            LOG.warn("Chaos rule {} expects route {}, but there is no route with that id (yet)", rule.id,
                    rule.expectation.route);
            return true;
        }
        return false;
    }

    /**
     * Moves the rules that ended, and whose expectation is decided (or that have none), from the rules to the report
     * history, to make room for new rules. Called with the lock held.
     */
    private void retireEnded(long now) {
        boolean retired = rules.removeIf(rule -> {
            boolean done = rule.state(now) == State.ENDED
                    && (rule.expectation == null
                            || rule.expectation.outcome(now) != ChaosRule.Expectation.Outcome.PENDING);
            if (done) {
                rule.removed(State.ENDED, now);
                remember(rule);
            }
            return done;
        });
        if (retired) {
            update();
        }
    }

    private void remember(ChaosRule rule) {
        history.addLast(rule);
        while (history.size() > MAX_HISTORY) {
            ChaosRule evicted = history.removeFirst();
            ChaosRule.Expectation expectation = evicted.expectation;
            // an unmet expectation keeps the verdict FAIL after its rule left the report
            ChaosRule.Expectation.Reading reading
                    = expectation != null ? expectation.read(TimeSource.currentTimeMillis()) : null;
            ChaosRule.Expectation.Outcome outcome = reading != null ? reading.outcome() : null;
            if (outcome == ChaosRule.Expectation.Outcome.UNMET) {
                evictedUnmet++;
                evictedLost += Math.max(0, expectation.count - reading.completed());
            } else if (outcome == ChaosRule.Expectation.Outcome.PENDING) {
                // a pending expectation that left the report keeps the report from being final
                evictedPending++;
            }
        }
    }

    /**
     * Publishes a new snapshot of the rules for the hooks, without the rules that ended and the expectations that are
     * decided, and counts the exchanges while there are rules (also ended ones). Called with the lock held.
     */
    private void update() {
        ruleSet = new RuleSet(rules, TimeSource.currentTimeMillis());
        boolean any = !rules.isEmpty();
        if (any && !counting) {
            used = true;
            counting = true;
            // the context may have found no event notifier that wants exchange events when it started
            camelContext.getCamelContextExtension().setEventNotificationApplicable(true);
        } else if (!any) {
            counting = false;
        }
    }

    /**
     * Installs the hooks of chaos in the context: the send hook, the step hook, the route policy for the messages that
     * enter a route, and the event notifier for the report. The poll strategies are put in place as the routes are
     * added (see {@link #wrapPollStrategies(Collection)}).
     */
    void install() {
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        try {
            if (ecc.getSendHook() != null) {
                LOG.warn("A SendHook is installed already ({}), so chaos rules do not fire at sends", ecc.getSendHook());
            } else {
                ecc.setSendHook(new ChaosSendHook(this));
            }
        } catch (UnsupportedOperationException e) {
            LOG.warn("This CamelContext does not support a SendHook, so chaos rules do not fire at sends");
        }
        try {
            if (ecc.getStepHook() != null) {
                LOG.warn("A StepHook is installed already ({}), so chaos rules do not fire at steps", ecc.getStepHook());
            } else {
                ecc.setStepHook(new ChaosStepHook(this));
            }
        } catch (UnsupportedOperationException e) {
            LOG.warn("This CamelContext does not support a StepHook, so chaos rules do not fire at steps");
        }
        camelContext.addRoutePolicyFactory(new ChaosRoutePolicyFactory(this));
        camelContext.getManagementStrategy().addEventNotifier(new ChaosEventNotifier(this));
    }

    /**
     * The rules for each hook, as the hooks read them: immutable, replaced as a whole when a rule is added, changed or
     * removed. A rule that ended by then is left out of the hooks (it stays in the report), and an expectation that is
     * decided is left out of the expectations the completed exchanges count for.
     */
    static final class RuleSet {

        static final RuleSet EMPTY = new RuleSet(List.of(), 0);

        final ChaosRule[] send;
        final ChaosRule[] poll;
        final ChaosRule[] entry;
        final ChaosRule[] step;
        final ChaosRule.Expectation[] expectations;
        final ChaosRule[] expectationRules;

        RuleSet(List<ChaosRule> rules, long now) {
            List<ChaosRule> live = rules.stream().filter(r -> r.live(now)).toList();
            send = live.stream().filter(r -> r.hooks.contains(Hook.SEND)).toArray(ChaosRule[]::new);
            poll = live.stream().filter(r -> r.hooks.contains(Hook.POLL)).toArray(ChaosRule[]::new);
            entry = live.stream().filter(r -> r.hooks.contains(Hook.ENTRY)).toArray(ChaosRule[]::new);
            step = live.stream().filter(r -> r.hooks.contains(Hook.STEP)).toArray(ChaosRule[]::new);
            // all of them, for the WARN about a route that does not exist when the context starts
            expectationRules = rules.stream().filter(r -> r.expectation != null).toArray(ChaosRule[]::new);
            expectations = Arrays.stream(expectationRules).map(r -> r.expectation)
                    .filter(e -> e.outcome(now) == ChaosRule.Expectation.Outcome.PENDING && !e.frozen())
                    .toArray(ChaosRule.Expectation[]::new);
        }
    }

    /**
     * The counts of a route: the exchanges that completed or failed in it, the completed ones after the error handler
     * handled a failure, and the redelivery attempts.
     */
    static final class RouteCounts {
        final LongAdder completed = new LongAdder();
        final LongAdder failed = new LongAdder();
        final LongAdder failedKept = new LongAdder();
        final LongAdder handled = new LongAdder();
        final LongAdder redeliveries = new LongAdder();
    }
}
