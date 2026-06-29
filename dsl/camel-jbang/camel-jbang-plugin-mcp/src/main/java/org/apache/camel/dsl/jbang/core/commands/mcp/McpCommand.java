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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "mcp",
                     description = "MCP server for AI coding assistants",
                     sortOptions = false)
public class McpCommand extends CamelCommand {

    @CommandLine.Option(names = { "--http" },
                        description = "Enable HTTP transport (Streamable HTTP and SSE). Default uses STDIO transport.",
                        defaultValue = "false")
    boolean http;

    @CommandLine.Option(names = { "--port" },
                        description = "HTTP server port (only used with --http)",
                        defaultValue = "8080")
    int port = 8080;

    @CommandLine.Option(names = { "--log-level" },
                        description = "Log level (ERROR, WARN, INFO, DEBUG, TRACE)",
                        defaultValue = "WARN")
    String logLevel = "WARN";

    @CommandLine.Option(names = { "--version" },
                        description = "Camel MCP server version to use (default: current Camel version)")
    String version;

    public McpCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String mcpVersion = version;
        if (mcpVersion == null || mcpVersion.isBlank()) {
            mcpVersion = VersionHelper.extractCamelVersion();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("jbang");
        cmd.add("-Dquarkus.log.level=" + logLevel);
        if (http) {
            cmd.add("-Dquarkus.http.host-enabled=true");
            cmd.add("-Dquarkus.http.port=" + port);
        }
        cmd.add("org.apache.camel:camel-jbang-mcp:" + mcpVersion + ":runner");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }
}
