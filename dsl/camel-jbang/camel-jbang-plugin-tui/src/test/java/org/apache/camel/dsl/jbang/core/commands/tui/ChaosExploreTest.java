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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.common.ChaosExperimentRun;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/**
 * Let there be chaos from the Chaos screen ({@code L}): the rules to pick from the suggestions of the integration, the
 * budget, and the exploration that tries the picked rules one at a time.
 */
class ChaosExploreTest {

    private static final String SUGGESTIONS = """
            {"suggestions": [
              {"route": "orders", "kind": "errors", "title": "Returns an error (HTTP status)",
               "rule": "endpoint=http://localhost:8080/payment error status=503 rate=0.25 until=10m expect=orders:failed<=0",
               "why": "an HTTP call without a circuit breaker", "feed": {"kind": "self", "fed": true}},
              {"route": "orders", "kind": "lost", "title": "The route after a hand-off fails",
               "rule": "route=shipping error rate=0.2 until=2m expect=orders->shipping:lost<=0 within=3m",
               "why": "a hand-off over seda", "feed": {"kind": "self", "fed": true}},
              {"route": "refunds", "kind": "consumer", "title": "The route fails",
               "rule": "route=refunds error rate=0.05 until=10m expect=refunds:failed<=0",
               "why": "an input from outside", "feed": {"kind": "send"}},
              {"route": "payment", "kind": "lifecycle", "title": "The route stalls",
               "rule": "route=payment stall duration=20s expect=payment:failed<=0", "tried": true,
               "why": "stops taking messages", "feed": {"kind": "http", "fed": true}},
              {"route": "orders", "kind": "dead letter channel", "title": "The dead letter channel is unreachable",
               "rule": "endpoint=direct:parked error at=dlq expect=orders:failed<=0",
               "why": "fails only when another fault sends it a message", "feed": {"kind": "self", "fed": true}},
              {"route": "orders", "kind": "combined", "title": "An outage while the dead letter channel fails",
               "rule": "endpoint=http://localhost:8080/payment error status=503 until=1m expect=orders:failed<=0",
               "rules": ["endpoint=http://localhost:8080/payment error status=503 until=1m expect=orders:failed<=0",
                         "endpoint=direct:parked error at=dlq until=1m"],
               "why": "two faults at once", "feed": {"kind": "self", "fed": true}}
            ]}
            """;

