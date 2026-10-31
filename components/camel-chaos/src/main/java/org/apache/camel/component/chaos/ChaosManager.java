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
import java.util.Deque;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.State;
import org.apache.camel.util.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rules of camel-chaos in a {@link CamelContext}, and the report of what they did.
 * <p/>
 * There is a manager only when chaos is switched on (see {@link ChaosContextServicePlugin}); {@link #get(CamelContext)}
 * returns it. Rules are added, changed and removed while the routes run, from any thread: the hooks read an immutable
 * snapshot of the rules, so a change applies to the next send or step, and one that is running keeps the rules it
 * started with.
 *
 * @since 4.24
 */
public final class ChaosManager {

    /**
     * The most rules at a time. When there are this many, adding a rule first moves the rules that ended into the
     * report history; remove some to add more when that does not make room.
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

    // the snapshot the hooks read
    private volatile RuleSet ruleSet = RuleSet.EMPTY;
    private volatile boolean used;

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
     * @throws IllegalStateException    when there are {@link #MAX_RULES} rules that have not ended
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
            return id;
        }
    }

    /**
     * Changes a rule: the rule with that id is replaced by the new one, which starts with new counts and a new end
     * time. The replaced rule stays in the report.
     *
     * @param  id                       the id of the rule
     * @param  rule                     the new rule in its one-line text form
     * @throws IllegalArgumentException when there is no rule with that id, or the new rule is not valid
     */
    public void changeRule(String id, String rule) {
        ChaosRule.Builder definition = ChaosRuleParser.parse(rule, camelContext.getClassResolver(), maxDuration);
        synchronized (lock) {
            int index = indexOf(id);
            if (index < 0) {
                throw new IllegalArgumentException("There is no chaos rule " + id);
            }
            ChaosRule changed = new ChaosRule(definition, id, seed, TimeSource.currentTimeMillis(), maxDuration);
            ChaosRule old = rules.set(index, changed);
            old.removed(State.REPLACED);
            remember(old);
            update();
            LOG.info("Chaos rule {} changed: {}", id, changed.text);
        }
    }

    /**
     * Removes a rule. It stays in the report.
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
            removed.removed(State.REMOVED);
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
            for (ChaosRule rule : rules) {
                rule.removed(State.REMOVED);
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
     * The report: the seed, and the rules and what they did.
     */
    public ChaosReport report() {
        List<ChaosRule> current;
        List<ChaosRule> removed;
        synchronized (lock) {
            current = new ArrayList<>(rules);
            removed = new ArrayList<>(history);
        }
        return new ChaosReport(seed, seedGiven, TimeSource.currentTimeMillis(), current, removed);
    }

    // -- for the hooks and the plugin

    RuleSet ruleSet() {
        return ruleSet;
    }

    /** Whether a rule was ever added (for the report when the context stops). */
    boolean isUsed() {
        return used;
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
     * Moves the rules that ended from the rules to the report history, to make room for new rules. Called with the lock
     * held.
     */
    private void retireEnded(long now) {
        boolean retired = rules.removeIf(rule -> {
            boolean ended = rule.state(now) == State.ENDED;
            if (ended) {
                rule.removed(State.ENDED);
                remember(rule);
            }
            return ended;
        });
        if (retired) {
            update();
        }
    }

    private void remember(ChaosRule rule) {
        history.addLast(rule);
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }

    /** Publishes a new snapshot of the rules for the hooks. Called with the lock held. */
    private void update() {
        ruleSet = new RuleSet(rules);
        if (!rules.isEmpty()) {
            used = true;
        }
    }

    /**
     * Installs the hooks of chaos in the context: the send hook and the step hook.
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
    }

    /**
     * The rules for each hook, as the hooks read them: immutable, replaced as a whole when a rule is added, changed or
     * removed.
     */
    static final class RuleSet {

        static final RuleSet EMPTY = new RuleSet(List.of());

        final ChaosRule[] send;
        final ChaosRule[] step;

        RuleSet(List<ChaosRule> rules) {
            send = rules.stream().filter(r -> r.hooks.contains(Hook.SEND)).toArray(ChaosRule[]::new);
            step = rules.stream().filter(r -> r.hooks.contains(Hook.STEP)).toArray(ChaosRule[]::new);
        }
    }
}
