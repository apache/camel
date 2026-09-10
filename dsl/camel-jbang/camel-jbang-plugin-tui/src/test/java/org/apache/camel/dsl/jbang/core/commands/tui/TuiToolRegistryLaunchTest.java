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

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher belongs to the facade, not to the tool registry: the AI panel and the MCP server each build their own
 * TuiToolRegistry over the one shared facade, and only the panel's used to be given a LaunchManager, which left
 * tui_run_example and tui_infra start dead for every external MCP client (CAMEL-24679).
 */
class TuiToolRegistryLaunchTest {

    private static final JsonObject UNKNOWN_EXAMPLE = new JsonObject(Map.of("name", "no-such/example"));
    private static final JsonObject START_KAFKA = new JsonObject(Map.of("action", "start", "alias", "kafka"));

    /** Records what it was asked to launch, so the wired path can be asserted without spawning anything. */
    private static final class RecordingLaunchManager extends LaunchManager {

        private String startedInfra;

        RecordingLaunchManager() {
            super(List::of);
        }

        @Override
        void startInfra(String alias) {
            this.startedInfra = alias;
        }
    }

    private static McpFacade bareFacade() {
        return new McpFacade(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void launchingToolsSayNothingCanBeLaunchedUntilTheFacadeHasTheLauncher() throws Exception {
        TuiToolRegistry registry = new TuiToolRegistry(bareFacade());

        assertTrue(registry.execute("tui_run_example", UNKNOWN_EXAMPLE).contains("Launching examples is not available"));
        assertTrue(registry.execute("tui_infra", START_KAFKA).contains("launching is not available"));
    }

    @Test
    void oneLauncherOnTheFacadeReachesEveryRegistryBuiltOverIt() throws Exception {
        McpFacade facade = bareFacade();
        // the AI panel builds its registry in setMcpFacade, the MCP server builds its own one in the constructor
        TuiToolRegistry panelRegistry = new TuiToolRegistry(facade);

        RecordingLaunchManager launcher = new RecordingLaunchManager();
        facade.setLaunchManager(launcher);

        // built after the wiring, like the MCP server's registry, and never handed the launcher itself
        TuiToolRegistry mcpServerRegistry = new TuiToolRegistry(facade);

        // the example name is unknown, but the catalog is only reached once there is a launcher
        assertTrue(panelRegistry.execute("tui_run_example", UNKNOWN_EXAMPLE).contains("Unknown example"));
        assertTrue(mcpServerRegistry.execute("tui_run_example", UNKNOWN_EXAMPLE).contains("Unknown example"));

        assertTrue(mcpServerRegistry.execute("tui_infra", START_KAFKA).contains("Starting infra service kafka"));
        assertEquals("kafka", launcher.startedInfra);
    }

    @Test
    void aRegistryWithoutAFacadeAnswersInsteadOfFailing() throws Exception {
        TuiToolRegistry registry = new TuiToolRegistry(null);

        assertTrue(registry.execute("tui_run_example", UNKNOWN_EXAMPLE).contains("Launching examples is not available"));
    }
}
