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
package org.apache.camel.component.mcp.server.vertx;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.conformance.McpServerPromptConformanceTestSupport;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.test.AvailablePortFinder;

/** Runs the prompt conformance kit against the Vert.x streamable HTTP engine. */
class VertxMcpServerPromptConformanceTest extends McpServerPromptConformanceTestSupport {

    private final int port = AvailablePortFinder.getNextAvailable();

    @Override
    protected void customizeCamelContext(CamelContext camelContext) throws Exception {
        VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
        configuration.setBindPort(port);
        camelContext.addService(new VertxPlatformHttpServer(configuration));
    }

    @Override
    protected String mcpServerBaseUrl() {
        return "http://localhost:" + port;
    }
}
