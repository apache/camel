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

/**
 * Transport-supplied context for a single MCP tool call, carrying the caller's authenticated security principal when
 * the transport can determine one. An engine builds it from its transport (for the Vert.x streamable HTTP transport,
 * from the authenticated {@code RoutingContext} user) and passes it to
 * {@link McpToolCallHandler#call(java.util.Map, McpToolCallContext)}; the bridge then stamps the principal onto the
 * tool exchange as the {@code CamelMcpSecurityPrincipal} exchange property so a tool route's
 * {@link org.apache.camel.spi.AuthorizationPolicy} can authorize on it.
 * <p>
 * The principal is an opaque {@link Object} (for example an {@code io.vertx.ext.auth.User}) so this transport-agnostic
 * API does not depend on any transport library.
 *
 * @param securityPrincipal the authenticated caller principal, or {@code null} when the transport has none
 * @since                   4.23
 */
public record McpToolCallContext(Object securityPrincipal) {

    /**
     * An empty context: no transport-supplied caller principal.
     */
    public static final McpToolCallContext EMPTY = new McpToolCallContext(null);

    /**
     * Exchange property under which the bridge stamps {@link #securityPrincipal()} on the tool exchange, so a tool
     * route (or its {@link org.apache.camel.spi.AuthorizationPolicy}) can read the MCP caller identity via
     * {@code exchangeProperty.CamelMcpSecurityPrincipal}.
     */
    public static final String SECURITY_PRINCIPAL_PROPERTY = "CamelMcpSecurityPrincipal";
}
