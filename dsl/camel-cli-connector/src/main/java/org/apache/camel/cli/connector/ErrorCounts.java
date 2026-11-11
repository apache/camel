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
package org.apache.camel.cli.connector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * How many errors happened, for the status file. The error registry keeps only the newest few entries of one kind of
 * error (a route, a node and an exception type), and the newest of them counts how many times that kind happened; its
 * size is the entries it kept, not the errors.
 */
final class ErrorCounts {

    // the kinds in the status file, the most frequent first
    static final int MAX_KINDS = 5;

    private ErrorCounts() {
    }

    /**
     * Adds to the summary the errors that happened ({@code total}), the kinds that happened most ({@code kinds}:
     * routeId, exceptionType, count), and when the newest happened ({@code lastTimestamp}, epoch millis, when there is
     * one), from the entries of the errors dev console.
     */
    static void summarize(Map<String, Object> console, JsonObject summary) {
        Map<String, Kind> kinds = new LinkedHashMap<>();
        long last = 0;
        if (console.get("errors") instanceof List<?> entries) {
            for (Object o : entries) {
                if (o instanceof Map<?, ?> e) {
                    String routeId = string(e.get("routeId"));
                    String type = e.get("exception") instanceof Map<?, ?> ex ? string(ex.get("type")) : null;
                    long count = e.get("repeatCount") instanceof Number n ? Math.max(1, n.longValue()) : 1;
                    String key = routeId + "|" + string(e.get("nodeId")) + "|" + type;
                    Kind k = kinds.computeIfAbsent(key, x -> new Kind(routeId, type));
                    k.count = Math.max(k.count, count);
                    if (e.get("timestamp") instanceof Number ts) {
                        last = Math.max(last, ts.longValue());
                    }
                }
            }
        }
        List<Kind> sorted = new ArrayList<>(kinds.values());
        sorted.sort(Comparator.comparingLong((Kind k) -> k.count).reversed());
        long total = 0;
        JsonArray top = new JsonArray();
        for (Kind k : sorted) {
            total += k.count;
            if (top.size() < MAX_KINDS) {
                JsonObject jo = new JsonObject();
                jo.put("routeId", k.routeId);
                jo.put("exceptionType", k.exceptionType);
                jo.put("count", k.count);
                top.add(jo);
            }
        }
        summary.put("total", total);
        summary.put("kinds", top);
        if (last > 0) {
            summary.put("lastTimestamp", last);
        }
    }

    private static String string(Object o) {
        return o != null ? o.toString() : null;
    }

    private static final class Kind {
        private final String routeId;
        private final String exceptionType;
        private long count;

        private Kind(String routeId, String exceptionType) {
            this.routeId = routeId;
            this.exceptionType = exceptionType;
        }
    }
}
