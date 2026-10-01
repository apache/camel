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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI panel's readme tool gives the project's integration summary when there is no README, so a question about what
 * the integration does starts from it instead of reading every file (CAMEL-25143).
 */
class ReadmeToolSummaryTest {

    @TempDir
    Path project;

    private TuiToolRegistry registry() {
        IntegrationInfo info = new IntegrationInfo();
        info.name = "shop";
        info.pid = "1";
        info.directory = project.toString();
        McpFacade facade = new McpFacade(
                null, new AtomicReference<>(List.of(info)), null, null, null, null, null, null, null, null, null,
                null, null);
        return new TuiToolRegistry(facade);
    }

    @Test
    void summaryWhenThereIsNoReadme() throws Exception {
        Files.writeString(project.resolve("orders.camel.yaml"), """
                - route:
                    id: orders
                    from:
                      uri: timer:tick
                      steps:
                        - to: log:orders
                """, StandardCharsets.UTF_8);
        TuiToolRegistry registry = registry();
        assertTrue(registry.execute("tui_get_readme", new HashMap<>(Map.of("name", "shop"))).startsWith("No README found"),
                "no summary yet: nothing to give");

        ProjectOverview.Overview o = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        IntegrationSummary.write(o, new IntegrationSummary.AiContent("Ticks orders.", List.of(), Map.of()),
                o.fingerprint(), "m");
        String answer = registry.execute("tui_get_readme", new HashMap<>(Map.of("name", "shop")));
        assertTrue(answer.contains(IntegrationSummary.FILE_NAME), answer);
        assertTrue(answer.contains("Ticks orders."), answer);
        assertTrue(answer.contains("were written by an AI"), answer);
        assertTrue(!answer.contains("ai:begin"), "tool comments left out: " + answer);
    }
}
