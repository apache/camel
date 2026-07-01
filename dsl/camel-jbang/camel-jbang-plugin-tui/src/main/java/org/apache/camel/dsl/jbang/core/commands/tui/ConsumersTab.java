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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ConsumersTab extends AbstractTableTab {

    ConsumersTab(MonitorContext ctx) {
        super(ctx, "id", "status", "type", "inflight", "polls", "uri");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.consumers.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);

        List<Row> rows = new ArrayList<>();
        for (ConsumerInfo ci : sorted) {
            String status = consumerStatus(ci);
            HealthCheckInfo hc = consumerHealthCheck(info, ci);
            boolean healthDown = hc != null && "DOWN".equals(hc.state);
            Style statusStyle = healthDown
                    ? Style.EMPTY.fg(Color.LIGHT_RED)
                    : ("Started".equals(ci.state) || "Polling".equals(status)
                            ? Style.EMPTY.fg(Color.GREEN)
                            : Style.EMPTY.fg(Color.LIGHT_RED));
            String statusText = healthDown ? "⚠ " + status : status;
            String type = consumerType(ci);
            String schedule = consumerSchedule(ci);
            String sinceLast = consumerSinceLast(ci);
            String uri = healthDown && hc.message != null
                    ? hc.message
                    : (ci.uri != null ? ci.uri : "");

            rows.add(Row.from(
                    Cell.from(Span.styled(ci.id != null ? ci.id : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(statusText, statusStyle)),
                    Cell.from(type),
                    rightCell(String.valueOf(ci.inflight), 8),
                    rightCell(ci.totalCounter != null ? String.valueOf(ci.totalCounter) : "", 8),
                    Cell.from(schedule),
                    Cell.from(sinceLast),
                    Cell.from(Span.styled(uri, healthDown ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No consumers", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("ROUTE", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        rightCell(sortLabel("INFLIGHT", "inflight"), 8, sortStyle("inflight")),
                        rightCell(sortLabel("POLLS", "polls"), 8, sortStyle("polls")),
                        Cell.from(Span.styled("SCHEDULE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(16),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(22),
                        Constraint.length(22),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Consumers sort:" + sort + " ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, lastTableArea, tableState, tableScrollState, sorted.size());
    }

    private int sortConsumer(ConsumerInfo a, ConsumerInfo b) {
        int result = switch (sort) {
            case "status" -> {
                String sa = consumerStatus(a);
                String sb = consumerStatus(b);
                yield sa.compareToIgnoreCase(sb);
            }
            case "type" -> {
                String ta = consumerType(a);
                String tb = consumerType(b);
                yield ta.compareToIgnoreCase(tb);
            }
            case "inflight" -> Integer.compare(b.inflight, a.inflight);
            case "polls" -> {
                long la = a.totalCounter != null ? a.totalCounter : 0;
                long lb = b.totalCounter != null ? b.totalCounter : 0;
                yield Long.compare(lb, la);
            }
            case "uri" -> {
                String ua = a.uri != null ? a.uri : "";
                String ub = b.uri != null ? b.uri : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "id"
                String ia = a.id != null ? a.id : "";
                String ib = b.id != null ? b.id : "";
                yield ia.compareToIgnoreCase(ib);
            }
        };
        return sortReversed ? -result : result;
    }

    private static String consumerStatus(ConsumerInfo ci) {
        if (ci.polling != null && ci.polling) {
            return "Polling";
        }
        return ci.state != null ? ci.state : "";
    }

    private static String consumerType(ConsumerInfo ci) {
        if (ci.className == null) {
            return "";
        }
        String s = ci.className;
        if (s.endsWith("Consumer")) {
            s = s.substring(0, s.length() - 8);
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static HealthCheckInfo consumerHealthCheck(IntegrationInfo info, ConsumerInfo ci) {
        if (ci.id == null) {
            return null;
        }
        String hcId = "consumer:" + ci.id;
        for (HealthCheckInfo hc : info.healthChecks) {
            if (hcId.equals(hc.name)) {
                return hc;
            }
        }
        return null;
    }

    static String consumerSchedule(ConsumerInfo ci) {
        // Try to extract cron expression from URI for cron/quartz components
        String cron = extractCronFromUri(ci.uri);
        if (cron != null) {
            return cron;
        }
        // Fall back to period/delay for timer and poll consumers
        if (ci.period != null && ci.period > 0) {
            return "every " + humanDuration(ci.period);
        } else if (ci.delay != null && ci.delay > 0) {
            return "every " + humanDuration(ci.delay);
        }
        return "";
    }

    private static String extractCronFromUri(String uri) {
        if (uri == null) {
            return null;
        }
        // cron:name?schedule=0/5+*+*+*+*+?
        if (uri.startsWith("cron:")) {
            String expr = extractParam(uri, "schedule");
            if (expr != null) {
                return decodeCron(expr);
            }
        }
        // quartz:group/name?cron=0/5+*+*+*+*+?
        if (uri.startsWith("quartz:")) {
            String expr = extractParam(uri, "cron");
            if (expr != null) {
                return decodeCron(expr);
            }
            String trigger = extractParam(uri, "trigger.repeatInterval");
            if (trigger != null) {
                try {
                    return "every " + humanDuration(Long.parseLong(trigger));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return null;
    }

    private static String extractParam(String uri, String param) {
        int q = uri.indexOf('?');
        if (q < 0) {
            return null;
        }
        String query = uri.substring(q + 1);
        for (String part : query.split("&")) {
            if (part.startsWith(param + "=")) {
                return part.substring(param.length() + 1);
            }
        }
        return null;
    }

    private static String decodeCron(String encoded) {
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded.replace('+', ' ');
        }
    }

    private static String humanDuration(long ms) {
        if (ms >= 60000 && ms % 60000 == 0) {
            long min = ms / 60000;
            return min + (min == 1 ? "min" : "min");
        }
        if (ms >= 1000 && ms % 1000 == 0) {
            long sec = ms / 1000;
            return sec + "s";
        }
        return ms + "ms";
    }

    private static String consumerSinceLast(ConsumerInfo ci) {
        String s1 = ci.sinceLastStarted != null ? ci.sinceLastStarted : "-";
        String s2 = ci.sinceLastCompleted != null ? ci.sinceLastCompleted : "-";
        String s3 = ci.sinceLastFailed != null ? ci.sinceLastFailed : "-";
        return s1 + "/" + s2 + "/" + s3;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.consumers.isEmpty()) {
            return null;
        }
        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);
        List<String> items = sorted.stream().map(c -> c.id != null ? c.id : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Consumers");
    }

    @Override
    public String getHelpText() {
        return """
                # Consumers

                Consumers are the **input** side of a Camel route. They listen for or poll
                data from external systems (message brokers, file directories, databases,
                timers, etc.) and create exchanges that flow through the route.

                There are two types of consumers:

                - **Event-driven** (e.g., `seda`, `direct`, `platform-http`): React instantly
                  when a message arrives. No polling — messages are pushed to the consumer.
                - **Polling** (e.g., `file`, `ftp`, `sql`, `timer`): Run on a scheduler that
                  periodically checks for new data. The POLLS column tracks these cycles.

                ## Table Columns

                - **ROUTE** — The route this consumer belongs to. Each route has exactly one consumer (its `from` endpoint)
                - **STATUS** — Consumer state: `Started` (running normally), `Polling` (currently in a poll cycle). Shows a health warning icon if the consumer health check is `DOWN`
                - **TYPE** — The Camel component type (e.g., `kafka`, `file`, `timer`, `cron`, `seda`)
                - **POLLS** — Total number of poll cycles the scheduler has executed. This counts poll **attempts**, not messages received — a poll may check for new files and find none. For event-driven consumers (like `seda`), this column is empty
                - **SCHEDULE** — How often the consumer polls: a fixed interval like `1s` or `5000ms`, or a cron expression like `0 0/5 * * *`. Only shown for polling consumers
                - **SINCE-LAST** — Three timestamps showing **started** / **completed** / **failed**: when the last exchange was created, when the last exchange completed successfully, and when the last exchange failed. Helps identify stale consumers or recurring failures
                - **URI** — The full endpoint URI (e.g., `timer://hello?period=2000`). If the consumer health check is DOWN, the failure message is shown here instead

                ## Example Screen

                ```
                 ROUTE           STATUS   TYPE     POLLS  SCHEDULE  SINCE-LAST        URI
                 timer-to-log    Started  timer    17     2s        1s/1s/-           timer://hello?period=2000
                 timer-to-seda   Started  timer    12     3s        0s/0s/-           timer://pump?period=3000
                 seda-consumer   Started  seda                      0s/0s/-           seda://queue
                ```

                Notice that `seda-consumer` has no POLLS or SCHEDULE because it is
                event-driven — it reacts immediately when a message is put into the
                SEDA queue by another route.

                ## Understanding POLLS vs TOTAL

                A common confusion: POLLS counts how many times the scheduler fired,
                not how many messages were received. Consider a file consumer polling
                every 5 seconds:

                - After 1 minute: POLLS = 12 (polled 12 times)
                - But TOTAL might be 3 (only 3 files were found and processed)
                - The other 9 polls found no new files

                A high POLLS count with zero TOTAL exchanges means the consumer is
                actively checking but finding no new data — which is normal for many
                use cases (e.g., watching a directory for new files).

                ## Backoff

                Some consumers support backoff — reducing poll frequency when polls
                return no data. This saves resources when the source is idle. The
                consumer automatically returns to normal frequency when data appears.

                ## Keys

                - `Up/Down` — select consumer
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Consumers");
        JsonArray rows = new JsonArray();
        for (ConsumerInfo ci : info.consumers) {
            JsonObject row = new JsonObject();
            row.put("id", ci.id);
            row.put("uri", ci.uri);
            row.put("state", ci.state);
            row.put("className", ci.className);
            row.put("scheduled", ci.scheduled);
            row.put("inflight", ci.inflight);
            if (ci.totalCounter != null) {
                row.put("totalCounter", ci.totalCounter);
            }
            if (ci.polling != null) {
                row.put("polling", ci.polling);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.consumers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
