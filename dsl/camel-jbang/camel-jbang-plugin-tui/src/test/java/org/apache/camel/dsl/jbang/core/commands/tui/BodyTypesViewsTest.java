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

import dev.tamboui.text.Line;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body types each step has been reached with, in the Routes view and the Info panel of the diagram (CAMEL-25892).
 */
class BodyTypesViewsTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "5678";
        info.name = "orders";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "5678";
    }

    @Test
    void theRoutesWithStepsThatVaried() {
        RouteInfo orders = route("orders", "platform-http:/orders");
        orders.variedBodyTypes = 2;
        orders.mixedBodyKinds = 1;
        RouteInfo stock = route("stock", "timer:stock");
        RouteInfo invoices = route("invoices", "file:inbox");
        invoices.variedBodyTypes = 1;

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).contains("BODY TYPES");
        assertThat(rendered).contains(TuiIcons.WARN + " 1 mixed · 2 varied");
        assertThat(rendered).contains("1 varied");
        assertThat(RoutesTab.bodyTypesLabel(stock)).isEmpty();
    }

    @Test
    void noColumnWhenNothingVaried() {
        route("stock", "timer:stock");

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).doesNotContain("BODY TYPES");
    }

    @Test
    void theBodyTypesOfEachStep() {
        RouteInfo orders = route("orders", "platform-http:/orders");
        orders.variedBodyTypes = 1;
        orders.mixedBodyKinds = 1;
        orders.processors.add(processor("unmarshal1", "unmarshal", 1,
                List.of("org.apache.camel.converter.stream.ByteArrayInputStreamCache"), List.of("payload")));
        orders.processors.add(processor("to1", "to", 1,
                List.of("byte[]", "java.util.LinkedHashMap"), List.of("payload", "data")));

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 40);

        assertThat(rendered).contains("ByteArrayInputStreamCache");
        assertThat(rendered).contains(TuiIcons.WARN + " byte[] | LinkedHashMap");
    }

    @Test
    void theStatusFileHasThem() {
        JsonObject status = Jsoner.deserialize("""
                {"routes": [{"routeId": "orders", "from": "platform-http:/orders", "state": "Started",
                  "variedBodyTypes": 1, "mixedBodyKinds": 1,
                  "processors": [{"id": "to1", "processor": "to", "level": 1,
                    "bodyTypes": ["byte[]", "java.util.LinkedHashMap"], "bodyKinds": ["payload", "data"]}]}]}
                """, (JsonObject) null);
        IntegrationInfo parsed = new IntegrationInfo();

        StatusParser.parseRoutes(parsed, status);

        RouteInfo route = parsed.routes.get(0);
        assertThat(route.variedBodyTypes).isEqualTo(1);
        assertThat(route.mixedBodyKinds).isEqualTo(1);
        assertThat(route.processors.get(0).bodyTypes).containsExactly("byte[]", "java.util.LinkedHashMap");
        assertThat(route.processors.get(0).bodyKinds).containsExactly("payload", "data");
    }

    @Test
    void theWords() {
        assertThat(BodyTypes.label(List.of("java.lang.String", "byte[]"), List.of("payload")))
                .isEqualTo("String | byte[]");
        assertThat(BodyTypes.label(List.of("null", "java.util.LinkedHashMap"), List.of("null", "data")))
                .isEqualTo(TuiIcons.WARN + " null | LinkedHashMap (null and data)");
        assertThat(BodyTypes.label(List.of(), List.of())).isEmpty();
        assertThat(BodyTypes.shortLabel(List.of("null", "java.util.LinkedHashMap"), List.of("null", "data")))
                .isEqualTo(TuiIcons.WARN + " null | LinkedHashMap");
        assertThat(BodyTypes.kinds(List.of("null", "payload", "data"))).isEqualTo("null, payload and data");
    }

    @Test
    void theTypesCutToTheRoomThereIs() {
        List<String> types = List.of("org.apache.camel.converter.stream.ByteArrayInputStreamCache", "java.util.LinkedHashMap");
        List<String> kinds = List.of("payload", "data");

        assertThat(BodyTypes.fit(types, kinds, 80))
                .isEqualTo(TuiIcons.WARN + " ByteArrayInputStreamCache | LinkedHashMap (payload and data)");
        assertThat(BodyTypes.fit(types, kinds, 45)).isEqualTo(TuiIcons.WARN + " ByteArrayInputStreamCache | LinkedHashMap");
        // the first type, and how many more
        assertThat(BodyTypes.fit(types, kinds, 30)).isEqualTo(TuiIcons.WARN + " ByteArrayInputStreamCache +1");
        assertThat(BodyTypes.fit(types, kinds, 16)).isEqualTo(TuiIcons.WARN + " ByteArrayI… +1");
        // no room for a name: the step still says it needs attention
        assertThat(BodyTypes.fit(types, kinds, 3)).isEqualTo(TuiIcons.WARN);
        assertThat(BodyTypes.fit(List.of("java.lang.String"), List.of("payload"), 3)).isEqualTo("St…");
        assertThat(BodyTypes.fit(List.of("java.lang.String"), List.of("payload"), 1)).isEmpty();
        assertThat(BodyTypes.fit(List.of(), List.of(), 40)).isEmpty();
    }

    @Test
    void theInfoPanelHasThemAll() {
        RouteDiagramLayoutEngine.NodeInfo to = new RouteDiagramLayoutEngine.NodeInfo();
        to.bodyTypes = List.of("org.apache.camel.converter.stream.ByteArrayInputStreamCache", "java.util.LinkedHashMap");
        to.bodyKinds = List.of("payload", "data");
        List<Line> lines = new ArrayList<>();

        DiagramDetailSupport.addBodyTypes(lines, to, 27);

        assertThat(lines).extracting(BodyTypesViewsTest::text).containsExactly(
                "",
                " Body types:",
                "   ByteArrayInputStreamCache",
                "   LinkedHashMap",
                " " + TuiIcons.WARN + " payload and data: the",
                "   body here depends on the",
                "   path a message takes");

        RouteDiagramLayoutEngine.NodeInfo log = new RouteDiagramLayoutEngine.NodeInfo();
        log.bodyTypes = List.of("java.lang.String", "byte[]");
        log.bodyKinds = List.of("payload");
        lines.clear();
        DiagramDetailSupport.addBodyTypes(lines, log, 27);
        assertThat(lines).extracting(BodyTypesViewsTest::text)
                .containsExactly("", " Body types (payload):", "   String", "   byte[]");

        // not reached: nothing
        lines.clear();
        DiagramDetailSupport.addBodyTypes(lines, new RouteDiagramLayoutEngine.NodeInfo(), 27);
        assertThat(lines).isEmpty();
    }

    private static String text(Line line) {
        StringBuilder sb = new StringBuilder();
        line.spans().forEach(span -> sb.append(span.content()));
        return sb.toString();
    }

    private RouteInfo route(String id, String from) {
        RouteInfo route = new RouteInfo();
        route.routeId = id;
        route.from = from;
        route.state = "Started";
        info.routes.add(route);
        return route;
    }

    private static ProcessorInfo processor(
            String id, String type, int level, List<String> bodyTypes, List<String> bodyKinds) {
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = id;
        proc.processor = type;
        proc.level = level;
        proc.bodyTypes = bodyTypes;
        proc.bodyKinds = bodyKinds;
        return proc;
    }
}
