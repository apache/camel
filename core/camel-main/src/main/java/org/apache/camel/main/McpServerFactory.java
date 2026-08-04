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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;

/**
 * Factory for creating the MCP server exposing ai-tool routes as MCP tools, for standalone Camel (not Spring Boot or
 * Quarkus). Provided by camel-mcp-server.
 *
 * @since 4.22
 */
public interface McpServerFactory {

    /**
     * Creates the MCP server bridge configured from the {@code camel.server.mcp-*} options.
     *
     * @param  camelContext  the camel context
     * @param  configuration server configuration carrying the mcp options
     * @return               the bridge as a {@link Service} to be managed by {@link org.apache.camel.CamelContext}.
     */
    Service newMcpServer(CamelContext camelContext, HttpServerConfigurationProperties configuration);

}
