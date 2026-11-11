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
 * for every tool schema on every request, so the SQL, tracing, resilience, HTTP and chaos tools only load when the
 * integration has a database, tracing, circuit breakers, HTTP endpoints or camel-chaos; each loaded group adds one line
 * of guidance that names what the integration has and which tool reads it. The tool names and the wording here are
 * those of the MCP server; the TUI maps the same groups to its own tools.
 */
public final class ToolGroups {

    public static final String SQL_TOOL = "camel_runtime_sql";
    public static final String DATASOURCES_TOOL = "camel_runtime_datasources";
    public static final String SQL_TRACE_TOOL = "camel_runtime_sql_trace";
    public static final String SPANS_TOOL = "camel_runtime_spans";
    public static final String TRACE_TOOL = "camel_runtime_trace";
    public static final String METRICS_TOOL = "camel_runtime_metrics";
    public static final String CIRCUIT_BREAKERS_TOOL = "camel_runtime_circuit_breakers";
    public static final String HTTP_ENDPOINTS_TOOL = "camel_runtime_http_endpoints";
    public static final String HTTP_REQUEST_TOOL = "camel_runtime_http_request";
    public static final String CHAOS_LIST_TOOL = "camel_runtime_chaos_list";
    public static final String CHAOS_REPORT_TOOL = "camel_runtime_chaos_report";
    public static final String CHAOS_ADD_TOOL = "camel_runtime_chaos_add";
    public static final String CHAOS_CHECK_TOOL = "camel_runtime_chaos_check";
    public static final String CHAOS_CHANGE_TOOL = "camel_runtime_chaos_change";
    public static final String CHAOS_REMOVE_TOOL = "camel_runtime_chaos_remove";
    public static final String CHAOS_CLEAR_TOOL = "camel_runtime_chaos_clear";
    public static final String CHAOS_SUGGEST_TOOL = "camel_runtime_chaos_suggest";
    public static final String CHAOS_LOAD_TOOL = "camel_runtime_chaos_load";
    public static final String CHAOS_EXPLORE_START_TOOL = "camel_runtime_chaos_explore_start";
    public static final String CHAOS_EXPERIMENT_TOOL = "camel_runtime_chaos_experiment";
    public static final String CHAOS_EXPLORE_FINISH_TOOL = "camel_runtime_chaos_explore_finish";

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
     * @param fingerprint stable for the same groups, datasources and HTTP base URL: a client rebuilds its tool list
     *                    only when it changes
     */
    public record Selection(List<Group> groups, String fingerprint) {

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

    /** The groups an integration needs. */
    public static Selection select(AppFeatures features) {
        AppFeatures f = features != null ? features : AppFeatures.none();
        List<Group> groups = new ArrayList<>();
        for (ToolGroup group : groups(f)) {
            switch (group) {
                case SEMANTIC -> groups.add(new Group(
                        group, List.of(),
                        "Semantic audit history is available through the Camel CLI: camel semantic audit."));
                case SQL -> groups.add(new Group(
                        group,
                        List.of(SQL_TOOL, DATASOURCES_TOOL, SQL_TRACE_TOOL),
                        "SQL: " + describeSql(f) + ". Table names come from the SQL trace (" + SQL_TRACE_TOOL
                                                                             + "); don't guess a schema."));
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
                case HTTP -> groups.add(new Group(
                        group, List.of(HTTP_ENDPOINTS_TOOL, HTTP_REQUEST_TOOL),
                        "HTTP: " + describeHttp(f) + "; " + HTTP_REQUEST_TOOL + " calls it, " + HTTP_ENDPOINTS_TOOL
                                                                                + " lists the operations."));
                // with chaos off the other four can only be refused
                case CHAOS -> groups.add(new Group(
                        group,
                        f.chaosOn()
                                ? List.of(CHAOS_LIST_TOOL, CHAOS_REPORT_TOOL, CHAOS_SUGGEST_TOOL, CHAOS_CHECK_TOOL,
                                        CHAOS_ADD_TOOL, CHAOS_CHANGE_TOOL, CHAOS_REMOVE_TOOL, CHAOS_CLEAR_TOOL,
                                        CHAOS_LOAD_TOOL, CHAOS_EXPLORE_START_TOOL, CHAOS_EXPERIMENT_TOOL,
                                        CHAOS_EXPLORE_FINISH_TOOL)
                                : List.of(CHAOS_LIST_TOOL, CHAOS_REPORT_TOOL),
                        describeChaos(f)));
            }
        }
        return new Selection(groups, fingerprint(groups(f), f));
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
        if (features.httpServed()) {
            groups.add(ToolGroup.HTTP);
        }
        if (features.semanticAudit() || features.semanticMonitoring()) {
            groups.add(ToolGroup.SEMANTIC);
        }
        if (features.chaos()) {
            groups.add(ToolGroup.CHAOS);
        }
        return groups;
    }

