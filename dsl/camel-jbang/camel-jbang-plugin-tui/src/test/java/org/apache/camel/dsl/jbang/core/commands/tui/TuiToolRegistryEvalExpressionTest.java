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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiToolRegistryEvalExpressionTest {

    @Test
    void predicatesAreRecognisedByTheirOperators() {
        assertTrue(McpFacade.looksLikePredicate("${body} > 200 && ${body} < 300"));
        assertTrue(McpFacade.looksLikePredicate("${header.type} in 'gold,silver'"));
        assertTrue(McpFacade.looksLikePredicate("${header.foo} == 'bar' || ${header.bar} != null"));
        assertTrue(McpFacade.looksLikePredicate("${header.title} contains 'Camel'"));
        assertFalse(McpFacade.looksLikePredicate("${random(1,10)}"));
        assertFalse(McpFacade.looksLikePredicate("${header.user} ?: 'Guest'"), "elvis gives a value");
        assertFalse(McpFacade.looksLikePredicate("${header.a} == 'x' ? 'yes' : 'no'"), "ternary gives a value");
        assertFalse(McpFacade.looksLikePredicate("Hello ${body}, price>100"), "operators need spaces around them");
        assertFalse(McpFacade.looksLikePredicate(null));
    }

    @Test
    void evalIsACoreToolAndNeedsAnExpressionAndASelectedIntegration() throws Exception {
        assertTrue(TuiToolRegistry.CORE_TOOLS.contains("tui_eval_expression"), "local models get it too");
        assertTrue(new TuiToolRegistry(null).getToolDefinitions().stream()
                .anyMatch(def -> "tui_eval_expression".equals(def.name())));

        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        McpFacade facade = new McpFacade(
                ctx, new AtomicReference<>(List.of()), null, null, null, null, null, null, null, null, null, null,
                null);
        TuiToolRegistry registry = new TuiToolRegistry(facade);

        assertEquals("Error: expression is required",
                registry.execute("tui_eval_expression", new JsonObject(Map.of("language", "simple"))));
        // nothing selected: the expression cannot be evaluated anywhere
        assertNull(facade.evalExpression("simple", "${body}", null));
        assertEquals("Error: no integration selected or PID unavailable",
                registry.execute("tui_eval_expression", new JsonObject(Map.of("expression", "${body}"))));
    }
}
