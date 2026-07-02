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

import org.apache.camel.spi.BacklogErrorEventMessage;
import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "errors", displayName = "Error Registry", description = "Display captured routing errors")
public class ErrorRegistryConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Filter by route id", javaType = "java.lang.String")
    public static final String ROUTE_ID = "routeId";

    @Metadata(label = "query", description = "Limits the number of entries displayed", javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    @Metadata(label = "query", description = "Whether to include stack traces", javaType = "java.lang.Boolean",
              defaultValue = "false")
    public static final String STACK_TRACE = "stackTrace";

    @Metadata(label = "query", description = "Filter by exception type (case-insensitive substring match)",
              javaType = "java.lang.String")
    public static final String EXCEPTION = "exception";

    @Metadata(label = "query",
              description = "Filter by time window as duration string (e.g. 60s, 5m, 1h). Only entries within this window are included.",
              javaType = "java.lang.String")
    public static final String AGO = "ago";

    @Metadata(label = "query", description = "Filter by handled status", javaType = "java.lang.Boolean")
    public static final String HANDLED = "handled";

    public ErrorRegistryConsole() {
        super("camel", "errors", "Error Registry", "Display captured routing errors");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        boolean includeStackTrace = optionBoolean(options, STACK_TRACE, false);

        StringBuilder sb = new StringBuilder();

        ErrorRegistry registry = getCamelContext().getErrorRegistry();
        sb.append(String.format("%n    Enabled: %s", registry.isEnabled()));
        sb.append(String.format("%n    Size: %s", registry.size()));

        List<BacklogErrorEventMessage> entries = fetchAndFilter(registry, options);

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
        boolean includeStackTrace = optionBoolean(options, STACK_TRACE, false);

        JsonObject root = new JsonObject();

        ErrorRegistry registry = getCamelContext().getErrorRegistry();
        root.put("enabled", registry.isEnabled());
        root.put("size", registry.size());
        root.put("maximumEntries", registry.getMaximumEntries());
        root.put("timeToLive", registry.getTimeToLive().toString());

        List<BacklogErrorEventMessage> entries = fetchAndFilter(registry, options);

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

    private static List<BacklogErrorEventMessage> fetchAndFilter(ErrorRegistry registry, Map<String, Object> options) {
        String routeId = (String) options.get(ROUTE_ID);
        String exceptionFilter = (String) options.get(EXCEPTION);
        String agoFilter = (String) options.get(AGO);
        String handledFilter = (String) options.get(HANDLED);
        int max = parseLimit(options);

        // fetch all entries (route-scoped if requested), apply filters, then limit
        Collection<BacklogErrorEventMessage> all;
        if (routeId != null) {
            all = registry.forRoute(routeId).browse();
        } else {
            all = registry.browse();
        }

        long agoCutoff = -1;
        if (agoFilter != null) {
            try {
                long millis = TimeUtils.toMilliSeconds(agoFilter);
                agoCutoff = System.currentTimeMillis() - millis;
            } catch (Exception e) {
                // ignore invalid ago value
            }
        }

        List<BacklogErrorEventMessage> result = new ArrayList<>();
        for (BacklogErrorEventMessage entry : all) {
            if (agoCutoff > 0 && entry.getTimestamp() < agoCutoff) {
                continue;
            }
            if (exceptionFilter != null
                    && !entry.getExceptionType().toLowerCase().contains(exceptionFilter.toLowerCase())) {
                continue;
            }
            if (handledFilter != null && !String.valueOf(entry.isHandled()).equals(handledFilter)) {
                continue;
            }
            result.add(entry);
            if (max > 0 && max < Integer.MAX_VALUE && result.size() >= max) {
                break;
            }
        }
        return result;
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
