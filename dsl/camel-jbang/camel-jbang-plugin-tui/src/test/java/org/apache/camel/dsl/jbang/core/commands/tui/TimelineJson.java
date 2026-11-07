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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The timeline of an integration as its timeline dev console reports it, for the tests.
 */
final class TimelineJson {

    private final long end;
    private final int seconds;
    private final Map<String, Map<String, long[]>> routes = new LinkedHashMap<>();
    private final List<JsonObject> errors = new ArrayList<>();
    private final List<JsonObject> exchanges = new ArrayList<>();
    private final long[] done;

    TimelineJson(long end, int seconds) {
        this.end = end;
        this.seconds = seconds;
        this.done = new long[seconds];
    }

    /** Sets a value of a route at the second i (0 is the oldest). */
    TimelineJson route(String routeId, String key, int i, long value) {
        routes.computeIfAbsent(routeId, r -> new LinkedHashMap<>()).computeIfAbsent(key, k -> new long[seconds])[i] = value;
        if (key.equals("completed") || key.equals("recovered") || key.equals("handled") || key.equals("failed")) {
            done[i] += value;
        }
        return this;
    }

    TimelineJson error(String routeId, String exception, Integer statusCode, String chaosRule, long[] counts) {
        JsonObject e = new JsonObject();
        e.put("routeId", routeId);
        e.put("exception", exception);
        if (statusCode != null) {
            e.put("statusCode", statusCode);
        }
        if (chaosRule != null) {
            e.put("chaosRule", chaosRule);
        }
        e.put("counts", list(counts));
        errors.add(e);
        return this;
    }

    /** An exchange kept, done at the second i. */
    TimelineJson exchange(
            String id, String routeId, int i, long elapsed, String outcome, String exception, String chaosRule,
            String... calls) {
        JsonObject e = new JsonObject();
        e.put("exchangeId", id);
        e.put("routeId", routeId);
        e.put("fromEndpointUri", "platform-http:///" + routeId);
        e.put("timestamp", (end - seconds + 1 + i) * 1000 + 500 - elapsed);
        e.put("elapsed", elapsed);
        e.put("failed", outcome.equals("failed"));
        e.put("handled", outcome.equals("handled"));
        e.put("redelivered", outcome.equals("recovered"));
        if (exception != null) {
            e.put("exception", exception);
        }
        if (chaosRule != null) {
            e.put("chaosRule", chaosRule);
        }
        if (calls.length > 0) {
            List<JsonObject> sends = new ArrayList<>();
            for (String c : calls) {
                int at = c.lastIndexOf('@');
                JsonObject s = new JsonObject();
                s.put("endpointUri", c.substring(0, at));
                s.put("elapsed", Long.parseLong(c.substring(at + 1)));
                sends.add(s);
            }
            e.put("endpointSends", sends);
        }
        exchanges.add(e);
        return this;
    }

    /** As the integration answers it: parsed JSON. */
    JsonObject json() {
        JsonObject root = new JsonObject();
        root.put("end", end);
        root.put("seconds", seconds);
        root.put("exchangesDone", list(done));
        List<JsonObject> rs = new ArrayList<>();
        routes.forEach((id, values) -> {
            JsonObject r = new JsonObject();
            r.put("routeId", id);
            values.forEach((k, v) -> r.put(k, list(v)));
            rs.add(r);
        });
        root.put("routes", rs);
        if (!errors.isEmpty()) {
            root.put("errors", errors);
        }
        root.put("exchanges", exchanges);
        try {
            return (JsonObject) Jsoner.deserialize(root.toJson());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Long> list(long[] values) {
        List<Long> answer = new ArrayList<>(values.length);
        for (long v : values) {
            answer.add(v);
        }
        return answer;
    }
}
