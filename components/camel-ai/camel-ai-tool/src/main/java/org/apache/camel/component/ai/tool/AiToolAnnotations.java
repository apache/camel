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

import java.util.Objects;

/**
 * Optional MCP tool annotation hints for a route-based {@code ai-tool}. Values are advisory for MCP clients and are not
 * enforced by Camel.
 *
 * @since 4.22
 */
public final class AiToolAnnotations {

    private final String title;
    private final Boolean readOnlyHint;
    private final Boolean destructiveHint;
    private final Boolean idempotentHint;
    private final Boolean openWorldHint;

    private AiToolAnnotations(
                              String title,
                              Boolean readOnlyHint,
                              Boolean destructiveHint,
                              Boolean idempotentHint,
                              Boolean openWorldHint) {
        this.title = title;
        this.readOnlyHint = readOnlyHint;
        this.destructiveHint = destructiveHint;
        this.idempotentHint = idempotentHint;
        this.openWorldHint = openWorldHint;
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
        if (title == null && readOnlyHint == null && destructiveHint == null && idempotentHint == null
                && openWorldHint == null) {
            return null;
        }
        return new AiToolAnnotations(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint);
    }

    public String title() {
        return title;
    }

    public Boolean readOnlyHint() {
        return readOnlyHint;
    }

    public Boolean destructiveHint() {
        return destructiveHint;
    }

    public Boolean idempotentHint() {
        return idempotentHint;
    }

    public Boolean openWorldHint() {
        return openWorldHint;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AiToolAnnotations that = (AiToolAnnotations) o;
        return Objects.equals(title, that.title)
                && Objects.equals(readOnlyHint, that.readOnlyHint)
                && Objects.equals(destructiveHint, that.destructiveHint)
                && Objects.equals(idempotentHint, that.idempotentHint)
                && Objects.equals(openWorldHint, that.openWorldHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint);
    }
}
