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
package org.apache.camel.dsl.jbang.core.commands.ai;

/**
 * The playbook of "let there be chaos" (CAMEL-26787): what an AI does to explore chaos on an integration by itself, for
 * the time of an exploration, and the report it comes back with. One text for the MCP prompt
 * {@code camel_chaos_explore} and for {@code camel cmd chaos-explore} (the AI of camel ask), each with the names of its
 * own tools.
 * <p/>
 * The AI steers on its own and asks the user nothing: the user agreed to the exploration, camel-chaos removes its rules
 * at the deadline, and the AI never changes the routes. One experiment per call keeps it to about ten calls in ten
 * minutes, which a small local model can do.
 */
public final class ChaosExplorePlaybook {

    /** The names of the tools the playbook tells the AI to call. */
    public record Tools(String start, String report, String suggest, String experiment, String finish, String errors) {
    }

    /** The tools of the MCP server. */
    public static final Tools MCP = new Tools(
            "camel_runtime_chaos_explore_start", "camel_runtime_chaos_report", "camel_runtime_chaos_suggest",
            "camel_runtime_chaos_experiment", "camel_runtime_chaos_explore_finish", "camel_get_errors");

    /** The tools of camel ask. */
    public static final Tools ASK = new Tools(
            "chaos_explore_start", "chaos_report", "chaos_suggest", "chaos_experiment", "chaos_explore_finish",
            "get_errors");

    private ChaosExplorePlaybook() {
    }

    /**
     * The playbook.
     *
     * @param tools       the names of the tools
     * @param integration the name of the integration, or null for the one the tools select
     * @param budget      how long the exploration runs, such as 10m (10m when null)
     * @param focus       what to explore, such as a route, or null for all routes
     * @param started     whether the exploration is already running (camel cmd chaos-explore starts it with the budget
     *                    of the user), else the AI starts it
     */
    public static String text(Tools tools, String integration, String budget, String focus, boolean started) {
        String where = integration != null && !integration.isBlank()
                ? "the integration " + integration.strip()
                : "the running integration";
        String time = budget != null && !budget.isBlank() ? budget.strip() : "10m";
        String about = focus != null && !focus.isBlank()
                ? "Focus on " + focus.strip() + ", and the routes it hands messages to."
                : "Explore all routes, the most worth trying first.";
        String start = started
                ? "The exploration is running (" + time + "): do not start another one."
                : "Start the exploration with " + tools.start() + " (budget " + time + ").";
        return """
                Let there be chaos: explore how %s copes with faults, by yourself, for %s. %s
                camel-chaos adds the faults and removes every rule at the deadline of the exploration. Do not ask \
                the user anything, and never change the routes or files: try, measure, and report at the end.

                1. %s Then read %s: the routes and their counts before chaos (the baseline), and what was tried.
                2. Call %s for ideas: rules per route with why, and the feed of each route (how messages get in). \
                Prefer the kinds not tried yet; single faults first, then the combined ones (two faults at once).
                3. Run one experiment per idea with %s. A rule makes exchanges fail or wait on purpose, so do not \
                expect that nothing fails where the fault is: expect what should hold despite it, such as nothing \
                lost after a hand-off to another route, a failure handled by the error handler or redelivered, or the \
                route still completing its messages in time.
                   - hypothesis: what should hold, in a few words, such as "a failed refund is retried, not lost"
                   - rule: the suggested rule with a short until= (such as until=30s), and one expectation that \
                decides the hypothesis, with within= a little longer than until=. Expect 0 where nothing may go \
                wrong, not a guess such as failed<=5, which hides the messages that were lost: expect=<route>:failed<=0 \
                (nothing fails), expect=<route>-><next route>:lost<=0 (nothing lost when a route hands messages to \
                the next route, as the suggestions of kind lost name them), \
                expect=<route>:20 (20 completed), expect=<route>:maxTime<=5s (none slower than 5s), \
                expect=<route>:duplicates<=0 for a duplicate fault (the route that receives the message did not do \
                the work twice; add key=${header.<id>} when the body does not tell the messages apart)
                   - feed: a route that does not feed itself gets no messages unless you give feed. When the feed \
                of the suggestion is kind send, always give its endpoint, with body (a message as the route \
                expects it, such as JSON), repeat (such as 20) and interval (such as 100..300); leave feed out \
                only when the route feeds itself (kind self). A route of kind http (platform-http, rest, or an HTTP server of its own) is fed \
                with HTTP requests to the server of the integration: give the id of the route as feed, or the path \
                to call when its path has a parameter (such as POST /orders/42); the traffic counts the status codes \
                the clients got. A route of kind file is fed with its id too: each message is written as a file the \
                route takes. An experiment refuses a feed that cannot reach its route, with the reason and \
                what to do instead, and warns when the feed goes to a real system that is not stubbed
                   - a rule at=dlq fails the dead letter channel, so it fires only when a message goes there: use \
                the combined suggestions, which add the fault that sends messages there too. A message the \
                dead letter channel did not deliver is handled there, so it does not fail in its route: the \
                experiment counts it as lost at the dead letter channel, and fails then
                   - wait: longer than the within= of the expectation
                   - note: what you make of the result, in one sentence
                4. Read the result: the verdict (FAIL, PASS or INCONCLUSIVE), the expectations, what the routes \
                did, the traffic, and the warnings: a rule that matched nothing made the experiment say nothing, so \
                fix the rule (its pattern, such as endpoint=file://parked) and run it again. On FAIL, call %s for the failed exchanges and their exceptions, and say why it \
                broke. On INCONCLUSIVE, run it again with a longer wait. On PASS, move on.
                5. Go on while timeLeft of the result (millis) is more than the next experiment needs: its wait \
                plus a minute. When it is less, do not begin another experiment: finish.
                6. Finish with %s, and write the report:
                   - Summary: how many experiments, how many held and how many broke
                   - Findings: per FAIL the route, what broke, the evidence (the expectation and its actual value, \
                the exception), and a hint for the route in terms of its error handling (a dead letter channel, \
                redelivery, onException, a circuit breaker, idempotency), not endpoint options you are not sure \
                exist; say when it looks like a bug in Camel rather than in the route
                   - What held: one line per PASS
                   - Not tried: the kinds of fault or routes left out, and why (no feed, out of time)
                """
                .formatted(where, time, about, start, tools.report(), tools.suggest(), tools.experiment(),
                        tools.errors(), tools.finish());
    }
}
