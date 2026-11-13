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
 * A URI-template resource published by the bridge into an {@link McpServerEngine}. The concrete URI supplied by the
 * client is passed to {@link #handler()} when the engine receives a matching {@code resources/read} request.
 *
 * @since 4.24
 */
public interface McpServerResourceTemplate {

    /**
     * RFC 6570 level-1 URI template exposed in {@code resources/templates/list}.
     */
    String uriTemplate();

    /**
     * Human-readable resource name shown in template listings.
     */
    String name();

    /**
     * Human-readable description.
     */
    String description();

    /**
     * MIME type of the content returned by the route.
     */
    String mimeType();

    /**
     * Handler for a concrete URI matched by this template.
     */
    McpResourceTemplateReadHandler handler();

    /**
     * Optional display title for template listings.
     */
    default String title() {
        return null;
    }
}
