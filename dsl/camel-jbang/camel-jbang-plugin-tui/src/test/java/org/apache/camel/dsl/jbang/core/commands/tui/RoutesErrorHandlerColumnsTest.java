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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26698: the processors of the Routes tab show the redeliveries and the failures handled by the error handler, so
 * an exchange that failed and then succeeded on redelivery leaves a trace at the step that was retried.
 */
class RoutesErrorHandlerColumnsTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "5678";
        info.name = "chaosdemo";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "5678";
    }

    @Test
    void theStatusHasTheCountersOfTheRouteAndItsProcessors() throws Exception {
        JsonObject route = (JsonObject) Jsoner.deserialize("""
                {"routeId": "inventory", "from": "direct://inventory", "state": "Started",
                 "statistics": {"exchangesTotal": 1715, "exchangesFailed": 0, "failuresHandled": 4, "redeliveries": 58},
                 "processors": [{"id": "reserve", "processor": "setHeader", "level": 1,
                                 "statistics": {"exchangesTotal": 1761, "exchangesFailed": 46, "failuresHandled": 0,
                                                "redeliveries": 132}}]}
                """);
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "chaosdemo")));
        root.put("routes", new JsonArray(List.of(route)));
        IntegrationInfo parsed = StatusParser.parseIntegration(ProcessHandle.current(), root);

        RouteInfo ri = parsed.routes.get(0);
        assertThat(ri.redeliveries).isEqualTo(58);
        assertThat(ri.failuresHandled).isEqualTo(4);
        assertThat(ri.processors.get(0).redeliveries).isEqualTo(132);
        assertThat(ri.processors.get(0).failed).isEqualTo(46);
    }

    @Test
    void theProcessorsShowTheRedeliveriesOfTheStepThatWasRetried() {
        RouteInfo route = route();
        route.redeliveries = 58;
        route.failuresHandled = 4;
        route.processors.get(0).failed = 46;
        route.processors.get(0).redeliveries = 132;

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).contains("REDEL").contains("HAND");
        assertThat(lineOf(rendered, "reserve")).contains("46").contains("132");
        assertThat(lineOf(rendered, "direct://inventory")).contains("58").contains("4");
        // the table data for an AI has the counters of the routes too
        JsonObject row = (JsonObject) ((List<?>) new RoutesTab(ctx).getTableDataAsJson().get("rows")).get(0);
        assertThat(row.getLong("redeliveries")).isEqualTo(58);
        assertThat(row.getLong("failuresHandled")).isEqualTo(4);
    }

    @Test
    void theColumnsAreLeftOutWhenNothingWasRedeliveredOrHandled() {
        route();

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).contains("INFLIGHT").doesNotContain("REDEL").doesNotContain("HAND");
    }

    private RouteInfo route() {
        RouteInfo route = new RouteInfo();
        route.routeId = "inventory";
        route.from = "direct://inventory";
        route.state = "Started";
        route.uptime = "10s";
        route.total = 1715;
        ProcessorInfo reserve = new ProcessorInfo();
        reserve.id = "reserve";
        reserve.processor = "setHeader";
        reserve.level = 1;
        reserve.total = 1761;
        route.processors.add(reserve);
        info.routes.add(route);
        return route;
    }

    private static String lineOf(String rendered, String text) {
        return rendered.lines().filter(l -> l.contains(text) && l.contains("│")).reduce((a, b) -> b).orElse("");
    }
}
