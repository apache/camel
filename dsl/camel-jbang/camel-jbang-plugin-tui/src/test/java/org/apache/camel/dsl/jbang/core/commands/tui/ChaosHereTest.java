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
 * Chaos from where you look ({@link ChaosHere}): the target of {@code !} on an endpoint, a consumer and a node of the
 * diagram, and the known rules the Chaos screen offers for it, filled in.
 */
class ChaosHereTest {

    private IntegrationInfo info;
    private MonitorContext ctx;
    private ChaosRecipes recipes;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = ChaosRecipesTest.chaosdemo();
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true, "rules": [], "report": {"verdict": "PASS", "final": true, "seed": 42}}
                """));
        info.producers.get(0).stepId = "callStock";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        recipes = ChaosRecipes.parse(ChaosRecipesTest.MODEL);
    }

    private static List<String> ids(ChaosRecipeForm form) {
        return form.recipes().stream().map(ChaosRecipes.Recipe::id).toList();
    }

    @Test
    void theTargetOfAnEndpointAConsumerAndADiagramNode() {
        EndpointInfo out = new EndpointInfo();
        out.uri = "http://localhost:8765/stock?bridgeEndpoint=true";
        out.direction = "out";
        ChaosHere.Target send = ChaosHere.forEndpoint(out, info);
        assertThat(send.endpoint()).isEqualTo("http://localhost:8765/stock");
        assertThat(send.rulePrefix()).isEqualTo("endpoint=http://localhost:8765/stock ");

        EndpointInfo in = new EndpointInfo();
        in.uri = "file://inbox?delay=1000";
        in.direction = "in";
        in.routeId = "poller";
        ChaosHere.Target from = ChaosHere.forEndpoint(in, info);
        assertThat(from.route()).isEqualTo("poller");
        assertThat(from.polled()).isEqualTo("file://inbox");

        ConsumerInfo timer = info.consumers.get(0);
        timer.id = "orders";
        ChaosHere.Target route = ChaosHere.forConsumer(timer);
        assertThat(route.route()).isEqualTo("orders");
        assertThat(route.polled()).isNull();

        // a to of the diagram: its step, and the endpoint it sends to (the producer of the step)
        ChaosHere.Target node = ChaosHere.forDiagramNode("callStock", "to", null, "orders", info);
        assertThat(node.step()).isEqualTo("callStock");
        assertThat(node.endpoint()).isEqualTo("http://localhost:8765/stock");
        assertThat(node.route()).isNull();
        assertThat(node.label()).isEqualTo("to callStock (http://localhost:8765/stock)");
        // the uri of the node, when the diagram has it
        assertThat(ChaosHere.forDiagramNode("toShipping", "to", "seda://shipping?size=10", "orders", info).endpoint())
                .isEqualTo("seda://shipping");
        // the from of a route is the route
        assertThat(ChaosHere.forDiagramNode(null, "from", null, "orders", info).route()).isEqualTo("orders");
    }

    @Test
    void theKnownRulesThatFitTheTargetWithItFilledIn() {
        ChaosHere.Target http = new ChaosHere.Target("to stock", "http://localhost:8765/stock", null, null, null);
        ChaosRecipeForm form = new ChaosRecipeForm(recipes, info, http);
        // the HTTP status and the missing header fit an HTTP endpoint; the route and step recipes do not
        assertThat(ids(form)).containsExactly("http-error", "missing-header");

        ChaosHere.Target seda = new ChaosHere.Target("to shipping", "seda://shipping", null, null, null);
        // no HTTP status for seda
        assertThat(ids(new ChaosRecipeForm(recipes, info, seda))).containsExactly("missing-header");

        ChaosHere.Target route = new ChaosHere.Target("route orders", null, null, "orders", null);
        assertThat(ids(new ChaosRecipeForm(recipes, info, route))).containsExactly("stall", "corrupt-entry");

        ChaosHere.Target polled = new ChaosHere.Target("route poller", null, "file://inbox", "poller", null);
        assertThat(ids(new ChaosRecipeForm(recipes, info, polled))).containsExactly("stall", "corrupt-entry",
                "poll-error");

        ChaosHere.Target step = new ChaosHere.Target("to callStock", "http://localhost:8765/stock", null, null, "callStock");
        assertThat(ids(new ChaosRecipeForm(recipes, info, step))).containsExactly("http-error", "missing-header",
                "step-error");
    }

    @Test
    void bangOnTheEndpointTabOpensTheChaosScreenForTheEndpoint() {
        EndpointInfo out = new EndpointInfo();
        out.uri = "http://localhost:8765/stock";
        out.direction = "out";
        info.endpoints.add(out);
        List<ChaosHere.Target> opened = new ArrayList<>();
        ctx.chaosHere = opened::add;
        EndpointsTab endpoints = new EndpointsTab(ctx, new MetricsCollector());
        TuiTestHelper.renderToString(endpoints, 160, 30);
        endpoints.navigateDown();
        endpoints.handleKeyEvent(KeyEvent.ofChar('!', KeyModifiers.NONE));

        assertThat(opened).hasSize(1);
        assertThat(opened.get(0).endpoint()).isEqualTo("http://localhost:8765/stock");
        List<Span> footer = new ArrayList<>();
        endpoints.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).contains("chaos");

        // without chaos there is no !
        info.chaos = null;
        endpoints.handleKeyEvent(KeyEvent.ofChar('!', KeyModifiers.NONE));
        assertThat(opened).hasSize(1);
    }

    @Test
    void theChaosScreenOpensTheFormFilledInForTheTarget() {
        List<JsonObject> requests = new ArrayList<>();
        ctx.confirmActions = true;
        ChaosTab tab = new ChaosTab(ctx, new ChaosControl(ctx, request -> {
            requests.add(request);
            return new JsonObject();
        }, () -> recipes));
        tab.openAt(new ChaosHere.Target("route orders", null, null, "orders", null));
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Add a chaos rule to chaosdemo at route orders")
                .contains("The route stalls").doesNotContain("Returns an error");

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        tab.handleKeyEvent(KeyEvent.ofChar('y', KeyModifiers.NONE));
        assertThat(requests.get(0).getString("rule")).isEqualTo("route=orders stall duration=20s");
    }

    @Test
    void withoutAKnownRuleForTheTargetTheRuleIsTypedFromIt() {
        ChaosTab tab = new ChaosTab(ctx, new ChaosControl(ctx, request -> new JsonObject(), () -> recipes));
        // no recipe has a placeholder for a component pattern: the typed line starts with the target
        tab.openAt(new ChaosHere.Target("to log", "log:audit", null, null, null));
        assertThat(tab.control().step()).isEqualTo(ChaosControl.Step.RECIPES);
        assertThat(ids(tab.control().form())).containsExactly("missing-header");

        ChaosTab plain = new ChaosTab(ctx, new ChaosControl(ctx, request -> new JsonObject()));
        plain.openAt(new ChaosHere.Target("step enrich", null, null, null, "enrich"));
        assertThat(plain.control().step()).isEqualTo(ChaosControl.Step.INPUT);
        assertThat(TuiTestHelper.renderToString(plain, 160, 40)).contains("step=enrich");
    }
}
