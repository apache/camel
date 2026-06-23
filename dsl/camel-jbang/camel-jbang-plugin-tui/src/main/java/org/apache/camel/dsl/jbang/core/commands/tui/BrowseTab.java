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

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class BrowseTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "uri", "size" };
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int VIEW_ENDPOINTS = 0;
    private static final int VIEW_MESSAGES = 1;
    private static final int VIEW_DETAIL = 2;

    private final MonitorContext ctx;
    private final TableState endpointTableState = new TableState();
    private final TableState messageTableState = new TableState();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private String sort = "uri";
    private int sortIndex;
    private boolean sortReversed;
    private int view = VIEW_ENDPOINTS;
    private int detailScroll;
    private boolean prettyPrint;

    private List<EndpointData> allEndpoints = Collections.emptyList();
    private EndpointData selectedEndpoint;
    private List<MessageData> messages = Collections.emptyList();
    private String lastPid;

    BrowseTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allEndpoints = Collections.emptyList();
            view = VIEW_ENDPOINTS;
        }
        if (allEndpoints.isEmpty()) {
            loadEndpoints();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allEndpoints = Collections.emptyList();
        messages = Collections.emptyList();
        selectedEndpoint = null;
        view = VIEW_ENDPOINTS;
        detailScroll = 0;
        lastPid = null;
        loadEndpoints();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (view == VIEW_DETAIL) {
            if (ke.isUp()) {
                detailScroll = Math.max(0, detailScroll - 1);
                return true;
            }
            if (ke.isDown()) {
                detailScroll++;
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                detailScroll = Math.max(0, detailScroll - 20);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                detailScroll += 20;
                return true;
            }
            if (ke.isChar('p')) {
                prettyPrint = !prettyPrint;
                return true;
            }
            return false;
        }

        if (view == VIEW_MESSAGES) {
            if (ke.isConfirm()) {
                view = VIEW_DETAIL;
                detailScroll = 0;
                return true;
            }
            if (ke.isCharIgnoreCase('r')) {
                if (selectedEndpoint != null) {
                    loadMessages(selectedEndpoint.uri);
                }
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                for (int i = 0; i < 20 && messageTableState.selected() != null && messageTableState.selected() > 0; i++) {
                    messageTableState.selectPrevious();
                }
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                for (int i = 0; i < 20; i++) {
                    messageTableState.selectNext(messages.size());
                }
                return true;
            }
            return false;
        }

        // VIEW_ENDPOINTS
        if (ke.isConfirm()) {
            Integer sel = endpointTableState.selected();
            List<EndpointData> sorted = sortedEndpoints();
            if (sel != null && sel >= 0 && sel < sorted.size()) {
                selectedEndpoint = sorted.get(sel);
                messageTableState.select(0);
                loadMessages(selectedEndpoint.uri);
                view = VIEW_MESSAGES;
            }
            return true;
        }
        if (ke.isChar('s')) {
            sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
            sort = SORT_COLUMNS[sortIndex];
            sortReversed = false;
            return true;
        }
        if (ke.isChar('S')) {
            sortReversed = !sortReversed;
            return true;
        }
        if (ke.isCharIgnoreCase('r')) {
            loadEndpoints();
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 20 && endpointTableState.selected() != null && endpointTableState.selected() > 0; i++) {
                endpointTableState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            List<EndpointData> sorted = sortedEndpoints();
            for (int i = 0; i < 20; i++) {
                endpointTableState.selectNext(sorted.size());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (view == VIEW_DETAIL) {
            view = VIEW_MESSAGES;
            return true;
        }
        if (view == VIEW_MESSAGES) {
            view = VIEW_ENDPOINTS;
            messages = Collections.emptyList();
            selectedEndpoint = null;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (view == VIEW_ENDPOINTS) {
            endpointTableState.selectPrevious();
        } else if (view == VIEW_MESSAGES) {
            messageTableState.selectPrevious();
        }
    }

    @Override
    public void navigateDown() {
        if (view == VIEW_ENDPOINTS) {
            List<EndpointData> sorted = sortedEndpoints();
            endpointTableState.selectNext(sorted.size());
        } else if (view == VIEW_MESSAGES) {
            messageTableState.selectNext(messages.size());
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && allEndpoints.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading browse endpoints...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Browse ").build())
                            .build(),
                    area);
            return;
        }

        switch (view) {
            case VIEW_MESSAGES -> renderMessages(frame, area);
            case VIEW_DETAIL -> renderDetail(frame, area);
            default -> renderEndpoints(frame, area);
        }
    }

    private void renderEndpoints(Frame frame, Rect area) {
        List<EndpointData> sorted = sortedEndpoints();
        List<Row> rows = new ArrayList<>();
        for (EndpointData ep : sorted) {
            String first = ep.firstTimestamp > 0 ? formatTimestamp(ep.firstTimestamp) : "";
            String last = ep.lastTimestamp > 0 ? formatTimestamp(ep.lastTimestamp) : "";
            rows.add(Row.from(
                    Cell.from(Span.styled(ep.uri != null ? ep.uri : "", Style.EMPTY.fg(Color.CYAN))),
                    rightCell(String.valueOf(ep.queueSize), 8),
                    Cell.from(first),
                    Cell.from(last)));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No browsable endpoints", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from("")));
        }

        String title = String.format(" Browse [%d] sort:%s ", sorted.size(), sort);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri"))),
                        rightCell(sortLabel("SIZE", "size"), 8, sortStyle("size")),
                        Cell.from(Span.styled("FIRST", Style.EMPTY.bold())),
                        Cell.from(Span.styled("LAST", Style.EMPTY.bold()))))
                .widths(
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(10))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, endpointTableState);
    }

    private void renderMessages(Frame frame, Rect area) {
        if (loading.get() && messages.isEmpty()) {
            String title = " " + (selectedEndpoint != null ? selectedEndpoint.uri : "Messages") + " ";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading messages...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                            .build(),
                    area);
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (MessageData msg : messages) {
            String bodyPreview = msg.body != null ? TuiHelper.truncate(msg.body.replace('\n', ' '), 60) : "";
            String ts = msg.timestamp > 0 ? formatTimestamp(msg.timestamp) : "";
            rows.add(Row.from(
                    rightCell(String.valueOf(msg.position), 5),
                    Cell.from(Span.styled(msg.exchangeId != null ? msg.exchangeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(ts),
                    Cell.from(Span.styled(bodyPreview, Style.EMPTY.dim()))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("No messages", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from("")));
        }

        String uri = selectedEndpoint != null ? selectedEndpoint.uri : "";
        String title = String.format(" %s [%d messages] ", TuiHelper.truncate(uri, 40), messages.size());

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 5, Style.EMPTY.bold()),
                        Cell.from(Span.styled("EXCHANGE ID", Style.EMPTY.bold())),
                        Cell.from(Span.styled("TIME", Style.EMPTY.bold())),
                        Cell.from(Span.styled("BODY", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(5),
                        Constraint.length(40),
                        Constraint.length(10),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, messageTableState);
    }

    private void renderDetail(Frame frame, Rect area) {
        Integer sel = messageTableState.selected();
        if (sel == null || sel < 0 || sel >= messages.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a message", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Message ").build())
                            .build(),
                    area);
            return;
        }

        MessageData msg = messages.get(sel);

        // Split: message list (40%) + detail (fill)
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(35), Constraint.fill())
                .split(area);
        renderMessages(frame, chunks.get(0));

        String title = " Message " + msg.position + " [" + (msg.exchangeId != null ? msg.exchangeId : "") + "] ";

        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(
                Span.styled("  Exchange ID: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(msg.exchangeId != null ? msg.exchangeId : "", Style.EMPTY.fg(Color.WHITE))));
        if (msg.exchangePattern != null) {
            lines.add(Line.from(
                    Span.styled("  Pattern:     ", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled(msg.exchangePattern, Style.EMPTY.fg(Color.WHITE))));
        }
        lines.add(Line.from(Span.raw("")));

        // Headers
        if (msg.headers != null && !msg.headers.isEmpty()) {
            lines.add(Line.from(Span.styled("  Headers:", Style.EMPTY.fg(Color.YELLOW).bold())));
            for (Map.Entry<String, String> entry : msg.headers.entrySet()) {
                lines.add(Line.from(
                        Span.styled("    " + entry.getKey(), Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(" = ", Style.EMPTY.dim()),
                        Span.styled(entry.getValue(), Style.EMPTY.fg(Color.WHITE))));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Body
        lines.add(Line.from(Span.styled("  Body:", Style.EMPTY.fg(Color.YELLOW).bold())));
        if (msg.body != null && !msg.body.isEmpty()) {
            String bodyText = msg.body;
            if (prettyPrint) {
                try {
                    bodyText = CamelCommandHelper.valueAsStringPretty(bodyText, false);
                } catch (Exception e) {
                    // use raw body
                }
            }
            for (String line : bodyText.split("\n", -1)) {
                lines.add(Line.from(Span.styled("    " + line, Style.EMPTY.fg(Color.WHITE))));
            }
        } else {
            lines.add(Line.from(Span.styled("    (empty)", Style.EMPTY.dim())));
        }

        // Scrolling
        int visibleLines = chunks.get(1).height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        detailScroll = Math.min(detailScroll, maxScroll);

        int end = Math.min(detailScroll + visibleLines, lines.size());
        List<Line> visible = lines.subList(detailScroll, end);

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(visible))
                        .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                        .build(),
                chunks.get(1));
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        if (view == VIEW_DETAIL) {
            hint(spans, "p", "pretty" + (prettyPrint ? " [on]" : ""));
            hintLast(spans, "↑↓", "scroll");
        } else if (view == VIEW_MESSAGES) {
            hint(spans, "r", "refresh");
            hintLast(spans, "Enter", "detail");
        } else {
            hint(spans, "s", "sort");
            hint(spans, "r", "refresh");
            hintLast(spans, "Enter", "browse");
        }
    }

    @Override
    public SelectionContext getSelectionContext() {
        if (view == VIEW_MESSAGES) {
            if (messages.isEmpty()) {
                return null;
            }
            List<String> items = messages.stream()
                    .map(m -> m.exchangeId != null ? m.exchangeId : "").toList();
            Integer sel = messageTableState.selected();
            return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Messages");
        }
        List<EndpointData> sorted = sortedEndpoints();
        if (sorted.isEmpty()) {
            return null;
        }
        List<String> items = sorted.stream().map(e -> e.uri != null ? e.uri : "").toList();
        Integer sel = endpointTableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Browse");
    }

    private List<EndpointData> sortedEndpoints() {
        List<EndpointData> result = new ArrayList<>(allEndpoints);
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "size" -> Integer.compare(b.queueSize, a.queueSize);
                default -> compareStr(a.uri, b.uri);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private static int compareStr(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private static String formatTimestamp(long ts) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(TIME_FMT);
    }

    // ---- Data loading ----

    private void loadEndpoints() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                loadEndpointsInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadEndpointsInBackground(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "browse");
        root.put("filter", "*");
        root.put("limit", 100);
        root.put("dump", false);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            return;
        }

        JsonArray arr = jo.getCollection("browse");
        if (arr == null) {
            return;
        }

        List<EndpointData> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            EndpointData ep = new EndpointData();
            ep.uri = o.getString("endpointUri");
            ep.queueSize = o.getIntegerOrDefault("queueSize", 0);
            ep.firstTimestamp = o.getLongOrDefault("firstTimestamp", 0);
            ep.lastTimestamp = o.getLongOrDefault("lastTimestamp", 0);
            result.add(ep);
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allEndpoints = result;
                lastPid = pid;
            });
        }
    }

    private void loadMessages(String endpointUri) {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                loadMessagesInBackground(pid, endpointUri);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadMessagesInBackground(String pid, String endpointUri) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "browse");
        root.put("filter", endpointUri);
        root.put("limit", 100);
        root.put("dump", true);
        root.put("includeBody", true);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            return;
        }

        JsonArray arr = jo.getCollection("browse");
        if (arr == null || arr.isEmpty()) {
            return;
        }

        JsonObject epObj = (JsonObject) arr.get(0);
        JsonArray msgArr = epObj.getCollection("messages");
        int position = epObj.getIntegerOrDefault("position", 0);

        List<MessageData> result = new ArrayList<>();
        if (msgArr != null) {
            for (int i = 0; i < msgArr.size(); i++) {
                JsonObject mjo = (JsonObject) msgArr.get(i);
                JsonObject message = mjo.getMap("message");
                if (message == null) {
                    continue;
                }
                MessageData md = new MessageData();
                md.position = position + i + 1;
                md.exchangeId = mjo.getString("exchangeId");
                if (md.exchangeId == null) {
                    md.exchangeId = message.getString("exchangeId");
                }
                md.exchangePattern = message.getString("exchangePattern");

                // Timestamp from headers
                JsonObject headers = message.getMap("headers");
                if (headers != null) {
                    md.headers = new LinkedHashMap<>();
                    for (String key : headers.keySet()) {
                        Object val = headers.get(key);
                        md.headers.put(key, val != null ? val.toString() : "null");
                    }
                    Object tsObj = headers.get("CamelMessageTimestamp");
                    if (tsObj instanceof Number) {
                        md.timestamp = ((Number) tsObj).longValue();
                    }
                }

                Object bodyObj = message.get("body");
                md.body = bodyObj != null ? bodyObj.toString() : null;

                result.add(md);
            }
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                messages = result;
            });
        }
    }

    static class EndpointData {
        String uri;
        int queueSize;
        long firstTimestamp;
        long lastTimestamp;
    }

    static class MessageData {
        int position;
        String exchangeId;
        String exchangePattern;
        Map<String, String> headers;
        String body;
        long timestamp;
    }

    @Override
    public String getHelpText() {
        return """
                # Browse

                The Browse tab lets you inspect messages currently queued in browsable
                endpoints. Browsable endpoints include `seda`, `browse`, and `stub` —
                these are in-memory queues where messages wait to be consumed.

                This is useful for debugging message flow: you can see what messages
                are waiting in a queue without consuming them (unlike a consumer
                which removes messages from the queue).

                ## Endpoint List

                The left panel shows all browsable endpoints with their current
                queue sizes. Select an endpoint to see its queued messages.

                ## Example Screen

                ```
                 Endpoint          Queue Size
                 seda://queue      5
                 seda://orders     12
                 browse://audit    3
                ```

                ## Message View

                When you select an endpoint, the right panel shows each queued message:

                - **Exchange ID** — Unique identifier for this exchange
                - **Exchange Pattern** — `InOnly` (fire-and-forget) or `InOut` (request-reply)
                - **Headers** — Message headers as key-value pairs (e.g., `Content-Type: application/json`, `CamelFileName: order.xml`)
                - **Body** — The message body content. This is the actual data being processed — could be JSON, XML, plain text, or binary

                ## When To Use

                - **Debugging routing**: If messages are not reaching their destination, check if they are accumulating in a SEDA queue
                - **Inspecting content**: Verify that message transformations (marshal, unmarshal, setBody) are producing the expected output
                - **Testing**: When using `stub` endpoints for testing, browse to verify messages were sent correctly
                - **Monitoring backlog**: A growing queue size may indicate that consumers cannot keep up with producers

                ## Keys

                - `Up/Down` — navigate endpoints and messages
                - `Enter` — browse selected endpoint
                - `Esc` — back to endpoint list
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (selectedEndpoint != null && !messages.isEmpty()) {
            JsonObject result = new JsonObject();
            result.put("tab", "Browse Messages");
            result.put("endpoint", selectedEndpoint.uri);
            JsonArray rows = new JsonArray();
            for (MessageData m : messages) {
                JsonObject row = new JsonObject();
                row.put("position", m.position);
                row.put("exchangeId", m.exchangeId);
                row.put("exchangePattern", m.exchangePattern);
                row.put("timestamp", m.timestamp);
                if (m.body != null) {
                    row.put("body", m.body);
                }
                if (m.headers != null && !m.headers.isEmpty()) {
                    row.put("headers", new JsonObject(m.headers));
                }
                rows.add(row);
            }
            result.put("rows", rows);
            result.put("totalRows", messages.size());
            Integer sel = messageTableState.selected();
            result.put("selectedIndex", sel != null ? sel : -1);
            return result;
        }
        List<EndpointData> endpoints = sortedEndpoints();
        if (endpoints.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Browse");
        JsonArray rows = new JsonArray();
        for (EndpointData e : endpoints) {
            JsonObject row = new JsonObject();
            row.put("uri", e.uri);
            row.put("queueSize", e.queueSize);
            row.put("firstTimestamp", e.firstTimestamp);
            row.put("lastTimestamp", e.lastTimestamp);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", endpoints.size());
        Integer sel = endpointTableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
