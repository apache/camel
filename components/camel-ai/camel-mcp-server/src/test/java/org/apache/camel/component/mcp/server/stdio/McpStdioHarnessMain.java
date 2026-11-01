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

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

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
        if (args.length > 1 && "cli".equals(args[1])) {
            // as Camel CLI (camel run): the thread context class loader cannot see the downloaded jars, such as
            // mcp-json-jackson2, while Camel Main configures the CamelContext
            Thread.currentThread().setContextClassLoader(new HidingClassLoader(McpStdioHarnessMain.class.getClassLoader()));
        }

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

                // writes to System.out, which in stdio mode must not reach the MCP stream
                from("ai-tool:print_tool?tags=" + tags + "&description=Print to System.out")
                        .process(e -> System.out.println("printed by a route"))
                        .setBody(constant("printed"));

                from("ai-tool:hidden_tool?description=Untagged tool, must not be exposed")
                        .setBody(constant("hidden"));
            }
        });
        main.run();
    }

    /**
     * Hides the service file of the MCP JSON mapper, as the class loader of Camel CLI does not have the downloaded
     * jars.
     */
    private static final class HidingClassLoader extends ClassLoader {

        private static final String HIDDEN = "META-INF/services/io.modelcontextprotocol.json.McpJsonMapperSupplier";

        HidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            return HIDDEN.equals(name) ? null : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return HIDDEN.equals(name) ? Collections.emptyEnumeration() : super.getResources(name);
        }
    }
}
