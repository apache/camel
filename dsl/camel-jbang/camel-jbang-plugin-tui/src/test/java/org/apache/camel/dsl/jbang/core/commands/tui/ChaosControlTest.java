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
    void theFooterHasTheKeys() {
        List<Span> footer = new ArrayList<>();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat))
                .contains("add").contains("change").contains("remove").contains("clear").contains("new experiment");
        key('x');
        footer.clear();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).contains("yes").contains("no");
    }
}
