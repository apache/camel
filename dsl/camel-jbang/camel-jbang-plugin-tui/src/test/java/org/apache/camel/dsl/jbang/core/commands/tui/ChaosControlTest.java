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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The actions of the chaos screen ({@link ChaosControl}): the keys, the confirmation, the request sent to the chaos
 * action of the CLI connector, and how the reply is shown.
 */
class ChaosControlTest {

    private MonitorContext ctx;
    private IntegrationInfo info;
    private final List<JsonObject> requests = new ArrayList<>();
    private final List<String> notifications = new ArrayList<>();
    private JsonObject reply;
    private ChaosTab tab;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "chaosdemo";
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true,
                 "rules": [{"id": "r1", "version": 1, "state": "active", "rule": "endpoint=http:stock* error status=503"}],
                 "report": {"verdict": "PASS", "final": true, "seed": 42}}
                """));
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.confirmActions = true;
        ctx.notificationCallback = (message, error) -> notifications.add(message);
        reply = new JsonObject();
        tab = new ChaosTab(ctx, new ChaosControl(ctx, request -> {
            requests.add(request);
            return reply;
        }));
        // the first rule is selected, as the screen does when it is drawn
        TuiTestHelper.renderToString(tab, 160, 40);
    }

    private void key(char ch) {
        tab.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
    }

    private void type(String text) {
        for (char ch : text.toCharArray()) {
            key(ch);
        }
    }

    private void enter() {
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
    }

    @Test
    void addARuleTypedAsOneLineAfterTheConfirmation() {
        key('a');
        assertThat(tab.isTextInputActive()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Add a chaos rule to chaosdemo");
        type("step=enrich delay duration=1s");
        enter();

        assertThat(tab.isConfirming()).isTrue();
        assertThat(requests).isEmpty();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40))
                .contains("Add this chaos rule to chaosdemo?").contains("step=enrich delay duration=1s");

        reply.put("id", "r2");
        key('y');
        assertThat(requests).hasSize(1);
        JsonObject request = requests.get(0);
        assertThat(request.getString("action")).isEqualTo("chaos");
        assertThat(request.getString("command")).isEqualTo("add");
        assertThat(request.getString("rule")).isEqualTo("step=enrich delay duration=1s");
        assertThat(notifications).containsExactly("Added chaos rule r2");
        assertThat(tab.control().isOpen()).isFalse();
    }

    @Test
    void changeTheSelectedRuleStartsFromItsText() {
        key('e');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Change chaos rule r1")
                .contains("endpoint=http:stock* error status=503");
        type(" rate=0.5");
        enter();
        reply.put("id", "r1");
        reply.put("version", 2);
        // the integration says the rule in plain words
        reply.put("description", "fail half of the sends to http:stock* with HTTP status 503");
        key('y');

        assertThat(requests.get(0).getString("command")).isEqualTo("change");
        assertThat(requests.get(0).getString("id")).isEqualTo("r1");
        assertThat(requests.get(0).getString("rule")).isEqualTo("endpoint=http:stock* error status=503 rate=0.5");
        assertThat(notifications)
                .containsExactly(
                        "Changed chaos rule r1 (version 2): fail half of the sends to http:stock* with HTTP status 503");
    }

    @Test
    void removeClearAndANewExperiment() {
        key('x');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Remove chaos rule r1 from chaosdemo?");
        key('y');
        key('c');
        key('y');
        key('R');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Start a new chaos experiment in chaosdemo?");
        key('y');

        assertThat(requests).extracting(r -> r.getString("command")).containsExactly("remove", "clear", "clear");
        assertThat(requests.get(0).getString("id")).isEqualTo("r1");
        assertThat(requests.get(1).containsKey("reset")).isFalse();
        assertThat(requests.get(2).getBoolean("reset")).isTrue();
        assertThat(notifications).containsExactly("Removed chaos rule r1", "Removed all chaos rules",
                "New chaos experiment: no rules, a new report");
    }

    @Test
    void escOrNoSendsNothing() {
        key('x');
        assertThat(tab.handleEscape()).isTrue();
        key('c');
        key('n');
        key('a');
        type("step=a error");
        assertThat(tab.handleEscape()).isTrue();

        assertThat(requests).isEmpty();
        assertThat(tab.control().isOpen()).isFalse();
        // Esc with nothing open goes back, as on the other screens
        assertThat(tab.handleEscape()).isFalse();
    }

    @Test
    void aRefusedRuleShowsTheReasonAndCanBeChanged() {
        reply.put("status", "failed");
        reply.put("error", "Invalid chaos rule [step=a drop]: the fault drop is not offered at any hook of the target step=");
        key('a');
        type("step=a drop");
        enter();
        key('y');

        assertThat(tab.control().step()).isEqualTo(ChaosControl.Step.RESULT);
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Chaos rule not added")
                .contains("the fault drop is not offered");
        assertThat(notifications).isEmpty();

        // e: type it again, from the rule that was refused
        key('e');
        assertThat(tab.isTextInputActive()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("step=a drop");
    }

    @Test
    void aRuleThatMatchesNothingShowsItsWarningsAndIsChangedFromThere() {
        reply.put("id", "r2");
        reply.put("warnings", new JsonArray(List.of("r2: endpoint=htp:stock* matches no endpoint of the context")));
        key('a');
        type("endpoint=htp:stock* error");
        enter();
        key('y');

        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Chaos rule matches nothing")
                .contains("Added chaos rule r2, with a warning").contains("matches no endpoint");
        // e changes the rule that was added
        key('e');
        type("x");
        enter();
        key('y');
        assertThat(requests.get(1).getString("command")).isEqualTo("change");
        assertThat(requests.get(1).getString("id")).isEqualTo("r2");
    }

    @Test
    void withoutConfirmActionsItDoesNotAsk() {
        ctx.confirmActions = false;
        key('x');
        assertThat(requests).hasSize(1);
        assertThat(tab.isConfirming()).isFalse();
    }

    @Test
    void theKeysAreOnlyThereWhenChaosIsOn() throws Exception {
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("{\"enabled\": false}"));
        key('a');
        assertThat(tab.control().isOpen()).isFalse();
        List<Span> footer = new ArrayList<>();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).doesNotContain("add");
    }

    @Test
    void whatToTryListsTheSuggestionsAndTypesThePickedOneIn() throws Exception {
        reply = (JsonObject) Jsoner.deserialize("""
                {"suggestions": [
                  {"route": "orders", "kind": "errors", "recipe": "http-error", "title": "Returns an error (HTTP status)",
                   "rule": "endpoint=http://stock/check error status=503 rate=0.25 until=10m",
                   "why": "an HTTP call to http://stock/check without a circuit breaker"},
                  {"route": "shipping", "kind": "dead letter channel", "recipe": "dlq-unreachable",
                   "title": "The dead letter channel is unreachable", "rule": "endpoint=seda:dlq error at=dlq",
                   "why": "route shipping sends what fails to the dead letter channel seda:dlq"}]}
                """);
        // what has been tried in route orders, from the report (CAMEL-26728)
        info.chaos.tried.put("orders",
                new ChaosInfo.TriedRoute("orders", List.of("slow"), List.of("errors", "lost")));
        key('w');
        assertThat(requests).singleElement().satisfies(r -> assertThat(r.getString("command")).isEqualTo("suggest"));
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(screen).contains("What to try in chaosdemo").contains("Returns an error (HTTP status)")
                .contains("without a circuit breaker").contains("The dead letter channel is unreachable")
                .contains("Tried in route orders: slow").contains("Not tried yet: errors, lost");

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        enter();
        assertThat(tab.isTextInputActive()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Add a chaos rule to chaosdemo")
                .contains("endpoint=seda:dlq error at=dlq");
        // only added after Enter and the confirmation
        assertThat(requests).hasSize(1);
    }

    @Test
    void twoFaultsAtOnceAreTypedInTogether() throws Exception {
        reply = (JsonObject) Jsoner.deserialize("""
                {"suggestions": [
                  {"route": "orders", "kind": "combined", "title": "An outage while the dead letter channel fails",
                   "rule": "endpoint=http://pay error status=503 until=1m",
                   "rules": ["endpoint=http://pay error status=503 until=1m", "endpoint=seda:dlq error at=dlq until=1m"],
                   "why": "route orders sends what fails to the dead letter channel seda:dlq"}]}
                """);
        key('w');
        enter();
        assertThat(tab.isTextInputActive()).isTrue();
        // added after Enter and the confirmation, both rules in one add
        enter();
        key('y');
        assertThat(requests.get(requests.size() - 1).getString("rule"))
                .isEqualTo("endpoint=http://pay error status=503 until=1m; endpoint=seda:dlq error at=dlq until=1m");
    }

    @Test
    void whatToTryWithNothingToSuggest() throws Exception {
        reply = (JsonObject) Jsoner.deserialize("{\"suggestions\": []}");
        key('w');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Nothing to suggest");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertThat(tab.control().isOpen()).isFalse();
    }

    @Test
    void saveTheRulesAsAScenarioOfTheProject(@TempDir Path dir) throws Exception {
        info.directory = dir.toString();
        key('s');
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Save the chaos rules of chaosdemo as a scenario")
                .contains("test/chaos/chaosdemo.chaos");
        enter();

        // a new file is written at once, and nothing goes to the integration
        Path file = dir.resolve("test/chaos/chaosdemo.chaos");
        assertThat(Files.readString(file)).contains("seed=42\nendpoint=http:stock* error status=503\n")
                .contains("# camel cmd chaos-load test/chaos/chaosdemo.chaos, or camel run --chaos=test/chaos/chaosdemo.chaos");
        assertThat(requests).isEmpty();
        assertThat(notifications).containsExactly(
                "Saved 1 chaos rule to test/chaos/chaosdemo.chaos (camel cmd chaos-load test/chaos/chaosdemo.chaos)");

        // a file that is there is replaced only after the question
        key('s');
        for (int i = 0; i < "chaosdemo.chaos".length(); i++) {
            tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE));
        }
        type("outage");
        Files.writeString(dir.resolve("test/chaos/outage.chaos"), "endpoint=mock:x drop\n");
        enter();
        assertThat(tab.isConfirming()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40))
                .contains("test/chaos/outage.chaos is there already: replace it with the rules of chaosdemo?");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE, KeyModifiers.NONE));
        assertThat(Files.readString(dir.resolve("test/chaos/outage.chaos"))).isEqualTo("endpoint=mock:x drop\n");
    }

    @Test
    void aScenarioIsTheSeedAndTheRules() {
        String text = ChaosScenarioFile.text(info, info.chaos, "test/chaos/x.chaos", LocalDateTime.of(2026, 11, 7, 22, 40));
        assertThat(text).isEqualTo("""
                # Chaos rules of chaosdemo, saved on 2026-11-07 22:40
                # camel cmd chaos-load test/chaos/x.chaos, or camel run --chaos=test/chaos/x.chaos
                # the times of from= and until= count from when the scenario is loaded
                seed=42
                endpoint=http:stock* error status=503
                """);
        assertThat(ChaosScenarioFile.resolve(info, "test/chaos/x")).isNull();
        info.directory = "/work/shop";
        assertThat(ChaosScenarioFile.resolve(info, "test/chaos/x")).isEqualTo(Path.of("/work/shop/test/chaos/x.chaos"));
        info.name = "my app";
        assertThat(ChaosScenarioFile.defaultPath(info)).isEqualTo("test/chaos/my-app.chaos");
    }

    @Test
    void aRuleOpenedTypedInIsAddedAfterTheConfirmation() {
        tab.openRule("step=callStock error exception=java.net.ConnectException until=10");
        // the input shows the end of the line, where the cursor is
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Add a chaos rule to chaosdemo")
                .contains("ConnectException until=10");
        enter();
        assertThat(tab.isConfirming()).isTrue();
        reply.put("id", "r2");
        key('y');
        assertThat(requests.get(0).getString("rule"))
                .isEqualTo("step=callStock error exception=java.net.ConnectException until=10");
    }

    @Test
    void theFooterHasTheKeys() {
        List<Span> footer = new ArrayList<>();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat))
                .contains("add").contains("what to try").contains("change").contains("remove").contains("clear")
                .contains("save as scenario")
                .contains("new experiment");
        key('x');
        footer.clear();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).contains("yes").contains("no");
    }
}
