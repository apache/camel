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
 * A resource published by the bridge into an {@link McpServerEngine}. Resources are read-only content addressed by
 * {@link #uri()}, which is what an MCP client sends in a {@code resources/read} request.
 *
 * @since 4.23
 */
public interface McpServerResource {

    /**
     * The resource uri, unique within the MCP server.
     */
    String uri();

    /**
     * Human-readable resource name shown in {@code resources/list}.
     */
    String name();

    /**
     * Human-readable resource description.
     */
    String description();

    /**
     * MIME type of the content this resource produces.
     */
    String mimeType();

    /**
     * The handler reading the resource. Blocking, timeout-bounded and pre-sanitized by the bridge.
     */
    McpResourceReadHandler handler();

    /**
     * Optional display title for resource listings, or {@code null} when none is configured.
     */
    default String title() {
        return null;
    }
}
