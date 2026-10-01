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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * tui_navigate finds an integration by its pid, its name, or the folder of the project it was opened as.
 */
class McpFacadeFindIntegrationTest {

    @Test
    void anOpenedProjectIsFoundByItsFolderOnceItRuns() {
        // opened as "metrics", running under the name its app gives itself
        IntegrationInfo running = integration("4242", "MyCamel", "/work/camel-spring-boot-examples/metrics");
        IntegrationInfo other = integration("4343", "metrics-report", "/work/other");
        List<IntegrationInfo> infos = List.of(other, running);

        assertSame(running, McpFacade.findIntegration(infos, "metrics"));
        assertSame(running, McpFacade.findIntegration(infos, "MyCamel"));
        assertSame(running, McpFacade.findIntegration(infos, "4242"));
        assertSame(other, McpFacade.findIntegration(infos, "METRICS-REPORT"));
        assertNull(McpFacade.findIntegration(infos, "work"));
    }

    @Test
    void aNameWinsOverAFolder() {
        IntegrationInfo byFolder = integration("1", "app", "/work/orders");
        IntegrationInfo byName = integration("2", "orders", "/work/x");
        assertSame(byName, McpFacade.findIntegration(List.of(byFolder, byName), "orders"));
    }

    @Test
    void aVanishingIntegrationIsNotFound() {
        IntegrationInfo gone = integration("1", "orders", "/work/orders");
        gone.vanishing = true;
        assertNull(McpFacade.findIntegration(List.of(gone), "orders"));
    }

    private static IntegrationInfo integration(String pid, String name, String dir) {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = pid;
        info.name = name;
        info.directory = dir;
        return info;
    }
}
