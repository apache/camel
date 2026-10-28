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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The processors table of the Routes tab shows where each step is in the source and what it does, as the route
 * structure has it, and an id only when the route sets one: one Camel generates (to1, setBody1) repeats the type.
 */
class RoutesProcessorLabelsTest {

    /** The structure of the orders route, as the route-topology console answers with the routes. */
    private static final String TOPOLOGY = """
            {"nodes": [], "edges": [], "routes": [{"routeId": "orders", "code": [
              {"line": 3, "type": "route", "id": "orders", "level": 0, "code": "route[orders]"},
              {"line": 4, "type": "from", "id": "orders", "level": 0, "code": "from[timer:orders]"},
              {"line": 8, "type": "setBody", "id": "setBody1", "level": 1, "code": "setBody[constant{hello}]"},
              {"line": 10, "type": "unmarshal", "id": "unmarshal1", "level": 1, "code": "unmarshal[jackson]"},
              {"line": 12, "type": "choice", "id": "choice1", "level": 1, "code": "choice"},
              {"line": 16, "type": "marshal", "id": "marshal1", "level": 3, "code": "marshal[jackson]"},
              {"line": 21, "type": "to", "id": "to1", "level": 1, "code": "to[log:outbox]", "uri": "log:outbox"},
              {"line": 22, "type": "multicast", "id": "audit-fanout", "level": 1, "code": "multicast"}]}]}
            """;

    private StructureContext ctx;
    private RouteInfo route;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "5678";
        info.name = "orders";
        route = new RouteInfo();
        route.routeId = "orders";
        route.from = "timer://orders";
        route.state = "Started";
        route.source = "orders.camel.yaml:3";
        route.processors.add(processor("setBody1", "setBody", 1, 8, null));
        route.processors.add(processor("unmarshal1", "unmarshal", 1, 10, null));
        route.processors.add(processor("choice1", "choice", 1, 12, null));
        route.processors.add(processor("marshal1", "marshal", 2, 16, null));
        route.processors.add(processor("to1", "to", 1, 21, "log://outbox"));
        route.processors.add(processor("audit-fanout", "multicast", 1, 22, null));
        info.routes.add(route);
        ctx = new StructureContext(info);
        ctx.selectedPid = "5678";
    }

    @Test
    void theStepsShowTheirLineAndWhatTheyDo() {
        ctx.diagramTopology.refreshStructure(ctx, "5678");

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).contains("LINE", "log:outbox", "jackson", "constant{hello}");
        // the tree of the steps is in TYPE: the marshal is inside the choice
        assertThat(rendered).contains("     marshal");
        assertThat(line(rendered, "log:outbox")).contains(" 21 ");
        // ids Camel generated are left out, one the route sets is shown
        assertThat(rendered).doesNotContain("to1", "setBody1", "unmarshal1", "marshal1", "choice1");
        assertThat(rendered).contains("audit-fanout");
    }

    @Test
    void beforeTheStructureIsThereASendingStepShowsWhereItSends() {
        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).contains("log://outbox").doesNotContain("to1", "jackson");
    }

    @Test
    void noLineColumnWithoutSourceLocations() {
        route.processors.forEach(p -> p.source = null);

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).doesNotContain("LINE");
    }

    @Test
    void topModeShowsThemToo() {
        ctx.diagramTopology.refreshStructure(ctx, "5678");
        RoutesTab tab = new RoutesTab(ctx);
        tab.handleKeyEvent(KeyEvent.ofChar('t'));

        String rendered = TuiTestHelper.renderToString(tab, 160, 30);

        assertThat(rendered).contains("LINE", "log:outbox", "audit-fanout").doesNotContain("unmarshal1");
    }

    @Test
    void theLineOfASourceLocation() {
        assertThat(ProcessorLabels.line("orders.camel.yaml:21")).isEqualTo(21);
        assertThat(ProcessorLabels.line("file:/work/Orders.java:7")).isEqualTo(7);
        assertThat(ProcessorLabels.line("orders.camel.yaml")).isNull();
        assertThat(ProcessorLabels.line("orders.camel.yaml:")).isNull();
        assertThat(ProcessorLabels.line(null)).isNull();
    }

    @Test
    void anIdTheRouteSets() {
        assertThat(ProcessorLabels.customId(processor("to1", "to", 1, 0, null))).isFalse();
        assertThat(ProcessorLabels.customId(processor("setBody12", "setBody", 1, 0, null))).isFalse();
        assertThat(ProcessorLabels.customId(processor("audit", "to", 1, 0, null))).isTrue();
        assertThat(ProcessorLabels.customId(processor("to", "to", 1, 0, null))).isTrue();
        assertThat(ProcessorLabels.customId(processor("toAudit1", "to", 1, 0, null))).isTrue();
        // a route template or kamelet puts its prefix in front of the ids it generates
        ProcessorInfo prefixed = processor("orders-to3", "to", 1, 0, null);
        prefixed.nodePrefixId = "orders-";
        assertThat(ProcessorLabels.customId(prefixed)).isFalse();
    }

    @Test
    void whatAStepDoes() {
        ProcessorInfo to = processor("to1", "to", 1, 0, "log://outbox");
        assertThat(ProcessorLabels.detail(to, new DiagramTopology.Step("to[log:outbox]", 21))).isEqualTo("log:outbox");
        assertThat(ProcessorLabels.detail(to, null)).isEqualTo("log://outbox");
        ProcessorInfo choice = processor("choice1", "choice", 1, 0, null);
        assertThat(ProcessorLabels.detail(choice, new DiagramTopology.Step("choice", 12))).isEmpty();
        assertThat(ProcessorLabels.detail(choice, null)).isEmpty();

        ProcessorInfo audit = processor("audit", "to", 1, 0, null);
        Line label = ProcessorLabels.label(audit, new DiagramTopology.Step("to[log:audit]", 30), 40, Style.EMPTY);
        assertThat(text(label)).isEqualTo("audit  log:audit");
        assertThat(text(ProcessorLabels.label(audit, new DiagramTopology.Step("to[log:audit]", 30), 12, Style.EMPTY)))
                .isEqualTo("audit  lo...");
    }

    private static ProcessorInfo processor(String id, String type, int level, int line, String uri) {
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = id;
        proc.processor = type;
        proc.level = level;
        proc.source = line > 0 ? "orders.camel.yaml:" + line : null;
        proc.uri = uri;
        return proc;
    }

    private static String line(String rendered, String text) {
        return rendered.lines().filter(l -> l.contains(text)).findFirst().orElse("");
    }

    private static String text(Line line) {
        StringBuilder sb = new StringBuilder();
        line.spans().forEach(span -> sb.append(span.content()));
        return sb.toString();
    }

    /** Answers the route-topology action with the structure of the orders route. */
    private static final class StructureContext extends MonitorContext {

        StructureContext(IntegrationInfo info) {
            super(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        }

        @Override
        JsonObject executeAction(String pid, JsonObject request, long timeoutMs) {
            return Jsoner.deserialize(TOPOLOGY, new JsonObject());
        }
    }
}
