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
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "sql-trace", displayName = "SQL Trace", description = "Trace SQL query executions")
@Configurer(extended = true)
public class SqlTraceDevConsole extends AbstractDevConsole {

    @Metadata(defaultValue = "200",
              description = "Maximum capacity of traced SQL statements (capacity must be between 25 and 1000)")
    private int capacity = 200;

    private StatementEntry[] events;
    private final AtomicInteger pos = new AtomicInteger();
    private final ConsoleEventNotifier listener = new ConsoleEventNotifier();

    public record StatementEntry(
            @Metadata(description = "Epoch time in milliseconds when the statement was executed") long timestamp,
            @Metadata(description = "The exchange ID") String exchangeId,
            @Metadata(description = "The route ID (only present when known)") String routeId,
            @Metadata(description = "The processor node ID (only present when known)") String nodeId,
            @Metadata(description = "The source location of the processor (only present when known)") String location,
            @Metadata(description = "The endpoint URI") String endpoint,
            @Metadata(description = "The SQL query (or the endpoint URI when the query could not be determined)") String query,
            @Metadata(description = "The SQL statement category") String category,
            @Metadata(description = "Duration in milliseconds") long duration,
            @Metadata(description = "Whether the exchange failed") boolean failed,
            @Metadata(description = "Number of rows returned (only present when known)") Integer rowCount,
            @Metadata(description = "Number of rows updated (only present when known)") Integer updateCount) {
    }

    public record Summary(
            @Metadata(description = "Total number of queries") long totalQueries,
            @Metadata(description = "Average duration in milliseconds") long avgTime,
            @Metadata(description = "Slowest duration in milliseconds") long slowestTime,
            @Metadata(description = "Number of queries considered slow (duration >= 100 ms)") long slowCount,
            @Metadata(description = "Number of failed queries") long failedCount,
            @Metadata(description = "Number of SELECT statements") long selectCount,
            @Metadata(description = "Number of INSERT statements") long insertCount,
            @Metadata(description = "Number of UPDATE statements") long updateCount,
            @Metadata(description = "Number of DELETE statements") long deleteCount) {
    }

    public record Response(
            @Metadata(description = "The traced SQL statements, most recent first (only present when statements have been traced)") List<StatementEntry> statements,
            @Metadata(description = "Summary statistics across the traced statements (only present when statements have been traced)") Summary summary) {
    }

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
        this.events = new StatementEntry[capacity];
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

        List<StatementEntry> list = collectEvents();
        for (StatementEntry e : list) {
            sb.append(String.format("    %s %s %s (%d ms) route:%s%n",
                    e.category(), e.query(), e.failed() ? "FAILED" : "OK", e.duration(), e.routeId()));
        }
        if (!list.isEmpty()) {
            sb.insert(0, String.format("Last %d SQL Statements:%n", list.size()));
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<StatementEntry> list = collectEvents();

        List<StatementEntry> statements = null;
        Summary summary = null;
        if (!list.isEmpty()) {
            statements = list;

            long total = list.size();
            long totalTime = 0;
            long slowest = 0;
            long slowCount = 0;
            long failedCount = 0;
            long selectCount = 0;
            long insertCount = 0;
            long updateCount = 0;
            long deleteCount = 0;

            for (StatementEntry e : list) {
                long duration = e.duration();
                totalTime += duration;
                if (duration > slowest) {
                    slowest = duration;
                }
                if (duration >= 100) {
                    slowCount++;
                }
                if (e.failed()) {
                    failedCount++;
                }
                switch (e.category()) {
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

            summary = new Summary(
                    total, total > 0 ? totalTime / total : 0, slowest, slowCount, failedCount, selectCount,
                    insertCount, updateCount, deleteCount);
        }

        Response response = new Response(statements, summary);
        return JsonRecordSupport.toJsonObject(response);
    }

    private List<StatementEntry> collectEvents() {
        List<StatementEntry> list = new ArrayList<>();
        int cursor = pos.get();
        // cursor points to the NEXT write slot, so walk backward from cursor-1
        for (int i = 0; i < capacity; i++) {
            cursor = (cursor - 1 + capacity) % capacity;
            StatementEntry event = events[cursor];
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

                    String nodeId = exchange.getExchangeExtension().getHistoryNodeId();
                    String location = exchange.getExchangeExtension().getHistoryNodeSource();
                    String finalQuery = query != null ? query : uri;
                    String category = query != null ? detectCategory(query) : "OTHER";

                    // row/update counts from sql and jdbc component headers
                    Object rc = exchange.getMessage().getHeader("CamelSqlRowCount");
                    if (rc == null) {
                        rc = exchange.getMessage().getHeader("CamelJdbcRowCount");
                    }
                    Integer rowCount = rc instanceof Number ? ((Number) rc).intValue() : null;
                    Object uc = exchange.getMessage().getHeader("CamelSqlUpdateCount");
                    if (uc == null) {
                        uc = exchange.getMessage().getHeader("CamelJdbcUpdateCount");
                    }
                    Integer updateCount = uc instanceof Number ? ((Number) uc).intValue() : null;

                    StatementEntry entry = new StatementEntry(
                            event.getTimestamp(), exchange.getExchangeId(), exchange.getFromRouteId(), nodeId, location,
                            uri, finalQuery, category, ese.getTimeTaken(), exchange.isFailed(), rowCount, updateCount);

                    int p = pos.getAndUpdate(operand -> ++operand % capacity);
                    events[p] = entry;
                }
            }
        }
    }
}
