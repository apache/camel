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

import java.util.Map;

import org.apache.camel.util.json.JsonObject;

record SpanEntry(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        String kind,
        String status,
        long startEpochNanos,
        long endEpochNanos,
        long durationMs,
        String routeId,
        String processorId,
        Map<String, Object> attributes) {

    @SuppressWarnings("unchecked")
    static SpanEntry fromJson(JsonObject jo) {
        Map<String, Object> attrs = null;
        JsonObject attrsObj = jo.getMap("attributes");
        if (attrsObj != null && !attrsObj.isEmpty()) {
            attrs = attrsObj;
        }
        return new SpanEntry(
                jo.getString("traceId"),
                jo.getString("spanId"),
                jo.getString("parentSpanId"),
                jo.getString("name"),
                jo.getString("kind"),
                jo.getString("status"),
                jo.getLongOrDefault("startEpochNanos", 0),
                jo.getLongOrDefault("endEpochNanos", 0),
                jo.getLongOrDefault("durationMs", 0),
                jo.getString("routeId"),
                jo.getString("processorId"),
                attrs);
    }

    boolean isRoot() {
        return parentSpanId == null || parentSpanId.isEmpty();
    }

    boolean isError() {
        return "ERROR".equals(status);
    }
}
