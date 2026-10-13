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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiToolRegistryCoreToolsTest {

    @Test
    void everyCoreToolExistsInTheRegistry() {
        TuiToolRegistry registry = new TuiToolRegistry(null);
        Set<String> all = registry.getToolDefinitions().stream()
                .map(TuiToolRegistry.ToolDef::name).collect(Collectors.toSet());

        assertTrue(all.containsAll(TuiToolRegistry.CORE_TOOLS),
                "core tools missing from registry: " + TuiToolRegistry.CORE_TOOLS.stream()
                        .filter(name -> !all.contains(name)).toList());
    }

    @Test
    void coreDefinitionsAreTheCoreSubsetInRegistryOrder() {
        TuiToolRegistry registry = new TuiToolRegistry(null);
        List<TuiToolRegistry.ToolDef> core = registry.getCoreToolDefinitions();
        List<TuiToolRegistry.ToolDef> all = registry.getToolDefinitions();

        assertEquals(TuiToolRegistry.CORE_TOOLS.size(), core.size());
        assertTrue(core.size() < all.size());
        assertTrue(core.stream().allMatch(def -> TuiToolRegistry.CORE_TOOLS.contains(def.name())));
        assertEquals(all.stream().filter(def -> TuiToolRegistry.CORE_TOOLS.contains(def.name())).toList(), core);
    }

    @Test
    void coreSetLeavesOutScreenAutomationTools() {
        Set<String> automation = Set.of("tui_draw", "tui_draw_shape", "tui_animate", "tui_send_keys", "tui_sleep",
                "tui_tape_start", "tui_canvas_open", "tui_set_theme");

        assertFalse(TuiToolRegistry.CORE_TOOLS.stream().anyMatch(automation::contains));
    }

    @Test
    void readOnlyToolsAreRegisteredAndCoverEveryGetter() {
        TuiToolRegistry registry = new TuiToolRegistry(null);
        Set<String> all = registry.getToolDefinitions().stream()
                .map(TuiToolRegistry.ToolDef::name).collect(Collectors.toSet());

        assertTrue(all.containsAll(TuiToolRegistry.READ_ONLY_TOOLS),
                "read-only tools missing from registry: " + TuiToolRegistry.READ_ONLY_TOOLS.stream()
                        .filter(name -> !all.contains(name)).toList());
        List<String> getters = all.stream().filter(name -> name.startsWith("tui_get_")).sorted().toList();
        assertTrue(TuiToolRegistry.READ_ONLY_TOOLS.containsAll(getters),
                "a new getter must be classified deliberately: " + getters.stream()
                        .filter(name -> !TuiToolRegistry.READ_ONLY_TOOLS.contains(name)).toList());
        for (String mutating : List.of("camel_control", "tui_execute_sql", "tui_send_message", "camel_write_file",
                "camel_run", "tui_infra")) {
            assertFalse(TuiToolRegistry.READ_ONLY_TOOLS.contains(mutating), mutating + " changes something");
        }
    }

    // a tool is one entry of TuiToolDefinitions: name, schema, handler and flags together, so none can be missed
    @Test
    void everyToolIsDefinedOnceWithItsHandler() {
        TuiToolRegistry registry = new TuiToolRegistry(null);
        Set<String> names = new HashSet<>();
        for (TuiTool tool : TuiToolDefinitions.all(registry)) {
            assertTrue(names.add(tool.name()), "defined twice: " + tool.name());
        }
        assertEquals(names, registry.getToolDefinitions().stream()
                .map(TuiToolRegistry.ToolDef::name).collect(Collectors.toSet()));
    }

    @Test
    void anUnknownToolIsRejected() {
        TuiToolRegistry registry = new TuiToolRegistry(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry.execute("tui_no_such_tool", Map.of()));
        assertTrue(e.getMessage().contains("Unknown tool: tui_no_such_tool"), e.getMessage());
    }
}
