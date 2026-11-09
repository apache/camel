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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One chaos experiment in one call (CAMEL-26786): begin, traffic, the wait for the verdict in the status, and the end,
 * against a fake integration and a clock that does not wait.
 */
class ChaosExperimentRunTest {

    /** The integration: what it was asked, and the status it shows, one per read (the last one stays). */
    private static final class FakeIntegration implements ChaosExperimentRun.Integration {
        final List<JsonObject> actions = new ArrayList<>();
        final Deque<JsonObject> statuses = new ArrayDeque<>();
        ChaosExperimentRun.Feed sent;
        RuntimeException sendFails;
        RuntimeException endFails;
        RuntimeException beginFails;
        JsonObject ended = experiment("FAIL", false);

        @Override
        public JsonObject chaos(String command, Consumer<JsonObject> options) {
            JsonObject action = new JsonObject();
            action.put("command", command);
            options.accept(action);
            actions.add(action);
            if ("experiment-begin".equals(command)) {
                if (beginFails != null) {
                    throw beginFails;
                }
                return experiment("PENDING", true);
            }
            if (endFails != null) {
                throw endFails;
            }
            return ended;
        }

        @Override
        public JsonObject send(ChaosExperimentRun.Feed feed) {
            sent = feed;
            if (sendFails != null) {
                throw sendFails;
            }
            return new JsonObject(Map.of("status", "success", "endpoint", "direct://refunds", "sent", 20L, "failed", 0L));
        }

        @Override
        public JsonObject status() {
            return statuses.size() > 1 ? statuses.poll() : statuses.peek();
        }

        void shows(String verdict, boolean open) {
            statuses.add(statusWith(experiment(verdict, open)));
        }
    }

    /** A clock that moves on when it is asked to sleep. */
    private static final class FakeClock implements ChaosExperimentRun.Clock {
        long now = 1_000_000;
        int sleeps;

        @Override
        public long now() {
            return now;
        }

        @Override
        public void sleep(long millis) {
            sleeps++;
            now += millis;
        }
    }

    private static JsonObject experiment(String verdict, boolean open) {
        return new JsonObject(Map.of("number", 1, "verdict", verdict, "open", open, "rules", new JsonArray(List.of("r1"))));
    }

    private static JsonObject statusWith(JsonObject experiment) {
        JsonObject exploration = new JsonObject(
                Map.of("running", true, "timeLeft", 300_000L, "experiments",
                        new JsonArray(List.of(experiment))));
        JsonObject report = new JsonObject(Map.of("exploration", exploration));
        return new JsonObject(Map.of("chaos", new JsonObject(Map.of("enabled", true, "report", report))));
    }

    private final FakeIntegration integration = new FakeIntegration();
    private final FakeClock clock = new FakeClock();

    private static final String RULE = "route=refunds error rate=every:5 expect=refunds:failed<=0 within=30s";
    private static final ChaosExperimentRun.Feed FEED
            = new ChaosExperimentRun.Feed("refunds", "file:/feed/", null, 20, "200ms");

    @Test
    void theTrafficThenTheWaitUntilTheVerdict() throws Exception {
        integration.shows("PENDING", true);
        integration.shows("PENDING", true);
        integration.shows("FAIL", true);

        JsonObject answer = ChaosExperimentRun.run(integration, "a failed refund is read again", RULE, FEED, 60_000,
                "kafka commits a failed record", clock);

        assertThat(integration.actions).extracting(a -> a.getString("command"))
                .containsExactly("experiment-begin", "experiment-end");
        assertThat(integration.actions.get(0).getString("hypothesis")).isEqualTo("a failed refund is read again");
        assertThat(integration.actions.get(0).getString("rule")).isEqualTo(RULE);
        assertThat(integration.actions.get(1).getString("note")).isEqualTo("kafka commits a failed record");
        assertThat(integration.sent).isEqualTo(FEED);
        // two reads that were pending, a second apart
        assertThat(clock.sleeps).isEqualTo(2);
        assertThat(answer.getString("verdict")).isEqualTo("FAIL");
        assertThat(answer.getLong("waited")).isEqualTo(2_000);
        assertThat(answer.getLong("timeLeft")).isEqualTo(300_000);
        assertThat(((JsonObject) answer.get("traffic")).getLong("sent")).isEqualTo(20);
    }

    @Test
    void theWaitIsUpBeforeTheVerdict() throws Exception {
        integration.shows("PENDING", true);
        integration.ended = experiment("INCONCLUSIVE", false);

        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, null, 5_000, null, clock);

        assertThat(answer.getString("verdict")).isEqualTo("INCONCLUSIVE");
        assertThat(answer.getLong("waited")).isEqualTo(5_000);
        assertThat(answer).doesNotContainKey("traffic");
        assertThat(integration.actions.get(0)).doesNotContainKey("hypothesis");
        assertThat(integration.actions.get(1)).doesNotContainKey("note");
    }

    @Test
    void endedWhenTheTrafficCannotBeSent() throws Exception {
        integration.shows("PENDING", true);
        integration.sendFails = new IllegalArgumentException("Header must be in key=value format, was: x");
        integration.ended = experiment("INCONCLUSIVE", false);

        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, FEED, 2_000, null, clock);

        JsonObject traffic = (JsonObject) answer.get("traffic");
        assertThat(traffic.getString("status")).isEqualTo("failed");
        assertThat(traffic.getString("error")).isEqualTo("Header must be in key=value format, was: x");
        assertThat(traffic.getString("endpoint")).isEqualTo("refunds");
        assertThat(integration.actions).extracting(a -> a.getString("command")).contains("experiment-end");
    }

    @Test
    void theExplorationEndedItAtItsDeadline() throws Exception {
        integration.shows("PENDING", true);
        // ended by the exploration: the end is refused, and the status has what came of it
        integration.shows("FAIL", false);
        integration.endFails = new IllegalStateException("No experiment is open");

        JsonObject answer = ChaosExperimentRun.run(integration, null, RULE, null, 60_000, null, clock);

        assertThat(answer.getString("verdict")).isEqualTo("FAIL");
        assertThat(answer.getBoolean("open")).isFalse();
    }

    @Test
    void aRefusedExperimentIsNotEnded() {
        integration.beginFails = new IllegalStateException("Experiment 1 is open: end it first");

        assertThatThrownBy(() -> ChaosExperimentRun.run(integration, null, RULE, FEED, 60_000, null, clock))
                .hasMessage("Experiment 1 is open: end it first");
        assertThat(integration.actions).hasSize(1);
        assertThat(integration.sent).isNull();
    }

    @Test
    void theWait() {
        assertThat(ChaosExperimentRun.waitMillis(null)).isEqualTo(ChaosExperimentRun.DEFAULT_WAIT);
        assertThat(ChaosExperimentRun.waitMillis("90s")).isEqualTo(90_000);
        assertThatThrownBy(() -> ChaosExperimentRun.waitMillis("11m")).hasMessageContaining("at most 10m");
        assertThatThrownBy(() -> ChaosExperimentRun.waitMillis("soon")).hasMessageContaining("not a time");
    }
}
