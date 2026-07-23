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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class HttpTab extends AbstractTableTab {

    private static final int MOUSE_SCROLL_LINES = 3;
    private static final Set<String> OPENAPI_HTTP_VERBS
            = Set.of("get", "post", "put", "delete", "patch", "options", "head", "trace");

    // Probe field constants
    private static final int PROBE_METHOD = 0;
    private static final int PROBE_PATH = 1;
    private static final int PROBE_HEADERS = 2;
    private static final int PROBE_BODY = 3;
    private static final int PROBE_HISTORY = 4;

    private static final String[] PROBE_METHODS = { "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" };
    private static final int MAX_PROBE_HISTORY = 20;

    private final AtomicBoolean specLoading = new AtomicBoolean(false);

    private int filter;
    private boolean showManagement;

    private boolean showSpec;
    private List<String> specLines = Collections.emptyList();
    private String specTitle;
    private int specScroll;

    // Probe mode state
    private boolean probeMode;
    private String probeRouteId;
    private String probeBaseUrl;
    private int probeField = PROBE_PATH;
    private int probeMethodIndex;
    private final TextInputState probePathState = new TextInputState("");
    private final TextInputState probeBodyState = new TextInputState("");
    private List<FormHelper.HeaderEntry> probeHeaders;
    private int probeSelectedHeader;
    private boolean probeEditingHeaderKey;
    private final AtomicBoolean probeSending = new AtomicBoolean(false);
    private String probeResponseStatus;
    private long probeResponseElapsed;
    private List<String> probeResponseLines;
    private boolean probeResponseError;
    private int probeResponseScroll;
    private final List<ProbeHistoryEntry> probeHistory = new ArrayList<>();
    private int probeHistoryIndex;
    private boolean probePrettyPrint;
    private String probeResponseRawBody;
    private List<String> probeResponseHeaderLines;

    private int detailPanelHeight = 10;
    private final DragSplit vSplit = new DragSplit();

    HttpTab(MonitorContext ctx) {
        super(ctx, "method", "path", "total", "consumes", "produces", "source");
    }

    @Override
    protected int getRowCount() {
        return sortedVisibleEndpoints(ctx.findSelectedIntegration()).size();
    }

    boolean isProbeMode() {
        return probeMode;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (probeMode) {
            return handleProbeKeyEvent(ke);
        }

        if (showSpec) {
            if (ke.isChar('c') || ke.isCancel()) {
                showSpec = false;
            } else if (ke.isUp()) {
                specScroll = Math.max(0, specScroll - 1);
            } else if (ke.isDown()) {
                specScroll++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                specScroll = Math.max(0, specScroll - 20);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                specScroll += 20;
            } else if (ke.isHome()) {
                specScroll = 0;
            } else if (ke.isEnd()) {
                specScroll = Integer.MAX_VALUE;
            } else {
                return false;
            }
            return true;
        }

        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isConfirm()) {
            enterProbeModeFromTable();
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            filter = (filter + 1) % 3;
            return true;
        }
        if (ke.isCharIgnoreCase('m')) {
            showManagement = !showManagement;
            return true;
        }
        if (ke.isChar('c')) {
            loadSpecForSelectedEndpoint();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!probeMode && !showSpec && vSplit.handleMouse(me, me.y())) {
            if (vSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                detailPanelHeight = Math.max(3, Math.min(area.y() + area.height() - me.y(), area.height() - 5));
            }
            return true;
        }
        if (!probeMode && !showSpec) {
            List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
            if (handleTableClick(me, lastTableArea, tableState, visible.size())) {
                return true;
            }
        }
        if (showSpec) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                specScroll = Math.max(0, specScroll - MOUSE_SCROLL_LINES);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                specScroll += MOUSE_SCROLL_LINES;
                return true;
            }
        }
        if (probeMode) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                probeResponseScroll = Math.max(0, probeResponseScroll - MOUSE_SCROLL_LINES);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                probeResponseScroll += MOUSE_SCROLL_LINES;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (probeMode) {
            probeMode = false;
            return true;
        }
        if (showSpec) {
            showSpec = false;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (probeMode) {
            if (probeField == PROBE_HISTORY && !probeHistory.isEmpty()) {
                probeHistoryIndex = Math.max(0, probeHistoryIndex - 1);
            }
            return;
        }
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        if (probeMode) {
            if (probeField == PROBE_HISTORY && !probeHistory.isEmpty()) {
                probeHistoryIndex = Math.min(probeHistory.size() - 1, probeHistoryIndex + 1);
            }
            return;
        }
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        tableState.selectNext(visible.size());
    }

    @Override
    public void onIntegrationChanged() {
        showSpec = false;
        specLines = Collections.emptyList();
        specTitle = null;
        specScroll = 0;
        probeMode = false;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (probeMode) {
            renderProbe(frame, area);
            return;
        }

        if (showSpec) {
            renderSpec(frame, area);
            return;
        }

        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(info);

        detailPanelHeight = Math.max(3, Math.min(detailPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(detailPanelHeight))
                .split(area);

        renderTable(frame, chunks.get(0), visible, info);
        vSplit.setBorderPos(chunks.get(1).y());
        renderDetail(frame, chunks.get(1), visible);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (probeMode) {
            hint(spans, "Esc", "back");
            hint(spans, "Tab", "fields");
            hint(spans, "Enter", "send");
            hint(spans, "+", "header");
            hint(spans, "p", "pretty" + (probePrettyPrint ? " [on]" : ""));
            if (!probeHistory.isEmpty()) {
                hintLast(spans, TuiIcons.HINT_SCROLL, "history");
            }
            return;
        }
        if (showSpec) {
            hint(spans, "c/Esc", "close");
            hint(spans, TuiIcons.HINT_SCROLL, "scroll");
            hintLast(spans, "PgUp/PgDn", "page");
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, "Enter", "probe");
        hint(spans, "s", "sort");
        String[] filterLabels = { "all", "rest", "http" };
        hint(spans, "f", "filter [" + filterLabels[filter] + "]");
        hint(spans, "m", "management" + (showManagement ? " [on]" : " [off]"));
        List<HttpEndpointInfo> hVisible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer hSel = tableState.selected();
        if (hSel != null && hSel >= 0 && hSel < hVisible.size() && hVisible.get(hSel).specificationUri != null) {
            hintLast(spans, "c", "spec");
        }
    }

    void handlePaste(String text) {
        if (!probeMode || probeSending.get()) {
            return;
        }
        FormHelper.handlePaste(text, probeActiveTextInput());
    }

    // ---- Probe mode ----

    private void enterProbeModeFromTable() {
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            return;
        }
        enterProbeMode(visible.get(sel));
    }

    private void enterProbeMode(HttpEndpointInfo ep) {
        probeMode = true;
        probeField = PROBE_PATH;
        probeRouteId = ep.routeId;
        probeBaseUrl = extractBaseUrl(ep.url);

        // Pre-fill method
        probeMethodIndex = 0;
        if (ep.method != null) {
            String m = ep.method.split(",")[0].trim().toUpperCase(Locale.ENGLISH);
            for (int i = 0; i < PROBE_METHODS.length; i++) {
                if (PROBE_METHODS[i].equals(m)) {
                    probeMethodIndex = i;
                    break;
                }
            }
        }

        // Pre-fill path
        String path = ep.path != null ? ep.path : (ep.url != null ? ep.url : "/");
        probePathState.clear();
        for (int i = 0; i < path.length(); i++) {
            probePathState.insert(path.charAt(i));
        }

        // Pre-fill headers from endpoint metadata
        probeHeaders = null;
        if (ep.consumes != null && !ep.consumes.isEmpty()) {
            addProbeHeader("Content-Type", ep.consumes);
        }
        if (ep.produces != null && !ep.produces.isEmpty()) {
            addProbeHeader("Accept", ep.produces);
        }
        probeSelectedHeader = 0;
        probeEditingHeaderKey = true;

        // Clear body and response
        probeBodyState.clear();
        probeResponseStatus = null;
        probeResponseElapsed = 0;
        probeResponseLines = null;
        probeResponseHeaderLines = null;
        probeResponseRawBody = null;
        probeResponseError = false;
        probeResponseScroll = 0;
        probeHistoryIndex = 0;
    }

    private void addProbeHeader(String key, String value) {
        if (probeHeaders == null) {
            probeHeaders = new ArrayList<>();
        }
        TextInputState keyState = new TextInputState("");
        TextInputState valState = new TextInputState("");
        for (int i = 0; i < key.length(); i++) {
            keyState.insert(key.charAt(i));
        }
        for (int i = 0; i < value.length(); i++) {
            valState.insert(value.charAt(i));
        }
        probeHeaders.add(new FormHelper.HeaderEntry(keyState, valState));
    }

    private boolean handleProbeKeyEvent(KeyEvent ke) {
        if (probeSending.get()) {
            return true;
        }
        if (ke.isConfirm()) {
            if (probeField == PROBE_HISTORY && !probeHistory.isEmpty()) {
                replayHistoryEntry(probeHistory.get(probeHistoryIndex));
            } else {
                doProbeRequest();
            }
            return true;
        }

        // PgUp/PgDn scroll response
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            probeResponseScroll = Math.max(0, probeResponseScroll - 10);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            probeResponseScroll += 10;
            return true;
        }

        // Toggle pretty print for response body
        if (ke.isChar('p') && probeField != PROBE_PATH && probeField != PROBE_BODY
                && probeField != PROBE_HEADERS) {
            probePrettyPrint = !probePrettyPrint;
            if (probeResponseLines != null) {
                reformatResponseBody();
            }
            return true;
        }

        // Field-specific handling
        if (probeField == PROBE_METHOD) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                probeField = PROBE_PATH;
                return true;
            }
            if (ke.isUp()) {
                if (!probeHistory.isEmpty()) {
                    probeField = PROBE_HISTORY;
                } else {
                    probeField = PROBE_BODY;
                }
                return true;
            }
            if (ke.isLeft()) {
                probeMethodIndex = (probeMethodIndex - 1 + PROBE_METHODS.length) % PROBE_METHODS.length;
                return true;
            }
            if (ke.isRight()) {
                probeMethodIndex = (probeMethodIndex + 1) % PROBE_METHODS.length;
                return true;
            }
            return true;
        }
        if (probeField == PROBE_PATH) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                if (hasProbeHeaders()) {
                    probeField = PROBE_HEADERS;
                    probeSelectedHeader = 0;
                    probeEditingHeaderKey = true;
                } else {
                    probeField = PROBE_BODY;
                }
                return true;
            }
            if (ke.isUp()) {
                probeField = PROBE_METHOD;
                return true;
            }
            if (ke.isChar('+')) {
                addProbeHeaderEmpty();
                return true;
            }
            FormHelper.handleTextInput(ke, probePathState);
            return true;
        }
        if (probeField == PROBE_HEADERS) {
            return handleProbeHeaderKeyEvent(ke);
        }
        if (probeField == PROBE_BODY) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                if (!probeHistory.isEmpty()) {
                    probeField = PROBE_HISTORY;
                } else {
                    probeField = PROBE_METHOD;
                }
                return true;
            }
            if (ke.isUp()) {
                if (hasProbeHeaders()) {
                    probeField = PROBE_HEADERS;
                    probeSelectedHeader = probeHeaders.size() - 1;
                    probeEditingHeaderKey = false;
                } else {
                    probeField = PROBE_PATH;
                }
                return true;
            }
            if (ke.isChar('+')) {
                addProbeHeaderEmpty();
                return true;
            }
            FormHelper.handleTextInput(ke, probeBodyState);
            return true;
        }
        if (probeField == PROBE_HISTORY) {
            if (ke.isKey(KeyCode.TAB)) {
                probeField = PROBE_METHOD;
                return true;
            }
            if (ke.isUp()) {
                if (probeHistoryIndex > 0) {
                    probeHistoryIndex--;
                } else {
                    probeField = PROBE_BODY;
                }
                return true;
            }
            if (ke.isDown()) {
                if (probeHistoryIndex < probeHistory.size() - 1) {
                    probeHistoryIndex++;
                }
                return true;
            }
            return true;
        }
        return true;
    }

    private boolean handleProbeHeaderKeyEvent(KeyEvent ke) {
        FormHelper.HeaderEntry current = probeHeaders.get(probeSelectedHeader);
        TextInputState activeInput = probeEditingHeaderKey ? current.keyInput() : current.valueInput();

        if (ke.isChar('+')) {
            addProbeHeaderEmpty();
            return true;
        }
        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            if (probeEditingHeaderKey) {
                probeEditingHeaderKey = false;
            } else if (probeSelectedHeader < probeHeaders.size() - 1) {
                probeSelectedHeader++;
                probeEditingHeaderKey = true;
            } else {
                probeField = PROBE_BODY;
            }
            return true;
        }
        if (ke.isUp()) {
            if (probeEditingHeaderKey) {
                if (probeSelectedHeader > 0) {
                    probeSelectedHeader--;
                    probeEditingHeaderKey = false;
                } else {
                    probeField = PROBE_PATH;
                }
            } else {
                probeEditingHeaderKey = true;
            }
            return true;
        }
        if (ke.isDeleteBackward()) {
            if (probeEditingHeaderKey && current.keyInput().text().isEmpty()) {
                probeHeaders.remove(probeSelectedHeader);
                if (probeHeaders.isEmpty()) {
                    probeHeaders = null;
                    probeField = PROBE_PATH;
                } else if (probeSelectedHeader >= probeHeaders.size()) {
                    probeSelectedHeader = probeHeaders.size() - 1;
                }
                return true;
            }
            activeInput.deleteBackward();
            return true;
        }
        if (ke.isDeleteForward()) {
            activeInput.deleteForward();
            return true;
        }
        if (ke.isLeft()) {
            if (!probeEditingHeaderKey && activeInput.cursorPosition() == 0) {
                probeEditingHeaderKey = true;
            } else {
                activeInput.moveCursorLeft();
            }
            return true;
        }
        if (ke.isRight()) {
            if (probeEditingHeaderKey && activeInput.cursorPosition() == activeInput.text().length()) {
                probeEditingHeaderKey = false;
            } else {
                activeInput.moveCursorRight();
            }
            return true;
        }
        if (ke.isHome()) {
            activeInput.moveCursorToStart();
            return true;
        }
        if (ke.isEnd()) {
            activeInput.moveCursorToEnd();
            return true;
        }
        if (ke.code() == KeyCode.CHAR) {
            activeInput.insert(ke.string().charAt(0));
            return true;
        }
        return true;
    }

    private void addProbeHeaderEmpty() {
        if (probeHeaders == null) {
            probeHeaders = new ArrayList<>();
        }
        probeHeaders.add(new FormHelper.HeaderEntry(new TextInputState(""), new TextInputState("")));
        probeField = PROBE_HEADERS;
        probeSelectedHeader = probeHeaders.size() - 1;
        probeEditingHeaderKey = true;
    }

    private boolean hasProbeHeaders() {
        return probeHeaders != null && !probeHeaders.isEmpty();
    }

    private TextInputState probeActiveTextInput() {
        if (probeField == PROBE_PATH) {
            return probePathState;
        }
        if (probeField == PROBE_BODY) {
            return probeBodyState;
        }
        if (probeField == PROBE_HEADERS && hasProbeHeaders()) {
            FormHelper.HeaderEntry he = probeHeaders.get(probeSelectedHeader);
            return probeEditingHeaderKey ? he.keyInput() : he.valueInput();
        }
        return null;
    }

    private void replayHistoryEntry(ProbeHistoryEntry entry) {
        // Fill fields from history
        for (int i = 0; i < PROBE_METHODS.length; i++) {
            if (PROBE_METHODS[i].equals(entry.method)) {
                probeMethodIndex = i;
                break;
            }
        }
        probePathState.clear();
        for (int i = 0; i < entry.path.length(); i++) {
            probePathState.insert(entry.path.charAt(i));
        }
        probeBodyState.clear();
        if (entry.body != null) {
            for (int i = 0; i < entry.body.length(); i++) {
                probeBodyState.insert(entry.body.charAt(i));
            }
        }
        probeHeaders = null;
        if (entry.headers != null) {
            for (FormHelper.HeaderEntry he : entry.headers) {
                addProbeHeader(he.keyInput().text(), he.valueInput().text());
            }
        }
        probeField = PROBE_BODY;
        doProbeRequest();
    }

    private void doProbeRequest() {
        if (ctx.runner == null || probeBaseUrl == null) {
            return;
        }
        if (!probeSending.compareAndSet(false, true)) {
            return;
        }

        probeResponseStatus = "Sending...";
        probeResponseElapsed = 0;
        probeResponseLines = null;
        probeResponseError = false;
        probeResponseScroll = 0;

        String method = PROBE_METHODS[probeMethodIndex];
        String path = probePathState.text();
        String body = probeBodyState.text();
        if (body != null && body.startsWith("file:")) {
            try {
                body = Files.readString(Path.of(body.substring(5)));
            } catch (IOException e) {
                probeResponseStatus = "Error reading file: " + e.getMessage();
                probeResponseError = true;
                probeSending.set(false);
                return;
            }
        }
        String sendBody = body;
        String baseUrl = probeBaseUrl;

        // Snapshot headers
        List<FormHelper.HeaderEntry> headerSnapshot = null;
        if (hasProbeHeaders()) {
            headerSnapshot = new ArrayList<>();
            for (FormHelper.HeaderEntry he : probeHeaders) {
                headerSnapshot.add(new FormHelper.HeaderEntry(
                        new TextInputState(he.keyInput().text()),
                        new TextInputState(he.valueInput().text())));
            }
        }
        List<FormHelper.HeaderEntry> hdrs = headerSnapshot;

        ctx.runner.scheduler().execute(() -> {
            try {
                doProbeRequestInBackground(baseUrl, method, path, sendBody, hdrs);
            } finally {
                probeSending.set(false);
            }
        });
    }

    private void doProbeRequestInBackground(
            String baseUrl, String method, String path, String body, List<FormHelper.HeaderEntry> hdrs) {

        String url = baseUrl + path;
        String statusText;
        long elapsed = 0;
        boolean error = false;
        List<String> headerLines = new ArrayList<>();
        String rawBody = null;
        int httpStatus = 0;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            boolean hasBody = body != null && !body.isEmpty();
            HttpRequest.BodyPublisher bodyPublisher = hasBody
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .method(method, bodyPublisher);

            // Add user headers
            if (hdrs != null) {
                for (FormHelper.HeaderEntry he : hdrs) {
                    String k = he.keyInput().text().trim();
                    String v = he.valueInput().text();
                    if (!k.isEmpty()) {
                        reqBuilder.header(k, v);
                    }
                }
            }

            long start = System.currentTimeMillis();
            HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            elapsed = System.currentTimeMillis() - start;

            httpStatus = response.statusCode();
            statusText = String.valueOf(httpStatus);

            // Response headers
            for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
                String k = entry.getKey();
                if (k == null || k.startsWith(":")) {
                    continue;
                }
                for (String v : entry.getValue()) {
                    headerLines.add(k + ": " + v);
                }
            }

            // Response body
            String responseBody = response.body();
            if (responseBody != null && !responseBody.isEmpty()) {
                rawBody = responseBody;
            }

            if (httpStatus >= 400) {
                error = true;
            }
        } catch (Exception e) {
            statusText = "Error";
            error = true;
            String msg = e.getMessage();
            headerLines.add(msg != null ? msg : e.getClass().getSimpleName());
        }

        // Build history entry
        List<FormHelper.HeaderEntry> histHeaders = null;
        if (hdrs != null && !hdrs.isEmpty()) {
            histHeaders = new ArrayList<>(hdrs);
        }
        ProbeHistoryEntry histEntry = new ProbeHistoryEntry(
                method, path, histHeaders, body,
                httpStatus, elapsed, statusText, error);

        // Apply results on render thread
        String finalStatus = statusText;
        long finalElapsed = elapsed;
        boolean finalError = error;
        List<String> finalHeaderLines = headerLines;
        String finalRawBody = rawBody;

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                probeResponseStatus = finalStatus;
                probeResponseElapsed = finalElapsed;
                probeResponseError = finalError;
                probeResponseHeaderLines = finalHeaderLines;
                probeResponseRawBody = finalRawBody;
                probeResponseScroll = 0;
                rebuildResponseLines();

                // Add to history (most recent first)
                probeHistory.add(0, histEntry);
                if (probeHistory.size() > MAX_PROBE_HISTORY) {
                    probeHistory.remove(probeHistory.size() - 1);
                }
                probeHistoryIndex = 0;
            });
        }
    }

    private static String extractBaseUrl(String url) {
        if (url == null) {
            return "http://localhost:8080";
        }
        // Extract scheme + host + port from full URL like http://0.0.0.0:8080/api/hello
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if ("0.0.0.0".equals(host)) {
                host = "localhost";
            }
            int port = uri.getPort();
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            if (port > 0) {
                return scheme + "://" + host + ":" + port;
            }
            return scheme + "://" + host;
        } catch (Exception e) {
            return "http://localhost:8080";
        }
    }

    private void rebuildResponseLines() {
        List<String> lines = new ArrayList<>();
        if (probeResponseHeaderLines != null) {
            lines.addAll(probeResponseHeaderLines);
        }
        if (probeResponseRawBody != null && !probeResponseRawBody.isEmpty()) {
            lines.add("");
            String body = probeResponseRawBody;
            if (probePrettyPrint) {
                body = prettyFormat(body);
            }
            for (String line : body.split("\n", -1)) {
                lines.add(line);
            }
        }
        probeResponseLines = lines;
    }

    private void reformatResponseBody() {
        rebuildResponseLines();
        probeResponseScroll = 0;
    }

    private static String prettyFormat(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return CamelCommandHelper.valueAsStringPretty(text, false);
    }

    // ---- Probe rendering ----

    private void renderProbe(Frame frame, Rect area) {
        frame.renderWidget(Clear.INSTANCE, area);

        int padX = 2;
        int padY = 1;
        Rect inner = new Rect(
                area.left() + padX, area.top() + padY,
                area.width() - padX * 2, area.height() - padY * 2);

        int headerCount = hasProbeHeaders() ? probeHeaders.size() : 0;
        int requestHeight = 7 + headerCount + (headerCount > 0 ? 1 : 0);
        int historyHeight = Math.min(4 + 2, probeHistory.size() + 2);
        if (historyHeight < 3) {
            historyHeight = 3;
        }

        List<Rect> chunks = Layout.vertical()
                .constraints(
                        Constraint.length(requestHeight),
                        Constraint.fill(),
                        Constraint.length(historyHeight))
                .split(inner);

        renderProbeRequest(frame, chunks.get(0));
        renderProbeResponse(frame, chunks.get(1));
        renderProbeHistory(frame, chunks.get(2));
    }

    private void renderProbeRequest(Frame frame, Rect area) {
        String method = PROBE_METHODS[probeMethodIndex];
        Title title = Title.from(Line.from(
                Span.styled(" HTTP Probe ", Style.EMPTY.bold()),
                Span.styled(method, methodStyle(method).bold()),
                Span.raw(" " + probePathState.text() + " ")));

        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build();
        frame.renderWidget(block, area);

        int innerX = area.left() + 2;
        int innerW = area.width() - 4;
        int labelW = 10;
        int fieldW = innerW - labelW;
        int row = area.top() + 1;

        // Method selector
        FormHelper.renderLabel(frame, innerX, row, labelW, "Method:", probeField == PROBE_METHOD);
        Rect methodArea = new Rect(innerX + labelW, row, fieldW, 1);
        Style methodSt = methodStyle(method);
        String leftArr = probeField == PROBE_METHOD ? TuiIcons.ARROW_LEFT + " " : "  ";
        String rightArr = probeField == PROBE_METHOD ? " " + TuiIcons.ARROW_RIGHT : "";
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(leftArr, methodSt),
                Span.styled(method, methodSt.bold()),
                Span.styled(rightArr, methodSt))), methodArea);

        // Full URL (read-only)
        row++;
        FormHelper.renderLabel(frame, innerX, row, labelW, "URL:", false);
        String fullUrl = (probeBaseUrl != null ? probeBaseUrl : "") + probePathState.text();
        Rect urlArea = new Rect(innerX + labelW, row, fieldW, 1);
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(fullUrl, Style.EMPTY.dim()))), urlArea);

        // Path input
        row++;
        FormHelper.renderLabel(frame, innerX, row, labelW, "Path:", probeField == PROBE_PATH);
        Rect pathArea = new Rect(innerX + labelW, row, fieldW, 1);
        if (probeField == PROBE_PATH && !probeSending.get()) {
            TextInput textInput = TextInput.builder().cursorStyle(Style.EMPTY.reversed()).build();
            frame.renderStatefulWidget(textInput, pathArea, probePathState);
        } else {
            String pathText = probePathState.text();
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(pathText.isEmpty() ? "/" : pathText,
                            pathText.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY))),
                    pathArea);
        }

        // Headers
        if (hasProbeHeaders()) {
            int keyW = Math.min(20, fieldW / 3);
            int valW = fieldW - keyW - 3;
            for (int i = 0; i < probeHeaders.size(); i++) {
                row++;
                boolean isSelected = probeField == PROBE_HEADERS && probeSelectedHeader == i;
                String label = i == 0 ? "Headers:" : "";
                FormHelper.renderLabel(frame, innerX, row, labelW, label,
                        isSelected || (i == 0 && probeField == PROBE_HEADERS));

                FormHelper.HeaderEntry he = probeHeaders.get(i);
                int fieldX = innerX + labelW;

                Rect keyArea = new Rect(fieldX, row, keyW, 1);
                if (isSelected && probeEditingHeaderKey && !probeSending.get()) {
                    TextInput keyInput = TextInput.builder().cursorStyle(Style.EMPTY.reversed()).build();
                    frame.renderStatefulWidget(keyInput, keyArea, he.keyInput());
                } else {
                    String keyText = he.keyInput().text();
                    Style keyStyle = keyText.isEmpty() ? Style.EMPTY.dim()
                            : isSelected ? Style.EMPTY.bold() : Style.EMPTY;
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(keyText.isEmpty() ? "<key>" : keyText, keyStyle))), keyArea);
                }

                Rect sepArea = new Rect(fieldX + keyW, row, 3, 1);
                frame.renderWidget(Paragraph.from(Line.from(
                        Span.styled(" : ", Style.EMPTY.dim()))), sepArea);

                Rect valArea = new Rect(fieldX + keyW + 3, row, valW, 1);
                if (isSelected && !probeEditingHeaderKey && !probeSending.get()) {
                    TextInput valInput = TextInput.builder().cursorStyle(Style.EMPTY.reversed()).build();
                    frame.renderStatefulWidget(valInput, valArea, he.valueInput());
                } else {
                    String valText = he.valueInput().text();
                    Style valStyle = valText.isEmpty() ? Style.EMPTY.dim()
                            : isSelected ? Style.EMPTY.bold() : Style.EMPTY;
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(valText.isEmpty() ? "<value>" : valText, valStyle))), valArea);
                }
            }
        }

        // Body input
        row++;
        FormHelper.renderLabel(frame, innerX, row, labelW, "Body:", probeField == PROBE_BODY);
        Rect bodyArea = new Rect(innerX + labelW, row, fieldW, 1);
        if (probeField == PROBE_BODY && !probeSending.get()) {
            TextInput textInput = TextInput.builder()
                    .cursorStyle(Style.EMPTY.reversed())
                    .placeholder("body text or file:payload.json")
                    .build();
            frame.renderStatefulWidget(textInput, bodyArea, probeBodyState);
        } else {
            String bodyText = probeBodyState.text();
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(bodyText.isEmpty() ? "—" : bodyText,
                            bodyText.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY))),
                    bodyArea);
        }
    }

    private void renderProbeResponse(Frame frame, Rect area) {
        String titleStr;
        Style titleStyle = Style.EMPTY.bold();
        if (probeResponseStatus == null) {
            titleStr = " Response ";
        } else if (probeResponseError) {
            titleStr = " Response " + probeResponseStatus + " ";
            titleStyle = Theme.error().bold();
        } else if ("Sending...".equals(probeResponseStatus)) {
            titleStr = " Sending... ";
            titleStyle = Theme.warning().bold();
        } else {
            titleStr = " Response " + probeResponseStatus;
            if (probeResponseElapsed > 0) {
                titleStr += " (" + probeResponseElapsed + "ms)";
            }
            titleStr += " ";
            titleStyle = statusStyle(probeResponseStatus);
        }

        Title title = Title.from(Line.from(Span.styled(titleStr, titleStyle)));

        if (probeResponseLines == null || probeResponseLines.isEmpty()) {
            String placeholder = probeResponseStatus == null
                    ? " Press Enter to send request"
                    : " No response content";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(placeholder, Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, probeResponseLines.size() - visibleLines);
        probeResponseScroll = Math.min(probeResponseScroll, maxScroll);

        int end = Math.min(probeResponseScroll + visibleLines, probeResponseLines.size());
        List<Line> lines = new ArrayList<>();
        for (int i = probeResponseScroll; i < end; i++) {
            String line = probeResponseLines.get(i);
            if (line.isEmpty()) {
                lines.add(Line.from(Span.raw("")));
            } else if (line.contains(": ") && !line.startsWith(" ") && !line.startsWith("{")
                    && !line.startsWith("[") && !line.startsWith("\"")) {
                // Header line
                int colon = line.indexOf(": ");
                lines.add(Line.from(
                        Span.styled(line.substring(0, colon + 1), Theme.label()),
                        Span.raw(line.substring(colon + 1))));
            } else {
                lines.add(Line.from(Span.raw(line)));
            }
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    private void renderProbeHistory(Frame frame, Rect area) {
        String title = " History [" + probeHistory.size() + "] ";

        if (probeHistory.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" No requests yet", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }

        // Ensure selected index is visible
        int start = 0;
        if (probeHistoryIndex >= visibleLines) {
            start = probeHistoryIndex - visibleLines + 1;
        }
        int end = Math.min(start + visibleLines, probeHistory.size());

        List<Line> lines = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ProbeHistoryEntry entry = probeHistory.get(i);
            boolean selected = probeField == PROBE_HISTORY && i == probeHistoryIndex;
            String pointer = selected ? TuiIcons.POINTER + " " : "  ";
            String methodStr = String.format("%-8s", entry.method);
            String statusStr = entry.error ? "ERR" : entry.statusText;
            String elapsedStr = entry.elapsed > 0 ? entry.elapsed + "ms" : "";
            String bodySnippet = entry.body != null && !entry.body.isEmpty()
                    ? " " + TuiHelper.truncate(entry.body, 30)
                    : "";

            Style lineStyle = selected ? Style.EMPTY.bold() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled(pointer, lineStyle),
                    Span.styled(methodStr, methodStyle(entry.method)),
                    Span.styled(entry.path + "  ", lineStyle),
                    Span.styled(statusStr, selected ? statusStyle(statusStr) : statusStyle(statusStr).dim()),
                    Span.styled("  " + elapsedStr, Style.EMPTY.dim()),
                    Span.styled(bodySnippet, Style.EMPTY.dim())));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    private static Style statusStyle(String status) {
        if (status == null) {
            return Style.EMPTY.bold();
        }
        try {
            int code = Integer.parseInt(status);
            if (code >= 200 && code < 300) {
                return Theme.success().bold();
            }
            if (code >= 300 && code < 400) {
                return Style.EMPTY.fg(Theme.accent()).bold();
            }
            if (code >= 400 && code < 500) {
                return Theme.warning().bold();
            }
            if (code >= 500) {
                return Theme.error().bold();
            }
        } catch (NumberFormatException e) {
            // not a number — treat as error
        }
        return Theme.error().bold();
    }

    // ---- Existing table/spec methods ----

    List<HttpEndpointInfo> sortedVisibleEndpoints(IntegrationInfo info) {
        List<HttpEndpointInfo> visible = visibleEndpoints(info);
        visible.sort((a, b) -> {
            int result = switch (sort) {
                case "path" -> compareStr(a.path, b.path);
                case "total" -> Long.compare(b.hits, a.hits);
                case "source" -> Boolean.compare(b.fromRest, a.fromRest);
                case "consumes" -> compareStr(a.consumes, b.consumes);
                case "produces" -> compareStr(a.produces, b.produces);
                default -> compareStr(a.method, b.method);
            };
            return sortReversed ? -result : result;
        });
        return visible;
    }

    private List<HttpEndpointInfo> visibleEndpoints(IntegrationInfo info) {
        if (info == null) {
            return Collections.emptyList();
        }
        List<HttpEndpointInfo> result = new ArrayList<>();
        for (HttpEndpointInfo ep : info.httpEndpoints) {
            if (ep.management && !showManagement) {
                continue;
            }
            if (filter == 1 && !ep.fromRest) {
                continue;
            }
            if (filter == 2 && ep.fromRest) {
                continue;
            }
            result.add(ep);
        }
        return result;
    }

    static Style methodStyle(String method) {
        if (method == null) {
            return Style.EMPTY;
        }
        String m = method.split(",")[0].trim().toUpperCase(Locale.ENGLISH);
        return switch (m) {
            case "GET" -> Theme.success();
            case "POST" -> Theme.label();
            case "PUT" -> Style.EMPTY.fg(Theme.accent());
            case "DELETE" -> Theme.error();
            case "PATCH" -> Theme.warning();
            default -> Style.EMPTY.dim();
        };
    }

    private Title buildTableTitle(IntegrationInfo info, List<HttpEndpointInfo> visible) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" HTTP Services [" + visible.size() + "]"));
        if (info.httpServer != null) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("Server: ", Theme.muted()));
            spans.add(Span.styled(info.httpServer, Style.EMPTY.fg(Theme.accent())));
        }
        long restCount = info.httpEndpoints.stream().filter(e -> e.fromRest && !e.specification).count();
        long specCount = info.httpEndpoints.stream().filter(e -> e.specification).count();
        long httpCount = info.httpEndpoints.stream().filter(e -> !e.fromRest && !e.management).count();
        long mgmtCount = info.httpEndpoints.stream().filter(e -> e.management).count();
        if (restCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("REST: ", Theme.success()));
            spans.add(Span.raw(restCount + ""));
        }
        if (specCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("Spec: ", Theme.notice()));
            spans.add(Span.raw(specCount + ""));
        }
        if (httpCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("HTTP: ", Style.EMPTY.fg(Theme.accent())));
            spans.add(Span.raw(httpCount + ""));
        }
        if (mgmtCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("Mgmt: ", Theme.label().dim()));
            spans.add(Span.raw(mgmtCount + ""));
        }
        spans.add(Span.raw(" "));
        return Title.from(Line.from(spans));
    }

    private void renderTable(Frame frame, Rect area, List<HttpEndpointInfo> visible, IntegrationInfo info) {
        List<Row> rows = new ArrayList<>();
        for (HttpEndpointInfo ep : visible) {
            String method = ep.method != null ? ep.method : "";
            String path = ep.path != null ? ep.path : (ep.url != null ? ep.url : "");
            String consumes = ep.consumes != null ? ep.consumes : "";
            String produces = ep.produces != null ? ep.produces : "";
            String source;
            if (ep.management) {
                source = "Mgmt";
            } else if (ep.specification) {
                source = "API Spec";
            } else if (ep.fromRest) {
                source = ep.contractFirst ? "REST(contract)" : "REST(code)";
            } else {
                source = "HTTP";
            }
            String state = ep.state != null ? ep.state : "";
            String hitsStr = ep.hits > 0 ? String.valueOf(ep.hits) : "";
            rows.add(Row.from(
                    Cell.from(Span.styled(method, methodStyle(method))),
                    Cell.from(path),
                    rightCell(hitsStr, 8),
                    Cell.from(consumes),
                    Cell.from(produces),
                    Cell.from(Span.styled(source,
                            ep.specification ? Theme.notice()
                                    : ep.fromRest ? Theme.success()
                                    : Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(state,
                            "Stopped".equals(state) ? Theme.error() : Style.EMPTY))));
        }

        Title title = buildTableTitle(info, visible);

        Row header = Row.from(
                Cell.from(Span.styled(sortLabel("METHOD", "method"), sortStyle("method"))),
                Cell.from(Span.styled(sortLabel("PATH", "path"), sortStyle("path"))),
                rightCell(sortLabel("TOTAL", "total"), 8, sortStyle("total")),
                Cell.from(Span.styled(sortLabel("CONSUMES", "consumes"), sortStyle("consumes"))),
                Cell.from(Span.styled(sortLabel("PRODUCES", "produces"), sortStyle("produces"))),
                Cell.from(Span.styled(sortLabel("SOURCE", "source"), sortStyle("source"))),
                Cell.from(Span.styled("STATE", Style.EMPTY.bold())));

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(30),
                        Constraint.length(30),
                        Constraint.length(15),
                        Constraint.length(9))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, visible.size());
    }

    private void renderDetail(Frame frame, Rect area, List<HttpEndpointInfo> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select an endpoint to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        HttpEndpointInfo ep = visible.get(sel);
        List<Span> titleSpans = new ArrayList<>();
        if (ep.method != null) {
            titleSpans.add(Span.raw(" "));
            titleSpans.add(Span.styled(ep.method, methodStyle(ep.method).bold()));
            titleSpans.add(Span.raw(" "));
        }
        if (ep.path != null) {
            titleSpans.add(Span.raw(ep.path + " "));
        }
        Title detailTitle = Title.from(Line.from(titleSpans));

        List<Line> lines = new ArrayList<>();
        addDetailLine(lines, "URL", ep.url);
        addDetailLine(lines, "Consumes", ep.consumes);
        addDetailLine(lines, "Produces", ep.produces);
        String sourceStr;
        if (ep.management) {
            sourceStr = "Platform-HTTP (management)";
        } else if (ep.specification) {
            sourceStr = "REST DSL (API specification - " + (ep.contractFirst ? "contract-first" : "code-first") + ")";
        } else if (ep.fromRest) {
            sourceStr = "REST DSL (" + (ep.contractFirst ? "contract-first" : "code-first") + ")";
        } else {
            sourceStr = "Platform-HTTP";
        }
        addDetailLine(lines, "Source", sourceStr);
        if (ep.routeId != null) {
            addDetailLine(lines, "Route", ep.routeId);
        }
        if (ep.operationId != null) {
            addDetailLine(lines, "Operation", ep.operationId);
        }
        if (ep.specificationUri != null) {
            addDetailLine(lines, "Spec", ep.specificationUri);
        }
        if (ep.state != null) {
            addDetailLine(lines, "State", ep.state);
        }
        if (ep.inType != null) {
            addDetailLine(lines, "In type", ep.inType);
        }
        if (ep.outType != null) {
            addDetailLine(lines, "Out type", ep.outType);
        }
        if (ep.description != null) {
            addDetailLine(lines, "Desc", ep.description);
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(detailTitle).build())
                        .build(),
                area);
    }

    private static void addDetailLine(List<Line> lines, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        lines.add(Line.from(
                Span.styled(String.format("  %-10s ", label + ":"), Theme.muted()),
                Span.raw(value)));
    }

    private void loadSpecForSelectedEndpoint() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            return;
        }
        HttpEndpointInfo ep = visible.get(sel);
        if (ep.specificationUri == null) {
            return;
        }
        if (!specLoading.compareAndSet(false, true)) {
            return;
        }

        specLines = List.of("(Loading spec...)");
        specTitle = ep.specificationUri;
        specScroll = 0;
        showSpec = true;

        String pid = ctx.selectedPid;
        String specUri = ep.specificationUri;
        String operationId = ep.operationId;

        ctx.runner.scheduler().execute(() -> {
            try {
                loadSpecInBackground(pid, specUri, operationId);
            } finally {
                specLoading.set(false);
            }
        });
    }

    private void loadSpecInBackground(String pid, String specUri, String operationId) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "rest-spec");
        root.put("filter", specUri);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applySpecResult(specUri, List.of("(No response from integration)"), 0);
            return;
        }

        JsonArray specs = (JsonArray) jo.get("specs");
        if (specs == null || specs.isEmpty()) {
            applySpecResult(specUri, List.of("(No spec content available for: " + specUri + ")"), 0);
            return;
        }

        JsonObject specObj = (JsonObject) specs.get(0);
        String content = specObj.getString("content");
        if (content == null || content.isBlank()) {
            applySpecResult(specUri, List.of("(Empty spec content for: " + specUri + ")"), 0);
            return;
        }

        List<String> lines = List.of(content.split("\n", -1));

        int scrollTo = 0;
        if (operationId != null) {
            int opIdLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("operationId") && line.contains(operationId)) {
                    opIdLine = i;
                    break;
                }
            }
            if (opIdLine >= 0) {
                scrollTo = findOperationDeclarationLine(lines, opIdLine);
            }
        }

        applySpecResult(specUri, lines, scrollTo);
    }

    private static int findOperationDeclarationLine(List<String> lines, int opIdLine) {
        int opIdIndent = leadingSpaces(lines.get(opIdLine));
        for (int i = opIdLine - 1; i >= 0; i--) {
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(raw);
            if (indent >= opIdIndent) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ENGLISH);
            for (String verb : OPENAPI_HTTP_VERBS) {
                if (lower.equals(verb + ":") || lower.startsWith(verb + ": ")
                        || lower.equals("\"" + verb + "\":") || lower.startsWith("\"" + verb + "\": ")) {
                    return Math.max(0, i - 1);
                }
            }
            break;
        }
        return Math.max(0, opIdLine - 2);
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 2;
            } else {
                break;
            }
        }
        return count;
    }

    private void applySpecResult(String specUri, List<String> lines, int scrollTo) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (!showSpec) {
                return;
            }
            specTitle = specUri;
            specLines = lines;
            specScroll = scrollTo;
        });
    }

    private void renderSpec(Frame frame, Rect area) {
        String title = " Spec [" + (specTitle != null ? specTitle : "") + "] ";

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, specLines.size() - visibleLines);
        specScroll = Math.min(specScroll, maxScroll);

        int end = Math.min(specScroll + visibleLines, specLines.size());
        List<Line> visible = new ArrayList<>();
        for (int i = specScroll; i < end; i++) {
            visible.add(Line.from(Span.raw(specLines.get(i))));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(visible))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(info);
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream()
                .map(ep -> (ep.method != null ? ep.method : "") + " " + (ep.path != null ? ep.path : ""))
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "HTTP");
    }

    record ProbeHistoryEntry(
            String method, String path,
            List<FormHelper.HeaderEntry> headers, String body,
            int statusCode, long elapsed,
            String statusText, boolean error) {
    }

    @Override
    public String description() {
        return "HTTP endpoint probe — send requests and inspect responses";
    }

    @Override
    public String getHelpText() {
        return """
                # HTTP

                The HTTP tab shows all HTTP endpoints exposed by this integration and
                lets you send test requests interactively. This includes REST API
                endpoints, management endpoints (health, metrics), and any other
                HTTP routes.

                ## Endpoint List

                - **METHOD** — HTTP method: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, etc. Some endpoints support multiple methods
                - **PATH** — URL path for this endpoint (e.g., `/api/users`, `/observe/health`)
                - **TOTAL** — Number of HTTP requests received by this endpoint since startup
                - **CONSUMES** — Content-Type this endpoint accepts (e.g., `application/json`, `text/xml`). Empty means any content type
                - **PRODUCES** — Content-Type this endpoint returns in responses
                - **SOURCE** — How the endpoint was registered (see below)
                - **STATE** — Endpoint state: `Started` (accepting requests) or `Stopped`

                ## Example Screen

                ```
                 METHOD  PATH                  TOTAL  CONSUMES          PRODUCES          SOURCE        STATE
                 GET     /api/users            142    application/json  application/json  REST(code)    Started
                 POST    /api/users            38     application/json  application/json  REST(code)    Started
                 GET     /observe/health       97                                         Mgmt          Started
                 GET     /observe/metrics      52                       text/plain        Mgmt          Started
                 GET     /q/openapi            15                       application/json  REST(contract) Started
                ```

                ## SOURCE Types

                The SOURCE column tells you how each endpoint was created:

                - **REST(code)** — Defined in Camel REST DSL code. The developer wrote `rest("/api").get("/users").to("direct:getUsers")` in the route definition
                - **REST(contract)** — Generated from an OpenAPI/Swagger specification file (contract-first approach). The API structure comes from a `.json` or `.yaml` spec file
                - **HTTP** — Registered directly via the `platform-http` component using `from("platform-http:/path")`
                - **Mgmt** — Management endpoint added automatically by Camel for observability: health checks, metrics, OpenAPI specs, developer console

                ## HTTP Probe

                Press `Enter` on an endpoint to open the interactive HTTP probe.
                This is a built-in HTTP client that lets you send test requests and
                inspect responses without leaving the TUI.

                The probe screen has these sections:

                - **Method**: Use `Left/Right` arrows to cycle through HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
                - **Path**: Editable URL path — modify it to test different routes or add query parameters
                - **Headers**: Custom request headers. Press `+` to add a new header, then edit the key and value fields
                - **Body**: Request body text. For file-based bodies, type `file:data.json` to load content from a file on disk
                - **Response**: Shows the HTTP status code, response headers, and response body after sending
                - **History**: Recent requests you have sent, with the ability to replay any previous request

                Press `Enter` to send the request. Press `p` to toggle pretty-print
                for JSON responses — this formats the response body with indentation
                for easier reading.

                ## Keys

                - `Up/Down` — select endpoint
                - `Enter` — open HTTP probe for selected endpoint
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Esc` — close probe view
                """;
    }

    @Override
    public boolean setInputValue(String field, String value) {
        if (!probeMode) {
            return false;
        }
        String v = value != null ? value : "";
        if ("path".equals(field)) {
            probePathState.setText(v);
            return true;
        }
        if ("body".equals(field)) {
            probeBodyState.setText(v);
            return true;
        }
        return false;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "HTTP");
        JsonArray rows = new JsonArray();
        for (HttpEndpointInfo hi : info.httpEndpoints) {
            JsonObject row = new JsonObject();
            row.put("method", hi.method);
            row.put("path", hi.path);
            row.put("url", hi.url);
            row.put("hits", hi.hits);
            row.put("routeId", hi.routeId);
            row.put("state", hi.state);
            row.put("fromRest", hi.fromRest);
            row.put("contractFirst", hi.contractFirst);
            if (hi.consumes != null) {
                row.put("consumes", hi.consumes);
            }
            if (hi.produces != null) {
                row.put("produces", hi.produces);
            }
            if (hi.description != null) {
                row.put("description", hi.description);
            }
            if (hi.operationId != null) {
                row.put("operationId", hi.operationId);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.httpEndpoints.size());
        if (info.httpServer != null) {
            result.put("httpServer", info.httpServer);
        }
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
