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
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adding a chaos rule from the known rules ({@link ChaosRecipeForm}), through the keys of the chaos screen.
 */
class ChaosRecipeFormTest {

    private final List<JsonObject> requests = new ArrayList<>();
    private ChaosTab tab;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        IntegrationInfo info = ChaosRecipesTest.chaosdemo();
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true, "rules": [], "report": {"verdict": "PASS", "final": true, "seed": 42}}
                """));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.confirmActions = true;
        ChaosRecipes recipes = ChaosRecipes.parse(ChaosRecipesTest.MODEL);
        tab = new ChaosTab(ctx, new ChaosControl(ctx, request -> {
            requests.add(request);
            JsonObject reply = new JsonObject();
            reply.put("id", "r1");
            return reply;
        }, () -> recipes));
    }

    private void key(char ch) {
        tab.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
    }

    private void key(KeyCode code) {
        tab.handleKeyEvent(KeyEvent.ofKey(code, KeyModifiers.NONE));
    }

    private String screen() {
        return TuiTestHelper.renderToString(tab, 160, 40);
    }

    @Test
    void theKnownRulesByWhatGoesWrong() {
        key('a');
        assertThat(tab.control().step()).isEqualTo(ChaosControl.Step.RECIPES);
        assertThat(tab.isTextInputActive()).isTrue();
        assertThat(screen()).contains("Add a chaos rule to chaosdemo").contains("What should go wrong?")
                .contains("Downstream").contains("Returns an error (HTTP status)").contains("Consumers and routes")
                .contains("The route stalls").contains("Write the rule yourself")
                // the rule of the recipe in focus
                .contains("endpoint=${endpoint} error status=${status} rate=${rate}");
    }

    @Test
    void theRecipeSaysWhatItUncoversWhenNotToUseItAndWhatToWatch() {
        key('a');
        assertThat(screen()).contains("What it can uncover").contains("• An error handler that does not retry")
                .contains("When not to use it").contains("• The endpoint is not HTTP: use Refuses the connection")
                .contains("What to watch").contains("• The redeliveries of the step");
        // and while it is filled in
        key(KeyCode.ENTER);
        assertThat(screen()).contains("What it can uncover").contains("Enter to add it");
        // a recipe without a guide shows none
        key(KeyCode.ESCAPE);
        key(KeyCode.DOWN);
        assertThat(screen()).contains("The route stalls").doesNotContain("What it can uncover");
    }

    @Test
    void aRecipeFilledWithTheEndpointOfTheIntegrationAndItsDefaults() {
        key('a');
        key(KeyCode.ENTER);
        assertThat(tab.control().form().inForm()).isTrue();
        // the HTTP endpoint the route calls is picked, the status and the rate are the defaults
        assertThat(screen()).contains("endpoint=http://localhost:8765/stock error status=503 rate=0.25")
                .contains("Enter to add it");

        key(KeyCode.ENTER);
        assertThat(tab.isConfirming()).isTrue();
        assertThat(screen()).contains("endpoint=http://localhost:8765/stock error status=503 rate=0.25");
        key('y');
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getString("command")).isEqualTo("add");
        assertThat(requests.get(0).getString("rule"))
                .isEqualTo("endpoint=http://localhost:8765/stock error status=503 rate=0.25");
    }

    @Test
    void leftAndRightPickAnotherValue() {
        key('a');
        key(KeyCode.DOWN);
        key(KeyCode.ENTER);
        // route: orders, then inventory
        assertThat(screen()).contains("route=orders stall duration=20s").contains("1 of 2");
        key(KeyCode.RIGHT);
        assertThat(screen()).contains("route=inventory stall duration=20s").contains("2 of 2");
        // a value is typed too: the duration
        key(KeyCode.DOWN);
        for (int i = 0; i < 3; i++) {
            key(KeyCode.BACKSPACE);
        }
        key('5');
        key('s');
        key(KeyCode.ENTER);
        key('y');
        assertThat(requests.get(0).getString("rule")).isEqualTo("route=inventory stall duration=5s");
    }

    @Test
    void aValueThatIsTypedMustBeThereBeforeEnter() {
        key('a');
        for (int i = 0; i < 3; i++) {
            key(KeyCode.DOWN);
        }
        key(KeyCode.ENTER);
        assertThat(screen()).contains("header needs a value");
        key(KeyCode.ENTER);
        assertThat(tab.isConfirming()).isFalse();
        key(KeyCode.DOWN);
        for (char ch : "X-Trace-Id".toCharArray()) {
            key(ch);
        }
        key(KeyCode.ENTER);
        assertThat(tab.isConfirming()).isTrue();
        assertThat(screen()).contains("corrupt mode=remove-header header=X-Trace-Id");
    }

    @Test
    void theLastEntryTypesTheRuleAndEscGoesBack() {
        key('a');
        key(KeyCode.ENTER);
        // Esc: from the form back to the list, then closed
        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.control().form().inForm()).isFalse();
        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.control().isOpen()).isFalse();

        key('a');
        for (int i = 0; i < 10; i++) {
            key(KeyCode.DOWN);
        }
        key(KeyCode.ENTER);
        assertThat(tab.control().step()).isEqualTo(ChaosControl.Step.INPUT);
        assertThat(requests).isEmpty();
    }

    @Test
    void theFooterSaysWhatTheKeysDo() {
        key('a');
        List<Span> footer = new ArrayList<>();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).contains("choose").contains("fill in");
        key(KeyCode.ENTER);
        footer.clear();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).contains("pick").contains("add");
    }
}
