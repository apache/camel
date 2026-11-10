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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.ChaosExperimentRun;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An exploration started from the Chaos screen (let there be chaos, CAMEL-26760): the picked rules are tried one at a
 * time, each as an experiment for its share of the budget, and the exploration is finished at the end. It runs on a
 * thread of its own, as an experiment waits for its verdict; camel-chaos enforces the budget, so its rules end at the
 * deadline also when the TUI is gone. The Explore view shows the experiments as the integration reports them, and this
 * says which rule is being tried.
 */
final class ChaosExploreRun {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosExploreRun.class);
    private static final Pattern UNTIL = Pattern.compile("\\buntil=\\S+");
    private static final Pattern WITHIN = Pattern.compile("\\bwithin=\\S+");
    // the faults that act on the route once, for their duration, and take no until=
    private static final Pattern ONCE = Pattern.compile("\\s(stall|lifecycle)\\b");
    // the order of the verdicts in the summary
    private static final List<String> VERDICTS = List.of("PASS", "FAIL", "INCONCLUSIVE", "REFUSED");
    // left over at the deadline, for the last experiment to end before camel-chaos ends it
    private static final long MARGIN = 3000;
    // how much longer than its share an experiment may wait for its verdict
    private static final long GRACE = 5000;

    // the chaos action and the status of the integration with the pid, for the experiments
    private final Function<String, ChaosExperimentRun.Integration> integrations;
    private Thread thread;
    // set on the thread of the run, read when the screen is drawn
    private volatile String pid;
    private volatile List<ChaosExploreDialog.Item> picks = List.of();
    private volatile int current;
    private volatile long share;
    private volatile long deadline;
    private volatile long ruleStarted;
    private volatile boolean running;
    private volatile boolean stopped;
    private volatile String outcome;
    // why the first rule the integration refused was refused
    private volatile String refusal;
    private volatile long started;
    private volatile String budget;
    private final Map<String, Integer> verdicts = new TreeMap<>(
            (a, b) -> Integer.compare(order(a), order(b)) != 0 ? Integer.compare(order(a), order(b)) : a.compareTo(b));

    ChaosExploreRun(MonitorContext ctx) {
        this(pid -> integration(ctx, pid));
    }

    ChaosExploreRun(Function<String, ChaosExperimentRun.Integration> integrations) {
        this.integrations = integrations;
    }

    /** Whether an exploration started here runs in the integration with the pid. */
    boolean isRunning(String pid) {
        return running && pid != null && pid.equals(this.pid);
    }

    /** Starts the exploration: the picked rules one at a time, each for its share of the budget. */
    synchronized void start(String pid, List<ChaosExploreDialog.Item> picks, String budget, long budgetMillis) {
        if (running) {
            return;
        }
        this.pid = pid;
        this.picks = List.copyOf(picks);
        this.share = budgetMillis / Math.max(1, picks.size());
        this.started = MonotonicClock.millis();
        this.ruleStarted = started;
        this.deadline = started + budgetMillis;
        this.budget = budget;
        this.current = 0;
        this.stopped = false;
        this.outcome = null;
        this.refusal = null;
        synchronized (verdicts) {
            verdicts.clear();
        }
        running = true;
        thread = new Thread(() -> run(budget), "camel-tui-chaos-explore");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the exploration: it is finished now, which removes its rules. */
    synchronized void stop() {
        if (!running) {
            return;
        }
        stopped = true;
        if (thread != null) {
            // the experiment that waits for its verdict is ended, and the exploration finished, on the way out
            thread.interrupt();
        }
    }

    /** Runs the exploration, on its own thread. */
    void run(String budget) {
        ChaosExperimentRun.Integration integration = integrations.apply(pid);
        try {
            integration.chaos("explore-start", root -> root.put("budget", budget));
        } catch (RuntimeException e) {
            outcome = "Not started: " + e.getMessage();
            running = false;
            return;
        }
        try {
            for (int i = 0; i < picks.size() && !stopped; i++) {
                current = i;
                ruleStarted = MonotonicClock.millis();
                // the time left, shared by the rules left: an experiment takes a little longer than its rules run
                share = Math.max(1000, (deadline - ruleStarted - MARGIN) / (picks.size() - i));
                ChaosExploreDialog.Item pick = picks.get(i);
                String verdict;
                try {
                    // a file route that does not get messages by itself takes the files it took again; the rule as it
                    // ran says until= and within= of the share, which the note explains
                    String note = "Tried for its " + ChaosTab.shortTime(share) + " share of the " + budget
                                  + " budget: until= and within= are set to it";
                    JsonObject ended = ChaosExperimentRun.run(integration, pick.route() + ": " + pick.title(),
                            forShare(pick.rule(), share),
                            pick.replays() ? ChaosExperimentRun.Feed.replay(pick.replayRoute()) : null,
                            share + GRACE, note);
                    verdict = ended.getString("verdict");
                    if (ended.getLongOrDefault("timeLeft", 1) <= 0) {
                        // the deadline of the exploration: camel-chaos has ended it
                        count(verdict);
                        break;
                    }
                } catch (IllegalStateException e) {
                    // interrupted by stop
                    break;
                } catch (RuntimeException e) {
                    // the integration refused it, such as a rule that is not valid there: the next one
                    LOG.debug("Chaos experiment {} refused: {}", pick.rule(), e.getMessage());
                    verdict = "REFUSED";
                    if (refusal == null) {
                        refusal = "rule " + (i + 1) + " refused: " + e.getMessage();
                    }
                }
                count(verdict);
            }
        } finally {
            // a stop interrupted the wait for the verdict: the finish is still sent
            Thread.interrupted();
            try {
                integration.chaos("explore-finish", root -> {
                });
            } catch (RuntimeException e) {
                // finished already, at its deadline
                LOG.debug("Chaos exploration finished already: {}", e.getMessage());
            }
            int tried = tried();
            String counts = tried + (tried == 1 ? " rule" : " rules") + " tried" + (tried == 0 ? "" : ", " + verdictCounts());
            outcome = (stopped
                    ? "Stopped after " + counts
                    : "Done after " + ChaosTab.shortTime(MonotonicClock.millis() - started) + " of " + budget + ": "
                      + counts)
                      + (refusal != null ? " (" + refusal + ")" : "");
            running = false;
        }
    }

    private static int order(String verdict) {
        int i = VERDICTS.indexOf(verdict);
        return i >= 0 ? i : VERDICTS.size();
    }

    private void count(String verdict) {
        synchronized (verdicts) {
            verdicts.merge(verdict != null ? verdict : "INCONCLUSIVE", 1, Integer::sum);
        }
    }

    private int tried() {
        synchronized (verdicts) {
            return verdicts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private String verdictCounts() {
        synchronized (verdicts) {
            return verdicts.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * What the exploration started here is doing, for the Explore view of the integration with the pid: the rule it
     * tries, or how it ended. Null when none was started here for it.
     */
    String progress(String pid) {
        if (pid == null || !pid.equals(this.pid)) {
            return null;
        }
        if (!running) {
            return outcome;
        }
        if (stopped) {
            return "Stopping: the rules are removed";
        }
        List<ChaosExploreDialog.Item> list = picks;
        int i = current;
        if (i >= list.size()) {
            return null;
        }
        ChaosExploreDialog.Item pick = list.get(i);
        long left = share - (MonotonicClock.millis() - ruleStarted);
        return "Rule " + (i + 1) + " of " + list.size() + ": " + pick.route() + ", " + pick.title()
               + (left > 0
                       ? ", " + ChaosTab.countdown(left) + " left of " + ChaosTab.shortTime(share)
                       : ", waiting for the verdict")
               + (tried() == 0 ? "" : " · " + verdictCounts());
    }

    /**
     * The line above the exploration: the rule being tried while it runs; after it, only what the exploration does not
     * show itself (a rule the integration refused, or why it did not start). Null when there is nothing to say.
     */
    String line(String pid) {
        String answer = progress(pid);
        if (answer == null || running || refusal != null || answer.startsWith("Not started")) {
            return answer;
        }
        return null;
    }

    /**
     * The rules for their share of the budget: each ends when its share is up ({@code until=}), and an expectation that
     * waits longer for its messages ({@code within=}) waits that long at most. A fault that acts once for its duration
     * (stall, lifecycle) takes no until: its expectation is decided when the share is up instead.
     */
    static String forShare(String rules, long share) {
        String seconds = Math.max(1, share / 1000) + "s";
        return Arrays.stream(rules.split(";")).map(String::strip).filter(r -> !r.isEmpty()).map(r -> {
            if (ONCE.matcher(r).find()) {
                if (WITHIN.matcher(r).find()) {
                    return WITHIN.matcher(r).replaceAll("within=" + seconds);
                }
                return r.contains(" expect=") ? r + " within=" + seconds : r;
            }
            String rule = UNTIL.matcher(r).find() ? UNTIL.matcher(r).replaceAll("until=" + seconds) : r + " until=" + seconds;
            return WITHIN.matcher(rule).replaceAll("within=" + seconds);
        }).collect(Collectors.joining("; "));
    }

    /** The integration with the pid, as the TUI reaches it: its chaos action, and its status file. */
    private static ChaosExperimentRun.Integration integration(MonitorContext ctx, String pid) {
        return new ChaosExperimentRun.Integration() {
            @Override
            public JsonObject chaos(String command, Consumer<JsonObject> options) {
                JsonObject request = new JsonObject();
                request.put("action", "chaos");
                request.put("command", command);
                options.accept(request);
                JsonObject reply = ctx.executeAction(pid, request, 10000);
                if (reply == null) {
                    throw new IllegalArgumentException("The integration did not answer");
                }
                if (reply.getString("error") != null) {
                    throw new IllegalArgumentException(reply.getString("error"));
                }
                return reply;
            }

            @Override
            public JsonObject send(ChaosExperimentRun.Feed feed) {
                // the exploration of the TUI sends no messages: its routes get them by themselves, or take the files they
                // took again (a replay, which is a chaos command)
                throw new UnsupportedOperationException("No feed");
            }

            @Override
            public JsonObject status() {
                return RuntimeHelper.readStatus(Long.parseLong(pid));
            }
        };
    }
}
