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

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared camel_eval_expression tool as the TUI exposes it (CAMEL-24695): with an integration selected the
 * expression is evaluated inside it, without one locally, so a simple expression can be checked before it is written.
 */
class TuiToolRegistryEvalExpressionTest {

    @Test
    void evalIsACoreToolAndFallsBackToALocalContextWithoutASelectedIntegration() throws Exception {
        assertTrue(TuiToolRegistry.CORE_TOOLS.contains("camel_eval_expression"), "local models get it too");
        assertTrue(TuiToolRegistry.READ_ONLY_TOOLS.contains("camel_eval_expression"));
        assertTrue(new TuiToolRegistry(null).getToolDefinitions().stream()
                .anyMatch(def -> "camel_eval_expression".equals(def.name())));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        McpFacade facade = new McpFacade(
                ctx, new AtomicReference<>(List.of()), null, null, null, null, null, null, null, null, null, null,
                null);
        TuiToolRegistry registry = new TuiToolRegistry(facade);
        assertEquals("Error: expression is required",
                registry.execute("camel_eval_expression", new JsonObject(Map.of("language", "simple"))));
        // nothing selected: the expression is evaluated in a scratch context instead
        JsonObject result = (JsonObject) Jsoner.deserialize(
                registry.execute("camel_eval_expression", new JsonObject(
                        Map.of("expression", "${body} == 'x'",
                                "body", "x"))));
        assertEquals("ok", result.getString("status"));
        assertEquals("true", result.getString("result"));
        assertTrue(result.getBoolean("predicate"));
        assertTrue(result.getString("evaluatedIn").contains("local"));
    }
}
