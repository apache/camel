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
package org.apache.camel.component.mcp.server.stdio;

import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

/**
 * Subprocess entry point for stdio MCP integration tests. Launched by {@link StdioMcpServerProcessIT} through the
 * official MCP {@code StdioClientTransport}.
 */
public final class McpStdioHarnessMain {

    private McpStdioHarnessMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("log4j2.configurationFile", "classpath:log4j2-mcp-stdio.properties");
        String tags = args.length > 0 ? args[0] : "harness";

        Main main = new Main();
        main.configure().setStartupSummaryLevel(StartupSummaryLevel.Off);
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-transport", "stdio");
        main.addInitialProperty("camel.server.mcp-tags", tags);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:say_hello?tags=" + tags + "&description=Say hello"
                     + "&parameter.name=string&parameter.name.description=Who to greet&parameter.name.required=true")
                        .routeId("say-hello-route")
                        .setBody(simple("Hello ${header.name}"));

                from("ai-tool:hidden_tool?description=Untagged tool, must not be exposed")
                        .setBody(constant("hidden"));
            }
        });
        main.run();
    }
}
