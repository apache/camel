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
package org.apache.camel.component.langchain4j.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.apache.camel.CamelContext;
import org.apache.camel.support.OAuthHelper;

/**
 * A simplified configuration POJO for declaratively defining MCP (Model Context Protocol) servers.
 *
 * <p>
 * This class provides a convenient way to configure MCP server connections without manually building
 * {@link DefaultMcpClient} and transport instances. It supports both stdio-based and HTTP-based transports.
 * </p>
 *
 * <p>
 * Example usage as a Spring bean:
 * </p>
 *
 * <pre>{@code
 * // Stdio transport (e.g., local MCP server via npx)
 * LangChain4jMcpServerDefinition serverDef = new LangChain4jMcpServerDefinition();
 * serverDef.setTransportType("stdio");
 * serverDef.setCommand(List.of("npx", "-y", "@modelcontextprotocol/server-everything"));
 *
 * // HTTP transport (e.g., remote MCP server)
 * LangChain4jMcpServerDefinition serverDef = new LangChain4jMcpServerDefinition();
 * serverDef.setTransportType("http");
 * serverDef.setUrl("http://localhost:3001/mcp");
 * }</pre>
 *
 * @since 4.19.0
 */
public class LangChain4jMcpServerDefinition {

    private String serverName;
    private String transportType = "stdio";
    private List<String> command;
    private Map<String, String> environment;
    private String url;
    private Duration timeout = Duration.ofSeconds(60);
    private boolean logRequests;
    private boolean logResponses;
    private String oauthProfile;

    /**
     * Gets the server name (used for identification and as the MCP client key).
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the server name (used for identification and as the MCP client key).
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Gets the transport type. Supported values: "stdio", "http".
     */
    public String getTransportType() {
        return transportType;
    }

    /**
     * Sets the transport type. Supported values: "stdio" (default), "http".
     */
    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    /**
     * Gets the command for stdio transport.
     */
    public List<String> getCommand() {
        return command;
    }

    /**
     * Sets the command for stdio transport. For example: ["npx", "-y", "@modelcontextprotocol/server-everything"].
     */
    public void setCommand(List<String> command) {
        this.command = command;
    }

    /**
     * Gets the environment variables for stdio transport.
     */
    public Map<String, String> getEnvironment() {
        return environment;
    }

    /**
     * Sets the environment variables for stdio transport.
     */
    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    /**
     * Gets the URL for HTTP transport.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the URL for HTTP transport. For example: "http://localhost:3001/mcp".
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the timeout for tool execution and connection.
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout for tool execution and connection. Default: 60 seconds.
     */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets whether request logging is enabled.
     */
    public boolean isLogRequests() {
        return logRequests;
    }

    /**
     * Sets whether to log requests sent to the MCP server.
     */
    public void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests;
    }

    /**
     * Gets whether response logging is enabled.
     */
    public boolean isLogResponses() {
        return logResponses;
    }

    /**
     * Sets whether to log responses received from the MCP server.
     */
    public void setLogResponses(boolean logResponses) {
        this.logResponses = logResponses;
    }

    /**
     * Gets the OAuth profile name for HTTP transport authentication.
     */
    public String getOauthProfile() {
        return oauthProfile;
    }

    /**
     * Sets the OAuth profile name for obtaining an access token via the OAuth 2.0 Client Credentials grant. When set,
     * the token is injected as an Authorization: Bearer header on HTTP-based MCP transports. Requires camel-oauth on
     * the classpath.
     */
    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
    }

    /**
     * Builds an {@link McpClient} from this definition.
     *
     * @param  camelContext             the CamelContext for resolving OAuth profiles (may be null if no oauthProfile is
     *                                  set)
     * @return                          a configured and ready-to-use McpClient
     * @throws IllegalArgumentException if required configuration is missing
     * @throws Exception                if OAuth token resolution fails
     */
    public McpClient buildClient(CamelContext camelContext) throws Exception {
        McpTransport transport = buildTransport(camelContext);

        DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(timeout);

        if (serverName != null) {
            clientBuilder.key(serverName);
        }

        return clientBuilder.build();
    }

    @SuppressWarnings("deprecation")
    private McpTransport buildTransport(CamelContext camelContext) throws Exception {
        // Resolve OAuth token for HTTP-based transports
        Map<String, String> authHeaders = null;
        if (oauthProfile != null && !oauthProfile.isBlank() && camelContext != null) {
            String token = OAuthHelper.resolveOAuthToken(camelContext, oauthProfile);
            authHeaders = Map.of("Authorization", "Bearer " + token);
        }

        if ("http".equalsIgnoreCase(transportType) || "streamableHttp".equalsIgnoreCase(transportType)) {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL is required for HTTP MCP transport");
            }
            StreamableHttpMcpTransport.Builder builder = new StreamableHttpMcpTransport.Builder()
                    .url(url)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .timeout(timeout);
            if (authHeaders != null) {
                builder.customHeaders(authHeaders);
            }
            return builder.build();
        } else if ("sse".equalsIgnoreCase(transportType)) {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL is required for SSE MCP transport");
            }
            HttpMcpTransport.Builder builder = new HttpMcpTransport.Builder()
                    .sseUrl(url)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .timeout(timeout);
            if (authHeaders != null) {
                builder.customHeaders(authHeaders);
            }
            return builder.build();
        } else if ("stdio".equalsIgnoreCase(transportType)) {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Command is required for stdio MCP transport");
            }
            StdioMcpTransport.Builder builder = new StdioMcpTransport.Builder()
                    .command(command)
                    .logEvents(logRequests || logResponses);
            if (environment != null) {
                builder.environment(environment);
            }
            return builder.build();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported MCP transport type: " + transportType
                                               + ". Supported values: stdio, http, streamableHttp, sse");
        }
    }
}
