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

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "sql-trace", displayName = "SQL Trace", description = "Trace SQL query executions")
@Configurer(extended = true)
public class SqlTraceDevConsole extends AbstractDevConsole {

    @Metadata(defaultValue = "200",
              description = "Maximum capacity of traced SQL statements (capacity must be between 25 and 1000)")
    private int capacity = 200;

    private JsonObject[] events;
    private final AtomicInteger pos = new AtomicInteger();
    private final ConsoleEventNotifier listener = new ConsoleEventNotifier();

    public SqlTraceDevConsole() {
        super("camel", "sql-trace", "SQL Trace", "Trace SQL query executions");
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    protected void doInit() throws Exception {
        if (capacity > 1000 || capacity < 25) {
            throw new IllegalArgumentException("Capacity must be between 25 and 1000");
        }
        this.events = new JsonObject[capacity];
    }

    @Override
    protected void doStart() throws Exception {
        getCamelContext().getManagementStrategy().addEventNotifier(listener);
    }

    @Override
    protected void doStop() throws Exception {
        getCamelContext().getManagementStrategy().removeEventNotifier(listener);
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<JsonObject> list = collectEvents();
        for (JsonObject jo : list) {
            sb.append(String.format("    %s %s %s (%d ms) route:%s%n",
                    jo.getString("category"),
                    jo.getString("query"),
                    jo.getBooleanOrDefault("failed", false) ? "FAILED" : "OK",
                    jo.getLongOrDefault("duration", 0),
                    jo.getString("routeId")));
        }
        if (!list.isEmpty()) {
            sb.insert(0, String.format("Last %d SQL Statements:%n", list.size()));
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<JsonObject> list = collectEvents();
        if (!list.isEmpty()) {
            JsonArray arr = new JsonArray();
            arr.addAll(list);
            root.put("statements", arr);

            // compute summary
            JsonObject summary = new JsonObject();
            long total = list.size();
            long totalTime = 0;
            long slowest = 0;
            long slowCount = 0;
            long failedCount = 0;
            long selectCount = 0;
            long insertCount = 0;
            long updateCount = 0;
            long deleteCount = 0;

            for (JsonObject jo : list) {
                long duration = jo.getLongOrDefault("duration", 0);
                totalTime += duration;
                if (duration > slowest) {
                    slowest = duration;
                }
                if (duration >= 100) {
                    slowCount++;
                }
                if (jo.getBooleanOrDefault("failed", false)) {
                    failedCount++;
                }
                String cat = jo.getStringOrDefault("category", "");
                switch (cat) {
                    case "SELECT":
                        selectCount++;
                        break;
                    case "INSERT":
                        insertCount++;
                        break;
                    case "UPDATE":
                        updateCount++;
                        break;
                    case "DELETE":
                        deleteCount++;
                        break;
                    default:
                        break;
                }
            }

            summary.put("totalQueries", total);
            summary.put("avgTime", total > 0 ? totalTime / total : 0);
            summary.put("slowestTime", slowest);
            summary.put("slowCount", slowCount);
            summary.put("failedCount", failedCount);
            summary.put("selectCount", selectCount);
            summary.put("insertCount", insertCount);
            summary.put("updateCount", updateCount);
            summary.put("deleteCount", deleteCount);
            root.put("summary", summary);
        }

        return root;
    }

    private List<JsonObject> collectEvents() {
        List<JsonObject> list = new ArrayList<>();
        int cursor = pos.get();
        // cursor points to the NEXT write slot, so walk backward from cursor-1
        for (int i = 0; i < capacity; i++) {
            cursor = (cursor - 1 + capacity) % capacity;
            JsonObject event = events[cursor];
            if (event != null) {
                list.add(event);
            }
        }
        return list;
    }

    private static String extractQuery(String endpointUri) {
        if (endpointUri.startsWith("sql:")) {
            String query = StringHelper.after(endpointUri, "sql:");
            if (query != null) {
                // strip :// scheme separator if present
                if (query.startsWith("//")) {
                    query = query.substring(2);
                }
                // remove query parameters
                int idx = query.indexOf('?');
                if (idx > 0) {
                    query = query.substring(0, idx);
                }
                // URI path is URL-encoded, decode it
                query = URLDecoder.decode(query, StandardCharsets.UTF_8);
                return query;
            }
        } else if (endpointUri.startsWith("jdbc:")) {
            // for jdbc component, the URI path is the datasource name, not the SQL query
            return null;
        }
        return null;
    }

    private String resolveResource(String uri) {
        try (InputStream is = ResourceHelper.resolveResourceAsInputStream(getCamelContext(), uri)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            // ignore
        }
        return "resource:" + uri;
    }

    private static String detectCategory(String query) {
        if (query != null && !query.isEmpty()) {
            String upper = query.stripLeading().toUpperCase(Locale.ENGLISH);
            if (upper.startsWith("SELECT")) {
                return "SELECT";
            } else if (upper.startsWith("INSERT")) {
                return "INSERT";
            } else if (upper.startsWith("UPDATE")) {
                return "UPDATE";
            } else if (upper.startsWith("DELETE")) {
                return "DELETE";
            } else if (upper.startsWith("CALL") || upper.startsWith("EXEC")) {
                return "CALL";
            }
        }
        return "OTHER";
    }

    private class ConsoleEventNotifier extends EventNotifierSupport implements NonManagedService {

        ConsoleEventNotifier() {
            setIgnoreCamelContextEvents(true);
            setIgnoreRouteEvents(true);
            setIgnoreServiceEvents(true);
            setIgnoreExchangeCreatedEvent(true);
            setIgnoreExchangeCompletedEvent(true);
            setIgnoreExchangeFailedEvents(true);
            setIgnoreExchangeRedeliveryEvents(true);
            setIgnoreExchangeSendingEvents(true);
            setIgnoreStepEvents(true);
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelEvent.ExchangeSentEvent ese) {
                String uri = ese.getEndpoint().getEndpointUri();
                if (uri.startsWith("sql:") || uri.startsWith("jdbc:")) {
                    Exchange exchange = ese.getExchange();

                    // prefer the CamelSqlQuery header (runtime override) over the URI
                    String query = null;
                    Object headerQuery = exchange.getMessage().getHeader("CamelSqlQuery");
                    if (headerQuery != null) {
                        query = headerQuery.toString();
                    }
                    if (query == null) {
                        query = extractQuery(uri);
                    }
                    // resolve resource: references to actual SQL content
                    if (query != null && query.startsWith("resource:")) {
                        query = resolveResource(query.substring("resource:".length()));
                    }

                    JsonObject jo = new JsonObject();
                    jo.put("timestamp", event.getTimestamp());
                    jo.put("exchangeId", exchange.getExchangeId());
                    jo.put("routeId", exchange.getFromRouteId());
                    jo.put("endpoint", uri);
                    if (query != null) {
                        jo.put("query", query);
                        jo.put("category", detectCategory(query));
                    } else {
                        jo.put("query", uri);
                        jo.put("category", "OTHER");
                    }
                    jo.put("duration", ese.getTimeTaken());
                    jo.put("failed", exchange.isFailed());

                    // row/update counts from sql and jdbc component headers
                    Object rc = exchange.getMessage().getHeader("CamelSqlRowCount");
                    if (rc == null) {
                        rc = exchange.getMessage().getHeader("CamelJdbcRowCount");
                    }
                    if (rc instanceof Number) {
                        jo.put("rowCount", ((Number) rc).intValue());
                    }
                    Object uc = exchange.getMessage().getHeader("CamelSqlUpdateCount");
                    if (uc == null) {
                        uc = exchange.getMessage().getHeader("CamelJdbcUpdateCount");
                    }
                    if (uc instanceof Number) {
                        jo.put("updateCount", ((Number) uc).intValue());
                    }

                    int p = pos.getAndUpdate(operand -> ++operand % capacity);
                    events[p] = jo;
                }
            }
        }
    }
}
