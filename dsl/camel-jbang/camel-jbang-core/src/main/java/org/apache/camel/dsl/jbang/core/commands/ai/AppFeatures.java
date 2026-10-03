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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.camel.util.json.JsonObject;

/**
 * What a running integration has that a tool group is for (CAMEL-24834): its datasources and SQL endpoints, circuit
 * breakers, OpenTelemetry, message tracing and Micrometer. Read from the status file the integration writes
 * ({@code ~/.camel/<pid>-status.json}); a key that is missing (an older Camel, a console that is not on the classpath)
 * just means fewer features, never an error.
 *
 * @param dataSources          the datasources in the registry, by name
 * @param sqlComponents        the SQL components the endpoints and routes use (sql, sql-stored, jdbc, spring-jdbc, jpa)
 * @param sqlTraced            whether SQL statements have been traced
 * @param circuitBreaker       whether the routes have a circuit breaker
 * @param circuitBreakerRoutes the routes with a circuit breaker, when known
 * @param openTelemetry        whether OpenTelemetry tracing is on
 * @param messageTracing       whether message tracing is enabled
 * @param micrometer           whether Micrometer metrics are on
 * @param signals              the status keys that gave each feature away, with what they said
 */
public record AppFeatures(
        List<DataSource> dataSources,
        List<String> sqlComponents,
        boolean sqlTraced,
        boolean circuitBreaker,
        List<String> circuitBreakerRoutes,
        boolean openTelemetry,
        boolean messageTracing,
        boolean micrometer,
        Map<String, String> signals) {

    /** The components whose endpoints talk SQL to a datasource. */
    static final Set<String> SQL_COMPONENTS = Set.of("sql", "sql-stored", "jdbc", "spring-jdbc", "jpa");

    private static final String CIRCUIT_BREAKER_PROCESSOR = "circuitBreaker";
    private static final List<String> CIRCUIT_BREAKER_SECTIONS
            = List.of("resilience4j", "fault-tolerance", "circuit-breaker");

    /**
     * A datasource of the registry.
     *
     * @param name     the bean name
     * @param poolType HikariCP, Agroal or Unknown, null when not known
     */
    public record DataSource(String name, String poolType) {
    }

    public AppFeatures {
        dataSources = List.copyOf(dataSources);
        sqlComponents = List.copyOf(sqlComponents);
        circuitBreakerRoutes = List.copyOf(circuitBreakerRoutes);
        signals = Collections.unmodifiableMap(new LinkedHashMap<>(signals));
    }

    /** No integration, or nothing to tell: no tool group loads. */
    public static AppFeatures none() {
        return new AppFeatures(List.of(), List.of(), false, false, List.of(), false, false, false, Map.of());
    }

    /** The names of the datasources. */
    public List<String> dataSourceNames() {
        return dataSources.stream().map(DataSource::name).toList();
    }

    /** Whether the integration works with a database: a datasource, a SQL endpoint or a traced statement. */
    public boolean sql() {
        return !dataSources.isEmpty() || !sqlComponents.isEmpty() || sqlTraced;
    }

    /** Whether there is anything to trace or measure: OpenTelemetry, message tracing or Micrometer. */
    public boolean tracing() {
        return openTelemetry || messageTracing || micrometer;
    }

    /**
     * Both feature sets together: what an integration had before a reload still counts after it, so the tools a model
     * was given do not come and go while it works.
     */
    public AppFeatures merge(AppFeatures other) {
        if (other == null) {
            return this;
        }
        Map<String, DataSource> ds = new TreeMap<>();
        for (DataSource d : dataSources) {
            ds.put(d.name(), d);
        }
        for (DataSource d : other.dataSources) {
            ds.putIfAbsent(d.name(), d);
        }
        Set<String> components = new TreeSet<>(sqlComponents);
        components.addAll(other.sqlComponents);
        Set<String> routes = new TreeSet<>(circuitBreakerRoutes);
        routes.addAll(other.circuitBreakerRoutes);
        Map<String, String> sig = new LinkedHashMap<>(signals);
        other.signals.forEach(sig::putIfAbsent);
        return new AppFeatures(
                new ArrayList<>(ds.values()), new ArrayList<>(components), sqlTraced || other.sqlTraced,
                circuitBreaker || other.circuitBreaker, new ArrayList<>(routes),
                openTelemetry || other.openTelemetry, messageTracing || other.messageTracing,
                micrometer || other.micrometer, sig);
    }

    /**
     * Reads the features from an integration's status document. Pure and defensive: a null document, a missing key or a
     * value of an unexpected type yields fewer features.
     */
    public static AppFeatures fromStatus(JsonObject status) {
        if (status == null) {
            return none();
        }
        Map<String, String> signals = new LinkedHashMap<>();

        // datasources: dataSources.dataSources[] with name and poolType
        Map<String, DataSource> dataSources = new TreeMap<>();
        for (Map<?, ?> entry : objects(section(status, "dataSources").get("dataSources"))) {
            String name = text(entry.get("name"));
            if (name != null) {
                dataSources.put(name, new DataSource(name, text(entry.get("poolType"))));
            }
        }
        if (!dataSources.isEmpty()) {
            signals.put("dataSources", String.join(",", dataSources.keySet()));
        }

        // SQL endpoints: the endpoint registry, the route inputs and the processors that send somewhere
        Set<String> components = new TreeSet<>();
        for (Map<?, ?> ep : objects(section(status, "endpoints").get("endpoints"))) {
            addSqlComponent(components, text(ep.get("uri")));
        }
        List<Map<?, ?>> routes = objects(status.get("routes"));
        Set<String> breakerRoutes = new TreeSet<>();
        boolean breakerProcessor = false;
        for (Map<?, ?> route : routes) {
            addSqlComponent(components, text(route.get("from")));
            for (Map<?, ?> p : objects(route.get("processors"))) {
                addSqlComponent(components, text(p.get("uri")));
                if (CIRCUIT_BREAKER_PROCESSOR.equals(text(p.get("processor")))) {
                    breakerProcessor = true;
                    String routeId = text(p.get("routeId"));
                    if (routeId == null) {
                        routeId = text(route.get("routeId"));
                    }
                    if (routeId != null) {
                        breakerRoutes.add(routeId);
                    }
                }
            }
        }
        List<Map<?, ?>> statements = objects(section(status, "sqlTrace").get("statements"));
        for (Map<?, ?> st : statements) {
            addSqlComponent(components, text(st.get("endpoint")));
        }
        if (!components.isEmpty()) {
            signals.put("endpoints", String.join(",", components));
        }
        boolean sqlTraced = false;
        for (Map<?, ?> st : statements) {
            if (text(st.get("query")) != null) {
                sqlTraced = true;
                break;
            }
        }
        if (sqlTraced) {
            signals.put("sqlTrace", statements.size() + " statement(s)");
        }

        // circuit breakers: the circuitBreaker EIP in a route, or the breakers a console reports
        if (breakerProcessor) {
            String where = breakerRoutes.isEmpty() ? "" : " in " + String.join(",", breakerRoutes);
            signals.put("routes.processors", CIRCUIT_BREAKER_PROCESSOR + where);
        }
        boolean breakers = false;
        for (String key : CIRCUIT_BREAKER_SECTIONS) {
            List<Map<?, ?>> list = objects(section(status, key).get("circuitBreakers"));
            if (!list.isEmpty()) {
                breakers = true;
                signals.put(key, list.size() + " circuit breaker(s)");
                for (Map<?, ?> b : list) {
                    String routeId = text(b.get("routeId"));
                    if (routeId != null) {
                        breakerRoutes.add(routeId);
                    }
                }
            }
        }

        // tracing and metrics
        Set<String> consoles = new TreeSet<>();
        for (Object id : list(status.get("devConsoles"))) {
            if (id != null) {
                consoles.add(id.toString());
            }
        }
        boolean otel = consoles.contains("opentelemetry");
        boolean micrometer = consoles.contains("micrometer") || status.get("micrometer") instanceof Map;
        if (otel || consoles.contains("micrometer")) {
            List<String> fired = new ArrayList<>();
            if (otel) {
                fired.add("opentelemetry");
            }
            if (consoles.contains("micrometer")) {
                fired.add("micrometer");
            }
            signals.put("devConsoles", String.join(",", fired));
        }
        if (status.get("micrometer") instanceof Map) {
            signals.put("micrometer", "present");
        }
        boolean tracing = Boolean.TRUE.equals(section(status, "trace").get("enabled"))
                || "true".equals(text(section(status, "trace").get("enabled")));
        if (tracing) {
            signals.put("trace.enabled", "true");
        }

        return new AppFeatures(
                new ArrayList<>(dataSources.values()), new ArrayList<>(components), sqlTraced,
                breakerProcessor || breakers, new ArrayList<>(breakerRoutes), otel, tracing, micrometer, signals);
    }

    private static void addSqlComponent(Set<String> components, String uri) {
        if (uri == null) {
            return;
        }
        int colon = uri.indexOf(':');
        if (colon > 0) {
            String scheme = uri.substring(0, colon);
            if (SQL_COMPONENTS.contains(scheme)) {
                components.add(scheme);
            }
        }
    }

    private static Map<?, ?> section(Map<?, ?> root, String key) {
        return root.get(key) instanceof Map<?, ?> m ? m : Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> l ? l : List.of();
    }

    private static List<Map<?, ?>> objects(Object value) {
        List<Map<?, ?>> answer = new ArrayList<>();
        for (Object o : list(value)) {
            if (o instanceof Map<?, ?> m) {
                answer.add(m);
            }
        }
        return answer;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
