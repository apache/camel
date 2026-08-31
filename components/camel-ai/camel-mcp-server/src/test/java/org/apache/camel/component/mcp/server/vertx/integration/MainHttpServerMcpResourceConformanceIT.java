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
package org.apache.camel.component.mcp.server.vertx.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.conformance.McpServerResourceConformanceTestSupport;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.test.AvailablePortFinder;

/**
 * Runs the resource conformance kit against the Camel main HTTP server ({@code camel-platform-http-main}) — the actual
 * serving path of a Camel Main / JBang application with {@code camel.server.enabled=true}.
 */
class MainHttpServerMcpResourceConformanceIT extends McpServerResourceConformanceTestSupport {

    private final int port = AvailablePortFinder.getNextAvailable();

    @Override
    protected void customizeCamelContext(CamelContext camelContext) throws Exception {
        MainHttpServer server = new MainHttpServer();
        server.setCamelContext(camelContext);
        server.setHost("0.0.0.0");
        server.setPort(port);
        camelContext.addService(server);
    }

    @Override
    protected String mcpServerBaseUrl() {
        return "http://localhost:" + port;
    }
}
