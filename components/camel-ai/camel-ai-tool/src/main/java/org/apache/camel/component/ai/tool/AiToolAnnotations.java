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
package org.apache.camel.component.ai.tool;

/**
 * Optional MCP tool annotation hints for a route-based {@code ai-tool}. Values are advisory for MCP clients and are not
 * enforced by Camel, except {@link #returnDirect()} which is also honoured by AI producers such as {@code camel-openai}
 * when executing route tools in an agentic loop.
 *
 * @since 4.22
 */
public record AiToolAnnotations(
        String title,
        Boolean readOnlyHint,
        Boolean destructiveHint,
        Boolean idempotentHint,
        Boolean openWorldHint,
        Boolean returnDirect) {

    /**
     * Convenience constructor for callers that do not configure {@link #returnDirect()}.
     *
     * @since 4.22
     */
    public AiToolAnnotations(
                             String title,
                             Boolean readOnlyHint,
                             Boolean destructiveHint,
                             Boolean idempotentHint,
                             Boolean openWorldHint) {
        this(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint, null);
    }

    /**
     * Builds annotations from endpoint configuration, or {@code null} when no hint is configured.
     */
    public static AiToolAnnotations fromConfiguration(AiToolConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        String title = blankToNull(configuration.getTitle());
        Boolean readOnlyHint = configuration.getReadOnlyHint();
        Boolean destructiveHint = configuration.getDestructiveHint();
        Boolean idempotentHint = configuration.getIdempotentHint();
        Boolean openWorldHint = configuration.getOpenWorldHint();
        Boolean returnDirect = configuration.getReturnDirect();
        if (title == null && readOnlyHint == null && destructiveHint == null && idempotentHint == null
                && openWorldHint == null && returnDirect == null) {
            return null;
        }
        return new AiToolAnnotations(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint, returnDirect);
    }

    /**
     * Whether the agentic loop should return this tool's result directly without sending it back to the model.
     */
    public boolean isReturnDirect() {
        return Boolean.TRUE.equals(returnDirect);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
