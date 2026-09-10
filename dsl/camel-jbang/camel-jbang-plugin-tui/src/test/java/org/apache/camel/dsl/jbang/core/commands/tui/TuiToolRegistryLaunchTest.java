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

import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiToolRegistryLaunchTest {

    /**
     * The launcher belongs to the facade, not to the registry: the AI panel and the MCP server each build their own
     * TuiToolRegistry over the one shared facade, and only the panel's used to be given a LaunchManager, which left
     * tui_run_example and tui_infra start dead for every external MCP client (CAMEL-24679).
     */
    @Test
    void examplesAreLaunchableAsSoonAsTheFacadeHasTheLauncher() throws Exception {
        McpFacade facade = new McpFacade(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        TuiToolRegistry registry = new TuiToolRegistry(facade);
        JsonObject args = new JsonObject(Map.of("name", "no-such/example"));

        String withoutLauncher = registry.execute("tui_run_example", args);
        assertTrue(withoutLauncher.contains("Launching examples is not available"), withoutLauncher);

        facade.setLaunchManager(new LaunchManager(List::of));

        // the name is unknown, but the catalog is only reached once a launcher is there
        String withLauncher = registry.execute("tui_run_example", args);
        assertTrue(withLauncher.contains("Unknown example"), withLauncher);
    }
}
