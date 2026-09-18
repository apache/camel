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
package org.apache.camel.component.mcp.server.main;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.mcp.server.McpServerConstants;
import org.apache.camel.component.mcp.server.McpServerEngine;
import org.apache.camel.component.mcp.server.stdio.StdioMcpServerEngine;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConstants;
import org.apache.camel.main.McpServerFactory;
import org.apache.camel.spi.annotations.JdkService;

/**
 * {@link McpServerFactory} creating the {@link McpServerBridge} from the {@code camel.server.mcp-*} options, so the MCP
 * server starts from properties alone on Camel Main / JBang.
 */
@JdkService(MainConstants.MCP_SERVER)
public class DefaultMcpServerFactory implements McpServerFactory {

    @Override
    public Service newMcpServer(CamelContext camelContext, HttpServerConfigurationProperties configuration) {
        if (isStdioTransport(configuration.getMcpTransport())
                && camelContext.getRegistry().findSingleByType(McpServerEngine.class) == null) {
            camelContext.getRegistry().bind("mcpServerEngine", new StdioMcpServerEngine());
        }
        McpServerConfiguration mcpConfiguration = new McpServerConfiguration();
        mcpConfiguration.setTags(configuration.getMcpTags());
        mcpConfiguration.setToolTimeout(configuration.getMcpToolTimeout());
        mcpConfiguration.setResourceTimeout(configuration.getMcpResourceTimeout());
        mcpConfiguration.setPath(configuration.getMcpPath());
        mcpConfiguration.setServerName(configuration.getMcpServerName());
        mcpConfiguration.setServerTitle(configuration.getMcpServerTitle());
        mcpConfiguration.setServerDescription(configuration.getMcpServerDescription());
        mcpConfiguration.setServerWebsiteUrl(configuration.getMcpServerWebsiteUrl());
        mcpConfiguration.setInstructions(configuration.getMcpInstructions());
        mcpConfiguration.setServerIcons(McpServerIconsParser.parse(configuration.getMcpServerIcons()));
        mcpConfiguration.setSessionKeepAliveInterval(configuration.getMcpSessionKeepAliveInterval());
        mcpConfiguration.setSessionIdleTtl(configuration.getMcpSessionIdleTtl());
        return new McpServerBridge(mcpConfiguration);
    }

    static boolean isStdioTransport(String transport) {
        return transport != null && McpServerConstants.TRANSPORT_STDIO.equalsIgnoreCase(transport.trim());
    }
}