    /**
     * Sorted group ids and datasource names, the HTTP base URL when the integration serves HTTP, and whether chaos is
     * off: the same integration gives the same fingerprint, whatever order its status lists things in.
     */
    static String fingerprint(List<ToolGroup> groups, AppFeatures features) {
        String ids = groups.stream().map(ToolGroup::id).sorted().collect(Collectors.joining(","));
        String ds = features.dataSourceNames().stream().sorted().collect(Collectors.joining(","));
        String fp = ids + "|" + ds;
        if (features.httpServed()) {
            String url = features.http().baseUrl();
            fp += "|" + (url != null ? url : "");
        }
        if (groups.contains(ToolGroup.CHAOS) && !features.chaosOn()) {
            // fewer chaos tools
            fp += "|chaos off";
        }
        return fp;
    }

    /**
     * Where the integration serves HTTP and its contract, e.g. {@code served on http://localhost:8080/api (contract
     * stock-api.json)}.
     */
    public static String describeHttp(AppFeatures features) {
        HttpEndpoints.Served http = features.http();
        if (http == null) {
            return "nothing served";
        }
        String where = http.baseUrl() != null
                ? "served on " + http.baseUrl()
                : "served, the port is not known yet";
        return http.contract() != null ? where + " (contract " + http.contract() + ")" : where;
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

    /**
     * The guidance of the chaos group: whether chaos is on, the tools that read the rules and the report, and the tool
     * that adds a fault, with the rule form.
     */
    public static String describeChaos(AppFeatures features) {
        if (!features.chaosOn()) {
            return "Chaos: camel-chaos is off (profile dev or camel.chaos.enabled=true switches it on); "
                   + CHAOS_LIST_TOOL + " says why.";
        }
        return "Chaos: camel-chaos is on, so failures may be injected on purpose; " + CHAOS_LIST_TOOL + " and "
               + CHAOS_REPORT_TOOL + " show the rules and what they did; " + CHAOS_ADD_TOOL
               + " takes a rule such as endpoint=kafka:orders* error rate=0.1 until=5m, and " + CHAOS_CHECK_TOOL
               + " says what it would hit without adding it; " + CHAOS_SUGGEST_TOOL
               + " suggests rules from what the routes do, and " + CHAOS_LOAD_TOOL + " loads a scenario; "
               + CHAOS_EXPLORE_START_TOOL + " starts an exploration with a time budget, in which " + CHAOS_EXPERIMENT_TOOL
               + " runs one experiment per call (rules, traffic and the wait for the verdict), until "
               + CHAOS_EXPLORE_FINISH_TOOL + ".";
    }

    /** {@code Circuit breakers in routes a, b}, or without the routes when they are not known. */
    public static String describeBreakers(AppFeatures features) {
        return features.circuitBreakerRoutes().isEmpty()
                ? "Circuit breakers"
                : "Circuit breakers in routes " + String.join(", ", features.circuitBreakerRoutes());
    }
}
