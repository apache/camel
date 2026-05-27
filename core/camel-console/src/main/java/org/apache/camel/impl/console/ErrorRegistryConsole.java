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

import java.util.Collection;
import java.util.Map;

import org.apache.camel.spi.BacklogErrorEventMessage;
import org.apache.camel.spi.ErrorRegistry;
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
        int max = parseLimit(options);
        boolean includeStackTrace = "true".equals(options.get(STACK_TRACE));

        StringBuilder sb = new StringBuilder();

        ErrorRegistry registry = getCamelContext().getErrorRegistry();
        sb.append(String.format("%n    Enabled: %s", registry.isEnabled()));
        sb.append(String.format("%n    Size: %s", registry.size()));

        Collection<BacklogErrorEventMessage> entries;
        if (routeId != null) {
            entries = registry.forRoute(routeId).browse(max);
        } else {
            entries = registry.browse(max);
        }

        for (BacklogErrorEventMessage entry : entries) {
            sb.append(String.format("%n    %s (route: %s, node: %s, endpoint: %s, handled: %s)",
                    entry.getExchangeId(), entry.getRouteId(), entry.getToNode(), entry.getEndpointUri(),
                    entry.isHandled()));
            sb.append(String.format("%n      Exception: %s - %s",
                    entry.getExceptionType(), entry.getExceptionMessage()));
            sb.append(String.format("%n      Timestamp: %s, Thread: %s",
                    entry.getTimestamp(), entry.getProcessingThreadName()));
            if (entry.getMessageHistory() != null) {
                sb.append(String.format("%n      Message History:"));
                for (String step : entry.getMessageHistory()) {
                    sb.append(String.format("%n        %s", step));
                }
            }
            if (includeStackTrace) {
                sb.append(String.format("%n      Stack Trace:"));
                for (StackTraceElement ste : entry.getException().getStackTrace()) {
                    sb.append(String.format("%n        %s", ste));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String routeId = (String) options.get(ROUTE_ID);
        int max = parseLimit(options);
        boolean includeStackTrace = "true".equals(options.get(STACK_TRACE));

        JsonObject root = new JsonObject();

        ErrorRegistry registry = getCamelContext().getErrorRegistry();
        root.put("enabled", registry.isEnabled());
        root.put("size", registry.size());
        root.put("maximumEntries", registry.getMaximumEntries());
        root.put("timeToLive", registry.getTimeToLive().toString());

        Collection<BacklogErrorEventMessage> entries;
        if (routeId != null) {
            entries = registry.forRoute(routeId).browse(max);
        } else {
            entries = registry.browse(max);
        }

        final JsonArray list = new JsonArray();
        for (BacklogErrorEventMessage entry : entries) {
            JsonObject jo = (JsonObject) entry.asJSon();
            if (!includeStackTrace) {
                // remove stack trace from the exception sub-object to keep output concise
                Object ex = jo.get("exception");
                if (ex instanceof JsonObject exObj) {
                    exObj.remove("stackTrace");
                }
            }
            list.add(jo);
        }
        root.put("errors", list);

        return root;
    }

    private static int parseLimit(Map<String, Object> options) {
        String limit = (String) options.get(LIMIT);
        if (limit == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
