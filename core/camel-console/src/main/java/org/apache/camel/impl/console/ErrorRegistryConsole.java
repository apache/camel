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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.ErrorRegistryEntry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "errors", displayName = "Error Registry", description = "Display captured routing errors")
public class ErrorRegistryConsole extends AbstractDevConsole {

    /**
     * Filter by route id
     */
    public static final String ROUTE_ID = "routeId";

    /**
     * Limits the number of entries displayed
     */
    public static final String LIMIT = "limit";

    /**
     * Whether to include stack traces
     */
    public static final String STACK_TRACE = "stackTrace";

    public ErrorRegistryConsole() {
        super("camel", "errors", "Error Registry", "Display captured routing errors");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String routeId = (String) options.get(ROUTE_ID);
        String limit = (String) options.get(LIMIT);
        int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);
        boolean includeStackTrace = "true".equals(options.get(STACK_TRACE));

        StringBuilder sb = new StringBuilder();

        ErrorRegistry registry = getCamelContext().getErrorRegistry();
        sb.append(String.format("%n    Enabled: %s", registry.isEnabled()));
        sb.append(String.format("%n    Size: %s", registry.size()));

        Collection<ErrorRegistryEntry> entries;
        if (routeId != null) {
            entries = registry.forRoute(routeId).browse(max);
        } else {
            entries = registry.browse(max);
        }

        for (ErrorRegistryEntry entry : entries) {
            sb.append(String.format("%n    %s (route: %s, endpoint: %s, handled: %s, type: %s, message: %s, timestamp: %s)",
                    entry.exchangeId(), entry.routeId(), entry.endpointUri(),
                    entry.handled(), entry.exceptionType(), entry.exceptionMessage(),
                    entry.timestamp()));
            if (includeStackTrace && entry.stackTrace() != null) {
                for (String line : entry.stackTrace()) {
                    sb.append(String.format("%n        %s", line));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String routeId = (String) options.get(ROUTE_ID);
        String limit = (String) options.get(LIMIT);
        int max = limit == null ? Integer.MAX_VALUE : Integer.parseInt(limit);
        boolean includeStackTrace = "true".equals(options.get(STACK_TRACE));

        JsonObject root = new JsonObject();

        ErrorRegistry registry = getCamelContext().getErrorRegistry();
        root.put("enabled", registry.isEnabled());
        root.put("size", registry.size());
        root.put("maximumEntries", registry.getMaximumEntries());
        root.put("timeToLive", registry.getTimeToLive().toString());
        root.put("stackTraceEnabled", registry.isStackTraceEnabled());

        Collection<ErrorRegistryEntry> entries;
        if (routeId != null) {
            entries = registry.forRoute(routeId).browse(max);
        } else {
            entries = registry.browse(max);
        }

        final List<JsonObject> list = new ArrayList<>();
        for (ErrorRegistryEntry entry : entries) {
            JsonObject jo = new JsonObject();
            jo.put("exchangeId", entry.exchangeId());
            jo.put("routeId", entry.routeId());
            jo.put("endpointUri", entry.endpointUri());
            jo.put("timestamp", entry.timestamp().toString());
            jo.put("handled", entry.handled());
            jo.put("exceptionType", entry.exceptionType());
            jo.put("exceptionMessage", entry.exceptionMessage());
            if (includeStackTrace && entry.stackTrace() != null) {
                JsonArray stackTrace = new JsonArray();
                for (String line : entry.stackTrace()) {
                    stackTrace.add(line);
                }
                jo.put("stackTrace", stackTrace);
            }
            list.add(jo);
        }
        root.put("errors", list);

        return root;
    }
}
