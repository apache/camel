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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class SendMessagePopup {

    private static final int FIELD_ROUTE = 0;
    private static final int FIELD_BODY = 1;
    private static final int FIELD_HEADERS = 2;
    private static final int FIELD_MODE = 3;
    private static final int FIELD_HISTORY = 4;

    private static final int MAX_HISTORY = 20;

    private boolean visible;
    private boolean sending;
    private String pid;
    private String integrationName;
    private List<RouteInfo> routes;
    private int selectedRouteIndex;
    private final TextAreaState bodyState = new TextAreaState("");
    private int selectedField = FIELD_BODY;
    private boolean inOut;

    private List<FormHelper.HeaderEntry> headers;
    private int selectedHeader;
    private boolean editingHeaderKey;

    // Response state
    private String responseStatus;
    private long responseElapsed;
    private String responseExchangeId;
    private List<String> responseHeaderLines;
    private String responseRawBody;
    private List<String> responseLines;
    private boolean responseError;
    private int responseScroll;
    private boolean prettyPrint;
    private boolean wordWrap = true;
    private boolean showResponseBody = true;
    private boolean showResponseHeaders;

    // History
    private final List<SendHistoryEntry> history = new CopyOnWriteArrayList<>();
    private int historyIndex;

    // File chooser
    private FolderBrowser fileBrowser;
    private String sourceDirectory;

    boolean isVisible() {
        return visible;
    }

    void open(MonitorContext ctx, String pid, String name, List<RouteInfo> routes, String preSelectRouteId) {
        open(ctx, pid, name, routes, preSelectRouteId, null);
    }

    void open(
            MonitorContext ctx, String pid, String name, List<RouteInfo> routes,
            String preSelectRouteId, String sourceDirectory) {
        if (pid == null || routes == null || routes.isEmpty()) {
            return;
        }
        this.pid = pid;
        this.integrationName = name;
        this.routes = new ArrayList<>(routes);
        this.selectedRouteIndex = findSmartDefault(preSelectRouteId);
        this.bodyState.clear();
        this.selectedField = FIELD_BODY;
        this.inOut = true;
        this.sending = false;
        this.headers = null;
        this.selectedHeader = 0;
        this.editingHeaderKey = true;
        this.sourceDirectory = sourceDirectory;
        clearResponse();
        this.historyIndex = 0;
        this.visible = true;
    }

    void close() {
        visible = false;
    }

    void setFileBrowser(FolderBrowser fileBrowser) {
        this.fileBrowser = fileBrowser;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (fileBrowser != null && fileBrowser.isVisible()) {
            return fileBrowser.handleKeyEvent(ke);
        }
        if (sending) {
            return true;
        }
        if (ke.isCancel()) {
            close();
            return true;
        }
        if (ke.isConfirm()) {
            if (selectedField == FIELD_BODY) {
                bodyState.insert('\n');
                return true;
            }
            if (selectedField == FIELD_HISTORY && !history.isEmpty()) {
                replayHistoryEntry(history.get(historyIndex));
            }
            return true;
        }

        // PgUp/PgDn scroll response
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            responseScroll = Math.max(0, responseScroll - 10);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            responseScroll += 10;
            return true;
        }

        // Toggle response display options (only when not editing text)
        if (selectedField != FIELD_BODY && selectedField != FIELD_HEADERS) {
            if (ke.isChar('b')) {
                showResponseBody = !showResponseBody;
                if (responseLines != null) {
                    rebuildResponseLines();
                    responseScroll = 0;
                }
                return true;
            }
            if (ke.isChar('h')) {
                showResponseHeaders = !showResponseHeaders;
                if (responseLines != null) {
                    rebuildResponseLines();
                    responseScroll = 0;
                }
                return true;
            }
            if (ke.isChar('p')) {
                prettyPrint = !prettyPrint;
                if (responseLines != null) {
                    rebuildResponseLines();
                    responseScroll = 0;
                }
                return true;
            }
            if (ke.isChar('w')) {
                wordWrap = !wordWrap;
                return true;
            }
        }

        if (selectedField == FIELD_BODY) {
            if (ke.isKey(KeyCode.TAB)) {
                if (hasHeaders()) {
                    selectedField = FIELD_HEADERS;
                    selectedHeader = 0;
                    editingHeaderKey = true;
                } else {
                    selectedField = FIELD_MODE;
                }
                return true;
            }
            if (ke.hasCtrl() && ke.isChar('f') && fileBrowser != null) {
                openFileBrowser();
                return true;
            }
            if (ke.isUp()) {
                if (bodyState.cursorRow() == 0) {
                    if (routes.size() > 1) {
                        selectedField = FIELD_ROUTE;
                    } else {
                        goToLastField();
                    }
                } else {
                    bodyState.moveCursorUp();
                }
                return true;
            }
            if (ke.isDown()) {
                if (bodyState.cursorRow() >= bodyState.lineCount() - 1) {
                    if (hasHeaders()) {
                        selectedField = FIELD_HEADERS;
                        selectedHeader = 0;
                        editingHeaderKey = true;
                    } else {
                        selectedField = FIELD_MODE;
                    }
                } else {
                    bodyState.moveCursorDown();
                }
                return true;
            }
            if (ke.isLeft()) {
                bodyState.moveCursorLeft();
                return true;
            }
            if (ke.isRight()) {
                bodyState.moveCursorRight();
                return true;
            }
            if (ke.isHome()) {
                bodyState.moveCursorToLineStart();
                return true;
            }
            if (ke.isEnd()) {
                bodyState.moveCursorToLineEnd();
                return true;
            }
            if (ke.isDeleteBackward()) {
                bodyState.deleteBackward();
                return true;
            }
            if (ke.isDeleteForward()) {
                bodyState.deleteForward();
                return true;
            }
            if (ke.code() == KeyCode.CHAR) {
                bodyState.insert(ke.character());
                return true;
            }
            return true;
        }
        if (selectedField == FIELD_ROUTE) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                selectedField = FIELD_BODY;
                return true;
            }
            if (ke.isUp()) {
                goToLastField();
                return true;
            }
            if (ke.isLeft()) {
                selectedRouteIndex = (selectedRouteIndex - 1 + routes.size()) % routes.size();
                return true;
            }
            if (ke.isRight()) {
                selectedRouteIndex = (selectedRouteIndex + 1) % routes.size();
                return true;
            }
            return true;
        }
        if (selectedField == FIELD_HEADERS) {
            return handleHeaderKeyEvent(ke);
        }
        if (selectedField == FIELD_MODE) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                if (!history.isEmpty()) {
                    selectedField = FIELD_HISTORY;
                    historyIndex = 0;
                } else if (routes.size() > 1) {
                    selectedField = FIELD_ROUTE;
                } else {
                    selectedField = FIELD_BODY;
                }
                return true;
            }
            if (ke.isUp()) {
                if (hasHeaders()) {
                    selectedField = FIELD_HEADERS;
                    selectedHeader = headers.size() - 1;
                    editingHeaderKey = false;
                } else {
                    selectedField = FIELD_BODY;
                }
                return true;
            }
            if (ke.isChar('+')) {
                addHeader();
                return true;
            }
            if (ke.isLeft() || ke.isRight() || ke.code() == KeyCode.CHAR) {
                inOut = !inOut;
                return true;
            }
            return true;
        }
        if (selectedField == FIELD_HISTORY) {
            if (ke.isKey(KeyCode.TAB)) {
                if (routes.size() > 1) {
                    selectedField = FIELD_ROUTE;
                } else {
                    selectedField = FIELD_BODY;
                }
                return true;
            }
            if (ke.isUp()) {
                if (historyIndex > 0) {
                    historyIndex--;
                } else {
                    selectedField = FIELD_MODE;
                }
                return true;
            }
            if (ke.isDown()) {
                if (historyIndex < history.size() - 1) {
                    historyIndex++;
                }
                return true;
            }
            return true;
        }
        return true;
    }

    private void goToLastField() {
        if (!history.isEmpty()) {
            selectedField = FIELD_HISTORY;
            historyIndex = 0;
        } else {
            selectedField = FIELD_MODE;
        }
    }

    private boolean handleHeaderKeyEvent(KeyEvent ke) {
        FormHelper.HeaderEntry current = headers.get(selectedHeader);
        TextInputState activeInput = editingHeaderKey ? current.keyInput() : current.valueInput();

        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            if (editingHeaderKey) {
                editingHeaderKey = false;
            } else if (selectedHeader < headers.size() - 1) {
                selectedHeader++;
                editingHeaderKey = true;
            } else {
                selectedField = FIELD_MODE;
            }
            return true;
        }
        if (ke.isUp()) {
            if (editingHeaderKey) {
                if (selectedHeader > 0) {
                    selectedHeader--;
                    editingHeaderKey = false;
                } else {
                    selectedField = FIELD_BODY;
                }
            } else {
                editingHeaderKey = true;
            }
            return true;
        }
        if (ke.isDeleteBackward()) {
            if (editingHeaderKey && current.keyInput().text().isEmpty()) {
                headers.remove(selectedHeader);
                if (headers.isEmpty()) {
                    headers = null;
                    selectedField = FIELD_BODY;
                } else if (selectedHeader >= headers.size()) {
                    selectedHeader = headers.size() - 1;
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
            if (!editingHeaderKey && activeInput.cursorPosition() == 0) {
                editingHeaderKey = true;
            } else {
                activeInput.moveCursorLeft();
            }
            return true;
        }
        if (ke.isRight()) {
            if (editingHeaderKey && activeInput.cursorPosition() == activeInput.text().length()) {
                editingHeaderKey = false;
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

    private void addHeader() {
        if (headers == null) {
            headers = new ArrayList<>();
        }
        headers.add(new FormHelper.HeaderEntry(new TextInputState(""), new TextInputState("")));
        selectedField = FIELD_HEADERS;
        selectedHeader = headers.size() - 1;
        editingHeaderKey = true;
    }

    private boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }

    private void openFileBrowser() {
        fileBrowser.setFileSelectMode(true);
        fileBrowser.setOnSelect(path -> bodyState.setText("file:" + path));
        String startDir = sourceDirectory;
        if (startDir == null) {
            String settingsFolder = TuiSettings.load().getDefaultFolder();
            startDir = settingsFolder != null ? settingsFolder : System.getProperty("user.dir");
        }
        fileBrowser.open(startDir);
    }

    void handlePaste(String text) {
        if (!visible || sending || text == null || text.isEmpty()) {
            return;
        }
        if (selectedField == FIELD_BODY) {
            bodyState.insert(text);
        } else if (selectedField == FIELD_HEADERS && hasHeaders()) {
            FormHelper.HeaderEntry he = headers.get(selectedHeader);
            TextInputState target = editingHeaderKey ? he.keyInput() : he.valueInput();
            FormHelper.handlePaste(text, target);
        }
    }

    void doSend(MonitorContext ctx, ExecutorService executor) {
        if (!visible || sending || ctx == null || executor == null) {
            return;
        }
        sending = true;
        responseStatus = "Sending...";
        responseError = false;
        responseScroll = 0;

        String body = bodyState.text();
        if (body != null && body.startsWith("file:")) {
            File f = new File(body.substring(5));
            if (f.exists() && f.isFile()) {
                body = "file:" + f.getAbsolutePath();
            }
        }
        String sendBody = body;
        RouteInfo route = routes.get(selectedRouteIndex);
        String endpoint = route.routeId;
        String mep = inOut ? "InOut" : "InOnly";
        String targetPid = pid;
        boolean captureInOut = inOut;
        List<FormHelper.HeaderEntry> hdrs = headers != null ? new ArrayList<>(headers) : null;
        String routeId = route.routeId;

        executor.execute(() -> {
            try {
                JsonObject root = new JsonObject();
                root.put("action", "send");
                root.put("endpoint", endpoint);
                root.put("body", sendBody);
                root.put("exchangePattern", mep);
                root.put("pollTimeout", 20000);
                root.put("poll", false);

                if (hdrs != null && !hdrs.isEmpty()) {
                    JsonArray arr = new JsonArray();
                    for (FormHelper.HeaderEntry he : hdrs) {
                        String k = he.keyInput().text().trim();
                        String v = he.valueInput().text();
                        if (!k.isEmpty()) {
                            JsonObject jo = new JsonObject();
                            jo.put("key", k);
                            jo.put("value", v);
                            arr.add(jo);
                        }
                    }
                    if (!arr.isEmpty()) {
                        root.put("headers", arr);
                    }
                }

                JsonObject response = ctx.executeAction(targetPid, root, 25000);

                if (response == null) {
                    applyResult(routeId, sendBody, hdrs, captureInOut,
                            null, 0, null, null, null,
                            true, "No response from integration");
                    return;
                }

                String status = response.getString("status");
                long elapsed = TuiHelper.objToLong(response.get("elapsed"));
                String exchangeId = response.getString("exchangeId");

                if ("success".equals(status)) {
                    List<String> hdrLines = new ArrayList<>();
                    String rawBody = null;

                    if (exchangeId != null) {
                        hdrLines.add("exchangeId: " + exchangeId);
                    }

                    JsonObject message = response.getMap("message");
                    if (captureInOut && message != null) {
                        // Parse exchange headers
                        Collection<JsonObject> msgHeaders = message.getCollection("headers");
                        if (msgHeaders != null) {
                            for (JsonObject h : msgHeaders) {
                                String k = h.getString("key");
                                Object v = h.get("value");
                                if (k != null) {
                                    hdrLines.add(k + ": " + (v != null ? v.toString() : ""));
                                }
                            }
                        }

                        // Parse body
                        JsonObject bodyObj = message.getMap("body");
                        if (bodyObj != null) {
                            Object bodyValue = bodyObj.get("value");
                            if (bodyValue != null) {
                                rawBody = bodyValue.toString();
                            }
                        }
                    }

                    applyResult(routeId, sendBody, hdrs, captureInOut,
                            "success", elapsed, exchangeId, hdrLines, rawBody,
                            false, null);
                } else {
                    List<String> errLines = new ArrayList<>();
                    JsonObject exception = response.getMap("exception");
                    String errMsg;
                    if (exception != null) {
                        errMsg = exception.getString("message");
                        if (errMsg == null) {
                            errMsg = status != null ? status : "unknown error";
                        }
                        try {
                            errMsg = Jsoner.unescape(errMsg);
                        } catch (Exception e) {
                            // ignore
                        }
                        errLines.add("Exception: " + errMsg);
                        String stackTrace = exception.getString("stackTrace");
                        if (stackTrace != null) {
                            try {
                                stackTrace = Jsoner.unescape(stackTrace);
                            } catch (Exception e) {
                                // ignore
                            }
                            for (String line : stackTrace.split("\n")) {
                                errLines.add(TuiHelper.fixControlChars(line));
                            }
                        }
                    } else {
                        errMsg = status != null ? status : "unknown error";
                        errLines.add("Error: " + errMsg);
                    }

                    applyResult(routeId, sendBody, hdrs, captureInOut,
                            status, elapsed, exchangeId, errLines, null,
                            true, errMsg);
                }
            } catch (Exception e) {
                applyResult(routeId, sendBody, hdrs, captureInOut,
                        null, 0, null, null, null,
                        true, "Error: " + e.getMessage());
            } finally {
                sending = false;
            }
        });
    }

    private void applyResult(
            String routeId, String body, List<FormHelper.HeaderEntry> hdrs, boolean wasInOut,
            String status, long elapsed, String exchangeId,
            List<String> hdrLines, String rawBody,
            boolean error, String errorMessage) {

        // Build history entry
        List<FormHelper.HeaderEntry> histHeaders = null;
        if (hdrs != null && !hdrs.isEmpty()) {
            histHeaders = new ArrayList<>();
            for (FormHelper.HeaderEntry he : hdrs) {
                histHeaders.add(new FormHelper.HeaderEntry(
                        new TextInputState(he.keyInput().text()),
                        new TextInputState(he.valueInput().text())));
            }
        }
        SendHistoryEntry histEntry = new SendHistoryEntry(
                routeId, body, histHeaders, wasInOut,
                status != null ? status : (error ? "failed" : "unknown"),
                elapsed, error);

        responseStatus = status;
        responseElapsed = elapsed;
        responseExchangeId = exchangeId;
        responseError = error;
        responseScroll = 0;

        if (error && hdrLines == null) {
            responseHeaderLines = null;
            responseRawBody = null;
            responseLines = errorMessage != null ? List.of(errorMessage) : List.of("Unknown error");
        } else {
            responseHeaderLines = hdrLines;
            responseRawBody = rawBody;
            rebuildResponseLines();
        }

        history.add(0, histEntry);
        if (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        historyIndex = 0;
    }

    private void replayHistoryEntry(SendHistoryEntry entry) {
        // Restore route selection
        if (entry.routeId != null) {
            for (int i = 0; i < routes.size(); i++) {
                if (entry.routeId.equals(routes.get(i).routeId)) {
                    selectedRouteIndex = i;
                    break;
                }
            }
        }

        // Restore body
        bodyState.setText(entry.body != null ? entry.body : "");

        // Restore headers
        headers = null;
        if (entry.headers != null) {
            for (FormHelper.HeaderEntry he : entry.headers) {
                addHeaderWithValues(he.keyInput().text(), he.valueInput().text());
            }
        }

        // Restore mode
        inOut = entry.inOut;
        selectedField = FIELD_BODY;
    }

    private void addHeaderWithValues(String key, String value) {
        if (headers == null) {
            headers = new ArrayList<>();
        }
        TextInputState keyState = new TextInputState("");
        TextInputState valState = new TextInputState("");
        for (int i = 0; i < key.length(); i++) {
            keyState.insert(key.charAt(i));
        }
        for (int i = 0; i < value.length(); i++) {
            valState.insert(value.charAt(i));
        }
        headers.add(new FormHelper.HeaderEntry(keyState, valState));
    }

    private void clearResponse() {
        responseStatus = null;
        responseElapsed = 0;
        responseExchangeId = null;
        responseHeaderLines = null;
        responseRawBody = null;
        responseLines = null;
        responseError = false;
        responseScroll = 0;
    }

    private void rebuildResponseLines() {
        List<String> lines = new ArrayList<>();
        if (responseHeaderLines != null && (responseError || showResponseHeaders)) {
            lines.addAll(responseHeaderLines);
        }
        if (showResponseBody && responseRawBody != null && !responseRawBody.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            String body = responseRawBody;
            if (prettyPrint) {
                body = CamelCommandHelper.valueAsStringPretty(body, false);
            }
            for (String line : body.split("\n", -1)) {
                lines.add(TuiHelper.fixControlChars(line));
            }
        }
        responseLines = lines;
    }

    // ---- Rendering ----

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }
        if (fileBrowser != null && fileBrowser.isVisible()) {
            fileBrowser.render(frame, area);
            return;
        }

        frame.renderWidget(Clear.INSTANCE, area);

        int padX = 2;
        int padY = 1;
        Rect inner = new Rect(
                area.left() + padX, area.top() + padY,
                area.width() - padX * 2, area.height() - padY * 2);

        int bodyHeight = 6;
        int headerCount = hasHeaders() ? headers.size() : 0;
        int requestHeight = 5 + bodyHeight + headerCount + (headerCount > 0 ? 1 : 0);
        if (routes.size() > 1) {
            requestHeight += 1;
        }

        int historyHeight = Math.min(6, history.size() + 2);
        if (historyHeight < 3) {
            historyHeight = 3;
        }

        List<Rect> chunks = Layout.vertical()
                .constraints(
                        Constraint.length(requestHeight),
                        Constraint.fill(),
                        Constraint.length(historyHeight))
                .split(inner);

        renderRequest(frame, chunks.get(0));
        renderResponse(frame, chunks.get(1));
        renderHistory(frame, chunks.get(2));
    }

    private void renderRequest(Frame frame, Rect area) {
        String title = " Send Message";
        if (integrationName != null) {
            title += " — " + TuiHelper.truncate(integrationName, 30);
        }
        title += " ";

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title)
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);

        int innerX = inner.left() + 1;
        int innerW = inner.width() - 2;
        int labelW = 8;
        int fieldW = innerW - labelW;
        int row = inner.top();

        // Route selector (only if multiple routes)
        if (routes.size() > 1) {
            FormHelper.renderLabel(frame, innerX, row, labelW, "Route:", selectedField == FIELD_ROUTE);
            RouteInfo ri = routes.get(selectedRouteIndex);
            String routeDisplay = ri.routeId + " (" + truncateUri(ri.from, fieldW - ri.routeId.length() - 6) + ")";
            String arrow = selectedField == FIELD_ROUTE ? TuiIcons.ARROW_LEFT + " " : "  ";
            String arrowR = selectedField == FIELD_ROUTE ? " " + TuiIcons.ARROW_RIGHT : "  ";
            Style routeStyle = selectedField == FIELD_ROUTE ? Style.EMPTY.bold() : Style.EMPTY;
            Rect routeArea = new Rect(innerX + labelW, row, fieldW, 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(arrow, routeStyle),
                    Span.styled(routeDisplay, routeStyle),
                    Span.styled(arrowR, routeStyle))), routeArea);
            row++;
        }

        // Body input (multi-line text area)
        row++;
        int bodyHeight = 6;
        FormHelper.renderLabel(frame, innerX, row, labelW, "Body:", selectedField == FIELD_BODY);
        Rect bodyArea = new Rect(innerX + labelW, row, fieldW, bodyHeight);
        Style bodyBorderStyle = selectedField == FIELD_BODY ? Style.EMPTY.fg(Theme.accent()) : Theme.muted();
        Block bodyBlock = Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(bodyBorderStyle)
                .build();
        Rect bodyInner = bodyBlock.inner(bodyArea);
        frame.renderWidget(bodyBlock, bodyArea);
        TextArea textArea = TextArea.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("body text, JSON, or file:payload.json")
                .build();
        if (selectedField == FIELD_BODY && !sending) {
            textArea.renderWithCursor(bodyInner, frame.buffer(), bodyState, frame);
        } else {
            textArea.render(bodyInner, frame.buffer(), bodyState);
        }
        row += bodyHeight - 1;

        // Headers section
        if (hasHeaders()) {
            int keyW = Math.min(20, fieldW / 3);
            int valW = fieldW - keyW - 3;
            for (int i = 0; i < headers.size(); i++) {
                row++;
                boolean isSelected = selectedField == FIELD_HEADERS && selectedHeader == i;
                String label = i == 0 ? "Hdrs:" : "";
                FormHelper.renderLabel(frame, innerX, row, labelW, label,
                        isSelected || (i == 0 && selectedField == FIELD_HEADERS));

                FormHelper.HeaderEntry he = headers.get(i);
                int fieldX = innerX + labelW;

                // Key field
                Rect keyArea = new Rect(fieldX, row, keyW, 1);
                if (isSelected && editingHeaderKey && !sending) {
                    TextInput keyInput = TextInput.builder()
                            .cursorStyle(Style.EMPTY.reversed())
                            .build();
                    frame.renderStatefulWidget(keyInput, keyArea, he.keyInput());
                } else {
                    String keyText = he.keyInput().text();
                    Style keyStyle;
                    if (keyText.isEmpty()) {
                        keyStyle = Style.EMPTY.dim();
                        keyText = "<key>";
                    } else {
                        keyStyle = isSelected ? Style.EMPTY.bold() : Style.EMPTY;
                    }
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(keyText, keyStyle))), keyArea);
                }

                // Separator
                Rect sepArea = new Rect(fieldX + keyW, row, 3, 1);
                frame.renderWidget(Paragraph.from(Line.from(
                        Span.styled(" : ", Style.EMPTY.dim()))), sepArea);

                // Value field
                Rect valArea = new Rect(fieldX + keyW + 3, row, valW, 1);
                if (isSelected && !editingHeaderKey && !sending) {
                    TextInput valInput = TextInput.builder()
                            .cursorStyle(Style.EMPTY.reversed())
                            .build();
                    frame.renderStatefulWidget(valInput, valArea, he.valueInput());
                } else {
                    String valText = he.valueInput().text();
                    Style valStyle;
                    if (valText.isEmpty()) {
                        valStyle = Style.EMPTY.dim();
                        valText = "<value>";
                    } else {
                        valStyle = isSelected ? Style.EMPTY.bold() : Style.EMPTY;
                    }
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(valText, valStyle))), valArea);
                }
            }
        }

        // Mode toggle
        row += 2;
        FormHelper.renderLabel(frame, innerX, row, labelW, "Mode:", selectedField == FIELD_MODE);
        Rect modeArea = new Rect(innerX + labelW, row, fieldW, 1);
        Style inOnlyStyle = !inOut ? Style.EMPTY.bold().reversed() : Style.EMPTY.dim();
        Style inOutStyle = inOut ? Style.EMPTY.bold().reversed() : Style.EMPTY.dim();
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(" InOnly ", inOnlyStyle),
                Span.raw("  "),
                Span.styled(" InOut ", inOutStyle))), modeArea);
    }

    private void renderResponse(Frame frame, Rect area) {
        String titleStr;
        Style titleStyle = Style.EMPTY.bold();

        if (responseStatus == null && !sending) {
            titleStr = " Response ";
        } else if (sending) {
            titleStr = " Sending... ";
            titleStyle = Theme.warning();
        } else if (responseError) {
            titleStr = " Response — Error ";
            if (responseElapsed > 0) {
                titleStr = " Response — Error (" + responseElapsed + "ms) ";
            }
            titleStyle = Theme.error().bold();
        } else {
            titleStr = " Response — " + (inOut ? "InOut" : "InOnly");
            if (responseElapsed > 0) {
                titleStr += " (" + responseElapsed + "ms)";
            }
            titleStr += " ";
            titleStyle = Theme.success().bold();
        }

        Title title = Title.from(Line.from(Span.styled(titleStr, titleStyle)));

        if (responseLines == null || responseLines.isEmpty()) {
            String placeholder = responseStatus == null && !sending
                    ? " Press F5 to send message"
                    : " No response content";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(placeholder, Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                            .build(),
                    area);
            return;
        }

        Overflow overflow = wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP;

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, responseLines.size() - visibleLines);
        responseScroll = Math.min(responseScroll, maxScroll);

        int end = Math.min(responseScroll + visibleLines, responseLines.size());
        List<Line> lines = new ArrayList<>();
        for (int i = responseScroll; i < end; i++) {
            String line = responseLines.get(i);
            if (line.isEmpty()) {
                lines.add(Line.from(Span.raw("")));
            } else if (line.contains(": ") && !line.startsWith(" ") && !line.startsWith("{")
                    && !line.startsWith("[") && !line.startsWith("\"")) {
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
                        .overflow(overflow)
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    private void renderHistory(Frame frame, Rect area) {
        String title = " History [" + history.size() + "]"
                       + (selectedField == FIELD_HISTORY ? " — Enter to replay " : " ");

        if (history.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" No messages sent yet", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }

        int start = 0;
        if (historyIndex >= visibleLines) {
            start = historyIndex - visibleLines + 1;
        }
        int end = Math.min(start + visibleLines, history.size());

        List<Line> lines = new ArrayList<>();
        for (int i = start; i < end; i++) {
            SendHistoryEntry entry = history.get(i);
            boolean selected = selectedField == FIELD_HISTORY && i == historyIndex;
            String pointer = selected ? TuiIcons.POINTER + " " : "  ";
            String routeStr = String.format("%-16s", entry.routeId != null ? entry.routeId : "");
            String modeStr = entry.inOut ? "InOut " : "InOnly";
            String statusStr = entry.error ? "ERR" : entry.status;
            String elapsedStr = entry.elapsed > 0 ? entry.elapsed + "ms" : "";
            String bodySnippet = entry.body != null && !entry.body.isEmpty()
                    ? " " + TuiHelper.truncate(entry.body, 30)
                    : "";

            Style lineStyle = selected ? Style.EMPTY.bold() : Style.EMPTY;
            Style statusStyle = entry.error ? Theme.error() : Theme.success();
            if (!selected) {
                statusStyle = statusStyle.dim();
            }

            lines.add(Line.from(
                    Span.styled(pointer, lineStyle),
                    Span.styled(routeStr, lineStyle),
                    Span.styled(modeStr + "  ", Style.EMPTY.dim()),
                    Span.styled(statusStr, statusStyle),
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

    void renderFooter(List<Span> spans) {
        if (fileBrowser != null && fileBrowser.isVisible()) {
            fileBrowser.renderFooter(spans);
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, "Tab", "fields");
        hint(spans, "F5", "send");
        hint(spans, "Ctrl+F", "file");
        hint(spans, "+", "header");
        hint(spans, "b", "body" + (showResponseBody ? " [on]" : ""));
        hint(spans, "h", "headers" + (showResponseHeaders ? " [on]" : ""));
        hint(spans, "p", "pretty" + (prettyPrint ? " [on]" : ""));
        hint(spans, "w", "wrap" + (wordWrap ? " [on]" : ""));
        if (responseLines != null && !responseLines.isEmpty()) {
            hintLast(spans, "PgUp/Dn", "response");
        }
    }

    // ---- Helpers ----

    private int findSmartDefault(String preSelectRouteId) {
        if (preSelectRouteId != null) {
            for (int i = 0; i < routes.size(); i++) {
                if (preSelectRouteId.equals(routes.get(i).routeId)) {
                    return i;
                }
            }
        }
        for (int i = 0; i < routes.size(); i++) {
            String from = routes.get(i).from;
            if (from != null && (from.startsWith("direct:") || from.startsWith("seda:")
                    || from.startsWith("platform-http:"))) {
                return i;
            }
        }
        return 0;
    }

    private static String truncateUri(String uri, int max) {
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        String clean = q > 0 ? uri.substring(0, q) : uri;
        return TuiHelper.truncate(clean, max);
    }

    record SendHistoryEntry(
            String routeId, String body,
            List<FormHelper.HeaderEntry> headers,
            boolean inOut, String status,
            long elapsed, boolean error) {
    }
}
