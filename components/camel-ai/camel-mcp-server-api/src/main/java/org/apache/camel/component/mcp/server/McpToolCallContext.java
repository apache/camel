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

import java.util.Collection;
import java.util.List;

/**
 * Transport-supplied context for a single MCP tool call, carrying the caller's authenticated security principal when
 * the transport can determine one, together with a runtime-neutral view of it. An engine builds it from its transport
 * (for the Vert.x streamable HTTP transport, from the authenticated {@code RoutingContext} user) and passes it to
 * {@link McpToolCallHandler#call(java.util.Map, McpToolCallContext)}; the bridge then stamps the principal and its
 * neutral view onto the tool exchange as exchange properties so a tool route's
 * {@link org.apache.camel.spi.AuthorizationPolicy} can authorize on them.
 * <p>
 * The raw {@link #securityPrincipal()} is an opaque {@link Object} (for example an {@code io.vertx.ext.auth.User}) so
 * this transport-agnostic API does not depend on any transport library. A policy can read it directly, but only when it
 * knows the runtime type. The {@link #principalName()} and {@link #principalRoles()} (both since 4.24) are the
 * runtime-neutral view the engine derives from that principal, so a route or the shipped identity policies can
 * authorize on a stable name and roles regardless of the MCP server runtime.
 *
 * @param securityPrincipal the authenticated caller principal, or {@code null} when the transport has none
 * @param principalName     the runtime-neutral caller name derived from the principal, or {@code null} when there is no
 *                          principal or the transport cannot determine a name
 * @param principalRoles    the runtime-neutral caller role names derived from the principal, or {@code null} when there
 *                          is no principal or the transport cannot determine any roles; when present it is an immutable
 *                          copy and never contains {@code null}
 * @since                   4.23
 */
public record McpToolCallContext(Object securityPrincipal, String principalName, Collection<String> principalRoles) {

    /**
     * An empty context: no transport-supplied caller principal.
     */
    public static final McpToolCallContext EMPTY = new McpToolCallContext(null, null, null);

    /**
     * Exchange property under which the bridge stamps {@link #securityPrincipal()} on the tool exchange, so a tool
     * route (or its {@link org.apache.camel.spi.AuthorizationPolicy}) can read the MCP caller identity via
     * {@code exchangeProperty.CamelMcpSecurityPrincipal}.
     */
    public static final String SECURITY_PRINCIPAL_PROPERTY = "CamelMcpSecurityPrincipal";

    /**
     * Exchange property under which the bridge stamps {@link #principalName()} on the tool exchange: the
     * runtime-neutral caller name, so a tool route (or its {@link org.apache.camel.spi.AuthorizationPolicy}) can
     * authorize on a stable identity via {@code exchangeProperty.CamelMcpSecurityPrincipalName}, regardless of the MCP
     * server runtime. Not set when there is no principal or no name could be determined.
     *
     * @since 4.24
     */
    public static final String SECURITY_PRINCIPAL_NAME_PROPERTY = "CamelMcpSecurityPrincipalName";

    /**
     * Exchange property under which the bridge stamps {@link #principalRoles()} on the tool exchange: the
     * runtime-neutral caller role names (a {@code Collection<String>}), so a tool route (or its
     * {@link org.apache.camel.spi.AuthorizationPolicy}) can authorize on stable roles via
     * {@code exchangeProperty.CamelMcpSecurityPrincipalRoles}, regardless of the MCP server runtime. Not set when there
     * is no principal or no roles could be determined.
     *
     * @since 4.24
     */
    public static final String SECURITY_PRINCIPAL_ROLES_PROPERTY = "CamelMcpSecurityPrincipalRoles";

    /**
     * Canonical constructor; defensively copies the roles into an immutable collection so the stamped exchange property
     * cannot be mutated through the caller's reference.
     */
    public McpToolCallContext {
        if (principalRoles != null) {
            principalRoles = List.copyOf(principalRoles);
        }
    }

    /**
     * Creates a context with only the raw principal and no runtime-neutral name or roles.
     *
     * @param securityPrincipal the authenticated caller principal, or {@code null} when the transport has none
     */
    public McpToolCallContext(Object securityPrincipal) {
        this(securityPrincipal, null, null);
    }
}
