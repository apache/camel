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
import java.util.List;
import java.util.Locale;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
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

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class KafkaTab extends AbstractTableTab {

    KafkaTab(MonitorContext ctx) {
        super(ctx, "route", "state", "group", "topic");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.kafkaConsumers.size() : 0;
    }

    @Override
    public void onTabSelected() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && !info.kafkaConsumers.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<KafkaConsumerInfo> sorted = new ArrayList<>(info.kafkaConsumers);
        sorted.sort(this::sortKafka);

        List<Row> rows = new ArrayList<>();
        for (KafkaConsumerInfo ki : sorted) {
            String state = ki.state != null ? ki.state : "";
            Style stateStyle = switch (state.toLowerCase(Locale.ROOT)) {
                case "running" -> Theme.success();
                case "paused", "resume_requested" -> Theme.warning();
                default -> state.isEmpty() ? Style.EMPTY : Theme.error();
            };
            String displayState = capitalize(state);
            String topic = ki.lastTopic != null ? ki.lastTopic : "";
            String partition = ki.lastTopic != null ? String.valueOf(ki.lastPartition) : "";
            String offset = ki.lastTopic != null ? String.valueOf(ki.lastOffset) : "";
            String error = ki.lastError != null ? ki.lastError : "";
            String uri = ki.uri != null ? ki.uri : "";

            rows.add(Row.from(
                    Cell.from(Span.styled(ki.routeId != null ? ki.routeId : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(displayState, stateStyle)),
                    Cell.from(ki.groupId != null ? ki.groupId : ""),
                    Cell.from(topic),
                    rightCell(partition, 9),
                    rightCell(offset, 10),
                    Cell.from(Span.styled(error, error.isEmpty() ? Style.EMPTY : Theme.error())),
                    Cell.from(Span.styled(uri, Style.EMPTY.dim()))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No Kafka consumers", 8));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "state"), sortStyle("state"))),
                        Cell.from(Span.styled(sortLabel("GROUP-ID", "group"), sortStyle("group"))),
                        Cell.from(Span.styled(sortLabel("TOPIC", "topic"), sortStyle("topic"))),
                        rightCell("PARTITION", 9, Style.EMPTY.bold()),
                        rightCell("OFFSET", 10, Style.EMPTY.bold()),
                        Cell.from(Span.styled("ERROR", Style.EMPTY.bold())),
                        Cell.from(Span.styled("ENDPOINT", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(12),
                        Constraint.length(20),
                        Constraint.length(20),
                        Constraint.length(9),
                        Constraint.length(10),
                        Constraint.length(30),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Kafka ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private int sortKafka(KafkaConsumerInfo a, KafkaConsumerInfo b) {
        int result = switch (sort) {
            case "state" -> compareStr(a.state, b.state);
            case "group" -> compareStr(a.groupId, b.groupId);
            case "topic" -> compareStr(a.lastTopic, b.lastTopic);
            default -> compareStr(a.routeId, b.routeId);
        };
        return sortReversed ? -result : result;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.kafkaConsumers.isEmpty()) {
            return null;
        }
        List<KafkaConsumerInfo> sorted = new ArrayList<>(info.kafkaConsumers);
        sorted.sort(this::sortKafka);
        List<String> items = sorted.stream()
                .map(ki -> (ki.routeId != null ? ki.routeId : "") + " " + (ki.groupId != null ? ki.groupId : ""))
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Kafka");
    }

    @Override
    public String description() {
        return "Apache Kafka consumer status (group, topic, partition, offset)";
    }

    @Override
    public String getHelpText() {
        return """
                # Kafka

                Displays live status for all Kafka consumers in the selected integration.
                Data comes from the Kafka dev console built into `camel-kafka`, which
                tracks each consumer worker thread's group membership, current position,
                and health state.

                ## Table Columns

                - **ROUTE** — Route ID that owns this Kafka consumer
                - **STATUS** — Worker state: `Running` (green, actively polling),
                  `Paused` (yellow, consumer paused), or error states (red)
                - **GROUP-ID** — Kafka consumer group ID this consumer belongs to
                - **TOPIC** — Topic of the last consumed record
                - **PARTITION** — Partition number of the last consumed record
                - **OFFSET** — Offset of the last consumed record
                - **ERROR** — Last error message if the consumer is unhealthy
                - **ENDPOINT** — Full Camel endpoint URI for this consumer

                ## What to Look For

                - **All consumers Running**: Normal operation. Offsets should be
                  advancing steadily if messages are flowing.
                - **Consumer Paused**: The consumer has been programmatically paused
                  (e.g., by a route policy or manual suspension).
                - **Error column populated**: The consumer worker is unhealthy.
                  Check the error message for connection issues, authentication
                  failures, or deserialization errors.
                - **Offset not advancing**: If messages are being produced but the
                  offset stays the same, the consumer may be stuck or the topic
                  may have no new messages on that partition.

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
        result.put("tab", "Kafka");
        JsonArray rows = new JsonArray();
        List<KafkaConsumerInfo> sorted = new ArrayList<>(info.kafkaConsumers);
        sorted.sort(this::sortKafka);
        for (KafkaConsumerInfo ki : sorted) {
            JsonObject row = new JsonObject();
            row.put("routeId", ki.routeId);
            row.put("state", ki.state);
            row.put("groupId", ki.groupId);
            row.put("lastTopic", ki.lastTopic);
            row.put("lastPartition", ki.lastPartition);
            row.put("lastOffset", ki.lastOffset);
            if (ki.lastError != null) {
                row.put("lastError", ki.lastError);
            }
            row.put("uri", ki.uri);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.kafkaConsumers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
