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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link McpServerFactory} creating the {@link McpServerBridge} from the {@code camel.server.mcp-*} options, so the MCP
 * server starts from properties alone on Camel Main / JBang.
 */
@JdkService(MainConstants.MCP_SERVER)
public class DefaultMcpServerFactory implements McpServerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMcpServerFactory.class);

    @Override
    public Service newMcpServer(CamelContext camelContext, HttpServerConfigurationProperties configuration) {
        validateOAuthConfiguration(configuration);
        if (isStdioTransport(configuration.getMcpTransport())
                && camelContext.getRegistry().findSingleByType(McpServerEngine.class) == null) {
            camelContext.getRegistry().bind("mcpServerEngine", new StdioMcpServerEngine());
        }
        McpServerConfiguration mcpConfiguration = new McpServerConfiguration();
        mcpConfiguration.setTags(configuration.getMcpTags());
        mcpConfiguration.setToolTimeout(configuration.getMcpToolTimeout());
        mcpConfiguration.setResourceTimeout(configuration.getMcpResourceTimeout());
        mcpConfiguration.setPromptTimeout(configuration.getMcpPromptTimeout());
        mcpConfiguration.setPath(configuration.getMcpPath());
        mcpConfiguration.setServerName(configuration.getMcpServerName());
        mcpConfiguration.setServerTitle(configuration.getMcpServerTitle());
        mcpConfiguration.setServerDescription(configuration.getMcpServerDescription());
        mcpConfiguration.setServerWebsiteUrl(configuration.getMcpServerWebsiteUrl());
        mcpConfiguration.setInstructions(configuration.getMcpInstructions());
        mcpConfiguration.setServerIcons(McpServerIconsParser.parse(configuration.getMcpServerIcons()));
        mcpConfiguration.setSessionKeepAliveInterval(configuration.getMcpSessionKeepAliveInterval());
        mcpConfiguration.setSessionIdleTtl(configuration.getMcpSessionIdleTtl());
        mcpConfiguration.setAllowedOrigins(configuration.getMcpAllowedOrigins());
        mcpConfiguration.setAllowedHosts(configuration.getMcpAllowedHosts());
        // OAuth options have no meaning on stdio. Keep the historical warn-and-ignore behavior here,
        // while programmatic bridge configurations fail closed on engines that do not support OAuth.
        if (!isStdioTransport(configuration.getMcpTransport())) {
            mcpConfiguration.setOauthProfile(configuration.getMcpOauthProfile());
            mcpConfiguration.setRequiredScopes(configuration.getMcpRequiredScopes());
            mcpConfiguration.setResourceUri(configuration.getMcpResourceUri());
        }
        return new McpServerBridge(mcpConfiguration);
    }

    /**
     * Validates the relationships between Main MCP OAuth options. The HTTP engine validates the named OAuth profile
     * once when it creates its OAuth security support. In stdio mode OAuth settings have no HTTP meaning and are
     * intentionally ignored with a warning.
     */
    private static void validateOAuthConfiguration(HttpServerConfigurationProperties configuration) {
        String profile = configuration.getMcpOauthProfile();
        String requiredScopes = configuration.getMcpRequiredScopes();
        String resourceUri = configuration.getMcpResourceUri();
        boolean hasProfile = profile != null && !profile.isBlank();
        boolean hasRequiredScopes = requiredScopes != null && !requiredScopes.isBlank();
        boolean hasResourceUri = resourceUri != null && !resourceUri.isBlank();

        if (isStdioTransport(configuration.getMcpTransport())) {
            if (hasProfile || hasRequiredScopes || hasResourceUri) {
                LOG.warn("MCP OAuth settings are ignored for stdio transport because stdio has no HTTP "
                         + "authentication or protected-resource metadata");
            }
            return;
        }

        if (hasRequiredScopes && !hasProfile) {
            throw new IllegalArgumentException(
                    "camel.server.mcp-required-scopes requires camel.server.mcp-oauth-profile to be configured");
        }
        if (hasResourceUri && !hasProfile) {
            throw new IllegalArgumentException(
                    "camel.server.mcp-resource-uri requires camel.server.mcp-oauth-profile to be configured");
        }

        if (hasProfile && !hasResourceUri) {
            throw new IllegalArgumentException(
                    "camel.server.mcp-oauth-profile requires camel.server.mcp-resource-uri to be configured "
                                               + "with the public HTTPS MCP endpoint URI");
        }
    }

    static boolean isStdioTransport(String transport) {
        return transport != null && McpServerConstants.TRANSPORT_STDIO.equalsIgnoreCase(transport.trim());
    }
}
