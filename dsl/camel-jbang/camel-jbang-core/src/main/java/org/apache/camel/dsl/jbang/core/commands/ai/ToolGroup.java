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
package org.apache.camel.dsl.jbang.core.commands.ai;

/**
 * A group of runtime tools that is only worth its schemas when the integration has what the tools read (CAMEL-24834): a
 * small local model gets the core tools plus the groups of the selected integration, see {@link ToolGroups}.
 */
public enum ToolGroup {

    /** Datasources, SQL queries and the SQL trace. */
    SQL("sql"),
    /** OpenTelemetry spans, message tracing and Micrometer metrics. */
    TRACING("tracing"),
    /** Circuit breakers. */
    RESILIENCE("resilience"),
    /** The HTTP endpoints the integration serves, and a request to them (CAMEL-25307). */
    HTTP("http");

    private final String id;

    ToolGroup(String id) {
        this.id = id;
    }

    /** The id clients see, e.g. {@code sql}. */
    public String id() {
        return id;
    }
}
