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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

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
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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
     * The exchange property with the id of the rule that faulted the exchange (the last one, when more did), such as
     * {@code r1}, so the tracer, the message history and a route of the dead letter channel can tell.
     */
    public static final String CHAOS_RULE = "CamelChaosRule";

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
    // where the seed came from (camel.chaos.seed, or a scenario), or null when it was picked at startup
    private final String seedFrom;
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
    private long evictedLostAtDlq;

    // the snapshot the hooks read
    private volatile RuleSet ruleSet = RuleSet.EMPTY;
    // whether the event notifier counts the exchanges (while there are rules)
    private volatile boolean counting;
    private volatile boolean used;
    private final ConcurrentMap<String, RouteCounts> routes = new ConcurrentHashMap<>();
    // the last faults of the rules, for the chaos screen
    private final ChaosRecentFaults recentFaults = new ChaosRecentFaults();
    // the route lifecycle faults (stall and lifecycle), and whether the routes of the context are created (guarded by
    // lock), so the route of such a rule can be checked
    private final ChaosLifecycle lifecycle;
    private boolean routesCreated;
    // the exploration running now or the last one (CAMEL-26773), guarded by the lock
    private ChaosExploration exploration;

    ChaosManager(CamelContext camelContext, long seed, String seedFrom, long maxDuration) {
        this.camelContext = camelContext;
        this.seed = seed;
        this.seedFrom = seedFrom;
        this.maxDuration = maxDuration;
        this.lifecycle = new ChaosLifecycle(camelContext);
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
        ChaosRule.Builder definition = parse(rule);
        synchronized (lock) {
            return add(List.of(definition)).get(0);
        }
    }

    /**
     * Adds rules together: all of them, or none when one is not valid. The rules are in the form of
     * {@code camel.chaos.rules}, separated by {@code ;}, so they are in place at the same time, such as a rule for the
     * dead letter channel and the rule that sends a message there.
     *
     * @param  rules                    the rules in their one-line text form, separated by {@code ;}
     * @return                          the ids of the rules, in their order
     * @throws IllegalArgumentException when a rule is not valid, with the reason; no rule is added then
     * @throws IllegalStateException    when the rules would be more than {@link #MAX_RULES}
     */
    public List<String> addRules(String rules) {
        List<String> texts = ChaosRuleParser.splitRules(rules);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("The chaos rule is empty");
        }
        List<ChaosRule.Builder> definitions = new ArrayList<>();
        for (String text : texts) {
            definitions.add(parse(text));
        }
        synchronized (lock) {
            return add(definitions);
        }
    }

    /**
     * Loads a scenario (see {@link ChaosScenario}): an experiment of its own, so the rules there are now are removed,
     * the report starts again (as with {@link #reset()}), and the rules of the scenario are added, all of them or none
     * when one is not valid. With a {@code seed=} line the rules of the scenario draw from that seed, by their place in
     * it, so loading the scenario again fails the same messages.
     *
     * @param  name                     the name of the scenario, such as the name of its file
     * @param  text                     the content of the scenario: one rule per line, {@code #} comments and an
     *                                  optional {@code seed=} line
     * @return                          the ids of the rules, in their order
     * @throws IllegalArgumentException when a line is not valid, with its line number; the rules there are now stay
     * @throws IllegalStateException    when the scenario has more than {@link #MAX_RULES} rules
     */
    public List<String> loadScenario(String name, String text) {
        List<ChaosRule.Builder> definitions = parse(ChaosScenario.parse(name, text));
        if (definitions.size() > MAX_RULES) {
            throw new IllegalStateException(
                    "The chaos scenario " + name + " has " + definitions.size() + " rules, and at most " + MAX_RULES
                                            + " are allowed");
        }
        synchronized (lock) {
            for (ChaosRule.Builder definition : definitions) {
                // checked before the rules there are now are removed
                checkRoute(definition);
            }
            reset();
            List<String> ids = add(definitions);
            LOG.info("Chaos scenario {} loaded: {} rules", name, ids.size());
            return ids;
        }
    }

    /**
     * Adds the rules of the scenario of {@code camel.chaos.scenario} when the context initializes, before the rules of
     * {@code camel.chaos.rules}.
     */
    List<String> addScenario(ChaosScenario scenario) {
        List<ChaosRule.Builder> definitions = parse(scenario);
        synchronized (lock) {
            return add(definitions);
        }
    }

    /** Parses the rules of a scenario, all of them before any is added, with the line of a rule that is not valid. */
    private List<ChaosRule.Builder> parse(ChaosScenario scenario) {
        List<ChaosRule.Builder> definitions = new ArrayList<>();
        List<String> texts = scenario.getRules();
        for (int i = 0; i < texts.size(); i++) {
            ChaosRule.Builder b;
            try {
                b = parse(texts.get(i));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(scenario.where(scenario.lineOf(i)) + e.getMessage(), e);
            }
            b.scenario = scenario.getName();
            b.seed = scenario.getSeed();
            // the id the rule gets when the scenario is the first thing loaded, so it draws the same wherever it is;
            // unless draw= names the rule it draws as
            if (b.drawKey == null) {
                b.drawKey = "r" + (i + 1);
            }
            definitions.add(b);
        }
        return definitions;
    }

    /**
     * Parses and checks a rule, and makes the predicate of its condition ({@code when=}) with the simple language of
     * the context, once.
     */
    private ChaosRule.Builder parse(String text) {
        ChaosRule.Builder b = ChaosRuleParser.parse(text, camelContext.getClassResolver(), maxDuration);
        if (b.when != null) {
            if (b.whenReadsBody && !camelContext.isStreamCaching()) {
                throw new IllegalArgumentException(
                        "Invalid chaos rule [" + b.text + "]: when=" + b.when + " reads the body, which consumes a"
                                                   + " stream body when stream caching is off (it is on by default)");
            }
            try {
                b.condition = camelContext.resolveLanguage("simple").createPredicate(b.when);
                b.condition.init(camelContext);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid chaos rule [" + b.text + "]: when=" + b.when + " is not a valid simple predicate: "
                                                   + e.getMessage(),
                        e);
            }
        }
        return b;
    }

    /** Adds parsed rules, all or none. Called with the lock held. */
    private List<String> add(List<ChaosRule.Builder> definitions) {
        for (ChaosRule.Builder definition : definitions) {
            checkRoute(definition);
        }
        long now = TimeSource.currentTimeMillis();
        // an exploration whose time is up removes its rules first
        checkDeadline(now);
        if (rules.size() + definitions.size() > MAX_RULES) {
            retireEnded(now);
        }
        if (rules.size() + definitions.size() > MAX_RULES) {
            throw new IllegalStateException(
                    "There are " + rules.size() + " chaos rules already, and at most " + MAX_RULES
                                            + "; remove some before adding more");
        }
        ChaosExploration running = exploration != null && exploration.isRunning() ? exploration : null;
        if (running != null) {
            for (ChaosRule.Builder definition : definitions) {
                // checked before any is added: all the rules or none
                checkStartsBeforeDeadline(new ChaosRule(definition, "check", seed, now, maxDuration), running);
            }
        }
        List<ChaosRule> added = new ArrayList<>();
        for (ChaosRule.Builder definition : definitions) {
            ChaosRule rule = new ChaosRule(definition, "r" + nextId++, seed, now, maxDuration);
            rule.recentFaults = recentFaults;
            // a rule added before the context started starts its clock when it has started
            rule.clockStarted = camelContext.isStarted();
            if (running != null) {
                endByDeadline(rule, running, now);
            }
            rules.add(rule);
            added.add(rule);
        }
        update();
        List<String> ids = new ArrayList<>();
        for (ChaosRule rule : added) {
            LOG.info("Chaos rule {} added: {}", rule.id, rule.text);
            if (camelContext.isStarted()) {
                warnIfNoRoute(rule);
                warnIfNoTarget(rule);
            }
            ids.add(rule.id);
        }
        return ids;
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
        ChaosRule.Builder definition = parse(rule);
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            // an exploration whose time is up removes its rules first, so the rule may be gone
            checkDeadline(now);
            int index = indexOf(id);
            if (index < 0) {
                throw new IllegalArgumentException("There is no chaos rule " + id);
            }
            checkRoute(definition);
            ChaosRule changed = new ChaosRule(definition, id, rules.get(index).version + 1, seed, now, maxDuration);
            changed.recentFaults = recentFaults;
            changed.clockStarted = camelContext.isStarted();
            if (exploration != null && exploration.isRunning()) {
                checkStartsBeforeDeadline(changed, exploration);
                endByDeadline(changed, exploration, now);
            }
            ChaosRule old = rules.set(index, changed);
            old.removed(State.REPLACED, now);
            remember(old);
            update();
            LOG.info("Chaos rule {} changed (version {}): {}", id, changed.version, changed.text);
            if (camelContext.isStarted()) {
                warnIfNoRoute(changed);
                warnIfNoTarget(changed);
            }
            return changed.version;
        }
    }

    /**
     * What a rule matches nothing of in the context now: its pattern matches no endpoint, route or step id, its
     * {@code at=poll} finds no consumer that polls on a schedule, or its expectation names a route that does not exist.
     * Such a rule fires 0 times, often because of a typo. The routes may still change (a {@code toD} creates its
     * endpoints when it sends, a route can be added later), so it is a warning, not an error. Empty before the context
     * has started, as its routes are not there yet.
     *
     * @param  id                       the id of the rule
     * @return                          the warnings, empty when the rule finds something to fire at
     * @throws IllegalArgumentException when there is no rule with that id
     */
    public List<String> checkRule(String id) {
        ChaosRule rule;
        synchronized (lock) {
            int index = indexOf(id);
            if (index < 0) {
                throw new IllegalArgumentException("There is no chaos rule " + id);
            }
            rule = rules.get(index);
        }
        return warnings(rule);
    }

    /**
     * Checks rules without adding them ({@code chaos-add --dry-run}, the chaos_check tool): each is parsed and checked
     * as {@link #addRules(String)} does, so a rule that is not valid is refused with the same reason, and nothing
     * changes in the context. For each rule, in their order: the rule, the rule in plain words at the hooks where it
     * can fire now, those hooks, the warnings of a rule that matches nothing now, and what it would hit now (the sends,
     * polls, routes or steps, each with its route; see {@link ChaosTargets#hits}).
     *
     * @param  rules                    the rules in their one-line text form, separated by {@code ;}
     * @return                          {@code {"rules": [...]}}, as JSON
     * @throws IllegalArgumentException when a rule is not valid, with the reason
     */
    public Map<String, Object> checkRules(String rules) {
        List<String> texts = ChaosRuleParser.splitRules(rules);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("The chaos rule is empty");
        }
        List<ChaosRule.Builder> definitions = new ArrayList<>();
        for (String text : texts) {
            ChaosRule.Builder definition = parse(text);
            checkRoute(definition);
            definitions.add(definition);
        }
        long now = TimeSource.currentTimeMillis();
        ChaosTargets.Reach reach = ChaosTargets.reach(camelContext);
        JsonArray checked = new JsonArray();
        for (ChaosRule.Builder definition : definitions) {
            // a rule of its own, which no hook sees: its id is only for the words of the warnings
            ChaosRule rule = new ChaosRule(definition, "check", seed, now, maxDuration);
            Set<Hook> hooks = ChaosTargets.hooksThatCanFire(reach, rule);
            JsonObject jo = new JsonObject();
            jo.put("rule", rule.text);
            jo.put("description", rule.describe(hooks));
            JsonArray names = new JsonArray();
            hooks.forEach(h -> names.add(h.key));
            jo.put("hooks", names);
            List<String> warnings = warnings(rule);
            if (!warnings.isEmpty()) {
                jo.put("warnings", new JsonArray(warnings));
            }
            if (camelContext.isStarted()) {
                jo.put("hits", ChaosTargets.hits(camelContext, rule));
            }
            if (rule.when != null) {
                // the condition is checked on each message as it comes
                jo.put("when", rule.when);
            }
            checked.add(jo);
        }
        JsonObject answer = new JsonObject();
        answer.put("rules", checked);
        return answer;
    }

    /** The warnings of a rule that matches nothing in the context now (none before the context started). */
    private List<String> warnings(ChaosRule rule) {
        List<String> answer = new ArrayList<>();
        if (!camelContext.isStarted()) {
            return answer;
        }
        if (rule.expectation != null && camelContext.getRoute(rule.expectation.route) == null) {
            answer.add("expects route " + rule.expectation.route + ", but there is no route with that id (yet)");
        }
        if (rule.expectation != null && rule.expectation.toRoute != null
                && camelContext.getRoute(rule.expectation.toRoute) == null) {
            answer.add("expects route " + rule.expectation.toRoute + ", but there is no route with that id (yet)");
        }
        String target = ChaosTargets.check(camelContext, rule);
        if (target != null) {
            answer.add(target);
        }
        String handOff = ChaosAdvisor.lostWithoutHandOff(camelContext, rule.expectation);
        if (handOff != null) {
            answer.add(handOff);
        }
        String dead = deadLetterOfFailedExpectation(rule);
        if (dead != null) {
            String route = rule.expectation.route;
            answer.add("expects failed exchanges in route " + route + ", but its error handler is the dead letter channel "
                       + dead + ", which handles what fails there, so nothing fails: expect=" + route
                       + ":handled<=0 counts what the dead letter channel handled");
        }
        return answer;
    }

    /**
     * The dead letter channel of the route of an expectation of failed exchanges ({@code expect=parked:failed<=0}), as
     * such an expectation is always met there (CAMEL-26790), or null.
     */
    private String deadLetterOfFailedExpectation(ChaosRule rule) {
        if (rule.expectation == null || rule.expectation.kind != ChaosRule.Expectation.Kind.FAILED) {
            return null;
        }
        Route route = camelContext.getRoute(rule.expectation.route);
        return route != null ? ChaosAdvisor.deadLetterUri(route) : null;
    }

    /**
     * A rule in plain words, such as {@code fail half of the sends to http:stock* with HTTP status 503, for 5 minutes},
     * at the hooks where it can fire in the context now (see the {@code hooks} of the report).
     *
     * @param  id                       the id of the rule
     * @return                          the rule in plain words
     * @throws IllegalArgumentException when there is no rule with that id
     */
    public String describeRule(String id) {
        ChaosRule rule;
        synchronized (lock) {
            int index = indexOf(id);
            if (index < 0) {
                throw new IllegalArgumentException("There is no chaos rule " + id);
            }
            rule = rules.get(index);
        }
        return rule.describe(ChaosTargets.hooksThatCanFire(ChaosTargets.reach(camelContext), rule));
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
     * What to try (CAMEL-26727): rules suggested per route from what the routes do, the most worth trying first, each
     * with the known rule it comes from and why, such as an HTTP call without a circuit breaker, a dead letter channel,
     * or a hand-off to another route over seda.
     *
     * @return {@code suggestions}: a list of {@code route}, {@code kind}, {@code recipe}, {@code title}, {@code rule},
     *         {@code why}, {@code tried} when a rule of its kind faulted the route already, and {@code routes} when it
     *         is for several routes (a dead letter channel they share)
     */
    public Map<String, Object> suggest() {
        JsonArray list = new JsonArray();
        // a suggestion whose kind of fault a rule tried in its route already is marked (CAMEL-26728)
        List<ChaosRule> all;
        synchronized (lock) {
            all = new ArrayList<>(rules);
            all.addAll(history);
        }
        Set<String> tried = new HashSet<>();
        for (ChaosAdvisor.RouteTried route : ChaosAdvisor.tried(all, Map.of(), TimeSource.currentTimeMillis())) {
            route.tried().forEach(k -> tried.add(route.route() + " " + k.kind().key));
        }
        Map<String, ChaosAdvisor.Feed> feeds = new HashMap<>();
        for (ChaosAdvisor.Suggestion s : ChaosAdvisor.suggest(camelContext)) {
            JsonObject jo = new JsonObject();
            // a dead letter channel that routes share is tried by a rule of its kind in any of them; two faults at once
            // are tried as their own kinds
            if (s.kind() != ChaosAdvisor.Kind.COMBINED
                    && s.routes().stream().anyMatch(r -> tried.contains(r + " " + s.kind().key))) {
                jo.put("tried", true);
            }
            jo.put("route", s.route());
            if (s.routes().size() > 1) {
                jo.put("routes", new JsonArray(s.routes()));
            }
            jo.put("kind", s.kind().key);
            if (s.recipe() != null) {
                jo.put("recipe", s.recipe());
            }
            jo.put("title", s.title());
            jo.put("rule", s.rule());
            if (!s.with().isEmpty()) {
                // two faults at once: all the rules to add together
                jo.put("rules", new JsonArray(s.rules()));
            }
            jo.put("why", s.why());
            Route route = camelContext.getRoute(s.route());
            ChaosAdvisor.Feed feed = route != null ? feeds.computeIfAbsent(s.route(), k -> ChaosAdvisor.feed(route)) : null;
            if (feed != null) {
                JsonObject f = new JsonObject();
                f.put("kind", feed.kind());
                f.put("hint", feed.hint());
                if (feed.real()) {
                    f.put("real", true);
                }
                jo.put("feed", f);
            }
            list.add(jo);
        }
        JsonObject answer = new JsonObject();
        answer.put("suggestions", list);
        return answer;
    }

    /**
     * Removes a rule. It stays in the report, and its expectation, if not decided yet, is cancelled.
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
     * Removes all rules and starts a new report: the removed rules, the counts per route, the recent faults and the
     * verdict are gone, as for an application that just started (with the same seed). The ids of new rules go on from
     * the last one.
     *
     * @return the number of rules removed
     */
    public int reset() {
        synchronized (lock) {
            int count = clearRules();
            history.clear();
            evictedUnmet = 0;
            evictedLost = 0;
            evictedPending = 0;
            evictedLostAtDlq = 0;
            routes.clear();
            recentFaults.clear();
            if (exploration != null && exploration.isRunning()) {
                // a scenario loaded while it runs: the counts start again from zero, and so does its baseline
                exploration.reportReset();
            } else {
                exploration = null;
            }
            LOG.info("Chaos report reset");
            return count;
        }
    }

    /**
     * The report: the rules and what they did, the counts per route, the expectations, the recent faults and the
     * verdict.
     */
    public ChaosReport report() {
        countWaitedTooLong(TimeSource.currentTimeMillis());
        List<ChaosRule> current;
        List<ChaosRule> removed;
        int unmet;
        long lost;
        int pending;
        long lostAtDlq;
        ChaosExploration explored;
        synchronized (lock) {
            // the status reads the report every second, which finishes an exploration whose time is up
            checkDeadline(TimeSource.currentTimeMillis());
            explored = exploration;
            current = new ArrayList<>(rules);
            removed = new ArrayList<>(history);
            unmet = evictedUnmet;
            lost = evictedLost;
            pending = evictedPending;
            lostAtDlq = evictedLostAtDlq;
        }
        Map<String, RouteCounts> counts = new TreeMap<>(routes);
        long now = TimeSource.currentTimeMillis();
        List<ChaosRule> all = new ArrayList<>(current);
        all.addAll(removed);
        List<ChaosAdvisor.RouteTried> tried = ChaosAdvisor.tried(all, suggestedKinds(now), now);
        ChaosReport report = new ChaosReport(
                seed, seedFrom, now, current, removed, counts, unmet, lost, pending,
                lostAtDlq, recentFaults.latestFirst(), ChaosTargets.reach(camelContext), tried);
        if (explored != null) {
            synchronized (lock) {
                Map<String, RouteCounts> live = new TreeMap<>(routes);
                report.exploration(explored.toJson(now, this::findRule, live), explored.text(now, this::findRule, live));
            }
        }
        return report;
    }

    /**
     * Starts an exploration (CAMEL-26773): an agent tries chaos rules for the time it was given. While it runs, a rule
     * ends at the deadline at the latest, and at the deadline the rules are removed and the exploration is finished.
     * The report has its journal: the baseline (the counts per route now) and the rules added while it runs.
     *
     * @param  budget                   how long, such as 10m (10 minutes when empty, at most 2 hours)
     * @param  focus                    what the agent explores, such as a route (optional)
     * @return                          the exploration, as in the report
     * @throws IllegalArgumentException when the budget is not a time, or too short or too long
     * @throws IllegalStateException    when an exploration is running
     */
    public Map<String, Object> startExploration(String budget, String focus) {
        long millis;
        try {
            millis = budget == null || budget.isBlank()
                    ? ChaosExploration.DEFAULT_BUDGET : TimeUtils.toMilliSeconds(budget.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The budget of the exploration is not a time: " + budget);
        }
        if (millis < ChaosExploration.MIN_BUDGET || millis > ChaosExploration.MAX_BUDGET) {
            throw new IllegalArgumentException(
                    "The budget of the exploration must be between 10s and 2h, was: " + budget);
        }
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            checkDeadline(now);
            if (exploration != null && exploration.isRunning()) {
                throw new IllegalStateException(
                        "An exploration is running, for another " + ChaosRuleParser.printTime(exploration.deadline - now)
                                                + ": finish it first");
            }
            exploration = new ChaosExploration(now, millis, focus);
            update();
            LOG.info("Chaos exploration started: {}{}", ChaosRuleParser.printTime(millis),
                    exploration.focus != null ? " (focus: " + exploration.focus + ")" : "");
            return exploration.toJson(now);
        }
    }

    /**
     * Finishes the running exploration before its deadline: its rules are removed, and its journal stays in the report
     * until the next one starts or the report is reset.
     *
     * @return                       the exploration, as in the report
     * @throws IllegalStateException when no exploration is running
     */
    public Map<String, Object> finishExploration() {
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            checkDeadline(now);
            if (exploration == null || !exploration.isRunning()) {
                throw new IllegalStateException("No exploration is running");
            }
            finish(now, ChaosExploration.Finish.FINISHED);
            return exploration.toJson(now, this::findRule, new TreeMap<>(routes));
        }
    }

    /**
     * Begins an experiment of the running exploration (CAMEL-26774): a hypothesis and the rules that test it, added
     * together (all or none), with at least one expectation ({@code expect=}) to decide it. The counts per route are
     * read now, so its end says what changed while it ran. One experiment is open at a time.
     *
     * @param  hypothesis               what the experiment tests, in a few words, such as "a failed refund is read
     *                                  again"
     * @param  rules                    the rules in their one-line text form, separated by {@code ;}
     * @return                          the experiment, as in the report
     * @throws IllegalArgumentException when a rule is not valid, or no rule has an expectation
     * @throws IllegalStateException    when no exploration is running, or an experiment is open
     */
    public Map<String, Object> beginExperiment(String hypothesis, String rules) {
        List<String> texts = ChaosRuleParser.splitRules(rules);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("The chaos rule is empty");
        }
        List<ChaosRule.Builder> definitions = new ArrayList<>();
        for (String text : texts) {
            definitions.add(parse(text));
        }
        if (definitions.stream().allMatch(d -> d.expectRoute == null)) {
            throw new IllegalArgumentException(
                    "An experiment needs an expectation (expect=) on one of its rules to decide its hypothesis, such as"
                                               + " expect=orders:failed<=0 within=1m");
        }
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            checkDeadline(now);
            if (exploration == null || !exploration.isRunning()) {
                throw new IllegalStateException("No exploration is running: an experiment is part of one");
            }
            ChaosExperiment open = exploration.openExperiment();
            if (open != null) {
                throw new IllegalStateException("Experiment " + open.number + " is open: end it first");
            }
            Map<String, RouteCounts> counts = new TreeMap<>(routes);
            List<String> ids = add(definitions);
            ChaosExperiment experiment = exploration.beginExperiment(hypothesis, ids, counts, now);
            LOG.info("Chaos experiment {} began with rules {}{}", experiment.number, ids,
                    experiment.hypothesis != null ? ": " + experiment.hypothesis : "");
            return experiment.toJson(this::findRule, counts, now);
        }
    }

    /**
     * Ends the open experiment: what came of it is read (the difference of the counts per route, the faults of each
     * rule, the states of the expectations and the verdict), its rules are removed, and it stays in the journal.
     *
     * @param  note                  what the agent makes of it (optional)
     * @return                       the experiment, as in the report
     * @throws IllegalStateException when no experiment is open
     */
    public Map<String, Object> endExperiment(String note) {
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            // an experiment open at the deadline was ended with the exploration
            checkDeadline(now);
            ChaosExperiment open = exploration != null ? exploration.openExperiment() : null;
            if (open == null) {
                throw new IllegalStateException("No experiment is open");
            }
            endExperiment(open, now, ChaosExperiment.End.ENDED, note);
            return open.result();
        }
    }

    // reads what came of the experiment, and then removes its rules; with the lock
    private int endExperiment(ChaosExperiment experiment, long now, ChaosExperiment.End by, String note) {
        experiment.end(this::findRule, new TreeMap<>(routes), now, by, note);
        int count = 0;
        for (String id : experiment.rules) {
            int index = indexOf(id);
            if (index >= 0) {
                ChaosRule removed = rules.remove(index);
                removed.removed(State.REMOVED, now);
                remember(removed);
                count++;
            }
        }
        update();
        LOG.info("Chaos experiment {} ended: {}", experiment.number,
                experiment.result().getString("verdict"));
        return count;
    }

    // the rule with the id: the one in place, else the last one removed (a changed rule keeps its id); with the lock
    private ChaosRule findRule(String id) {
        int index = indexOf(id);
        if (index >= 0) {
            return rules.get(index);
        }
        for (Iterator<ChaosRule> it = history.descendingIterator(); it.hasNext();) {
            ChaosRule rule = it.next();
            if (rule.id.equals(id)) {
                return rule;
            }
        }
        return null;
    }

    // finishes the running exploration whose time is up; with the lock
    private void checkDeadline(long now) {
        if (exploration != null && exploration.isRunning() && now >= exploration.deadline) {
            finish(now, ChaosExploration.Finish.DEADLINE);
        }
    }

    private void finish(long now, ChaosExploration.Finish by) {
        ChaosExperiment open = exploration.openExperiment();
        int removedRules = 0;
        if (open != null) {
            // read before the rules are removed
            removedRules = endExperiment(open, now,
                    by == ChaosExploration.Finish.DEADLINE ? ChaosExperiment.End.DEADLINE : ChaosExperiment.End.FINISHED,
                    null);
        }
        removedRules += clearRules();
        exploration.finished(now, by, removedRules);
        // no rule is left and the exploration is done: the counting stops
        update();
        LOG.info("Chaos exploration {}: {} rules tried, {} removed",
                by == ChaosExploration.Finish.DEADLINE ? "finished at its deadline" : "finished",
                exploration.ruleCount(), removedRules);
    }

    private static void checkStartsBeforeDeadline(ChaosRule rule, ChaosExploration running) {
        if (rule.startsAt >= running.deadline) {
            throw new IllegalArgumentException(
                    "The chaos rule would start after the end of the exploration, in "
                                               + ChaosRuleParser.printTime(running.deadline - rule.addedAt)
                                               + ": " + rule.text);
        }
    }

    // a rule added while an exploration runs ends at its deadline at the latest; its first rule takes the baseline
    private void endByDeadline(ChaosRule rule, ChaosExploration running, long now) {
        if (rule.endsAt > running.deadline) {
            rule.endsAt = running.deadline;
        }
        running.ruleAdded(rule.id, new TreeMap<>(routes), now);
    }

    // the kinds of fault the suggestions offer per route, read again at most every 10 seconds (the report is read
    // every second for the status of the application), or when the routes change
    private static final long SUGGESTED_FOR_MILLIS = 10_000;
    private volatile Suggested suggested;

    /** The kinds the suggestions offer, with when they were read and for how many routes: one value, read as one. */
    private record Suggested(Map<String, Map<ChaosAdvisor.Kind, List<String>>> kinds, long at, int routes) {
    }

    private Map<String, Map<ChaosAdvisor.Kind, List<String>>> suggestedKinds(long now) {
        Suggested answer = suggested;
        int routeCount = camelContext.getRoutes().size();
        if (answer == null || now - answer.at() > SUGGESTED_FOR_MILLIS || routeCount != answer.routes()) {
            answer = new Suggested(ChaosAdvisor.suggestedKinds(ChaosAdvisor.suggest(camelContext)), now, routeCount);
            suggested = answer;
        }
        return answer.kinds();
    }

    // -- for the hooks, the event notifier and the plugin

    /** The rules there are now. */
    List<ChaosRule> rules() {
        synchronized (lock) {
            return List.copyOf(rules);
        }
    }

    RuleSet ruleSet() {
        return ruleSet;
    }

    boolean isCounting() {
        return counting;
    }

    // the faults of the copies of circuit breakers, by the id of the exchange they are a copy of, counted when that
    // exchange is done (CAMEL-26701); bounded, and counted with the copy when no event comes for a minute
    private static final int MAX_WAITING = 10_000;
    private static final long WAIT_FOR_ORIGINAL_MILLIS = 60_000;
    private final ConcurrentMap<String, WaitingFaults> waitingForOriginal = new ConcurrentHashMap<>();

    /** The faults of copies of an exchange, and how the last copy was done. */
    private static final class WaitingFaults {
        final ChaosExchangeState.Faults faults;
        final boolean copyFailed;
        final boolean copyHandled;
        final long since;

        WaitingFaults(ChaosExchangeState.Faults faults, boolean copyFailed, boolean copyHandled, long since) {
            this.faults = faults;
            this.copyFailed = copyFailed;
            this.copyHandled = copyHandled;
            this.since = since;
        }
    }

    /**
     * The faults of a copy of a circuit breaker wait for the exchange it is a copy of.
     *
     * @return false when too many wait already, so the faults count now, with the copy
     */
    boolean waitForOriginal(String exchangeId, ChaosExchangeState.Faults faults, boolean copyFailed, boolean copyHandled) {
        long now = TimeSource.currentTimeMillis();
        countWaitedTooLong(now);
        if (waitingForOriginal.size() >= MAX_WAITING) {
            return false;
        }
        // another copy when the circuit breaker runs again at a redelivery of the exchange: one message
        waitingForOriginal.merge(exchangeId, new WaitingFaults(faults, copyFailed, copyHandled, now), (before, copy) -> {
            before.faults.merge(copy.faults);
            return new WaitingFaults(before.faults, copy.copyFailed, copy.copyHandled, copy.since);
        });
        return true;
    }

    /** Whether faults wait for an exchange to be done, so the events of done exchanges are listened to. */
    boolean isWaitingForOriginals() {
        return !waitingForOriginal.isEmpty();
    }

    /** An exchange is done: the faults of the copies of circuit breakers that wait for it count. */
    void originalDone(Exchange exchange) {
        WaitingFaults waiting = waitingForOriginal.remove(exchange.getExchangeId());
        if (waiting != null) {
            waiting.faults.count(exchange);
        }
    }

    /** The faults that waited a minute for an exchange count with how their copy was done. */
    void countWaitedTooLong(long now) {
        if (waitingForOriginal.isEmpty()) {
            return;
        }
        for (Map.Entry<String, WaitingFaults> e : waitingForOriginal.entrySet()) {
            WaitingFaults w = e.getValue();
            // removed only when no copy merged into it meanwhile (a merge replaces the value), so the faults count
            // once, and no longer in the map, as nothing else can change them
            if (now - w.since >= WAIT_FOR_ORIGINAL_MILLIS && waitingForOriginal.remove(e.getKey(), w)) {
                w.faults.count(w.copyFailed, w.copyHandled, false);
            }
        }
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
     * <p/>
     * An exchange counts once per route, with how the route was done with it the last time: camel-core calls the route
     * policy each time the exchange goes through the route (a {@code direct} route called in a loop, or again by a
     * redelivery of the calling route), so a later pass moves the count (a failed pass and then a completed one count
     * as one completed exchange). The expectations of the route count it the first time it completes, is handled by the
     * error handler, or fails.
     */
    void routeDone(Route route, Exchange exchange) {
        String routeId = route.getRouteId();
        RouteCounts counts = counts(routeId);
        if (!counts.consumerChecked) {
            counts.stubbedConsumer = ChaosStubs.stubbedScheme(route.getEndpoint());
            counts.consumerChecked = true;
        }
        ChaosExchangeState.Outcome outcome = outcome(route, exchange);
        ChaosExchangeState.RouteState state = ChaosExchangeState.of(exchange).route(routeId);
        ChaosExchangeState.Outcome previous = state.counts == counts ? state.outcome : ChaosExchangeState.Outcome.ENTERED;
        if (previous == outcome) {
            return;
        }
        counts.uncount(previous);
        counts.count(outcome);
        state.outcome = outcome;
        state.counts = counts;
        if (outcome.completed() && !state.expectedCompleted) {
            state.expectedCompleted = true;
            record(routeId, ChaosRule.Expectation.Kind.COMPLETED, outcome == ChaosExchangeState.Outcome.HANDLED);
        }
        if (outcome == ChaosExchangeState.Outcome.HANDLED && !state.expectedHandled) {
            state.expectedHandled = true;
            record(routeId, ChaosRule.Expectation.Kind.HANDLED);
        }
        if (!outcome.completed() && outcome != ChaosExchangeState.Outcome.ENTERED && !state.expectedFailed) {
            state.expectedFailed = true;
            record(routeId, ChaosRule.Expectation.Kind.FAILED);
        }
        if (state.enteredAt >= 0 && !state.expectedTime && outcome != ChaosExchangeState.Outcome.ENTERED) {
            // the time of the exchange in the route, from when it entered it until the route was done with it the
            // first time
            state.expectedTime = true;
            long millis = Math.max(0, TimeSource.currentTimeMillis() - state.enteredAt);
            for (ChaosRule.Expectation expectation : ruleSet.expectations) {
                if (expectation.kind.time() && expectation.route.equals(routeId)) {
                    expectation.recordTime(millis);
                }
            }
        }
    }

    /** The error handler redelivers a step of the route: counted for the report and the expectations. */
    void redelivered(String routeId) {
        counts(routeId).redeliveries.increment();
        record(routeId, ChaosRule.Expectation.Kind.REDELIVERIES);
    }

    private void record(String routeId, ChaosRule.Expectation.Kind kind) {
        record(routeId, kind, false);
    }

    private void record(String routeId, ChaosRule.Expectation.Kind kind, boolean handled) {
        for (ChaosRule.Expectation expectation : ruleSet.expectations) {
            if (expectation.kind == kind && expectation.route.equals(routeId)) {
                expectation.record();
            } else if (kind == ChaosRule.Expectation.Kind.COMPLETED && expectation.kind == ChaosRule.Expectation.Kind.LOST) {
                // the messages one route completed, and those the next route completed; a message the error handler
                // of the first route handled (such as one parked by its dead letter channel) is not meant to reach
                // the next route, so it is not lost
                if (expectation.route.equals(routeId)) {
                    if (!handled) {
                        expectation.recordLost(false);
                    }
                } else if (routeId.equals(expectation.toRoute)) {
                    expectation.recordLost(true);
                }
            }
        }
    }

    private static ChaosExchangeState.Outcome outcome(Route route, Exchange exchange) {
        // an exchange marked rollback only has no exception, but its message is not processed either
        boolean failed = exchange.isFailed();
        if (failed || exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
            // the JMS listener rolls back for isRollbackOnly, not for isRollbackOnlyLast (which a transaction policy
            // of the route handles)
            boolean kept = failed
                    ? ChaosConsumers.keepsFailed(route.getEndpoint())
                    : exchange.isRollbackOnly() && ChaosConsumers.keepsRollbackOnly(route.getEndpoint());
            return kept ? ChaosExchangeState.Outcome.FAILED_KEPT : ChaosExchangeState.Outcome.FAILED;
        }
        ExchangeExtension ee = exchange.getExchangeExtension();
        return ee.isErrorHandlerHandledSet() && ee.isErrorHandlerHandled()
                ? ChaosExchangeState.Outcome.HANDLED : ChaosExchangeState.Outcome.COMPLETED;
    }

    /**
     * The context stops: camel-core does not call the route policies while it stops, so the exchanges that finish while
     * the context drains them are not counted. The expectations that are not decided yet are cancelled, as for a
     * removed rule, so they are not called unmet for messages that were not counted.
     */
    void contextStopping() {
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            for (ChaosRule rule : rules) {
                if (rule.expectation != null) {
                    rule.expectation.freeze(now);
                }
            }
            // no lifecycle rule acts any more, and a route that is down is not brought back
            lifecycle.contextStopping();
        }
    }

    /**
     * The context starts, with its routes created: checks the routes of the lifecycle rules that have not acted yet
     * (such as those of camel.chaos.rules), as {@link #addRule(String)} checks them from now on.
     *
     * @throws IllegalArgumentException when such a rule names a route that does not exist
     */
    void contextStarting() {
        synchronized (lock) {
            routesCreated = true;
            for (ChaosRule rule : rules) {
                if (rule.lifecycle != null && rule.lifecycle.isPending()) {
                    checkRoute(rule.text, rule.stall, rule.action, rule.pattern);
                }
            }
        }
    }

    /**
     * Checks the route of a lifecycle rule (stall or lifecycle) once the routes are created; before that, the check
     * waits for {@link #contextStarting()}. Called with the lock held.
     */
    private void checkRoute(ChaosRule.Builder definition) {
        if (routesCreated && definition.fault == ChaosRule.Fault.LIFECYCLE) {
            checkRoute(definition.text, definition.stall, definition.action, definition.pattern);
        }
    }

    private void checkRoute(String text, boolean stall, ChaosRule.Action action, String route) {
        // a route that a lifecycle rule removed is still there for a rule (to change that rule, for example)
        ChaosRuleParser.checkRoute(text, stall, action, route,
                id -> ChaosLifecycle.routeExists(camelContext, id) || lifecycle.isHeld(id),
                id -> lifecycle.isHeld(id) ? null : ChaosLifecycle.notRemovable(camelContext, id));
    }

    // for the tests
    ChaosLifecycle lifecycle() {
        return lifecycle;
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
     * The context started: the rules added before it started (such as those of camel.chaos.rules) start their clock, so
     * their until, within and lifecycle times do not include the startup, and it warns for the expectations of the
     * current rules whose route does not exist. It runs at every start of the context.
     *
     * @return the ids of the rules it warned for
     */
    List<String> contextStarted() {
        synchronized (lock) {
            long now = TimeSource.currentTimeMillis();
            for (ChaosRule rule : rules) {
                if (!rule.clockStarted) {
                    rule.clockStarted = true;
                    rule.startClock(now);
                }
            }
            // the lifecycle rules that wait for the context to start act now
            lifecycle.contextStarted();
            update();
        }
        List<String> warned = new ArrayList<>();
        for (ChaosRule rule : ruleSet.expectationRules) {
            if (warnIfNoRoute(rule)) {
                warned.add(rule.id);
            }
        }
        List<ChaosRule> current;
        synchronized (lock) {
            current = new ArrayList<>(rules);
        }
        for (ChaosRule rule : current) {
            warnIfNoTarget(rule);
        }
        return warned;
    }

    // the WARNs for a rule that matches nothing, for the tests
    final AtomicInteger noTargetWarnings = new AtomicInteger();

    private void warnIfNoTarget(ChaosRule rule) {
        String warning = ChaosTargets.check(camelContext, rule);
        if (warning != null) {
            noTargetWarnings.incrementAndGet();
            LOG.warn("Chaos rule {}: {}", rule.id, warning);
        }
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
            // the messages it lost at the dead letter channel keep the verdict FAIL too
            evictedLostAtDlq += evicted.lostAtDlqCount();
            ChaosRule.Expectation expectation = evicted.expectation;
            // an unmet expectation keeps the verdict FAIL after its rule left the report
            ChaosRule.Expectation.Reading reading
                    = expectation != null ? expectation.read(TimeSource.currentTimeMillis()) : null;
            ChaosRule.Expectation.Outcome outcome = reading != null ? reading.outcome() : null;
            if (outcome == ChaosRule.Expectation.Outcome.UNMET) {
                evictedUnmet++;
                evictedLost += expectation.lost(reading);
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
        for (ChaosRule rule : rules) {
            if (rule.lifecycle != null) {
                // acts once, when the context has started
                lifecycle.schedule(rule);
            }
        }
        // an exploration counts from its start, for the baseline before its first rule (CAMEL-26773)
        boolean any = !rules.isEmpty() || exploration != null && exploration.isRunning();
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
        // the hooks of chaos take the lowest order, so they run innermost, next to the producer and the processor,
        // after the other hooks that may be installed (such as hooks that observe the sends and steps)
        try {
            ecc.addSendHook(new ChaosSendHook(this));
        } catch (UnsupportedOperationException e) {
            LOG.warn("This CamelContext does not support a SendHook, so chaos rules do not fire at sends");
        }
        try {
            ecc.addStepHook(new ChaosStepHook(this));
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
        final ChaosRule[] reply;
        final ChaosRule[] dlq;
        final ChaosRule[] poll;
        final ChaosRule[] entry;
        final ChaosRule[] step;
        // some rules with route= fail or delay the exchanges at the first step of their route, so the step hook looks
        // for them
        final boolean entryAtStep;
        // the lifecycle rules (stall and lifecycle), for the exchanges sent to their route while it is down
        final ChaosRule[] lifecycle;
        final ChaosRule.Expectation[] expectations;
        final ChaosRule[] expectationRules;
        // the routes with an expectation on their time, whose route policy notes when an exchange enters
        final Set<String> timedRoutes;

        RuleSet(List<ChaosRule> rules, long now) {
            // also the rules that start later (from=): the hooks skip them until then
            List<ChaosRule> live = rules.stream().filter(r -> !r.ended(now)).toList();
            send = live.stream().filter(r -> r.hooks.contains(Hook.SEND)).toArray(ChaosRule[]::new);
            reply = live.stream().filter(r -> r.hooks.contains(Hook.REPLY)).toArray(ChaosRule[]::new);
            dlq = live.stream().filter(r -> r.hooks.contains(Hook.DLQ)).toArray(ChaosRule[]::new);
            poll = live.stream().filter(r -> r.hooks.contains(Hook.POLL)).toArray(ChaosRule[]::new);
            entry = live.stream().filter(r -> r.hooks.contains(Hook.ENTRY)).toArray(ChaosRule[]::new);
            step = live.stream().filter(r -> r.hooks.contains(Hook.STEP)).toArray(ChaosRule[]::new);
            entryAtStep = Arrays.stream(entry).anyMatch(r -> r.fault != ChaosRule.Fault.CORRUPT);
            lifecycle = rules.stream().filter(r -> r.lifecycle != null).toArray(ChaosRule[]::new);
            // all of them, for the WARN about a route that does not exist when the context starts
            expectationRules = rules.stream().filter(r -> r.expectation != null).toArray(ChaosRule[]::new);
            expectations = Arrays.stream(expectationRules).map(r -> r.expectation)
                    .filter(e -> e.outcome(now) == ChaosRule.Expectation.Outcome.PENDING && !e.frozen())
                    .toArray(ChaosRule.Expectation[]::new);
            timedRoutes = Arrays.stream(expectations).filter(e -> e.kind.time()).map(e -> e.route)
                    .collect(Collectors.toUnmodifiableSet());
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
        // the component the consumer of the route is a stub of (camel run --stub), once a route was done with an
        // exchange: a stub does not read a kept message again
        volatile String stubbedConsumer;
        volatile boolean consumerChecked;

        void count(ChaosExchangeState.Outcome outcome) {
            add(outcome, 1);
        }

        void uncount(ChaosExchangeState.Outcome outcome) {
            add(outcome, -1);
        }

        private void add(ChaosExchangeState.Outcome outcome, int n) {
            switch (outcome) {
                case COMPLETED -> completed.add(n);
                case HANDLED -> {
                    completed.add(n);
                    handled.add(n);
                }
                case FAILED -> failed.add(n);
                case FAILED_KEPT -> {
                    failed.add(n);
                    failedKept.add(n);
                }
                default -> {
                    // entered, not counted
                }
            }
        }
    }
}
