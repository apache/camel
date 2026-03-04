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

import java.util.List;
import java.util.Map;

import dev.langchain4j.mcp.client.McpClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentFactory;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jAgentConfiguration implements Cloneable {

    @UriParam(description = "The agent to use for the component")
    @Metadata(autowired = true)
    private Agent agent;

    @UriParam(description = "The agent factory to use for creating agents if no Agent is provided")
    @Metadata(autowired = true)
    private AgentFactory agentFactory;

    @UriParam(description = "Tags for discovering and calling Camel route tools")
    private String tags;

    @UriParam(description = "Pre-built MCP (Model Context Protocol) client instances for external tool integration."
                            + " Reference beans from the registry, e.g., #myMcpClient1,#myMcpClient2",
              label = "advanced")
    private List<McpClient> mcpClients;

    @UriParam(description = "MCP server definitions in the form of mcpServer.<name>.<property>=<value>."
                            + " Supported properties: transportType (stdio, http, streamableHttp, or sse, default: stdio),"
                            + " command (comma-separated, for stdio), url (for http/sse),"
                            + " environment.<key>=<value> (for stdio), timeout (in seconds, default: 60),"
                            + " logRequests, logResponses,"
                            + " oauthProfile (OAuth profile for HTTP auth, requires camel-oauth).",
              prefix = "mcpServer.", multiValue = true, label = "advanced")
    private Map<String, Object> mcpServer;

    public LangChain4jAgentConfiguration() {
    }

    /**
     * Tags for discovering and calling Camel route tools
     *
     * @return the tags
     */
    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public LangChain4jAgentConfiguration copy() {
        try {
            return (LangChain4jAgentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * The agent providing the service
     *
     * @return the instance of the agent providing the service
     */
    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    /**
     * An agent factory creating the agents
     *
     * @return the instance of the agent factory in use
     */
    public AgentFactory getAgentFactory() {
        return agentFactory;
    }

    public void setAgentFactory(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * Pre-built MCP client instances for external tool integration
     *
     * @return the list of MCP clients
     */
    public List<McpClient> getMcpClients() {
        return mcpClients;
    }

    public void setMcpClients(List<McpClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    /**
     * MCP server definitions for inline URI configuration.
     *
     * <p>
     * The map keys are in the form {@code <serverName>.<property>} and are collected from URI parameters with the
     * {@code mcpServer.} prefix. For example:
     * </p>
     *
     * <pre>
     * mcpServer.weather.transportType=http&amp;mcpServer.weather.url=http://localhost:8080
     * mcpServer.fs.transportType=stdio&amp;mcpServer.fs.command=npx,-y,@modelcontextprotocol/server-filesystem
     * </pre>
     *
     * @return the map of MCP server properties
     */
    public Map<String, Object> getMcpServer() {
        return mcpServer;
    }

    public void setMcpServer(Map<String, Object> mcpServer) {
        this.mcpServer = mcpServer;
    }
}
