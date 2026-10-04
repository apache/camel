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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolGroup;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolGroups;

/**
 * The AI panel's side of {@link ToolGroups} (CAMEL-24834): the tui_* tools each group adds to the core set, and the
 * guidance line in the panel's words. Only SQL and HTTP need tools of their own; the spans, the traced messages, the
 * metrics and the circuit breakers are read with core tools (tui_get_spans, tui_get_history, tui_get_table), so those
 * groups only add the line that tells a small model where to look.
 */
final class TuiToolGroups {

    static final String SQL_TOOL = "tui_execute_sql";
    static final String UPDATE_ROW_TOOL = "tui_update_row";
    static final String HTTP_ENDPOINTS_TOOL = "tui_http_endpoints";
    static final String HTTP_REQUEST_TOOL = "tui_http_request";

    /**
     * The groups of an integration as the panel uses them.
     *
     * @param groups   the loaded groups
     * @param tools    the tools the groups add to the core set
     * @param guidance one line per group, appended to the system prompt
     */
    record Selection(List<ToolGroup> groups, List<String> tools, List<String> guidance) {

        Selection {
            groups = List.copyOf(groups);
            tools = List.copyOf(tools);
            guidance = List.copyOf(guidance);
        }

        static Selection none() {
            return new Selection(List.of(), List.of(), List.of());
        }

        String groupIds() {
            return groups.stream().map(ToolGroup::id).collect(Collectors.joining(", "));
        }
    }

    private TuiToolGroups() {
    }

    static Selection select(AppFeatures features) {
        AppFeatures f = features != null ? features : AppFeatures.none();
        List<ToolGroup> groups = ToolGroups.groups(f);
        List<String> tools = new ArrayList<>();
        List<String> guidance = new ArrayList<>();
        for (ToolGroup group : groups) {
            tools.addAll(tools(group));
            guidance.add(guidance(group, f));
        }
        return new Selection(groups, tools, guidance);
    }

    static List<String> tools(ToolGroup group) {
        return switch (group) {
            case SQL -> List.of(SQL_TOOL, UPDATE_ROW_TOOL);
            case HTTP -> List.of(HTTP_ENDPOINTS_TOOL, HTTP_REQUEST_TOOL);
            default -> List.of();
        };
    }

    static String guidance(ToolGroup group, AppFeatures f) {
        return switch (group) {
            case SQL -> "SQL: " + ToolGroups.describeSql(f)
                        + ". Table names come from the SQL trace (tui_get_table tab 'SQL Trace'); don't guess a schema.";
            case TRACING -> {
                List<String> parts = new ArrayList<>();
                if (f.openTelemetry()) {
                    parts.add("OpenTelemetry is on, tui_get_spans has the spans per trace");
                }
                if (f.messageTracing()) {
                    parts.add("message tracing is on, tui_get_history has the traced messages");
                }
                if (f.micrometer()) {
                    parts.add("tui_get_table tab 'Metrics' has the Micrometer metrics");
                }
                yield "Tracing: " + String.join("; ", parts) + ".";
            }
            case RESILIENCE -> ToolGroups.describeBreakers(f)
                               + ": tui_get_table tab 'Circuit Breaker' shows state (CLOSED/OPEN/HALF_OPEN) and failure"
                               + " rate; OPEN means the fallback runs.";
            case HTTP -> "HTTP: " + ToolGroups.describeHttp(f) + "; " + HTTP_REQUEST_TOOL + " calls it, "
                         + HTTP_ENDPOINTS_TOOL + " lists the operations.";
        };
    }
}
