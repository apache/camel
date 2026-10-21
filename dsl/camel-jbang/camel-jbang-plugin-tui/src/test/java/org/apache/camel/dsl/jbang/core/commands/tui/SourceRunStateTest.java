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
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Line;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether the integration runs the file open in the source editor as it is saved (CAMEL-25771): when the file was
 * saved, against when the integration loaded the routes it has from the file.
 */
class SourceRunStateTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final String FILE = "/work/orders.camel.yaml";

    @TempDir
    Path dir;

    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "5678";
        info.name = "orders";
        // started a minute ago
        info.uptime = NOW - 60_000;
    }

    @Test
    void savedBeforeTheIntegrationStarted() {
        route("orders", "orders.camel.yaml:3", "59s");
        route("audit", "file:/work/orders.camel.yaml:20", "59s");

        SourceRunState.State state = SourceRunState.of(info, FILE, NOW - 3_600_000, NOW);

        assertThat(state.kind()).isEqualTo(SourceRunState.Kind.RUNNING);
        assertThat(state.routes()).isEqualTo(2);
        assertThat(text(SourceRunState.line(state))).isEqualTo(TuiIcons.SELECTED + " running as saved");
    }

    @Test
    void savedAfterTheIntegrationStartedWithoutDevMode() {
        route("orders", "orders.camel.yaml:3", "59s");

        SourceRunState.State state = SourceRunState.of(info, FILE, NOW - 10_000, NOW);

        assertThat(state.kind()).isEqualTo(SourceRunState.Kind.NOT_RUNNING);
        assertThat(state.stale()).isEqualTo(1);
        assertThat(text(SourceRunState.line(state)))
                .isEqualTo(TuiIcons.IDLE + " not running yet: the integration runs without --dev, restart it");
    }

    @Test
    void aRouteStoppedAndStartedAgainKeepsWhatItLoaded() {
        // started by hand 2s ago, but never reloaded: it runs what it loaded when the integration started
        route("orders", "orders.camel.yaml:3", "2s");

        SourceRunState.State state = SourceRunState.of(info, FILE, NOW - 10_000, NOW);

        assertThat(state.kind()).isEqualTo(SourceRunState.Kind.NOT_RUNNING);
    }

    @Test
    void devModeIsAboutToReloadARecentSave() {
        info.devMode = true;
        route("orders", "orders.camel.yaml:3", "59s");

        SourceRunState.State state = SourceRunState.of(info, FILE, NOW - 1_000, NOW);

        assertThat(state.kind()).isEqualTo(SourceRunState.Kind.RELOADING);
        assertThat(text(SourceRunState.line(state))).isEqualTo(TuiIcons.IDLE + " reloading...");

        // and when it did not
        state = SourceRunState.of(info, FILE, NOW - 10_000, NOW);
        assertThat(state.kind()).isEqualTo(SourceRunState.Kind.NOT_RUNNING);
        assertThat(state.detail()).isEqualTo("not reloaded since it was saved");
    }

    @Test
    void devModeReloadedAfterTheSave() {
        info.devMode = true;
        info.reloaded = 1;
        route("orders", "orders.camel.yaml:3", "9s");

        assertThat(SourceRunState.of(info, FILE, NOW - 10_000, NOW).kind()).isEqualTo(SourceRunState.Kind.RUNNING);

        // the reload was before the save
        assertThat(SourceRunState.of(info, FILE, NOW - 8_000, NOW).kind()).isEqualTo(SourceRunState.Kind.NOT_RUNNING);
    }

    @Test
    void aFailedReloadPutsBackWhatRanBefore() {
        info.devMode = true;
        info.reloaded = 1;
        info.reloadError = "Cannot load orders.camel.yaml: line 7 is not a route\n\tat org.apache.camel.Foo";
        // the routes of before started again
        route("orders", "orders.camel.yaml:3", "1s");

        SourceRunState.State state = SourceRunState.of(info, FILE, NOW - 3_000, NOW);

        assertThat(state.kind()).isEqualTo(SourceRunState.Kind.RELOAD_FAILED);
        assertThat(text(SourceRunState.line(state)))
                .isEqualTo(TuiIcons.WARN + " the reload failed: Cannot load orders.camel.yaml: line 7 is not a route");
    }

    @Test
    void onlyTheStartedRoutesOfTheFile() {
        route("stock", "stock.camel.yaml:3", "59s");
        route("orders", "orders.camel.yaml:3", "59s").state = "Stopped";

        assertThat(SourceRunState.of(info, FILE, NOW - 10_000, NOW)).isNull();

        // a file the integration has no route from
        assertThat(SourceRunState.of(info, "/work/README.md", NOW - 10_000, NOW)).isNull();
    }

    @Test
    void theUptimeOfARoute() {
        assertThat(SourceRunState.uptimeMillis("0s")).isZero();
        assertThat(SourceRunState.uptimeMillis("5s")).isEqualTo(5_000);
        assertThat(SourceRunState.uptimeMillis("1m5s")).isEqualTo(65_000);
        assertThat(SourceRunState.uptimeMillis("2h5m")).isEqualTo(7_500_000);
        assertThat(SourceRunState.uptimeMillis("1d3h")).isEqualTo(97_200_000);
        assertThat(SourceRunState.uptimeMillis("")).isEqualTo(-1);
        assertThat(SourceRunState.uptimeMillis(null)).isEqualTo(-1);
        assertThat(SourceRunState.uptimeMillis("soon")).isEqualTo(-1);
    }

    @Test
    void theStateForAnAgent() {
        route("orders", "orders.camel.yaml:3", "59s");

        JsonObject json = SourceRunState.toJson(SourceRunState.of(info, FILE, NOW - 10_000, NOW));

        assertThat(json.getString("state")).isEqualTo("notRunning");
        assertThat(json.getInteger("routes")).isEqualTo(1);
        assertThat(json.getInteger("routesLoadedBeforeSave")).isEqualTo(1);
        assertThat(json.getString("detail")).isEqualTo("the integration runs without --dev, restart it");
    }

    @Test
    void theSourceTabShowsItUnderTheFile() throws Exception {
        Path file = Files.writeString(dir.resolve("orders.camel.yaml"), "- from:\n    uri: timer:orders\n");
        long now = System.currentTimeMillis();
        info.uptime = now - 60_000;
        route("orders", "orders.camel.yaml:1", "59s");
        Files.setLastModifiedTime(file, FileTime.fromMillis(now - 3_600_000));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "5678";
        SourceTab tab = new SourceTab(ctx);
        // the integration is taken in on the first draw
        TuiTestHelper.renderToString(tab, 120, 30);
        tab.openFileForReplay(file);
        TuiTestHelper.renderToString(tab, 120, 30);

        tab.refreshWhileVisible();
        String rendered = TuiTestHelper.renderToString(tab, 120, 30);
        assertThat(rendered).contains(TuiIcons.SELECTED + " running as saved");
        JsonObject runState = (JsonObject) tab.getTableDataAsJson().get("runState");
        assertThat(runState.getString("state")).isEqualTo("running");

        // saved again: the integration has not loaded it
        Files.setLastModifiedTime(file, FileTime.fromMillis(now - 1_000));
        tab.refreshWhileVisible();
        rendered = TuiTestHelper.renderToString(tab, 120, 30);
        assertThat(rendered).contains(TuiIcons.IDLE + " not running yet: the integration runs without --dev, restart it");
    }

    private static String text(Line line) {
        StringBuilder sb = new StringBuilder();
        line.spans().forEach(span -> sb.append(span.content()));
        return sb.toString();
    }

    private RouteInfo route(String id, String source, String uptime) {
        RouteInfo route = new RouteInfo();
        route.routeId = id;
        route.source = source;
        route.state = "Started";
        route.uptime = uptime;
        info.routes.add(route);
        return route;
    }
}
