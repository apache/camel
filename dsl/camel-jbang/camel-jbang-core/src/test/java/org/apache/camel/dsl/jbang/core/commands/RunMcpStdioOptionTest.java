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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.Properties;

import org.apache.camel.main.KameletMain;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunMcpStdioOptionTest {

    @Test
    void parseMcpStdioFlag() {
        Run run = new Run(new CamelJBangMain());
        CommandLine cmd = new CommandLine(run);
        cmd.parseArgs("tools.yaml", "--mcp-stdio", "--mcp-tags=agent,crm");
        assertThat(run.serverOptions.mcpStdio).isTrue();
        assertThat(run.serverOptions.mcpTags).isEqualTo("agent,crm");
    }

    @Test
    void mcpStdioDefaultsToFalse() {
        Run run = new Run(new CamelJBangMain());
        CommandLine cmd = new CommandLine(run);
        cmd.parseArgs("tools.yaml");
        assertThat(run.serverOptions.mcpStdio).isFalse();
    }

    @Test
    void applyMcpStdioOverridesProfileHttpTransport() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--mcp-stdio", "--mcp-tags=agent", "tools.yaml");
        KameletMain main = new KameletMain("jbang");
        Properties profile = new Properties();
        profile.setProperty("camel.server.mcp-transport", "http");
        profile.setProperty("camel.server.mcp-enabled", "false");

        run.applyMcpStdioRuntimeOptions(main, profile);

        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-transport")).isEqualTo("stdio");
        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-enabled")).isEqualTo("true");
        assertThat(main.getOverrideProperties().getProperty("camel.server.mcp-tags")).isEqualTo("agent");
        assertThat(main.getOverrideProperties().getProperty("camel.main.startupSummaryLevel")).isEqualTo("Off");
    }

    @Test
    void mcpStdioIsExclusiveFromDevMcpFlag() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--mcp-stdio", "--mcp", "tools.yaml");
        KameletMain main = new KameletMain("jbang");

        assertThatThrownBy(() -> run.applyMcpStdioRuntimeOptions(main, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--mcp-stdio and --mcp cannot be used together");
    }

    @Test
    void mcpStdioIsExclusiveFromProfileDevMcpFlag() {
        Run run = new Run(new CamelJBangMain());
        new CommandLine(run).parseArgs("--mcp-stdio", "tools.yaml");
        KameletMain main = new KameletMain("jbang");
        Properties profile = new Properties();
        profile.setProperty("camel.jbang.mcp", "true");

        assertThatThrownBy(() -> run.applyMcpStdioRuntimeOptions(main, profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--mcp-stdio and --mcp cannot be used together");
    }
}