    private IntegrationInfo info;
    private ChaosTab tab;
    private FakeIntegration integration;
    private ChaosExploreRun explorer;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "shop";
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true, "rules": [], "report": {"verdict": "PASS", "final": true, "seed": 42}}
                """));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        integration = new FakeIntegration();
        explorer = new ChaosExploreRun(pid -> integration);
        JsonObject suggestions = (JsonObject) Jsoner.deserialize(SUGGESTIONS);
        tab = new ChaosTab(ctx, new ChaosControl(ctx, request -> suggestions, () -> null, explorer));
        TuiTestHelper.renderToString(tab, 160, 40);
    }

    private static ChaosExploreDialog dialog() throws Exception {
        return new ChaosExploreDialog("shop", (JsonObject) Jsoner.deserialize(SUGGESTIONS));
    }

    private void key(char ch) {
        tab.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
    }

    private void press(KeyCode code) {
        tab.handleKeyEvent(KeyEvent.ofKey(code, KeyModifiers.NONE));
    }

    private String footer() {
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        return spans.stream().map(Span::content).reduce("", String::concat);
    }

    @Test
    void theFaultsNotTriedWhoseRoutesGetMessagesArePicked() throws Exception {
        ChaosExploreDialog dialog = dialog();
        assertThat(dialog.items()).hasSize(6);
        // not refunds (its messages come from outside), not the stall (tried), not the dead letter channel on its own
        // (it fails only when another fault sends it a message), and the pair after the single ones
        assertThat(dialog.picked()).extracting(ChaosExploreDialog.Item::title)
                .containsExactly("Returns an error (HTTP status)", "The route after a hand-off fails",
                        "An outage while the dead letter channel fails");
        assertThat(dialog.items().get(0).expect()).isEqualTo("orders:failed<=0");
        assertThat(dialog.items().get(5).rule()).contains("; endpoint=direct:parked error at=dlq");
        // 10 minutes for three rules
        assertThat(dialog.budget()).isEqualTo("10m");
        assertThat(dialog.share()).isEqualTo(200_000);
    }

    @Test
    void spacePicksAndTheArrowsChangeTheBudget() throws Exception {
        ChaosExploreDialog dialog = dialog();
        dialog.handleKey(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        dialog.handleKey(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        dialog.handleKey(KeyEvent.ofChar(' ', KeyModifiers.NONE));
        assertThat(dialog.picked()).hasSize(4);
        dialog.handleKey(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));
        dialog.handleKey(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));
        assertThat(dialog.budget()).isEqualTo("2m");
        assertThat(dialog.share()).isEqualTo(30_000);
        assertThat(dialog.cannotStart()).isNull();

        // too many rules for the budget: each needs time for its faults and messages
        dialog.handleKey(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        dialog.handleKey(KeyEvent.ofChar(' ', KeyModifiers.NONE));
        assertThat(dialog.picked()).hasSize(5);
        assertThat(dialog.cannotStart()).startsWith("Too many rules for 2m");
        assertThat(dialog.handleKey(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE)))
                .isEqualTo(ChaosExploreDialog.Outcome.NONE);
    }

    @Test
    void eachRuleRunsForItsShare() {
        assertThat(ChaosExploreRun.forShare(
                "route=shipping error rate=0.2 until=2m expect=orders->shipping:lost<=0 within=3m", 150_000))
                .isEqualTo("route=shipping error rate=0.2 until=150s expect=orders->shipping:lost<=0 within=150s");
        // a stall acts once, for its duration: no until, and its expectation is decided when the share is up
        assertThat(ChaosExploreRun.forShare("route=payment stall duration=20s expect=orders:failed<=0", 60_000))
                .isEqualTo("route=payment stall duration=20s expect=orders:failed<=0 within=60s");
        // two faults at once: both end with the share
        assertThat(ChaosExploreRun.forShare("endpoint=a error until=1m; endpoint=b error at=dlq until=1m", 30_000))
                .isEqualTo("endpoint=a error until=30s; endpoint=b error at=dlq until=30s");
    }

    @Test
    void letThereBeChaosTriesThePickedRulesOneAtATime() {
        key('L');
        assertThat(tab.isTextInputActive()).isTrue();
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(screen).contains("Let there be chaos: shop", "Budget", "10m", "3 rules, 3m20s each",
                "[x] Returns an error (HTTP status)", "[ ] The route fails", "needs messages");
        assertThat(footer()).contains("Space", "pick", "budget", "Enter", "start");

        press(KeyCode.ENTER);
        // the exploration shows
        assertThat(tab.isExplore()).isTrue();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !explorer.isRunning("1234"));
        assertThat(integration.commands).containsExactly(
                "explore-start", "experiment-begin", "experiment-end", "experiment-begin", "experiment-end",
                "experiment-begin", "experiment-end", "explore-finish");
        assertThat(integration.budget).isEqualTo("10m");
        // each rule for its share of the time left (the fake ends each experiment at once, so the shares grow)
        assertThat(integration.rules).hasSize(3);
        assertThat(integration.rules.get(0)).matches(
                "endpoint=http://localhost:8080/payment error status=503 rate=0\\.25 until=19\\ds expect=orders:failed<=0");
        assertThat(integration.rules.get(1)).matches(
                "route=shipping error rate=0\\.2 until=29\\ds expect=orders->shipping:lost<=0 within=29\\ds");
        // two faults at once: both end with the share
        assertThat(integration.rules.get(2)).matches(
                "endpoint=http://localhost:8080/payment error status=503 until=59\\ds expect=orders:failed<=0; "
                                                     + "endpoint=direct:parked error at=dlq until=59\\ds");
        assertThat(integration.hypotheses).containsExactly(
                "orders: Returns an error (HTTP status)", "orders: The route after a hand-off fails",
                "orders: An outage while the dead letter channel fails");
        assertThat(explorer.progress("1234")).matches("Done after \\d+ms of 10m: 3 rules tried, 3 FAIL");
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("of 10m: 3 rules tried, 3 FAIL");
    }

    @Test
    void aFileRouteTakesTheFilesItTookAgain() throws Exception {
        ChaosExploreDialog dialog = new ChaosExploreDialog(
                "shop", (JsonObject) Jsoner.deserialize(
                        """
                                {"suggestions": [
                                  {"route": "inbox", "kind": "errors", "title": "Fails to write",
                                   "rule": "endpoint=file://archive error exception=java.io.IOException rate=0.25 until=10m expect=inbox:failed<=0",
                                   "why": "a write", "feed": {"kind": "send", "replay": "/work/inbox/.camel", "files": 3, "replayRoute": "files"}},
                                  {"route": "uploads", "kind": "consumer", "title": "The route fails",
                                   "rule": "route=uploads error rate=0.05 until=10m expect=uploads:failed<=0",
                                   "why": "an input from outside", "feed": {"kind": "file"}}
                                ]}
                                """));
        // the files it took are put back, so it has messages; a route that took none needs them from outside
        assertThat(dialog.picked()).extracting(ChaosExploreDialog.Item::route).containsExactly("inbox");
        assertThat(dialog.items().get(0).replay()).isEqualTo(3);
        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        dialog.render(Frame.forTesting(buffer), area);
        assertThat(TuiTestHelper.bufferToString(buffer))
                .contains("[x] Fails to write  inbox  replays 3 files", "[ ] The route fails  uploads  needs messages");

        explorer.start("1234", dialog.picked(), "2m", 120_000);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !explorer.isRunning("1234"));
        // after the rule is added, so its faults meet the files
        assertThat(integration.commands).containsExactly(
                "explore-start", "experiment-begin", "replay", "experiment-end", "explore-finish");
        // the file route that sends to it
        assertThat(integration.replayed).containsExactly("files");
    }

    @Test
    void stopEndsTheExploration() {
        integration.decided = false;
        key('L');
        press(KeyCode.ENTER);
        await().atMost(10, TimeUnit.SECONDS).until(() -> integration.commands.contains("experiment-begin"));
        assertThat(footer()).contains("x", "stop");
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Rule 1 of 3: orders, Returns an error");

        key('x');
        await().atMost(10, TimeUnit.SECONDS).until(() -> !explorer.isRunning("1234"));
        // the experiment is ended and the exploration finished, which removes the rules
        assertThat(integration.commands).endsWith("experiment-end", "explore-finish");
        assertThat(explorer.progress("1234")).startsWith("Stopped after 0 rules tried");
        assertThat(footer()).contains("L", "let there be chaos");
    }

    @Test
    void aKindOfFaultOnceBeforeItIsTriedAgainInAnotherRoute() throws Exception {
        JsonObject reply = (JsonObject) Jsoner.deserialize("""
                {"suggestions": [
                  {"route": "small", "kind": "lost", "title": "The route after a hand-off fails",
                   "rule": "route=a error expect=small->a:lost<=0", "feed": {"kind": "self", "fed": true}},
                  {"route": "medium", "kind": "lost", "title": "The route after a hand-off fails",
                   "rule": "route=b error expect=medium->b:lost<=0", "feed": {"kind": "self", "fed": true}},
                  {"route": "large", "kind": "lost", "title": "The route after a hand-off fails",
                   "rule": "route=c error expect=large->c:lost<=0", "feed": {"kind": "self", "fed": true}},
                  {"route": "small", "kind": "errors", "title": "Returns an error (HTTP status)",
                   "rule": "endpoint=http://x error status=503 expect=small:failed<=0", "feed": {"kind": "self", "fed": true}}
                ]}
                """);
        ChaosExploreDialog dialog = new ChaosExploreDialog("shop", reply);
        // the HTTP error before a second hand-off, then the hand-offs again while there is room
        assertThat(dialog.picked()).extracting(ChaosExploreDialog.Item::route, ChaosExploreDialog.Item::kind)
                .containsExactly(tuple("small", "lost"), tuple("medium", "lost"), tuple("small", "errors"));
    }

    @Test
    void lSaysWhyWhenChaosIsOff() {
        info.chaos = null;
        key('L');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40))
                .contains("camel-chaos is not in shop (yet): run it with camel run --chaos");
    }

    @Test
    void oneExplorationAtATime() throws Exception {
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true, "rules": [],
                 "report": {"verdict": "PASS", "final": false, "seed": 42,
                            "exploration": {"running": true, "budget": 600000, "timeLeft": 300000, "experiments": []}}}
                """));
        key('L');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("An exploration runs in shop already");
    }

    /** The integration, as the chaos action and the status answer: every experiment fails at once. */
    private static final class FakeIntegration implements ChaosExperimentRun.Integration {
        final List<String> commands = new CopyOnWriteArrayList<>();
        final List<String> rules = new CopyOnWriteArrayList<>();
        final List<String> hypotheses = new CopyOnWriteArrayList<>();
        final List<String> replayed = new CopyOnWriteArrayList<>();
        volatile String budget;
        volatile boolean decided = true;
        private volatile int number;

        @Override
        public JsonObject chaos(String command, Consumer<JsonObject> options) {
            JsonObject request = new JsonObject();
            options.accept(request);
            commands.add(command);
            JsonObject answer = new JsonObject();
            switch (command) {
                case "explore-start" -> budget = request.getString("budget");
                case "experiment-begin" -> {
                    number++;
                    rules.add(request.getString("rule"));
                    hypotheses.add(request.getString("hypothesis"));
                    answer.put("number", number);
                }
                case "replay" -> replayed.add(request.getString("route"));
                case "experiment-end" -> {
                    answer.put("number", number);
                    answer.put("verdict", "FAIL");
                    answer.put("open", false);
                }
                default -> {
                    // finished
                }
            }
            return answer;
        }

        @Override
        public JsonObject send(ChaosExperimentRun.Feed feed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonObject status() {
            JsonObject experiment = new JsonObject();
            experiment.put("number", number);
            experiment.put("verdict", decided ? "FAIL" : "PENDING");
            experiment.put("open", !decided);
            JsonObject exploration = new JsonObject();
            exploration.put("experiments", List.of(experiment));
            exploration.put("timeLeft", 60_000);
            JsonObject report = new JsonObject();
            report.put("exploration", exploration);
            JsonObject chaos = new JsonObject();
            chaos.put("report", report);
            JsonObject status = new JsonObject();
            status.put("chaos", chaos);
            return status;
        }
    }
}
