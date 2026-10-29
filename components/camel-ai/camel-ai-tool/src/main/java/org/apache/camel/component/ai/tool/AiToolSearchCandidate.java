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
 * One route or remote tool participating in a shared tool search.
 * <p>
 * The candidate keeps the complete parameter schema and an opaque {@link #source()} so the producer that built it can
 * execute a hit through the original route or MCP handler. This type does not invoke anything and does not depend on a
 * model SDK or an MCP client.
 *
 * @since 4.24
 */
public final class AiToolSearchCandidate {

    private final String name;
    private final String description;
    private final String parametersJsonSchema;
    private final boolean searchable;
    private final Object source;

    public AiToolSearchCandidate(
                                 String name, String description, String parametersJsonSchema, boolean searchable,
                                 Object source) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.parametersJsonSchema = parametersJsonSchema;
        this.searchable = searchable;
        this.source = source;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /**
     * Complete parameter JSON Schema, or {@code null} when the tool takes no arguments.
     */
    public String parametersJsonSchema() {
        return parametersJsonSchema;
    }

    /**
     * Whether this tool is omitted from the initial model request and found only by search.
     */
    public boolean searchable() {
        return searchable;
    }

    /**
     * Adapter-owned identity used to map a hit back to its original invocation handler.
     */
    public Object source() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AiToolSearchCandidate that = (AiToolSearchCandidate) o;
        return searchable == that.searchable
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(parametersJsonSchema, that.parametersJsonSchema)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, parametersJsonSchema, searchable, source);
    }

    @Override
    public String toString() {
        return "AiToolSearchCandidate{name=" + name + ", searchable=" + searchable + '}';
    }
}
