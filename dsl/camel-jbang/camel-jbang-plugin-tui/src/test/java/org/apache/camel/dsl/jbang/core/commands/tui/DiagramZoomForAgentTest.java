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

import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDensity;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An agent zooms the diagram with tui_diagram_zoom, and reads how it is zoomed.
 */
class DiagramZoomForAgentTest {

    private final MonitorContext ctx = new MonitorContext(
            new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));

    @Test
    void aLevelFitTheGroupsAndThePanels() {
        DiagramTab tab = new DiagramTab(ctx);

        JsonObject state = tab.zoomForAgent("bare", null, null, false, false, null);
        assertThat(ctx.routeZoom.density()).isEqualTo(RouteDensity.BARE);
        assertThat(state.getString("level")).isEqualTo("bare");
        assertThat(state.getBoolean("fit")).isFalse();

        state = tab.zoomForAgent("fit", "utility", null, false, false, false);
        assertThat(state.getBoolean("fit")).isTrue();
        List<Object> groups = state.getCollection("foldedGroups");
        assertThat(groups).containsExactly("utility");
        assertThat(state.getBoolean("panels")).isFalse();

        state = tab.zoomForAgent(null, null, "utility", false, false, true);
        groups = state.getCollection("foldedGroups");
        assertThat(groups).isEmpty();
        assertThat(state.getBoolean("panels")).isTrue();
        // what is not given stays as it is
        assertThat(state.getBoolean("fit")).isTrue();
    }
}
