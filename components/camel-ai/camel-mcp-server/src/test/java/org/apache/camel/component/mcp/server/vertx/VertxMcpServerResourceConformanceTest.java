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
import org.apache.camel.component.mcp.server.conformance.McpServerResourceConformanceTestSupport;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServer;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpServerConfiguration;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;

/**
 * Runs the resource conformance kit against {@link VertxMcpServerEngine} serving through a standalone Vert.x platform
 * HTTP server.
 */
class VertxMcpServerResourceConformanceTest extends McpServerResourceConformanceTestSupport {

    private AvailablePortFinder.Port portRef;

    @AfterEach
    void releasePort() {
        if (portRef != null) {
            portRef.release();
            portRef = null;
        }
    }

    @Override
    protected void customizeCamelContext(CamelContext camelContext) throws Exception {
        portRef = AvailablePortFinder.find();
        VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
        configuration.setBindPort(portRef.getPort());
        camelContext.addService(new VertxPlatformHttpServer(configuration));
    }

    @Override
    protected String mcpServerBaseUrl() {
        return "http://localhost:" + portRef.getPort();
    }
}
