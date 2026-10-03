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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Picks the runtime tool groups for an integration from its {@link AppFeatures} (CAMEL-24834). A small local model pays
 * for every tool schema on every request, so the SQL, tracing and resilience tools only load when the integration has a
 * database, tracing or circuit breakers; each loaded group adds one line of guidance that names what the integration
 * has and which tool reads it. The tool names and the wording here are those of the MCP server; the TUI maps the same
 * groups to its own tools.
 */
public final class ToolGroups {

    public static final String SQL_QUERY_TOOL = "camel_runtime_sql_query";
    public static final String SQL_TOOL = "camel_runtime_sql";
    public static final String DATASOURCES_TOOL = "camel_runtime_datasources";
    public static final String SQL_TRACE_TOOL = "camel_runtime_sql_trace";
    public static final String SPANS_TOOL = "camel_runtime_spans";
    public static final String TRACE_TOOL = "camel_runtime_trace";
    public static final String METRICS_TOOL = "camel_runtime_metrics";
    public static final String CIRCUIT_BREAKERS_TOOL = "camel_runtime_circuit_breakers";

    /**
     * A loaded group.
     *
     * @param group    the group
     * @param tools    the tools it adds, in the order to offer them
     * @param guidance one line for the model: what the integration has and which tool reads it
     */
    public record Group(ToolGroup group, List<String> tools, String guidance) {

        public Group {
            tools = List.copyOf(tools);
        }
    }

    /**
     * The groups for an integration.
     *
     * @param groups      the loaded groups, in {@link ToolGroup} order
     * @param sqlReadOnly whether SQL is limited to reading (only meaningful when the SQL group is loaded)
     * @param fingerprint stable for the same groups, datasources and SQL mode: a client rebuilds its tool list only
     *                    when it changes
     */
    public record Selection(List<Group> groups, boolean sqlReadOnly, String fingerprint) {

        public Selection {
            groups = List.copyOf(groups);
        }

        public List<ToolGroup> toolGroups() {
            return groups.stream().map(Group::group).toList();
        }

        public boolean has(ToolGroup group) {
            return groups.stream().anyMatch(g -> g.group() == group);
        }

        /** The tools of all loaded groups. */
        public List<String> mcpTools() {
            return groups.stream().flatMap(g -> g.tools().stream()).distinct().toList();
        }

        /** The guidance lines of all loaded groups. */
        public List<String> guidance() {
            return groups.stream().map(Group::guidance).toList();
        }
    }

    private ToolGroups() {
    }

    /** The groups an integration needs; with {@code sqlWrites} the SQL group also offers the tool that writes. */
    public static Selection select(AppFeatures features, boolean sqlWrites) {
        AppFeatures f = features != null ? features : AppFeatures.none();
        List<Group> groups = new ArrayList<>();
        for (ToolGroup group : groups(f)) {
            switch (group) {
                case SQL -> groups.add(new Group(
                        group,
                        sqlWrites
                                ? List.of(SQL_QUERY_TOOL, SQL_TOOL, DATASOURCES_TOOL, SQL_TRACE_TOOL)
                                : List.of(SQL_QUERY_TOOL, DATASOURCES_TOOL, SQL_TRACE_TOOL),
                        sqlGuidance(f, sqlWrites)));
                case TRACING -> {
                    List<String> tools = new ArrayList<>();
                    List<String> parts = new ArrayList<>();
                    if (f.openTelemetry()) {
                        tools.add(SPANS_TOOL);
                        parts.add("OpenTelemetry is on, " + SPANS_TOOL + " has the spans per trace");
                    }
                    tools.add(TRACE_TOOL);
                    parts.add(f.messageTracing()
                            ? "message tracing is on, " + TRACE_TOOL + " dump returns the traced messages"
                            : TRACE_TOOL + " enables message tracing");
                    if (f.micrometer()) {
                        tools.add(METRICS_TOOL);
                        parts.add(METRICS_TOOL + " has the Micrometer metrics");
                    }
                    groups.add(new Group(group, tools, "Tracing: " + String.join("; ", parts) + "."));
                }
                case RESILIENCE -> groups.add(new Group(
                        group, List.of(CIRCUIT_BREAKERS_TOOL),
                        describeBreakers(f) + ": " + CIRCUIT_BREAKERS_TOOL
                                                               + " shows state (CLOSED/OPEN/HALF_OPEN) and failure rate;"
                                                               + " OPEN means the fallback runs."));
            }
        }
        return new Selection(groups, !sqlWrites, fingerprint(groups(f), f, sqlWrites));
    }

    private static String sqlGuidance(AppFeatures f, boolean sqlWrites) {
        String mode = sqlWrites
                ? SQL_QUERY_TOOL + " reads, " + SQL_TOOL + " also writes."
                : "Read-only: SELECT only (" + SQL_QUERY_TOOL + ").";
        return "SQL: " + describeSql(f) + ". " + mode + " Table names come from the SQL trace (" + SQL_TRACE_TOOL
               + "); don't guess a schema.";
    }

    /** The groups the features call for, in {@link ToolGroup} order. */
    public static List<ToolGroup> groups(AppFeatures features) {
        List<ToolGroup> groups = new ArrayList<>();
        if (features.sql()) {
            groups.add(ToolGroup.SQL);
        }
        if (features.tracing()) {
            groups.add(ToolGroup.TRACING);
        }
        if (features.circuitBreaker()) {
            groups.add(ToolGroup.RESILIENCE);
        }
        return groups;
    }

    /**
     * Sorted group ids and datasource names, and the SQL mode when the SQL group is loaded: the same integration gives
     * the same fingerprint, whatever order its status lists things in.
     */
    static String fingerprint(List<ToolGroup> groups, AppFeatures features, boolean sqlWrites) {
        String ids = groups.stream().map(ToolGroup::id).sorted().collect(Collectors.joining(","));
        String ds = features.dataSourceNames().stream().sorted().collect(Collectors.joining(","));
        String mode = groups.contains(ToolGroup.SQL) ? (sqlWrites ? "rw" : "ro") : "";
        return ids + "|" + ds + "|" + mode;
    }

    /**
     * The datasources with their pool, and the SQL components the routes use, e.g. {@code datasource(s) orders
     * (HikariCP), used by sql endpoints}.
     */
    public static String describeSql(AppFeatures features) {
        StringBuilder sb = new StringBuilder();
        if (features.dataSources().isEmpty()) {
            sb.append("no datasource listed yet");
        } else {
            sb.append("datasource(s) ").append(features.dataSources().stream()
                    .map(d -> d.poolType() != null && !"Unknown".equals(d.poolType())
                            ? d.name() + " (" + d.poolType() + ")" : d.name())
                    .collect(Collectors.joining(", ")));
        }
        if (!features.sqlComponents().isEmpty()) {
            sb.append(", used by ").append(String.join(", ", features.sqlComponents())).append(" endpoints");
        }
        return sb.toString();
    }

    /** {@code Circuit breakers in routes a, b}, or without the routes when they are not known. */
    public static String describeBreakers(AppFeatures features) {
        return features.circuitBreakerRoutes().isEmpty()
                ? "Circuit breakers"
                : "Circuit breakers in routes " + String.join(", ", features.circuitBreakerRoutes());
    }
}
