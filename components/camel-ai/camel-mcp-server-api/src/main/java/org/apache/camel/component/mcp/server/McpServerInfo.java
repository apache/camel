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
 * Identity and serving hints passed to an {@link McpServerEngine} before it is started.
 * <p>
 * Engines backed by a native runtime MCP server (Quarkus, Spring Boot) MAY ignore the serving hints ({@code path}) —
 * their own runtime configuration decides how the server is exposed.
 *
 * @param serverName the MCP server name advertised to clients (defaults to the CamelContext name)
 * @param version    the MCP server version advertised to clients
 * @param path       the HTTP path where the MCP endpoint should be served
 */
public record McpServerInfo(String serverName, String version, String path) {
}
