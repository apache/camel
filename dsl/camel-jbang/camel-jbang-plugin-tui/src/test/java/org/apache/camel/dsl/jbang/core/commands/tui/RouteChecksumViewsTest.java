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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checksum and the revision of the routes in the Routes view, the Info panel of the diagram and tui_get_table
 * (CAMEL-25771).
 */
class RouteChecksumViewsTest {

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
    void theStatusFileHasThem() {
        JsonObject status = Jsoner.deserialize("""
                {"routes": [
                  {"routeId": "orders", "from": "timer:orders", "state": "Started", "checksum": "aa4535cb1725",
                   "revision": 2},
                  {"routeId": "stock", "from": "timer:stock", "state": "Started"}]}
                """, (JsonObject) null);
        IntegrationInfo parsed = new IntegrationInfo();

        StatusParser.parseRoutes(parsed, status);

        assertThat(parsed.routes.get(0).checksum).isEqualTo("aa4535cb1725");
        assertThat(parsed.routes.get(0).revision).isEqualTo(2);
        // an integration of an older Camel has neither
        assertThat(parsed.routes.get(1).checksum).isNull();
        assertThat(parsed.routes.get(1).revision).isZero();
    }

    @Test
    void aRouteChangedInThisRunHasItsRevisionAfterTheId() {
        route("orders", "aa4535cb1725", 3);
        route("stock", "71afc6e9b46a", 1);

        String rendered = TuiTestHelper.renderToString(new RoutesTab(ctx), 160, 30);

        assertThat(rendered).contains("orders (rev 3)");
        assertThat(rendered).doesNotContain("stock (rev");
        // not a column of hex
        assertThat(rendered).doesNotContain("aa4535cb1725");
    }

    @Test
    void aLongIdIsCutToLeaveRoomForTheRevision() {
        RouteInfo route = route("a-route-with-a-rather-long-id", "aa4535cb1725", 12);

        assertThat(text(RoutesTab.routeIdLine(route, 24))).isEqualTo("a-route-with... (rev 12)");
        assertThat(text(RoutesTab.routeIdLine(route("orders", null, 0), 24))).isEqualTo("orders");
    }

    @Test
    void theInfoPanelHasTheChecksumAndTheRevision() {
        RouteInfo route = route("orders", "aa4535cb1725", 3);
        List<Line> lines = new ArrayList<>();

        DiagramDetailSupport.addRevision(lines, route);

        assertThat(lines).extracting(RouteChecksumViewsTest::text).containsExactly(
                " Checksum:   aa4535cb1725",
                " Revision:   3 · 2 changes");

        lines.clear();
        DiagramDetailSupport.addRevision(lines, route("stock", "71afc6e9b46a", 2));
        assertThat(lines).extracting(RouteChecksumViewsTest::text).containsExactly(
                " Checksum:   71afc6e9b46a",
                " Revision:   2 · 1 change");

        // not changed in this run: the checksum only
        lines.clear();
        DiagramDetailSupport.addRevision(lines, route("invoices", "0c5d1e6b2f9a", 1));
        assertThat(lines).extracting(RouteChecksumViewsTest::text).containsExactly(" Checksum:   0c5d1e6b2f9a");

        // unknown (an older Camel): nothing
        lines.clear();
        DiagramDetailSupport.addRevision(lines, route("legacy", null, 0));
        assertThat(lines).isEmpty();
    }

    @Test
    void theTableHasThemForAnAgent() {
        route("orders", "aa4535cb1725", 3);
        route("legacy", null, 0);

        JsonArray rows = (JsonArray) new RoutesTab(ctx).getTableDataAsJson().get("rows");

        JsonObject orders = (JsonObject) rows.get(0);
        assertThat(orders.getString("checksum")).isEqualTo("aa4535cb1725");
        assertThat(orders.getInteger("revision")).isEqualTo(3);
        JsonObject legacy = (JsonObject) rows.get(1);
        assertThat(legacy).doesNotContainKeys("checksum", "revision");
    }

    private static String text(Line line) {
        StringBuilder sb = new StringBuilder();
        line.spans().forEach(span -> sb.append(span.content()));
        return sb.toString();
    }

    private RouteInfo route(String id, String checksum, int revision) {
        RouteInfo route = new RouteInfo();
        route.routeId = id;
        route.from = "timer:" + id;
        route.state = "Started";
        route.checksum = checksum;
        route.revision = revision;
        info.routes.add(route);
        return route;
    }
}
