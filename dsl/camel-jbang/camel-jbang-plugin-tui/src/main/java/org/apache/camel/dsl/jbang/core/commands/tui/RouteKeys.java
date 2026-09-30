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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;

/**
 * The source routes of a project and the running routes, as one (CAMEL-25148). A route without an id in the source is
 * known to the overview by where it is written ({@code src/main/java/.../OrderRoute.java:32}) and to Camel by the id it
 * made up ({@code route1}); it is matched to the running route that consumes from the same endpoint, when exactly one
 * does. The views use the running id, so the architecture highlights the route in the topology, and show it, or else
 * the file name and line, never the whole path.
 */
final class RouteKeys {

    /** A running route: its id and what it consumes from. */
    record Running(String id, String from) {
    }

    private record Entry(long checkedAt, List<Running> running, Map<String, String> toRunning) {
    }

    private static final long RECHECK_MS = 3000;
    private static final Map<Path, List<Running>> RUNNING = new ConcurrentHashMap<>();
    private static final Map<Path, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Set<Path> LOADING = ConcurrentHashMap.newKeySet();

    private RouteKeys() {
    }

    /** The running routes of the integration whose sources are in {@code dir}, as the views last saw them. */
    static void remember(Path dir, List<RouteInfo> routes) {
        if (dir == null || routes == null) {
            return;
        }
        List<Running> running = new ArrayList<>();
        for (RouteInfo r : routes) {
            if (r.routeId != null) {
                running.add(new Running(r.routeId, r.from));
            }
        }
        RUNNING.put(dir.toAbsolutePath().normalize(), List.copyOf(running));
    }

    /** The running id of each source route matched to one, by the source key; read again in the background. */
    static Map<String, String> runningIds(Path dir) {
        if (dir == null) {
            return Map.of();
        }
        Path key = dir.toAbsolutePath().normalize();
        List<Running> running = RUNNING.getOrDefault(key, List.of());
        Entry e = CACHE.get(key);
        boolean stale = e == null || !e.running().equals(running)
                || System.currentTimeMillis() - e.checkedAt() > RECHECK_MS;
        if (stale && !running.isEmpty() && LOADING.add(key)) {
            Thread t = new Thread(() -> {
                try {
                    CamelCatalog catalog = ProjectOverviewAssist.catalog();
                    ProjectOverview.Overview o = ProjectOverview.analyze(key, catalog);
                    CACHE.put(key, new Entry(System.currentTimeMillis(), running, match(o, running, catalog)));
                } catch (RuntimeException ex) {
                    CACHE.put(key, new Entry(System.currentTimeMillis(), running, Map.of()));
                } finally {
                    LOADING.remove(key);
                }
            }, "tui-route-keys");
            t.setDaemon(true);
            t.start();
        }
        return e != null ? e.toRunning() : Map.of();
    }

    /** The running id of a source route, or the key itself. */
    static String runningId(Path dir, String key) {
        return runningIds(dir).getOrDefault(key, key);
    }

    /** The source key of a running route, or the id itself. */
    static String sourceKey(Path dir, String runningId) {
        for (Map.Entry<String, String> e : runningIds(dir).entrySet()) {
            if (e.getValue().equals(runningId)) {
                return e.getKey();
            }
        }
        return runningId;
    }

    /** How a view names a source route: its running id when matched, else without the directories of its file. */
    static String display(Path dir, String key) {
        String id = runningIds(dir).get(key);
        return id != null ? id : shortKey(key);
    }

    /** {@code src/main/java/sample/OrderRoute.java:32} as {@code OrderRoute.java:32}; other keys as they are. */
    static String shortKey(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 && key.matches(".*:\\d+$") ? key.substring(slash + 1) : key;
    }

    /**
     * The source routes without an id matched to running routes by the endpoint they consume from, compared as the
     * overview links endpoints (scheme family, path without {@code //} or query); only a match of exactly one.
     */
    static Map<String, String> match(ProjectOverview.Overview overview, List<Running> running, CamelCatalog catalog) {
        Set<String> sourceIds = new HashSet<>();
        for (ProjectRoutes.Route r : overview.routes()) {
            if (r.id() != null) {
                sourceIds.add(r.id());
            }
        }
        Map<String, String> answer = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (ProjectRoutes.Route r : overview.routes()) {
            if (r.id() != null || r.from() == null) {
                continue;
            }
            List<String> candidates = new ArrayList<>();
            for (Running run : running) {
                if (sourceIds.contains(run.id()) || used.contains(run.id()) || run.from() == null) {
                    continue;
                }
                ProjectRoutes.Endpoint ep = ProjectRoutes.endpoint(run.from(), null, false, catalog);
                if (ep != null && ep.key().equals(r.from().key())) {
                    candidates.add(run.id());
                }
            }
            if (candidates.size() == 1) {
                answer.put(r.key(), candidates.get(0));
                used.add(candidates.get(0));
            }
        }
        return answer;
    }

    /** For tests: forget what was seen. */
    static void resetForTesting() {
        RUNNING.clear();
        CACHE.clear();
    }
}
