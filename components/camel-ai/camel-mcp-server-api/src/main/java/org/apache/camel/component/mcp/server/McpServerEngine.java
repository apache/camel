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
package org.apache.camel.component.mcp.server;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;

/**
 * SPI for the runtime-specific serving layer of the Camel MCP server: a sink the bridge publishes tools into.
 * <p>
 * The bridge owns tool selection, execution, timeout and error sanitization — identical on every runtime. The engine
 * owns protocol serving (HTTP transport, sessions, notifications). One logical MCP server exists per CamelContext.
 * <p>
 * Resolution: a bean of this type in the Camel registry wins; otherwise the engine is discovered via FactoryFinder
 * under {@link McpServerConstants#MCP_SERVER_ENGINE_FACTORY}.
 * <p>
 * Lifecycle: the bridge calls {@link #initialize(McpServerInfo)} once before starting the engine, then
 * {@link #toolAdded(McpServerTool)} for the initial tool set and for every later change (driven by route
 * start/stop/suspend/resume of {@code ai-tool} routes). Engines with a {@code listChanged} capability should emit
 * {@code notifications/tools/list_changed} on add/remove.
 * <p>
 * Resources follow the same lifecycle through {@link #resourceAdded(McpServerResource)} and
 * {@link #resourceRemoved(String)}, driven by {@code ai-resource} routes. Both default to no-ops so an engine that
 * serves tools only keeps working unchanged; such an engine reports {@link #supportsResources()} as false and the
 * bridge then warns when resources are configured but cannot be served.
 *
 * @since 4.22
 */
public interface McpServerEngine extends Service, CamelContextAware {

    /**
     * Passes the server identity and serving hints. Called once, before {@link #start()}. Engines backed by a native
     * runtime MCP server MAY ignore the serving hints — see {@link #consumesServingConfiguration()}.
     */
    void initialize(McpServerInfo info);

    /**
     * Publishes a tool. Called for the initial set and whenever a matching {@code ai-tool} route starts or resumes.
     */
    void toolAdded(McpServerTool tool);

    /**
     * Removes a tool by name. Called whenever a matching {@code ai-tool} route stops or suspends.
     */
    void toolRemoved(String toolName);

    /**
     * Publishes a resource. Called for the initial set and whenever a matching {@code ai-resource} route starts or
     * resumes. Defaults to a no-op for engines that serve tools only.
     *
     * @since 4.23
     */
    default void resourceAdded(McpServerResource resource) {
    }

    /**
     * Removes a resource by uri. Called whenever a matching {@code ai-resource} route stops or suspends. Defaults to a
     * no-op for engines that serve tools only.
     *
     * @since 4.23
     */
    default void resourceRemoved(String resourceUri) {
    }

    /**
     * Whether this engine serves MCP resources. Engines returning false ignore
     * {@link #resourceAdded(McpServerResource)} and {@link #resourceRemoved(String)}, and the bridge warns once when
     * {@code ai-resource} routes match the configured tags but cannot be served.
     *
     * @since 4.23
     */
    default boolean supportsResources() {
        return false;
    }

    /**
     * Whether this engine consumes the Camel-owned serving configuration ({@code path}, {@code serverName}). Engines
     * backed by a native runtime MCP server return false — their own configuration decides serving concerns — and the
     * bridge then warns when Camel serving properties are set but ignored.
     */
    default boolean consumesServingConfiguration() {
        return false;
    }
}
