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
package org.apache.camel.dsl.jbang.core.common;

import java.util.List;
import java.util.function.Consumer;

import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * One chaos experiment in one call (CAMEL-26786), as camel cmd chaos-experiment and the chaos_experiment tool run it:
 * it begins the experiment in the running exploration (its hypothesis and rules), sends the traffic of its feed, waits
 * until its expectations are decided, and ends it. The answer is what came of it (as the journal has it), with the
 * traffic that was sent and the time left of the exploration.
 * <p/>
 * The wait reads the report in the status of the integration, which is written every second. It ends as soon as an
 * expectation is unmet (FAIL) or all are met (PASS), when the exploration ended the experiment at its deadline, or
 * after the longest wait; an experiment ended then with an expectation not decided is INCONCLUSIVE. It is ended in any
 * case, also when the traffic could not be sent, so its rules do not stay.
 */
public final class ChaosExperimentRun {

    /** How long to wait for the verdict after the traffic, by default. */
    public static final long DEFAULT_WAIT = 2 * 60_000L;
    /** The longest wait for the verdict (the exploration ends the experiment at its deadline anyway). */
    public static final long MAX_WAIT = 10 * 60_000L;
    // the status of the integration is written every second
    private static final long POLL = 1000;

    private ChaosExperimentRun() {
    }

    /** What the experiment needs of the integration: the CLI and the tools each reach it their own way. */
    public interface Integration {

        /**
         * Sends the chaos action, and returns the answer.
         *
         * @throws RuntimeException when the integration refuses it, with the reason
         */
        JsonObject chaos(String command, Consumer<JsonObject> options);

        /** Sends the messages of the feed, and returns the summary of the send action. */
        JsonObject send(Feed feed);

        /** The status of the integration, as it wrote it last, or null. */
        JsonObject status();
    }

    /**
     * The traffic of an experiment, as camel cmd send sends it.
     *
     * @param endpoint the endpoint or the id of the route to send to
     * @param body     the body, file:&lt;path&gt;, or file:&lt;folder&gt; for a message per file
     * @param headers  the headers as key=value lines, or null
     * @param repeat   how many messages in all; 0 to send once (each file of a folder once)
     * @param interval the pause between two messages, such as 200ms or a range such as 100..500, or null
     */
    public record Feed(String endpoint, String body, String headers, int repeat, String interval) {
    }

    /** The time, and a pause; the tests give a clock that does not wait. */
    interface Clock {
        long now();

        void sleep(long millis) throws InterruptedException;
    }

    private static final Clock SYSTEM = new Clock() {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    };

    /** The longest wait as text, such as 2m: 2 minutes when empty. */
    public static long waitMillis(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_WAIT;
        }
        long millis;
        try {
            millis = TimeUtils.toMilliSeconds(text.strip());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The wait is not a time, such as 90s or 2m: " + text);
        }
        if (millis < 0 || millis > MAX_WAIT) {
            throw new IllegalArgumentException("The wait must be at most 10m, was: " + text);
        }
        return millis;
    }

    /**
     * Runs the experiment.
     *
     * @param  hypothesis what it tests, such as "a failed refund is read again" (optional)
     * @param  rules      its rules in their one-line form, separated by ;, one of them with an expectation
     * @param  feed       the traffic, or null when the routes feed themselves (a timer) or are fed otherwise
     * @param  wait       the longest wait for the verdict after the traffic, in millis
     * @param  note       what to note with it when it ends (optional)
     * @return            the experiment as it ended, with traffic, waited and timeLeft
     */
    public static JsonObject run(Integration integration, String hypothesis, String rules, Feed feed, long wait, String note) {
        try {
            return run(integration, hypothesis, rules, feed, wait, note, SYSTEM);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the verdict of the experiment", e);
        }
    }

    static JsonObject run(
            Integration integration, String hypothesis, String rules, Feed feed, long wait, String note, Clock clock)
            throws InterruptedException {
        JsonObject begun = integration.chaos("experiment-begin", root -> {
            if (hypothesis != null && !hypothesis.isBlank()) {
                root.put("hypothesis", hypothesis);
            }
            root.put("rule", rules);
        });
        int number = begun.getIntegerOrDefault("number", 0);
        JsonObject traffic = null;
        long waited = 0;
        try {
            if (feed != null) {
                traffic = traffic(integration, feed);
            }
            long start = clock.now();
            while (true) {
                JsonObject seen = experiment(integration.status(), number);
                if (seen != null && (!seen.getBooleanOrDefault("open", true)
                        || !"PENDING".equals(seen.getString("verdict")))) {
                    break;
                }
                if (clock.now() - start >= wait) {
                    break;
                }
                clock.sleep(POLL);
            }
            waited = clock.now() - start;
        } finally {
            // ended whatever happened, so its rules do not stay
            JsonObject result = end(integration, number, note, clock);
            if (result != null) {
                begun = result;
            }
        }
        JsonObject answer = new JsonObject(begun);
        if (traffic != null) {
            answer.put("traffic", traffic);
        }
        answer.put("waited", waited);
        JsonObject exploration = exploration(integration.status());
        if (exploration != null && exploration.containsKey("timeLeft")) {
            answer.put("timeLeft", exploration.getLong("timeLeft"));
        }
        return answer;
    }

    /** Sends the traffic: the summary of the send, or why it could not be sent. */
    private static JsonObject traffic(Integration integration, Feed feed) {
        JsonObject answer;
        try {
            answer = integration.send(feed);
        } catch (RuntimeException e) {
            answer = new JsonObject();
            answer.put("status", "failed");
            answer.put("error", e.getMessage());
        }
        if (answer == null) {
            answer = new JsonObject();
            answer.put("status", "failed");
        }
        if (!answer.containsKey("endpoint")) {
            answer.put("endpoint", feed.endpoint());
        }
        return answer;
    }

    /** Ends the experiment: what came of it, also when the exploration ended it at its deadline meanwhile. */
    private static JsonObject end(Integration integration, int number, String note, Clock clock)
            throws InterruptedException {
        try {
            return integration.chaos("experiment-end", root -> {
                if (note != null && !note.isBlank()) {
                    root.put("note", note);
                }
            });
        } catch (RuntimeException e) {
            // no experiment is open: the exploration ended it, which the next status says
            for (int i = 0; i < 3; i++) {
                JsonObject seen = experiment(integration.status(), number);
                if (seen != null && !seen.getBooleanOrDefault("open", true)) {
                    return seen;
                }
                clock.sleep(POLL);
            }
            throw e;
        }
    }

    /** The experiment with the number in the report of the status, or null. */
    static JsonObject experiment(JsonObject status, int number) {
        JsonObject exploration = exploration(status);
        List<Object> experiments = exploration != null ? exploration.getCollection("experiments") : null;
        if (experiments == null) {
            return null;
        }
        for (Object o : experiments) {
            if (o instanceof JsonObject experiment && experiment.getIntegerOrDefault("number", -1) == number) {
                return experiment;
            }
        }
        return null;
    }

    /** The exploration in the report of the chaos console in the status, or null. */
    public static JsonObject exploration(JsonObject status) {
        if (status == null || !(status.get("chaos") instanceof JsonObject chaos)
                || !(chaos.get("report") instanceof JsonObject report)) {
            return null;
        }
        return report.get("exploration") instanceof JsonObject exploration ? exploration : null;
    }
}
