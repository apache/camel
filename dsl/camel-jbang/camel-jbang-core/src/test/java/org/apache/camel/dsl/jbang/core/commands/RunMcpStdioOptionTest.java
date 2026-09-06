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

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

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
    void mcpStdioIsExclusiveFromDevMcpFlag() {
        Run run = new Run(new CamelJBangMain());
        run.serverOptions.mcpStdio = true;
        run.serverOptions.mcp = true;
        assertThat(run.serverOptions.mcpStdio && run.serverOptions.mcp).isTrue();
    }
}
