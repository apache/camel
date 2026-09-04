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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.HttpTab.methodStyle;

class HttpProbe {

    private static final int MOUSE_SCROLL_LINES = 3;
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

    // Probe field constants
    private static final int PROBE_METHOD = 0;
    private static final int PROBE_PATH = 1;
    private static final int PROBE_PATH_PARAMS = 2;
    private static final int PROBE_QUERY_PARAMS = 3;
    private static final int PROBE_CONTENT_TYPE = 4;
    private static final int PROBE_ACCEPT = 5;
    private static final int PROBE_HEADERS = 6;
    private static final int PROBE_BODY = 7;
    private static final int PROBE_HISTORY = 8;

    static final String[] PROBE_METHODS = { "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" };
    private static final String[] CONTENT_TYPES = {
            "", "application/json", "application/xml", "text/plain",
            "text/xml", "application/x-www-form-urlencoded", "multipart/form-data"
    };
    private static final String[] ACCEPT_TYPES = {
            "", "application/json", "application/xml", "text/plain", "*/*"
    };
    private static final int MAX_PROBE_HISTORY = 20;

    private final MonitorContext ctx;

    // Probe mode state
    private boolean active;
    private String probeRouteId;
    private String probeBaseUrl;
    private int probeField = PROBE_PATH;
    private int probeMethodIndex;
    private final TextInputState probePathState = new TextInputState("");
    private final TextAreaState probeBodyState = new TextAreaState("");
    private List<FormHelper.HeaderEntry> probeHeaders;
    private int probeSelectedHeader;
    private boolean probeEditingHeaderKey;
    private List<PathParam> probePathParams;
    private int probeSelectedPathParam;
    private List<FormHelper.HeaderEntry> probeQueryParams;
    private int probeSelectedQueryParam;
    private boolean probeEditingQueryKey;
    private int probeContentTypeIndex;
    private int probeAcceptIndex;
    private final AtomicBoolean probeSending = new AtomicBoolean(false);
    private String probeResponseStatus;
    private long probeResponseElapsed;
    private List<String> probeResponseLines;
    private boolean probeResponseError;
    private int probeResponseScroll;
    private boolean probeViewRequest;
    private final List<ProbeHistoryEntry> probeHistory = new ArrayList<>();
    private int probeHistoryIndex;
    private boolean probePrettyPrint;
    private String probeResponseRawBody;
    private List<String> probeResponseHeaderLines;

    HttpProbe(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isActive() {
        return active;
    }

    boolean isSending() {
        return probeSending.get();
    }

    void enter(HttpEndpointInfo ep) {
        active = true;
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

        // Scan for {xxx} placeholders in path
        probePathParams = scanPathParams(path);
        probeSelectedPathParam = 0;

        // Pre-fill Content-Type and Accept from endpoint metadata
        probeContentTypeIndex = findPresetIndex(CONTENT_TYPES, ep.consumes);
        probeAcceptIndex = findPresetIndex(ACCEPT_TYPES, ep.produces);

        // Clear manual headers (Content-Type/Accept are handled by cyclers)
        probeHeaders = null;
        probeSelectedHeader = 0;
        probeEditingHeaderKey = true;

        // Clear query params
        probeQueryParams = null;
        probeSelectedQueryParam = 0;
        probeEditingQueryKey = true;

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

    void exit() {
        active = false;
    }

    // ---- Key handling ----

    boolean handleKeyEvent(KeyEvent ke) {
        if (probeSending.get()) {
            return true;
        }

        // F5 to send request
        if (ke.code() == KeyCode.F5) {
            doProbeRequest();
            probeViewRequest = false;
            return true;
        }

        // F4 to toggle request/response view
        if (ke.code() == KeyCode.F4) {
            probeViewRequest = !probeViewRequest;
            return true;
        }

        // Enter: newline in body, replay in history, advance field otherwise
        if (ke.isConfirm()) {
            if (probeField == PROBE_BODY) {
                probeBodyState.insert('\n');
            } else if (probeField == PROBE_HISTORY && !probeHistory.isEmpty()) {
                replayHistoryEntry(probeHistory.get(probeHistoryIndex));
            } else {
                advanceProbeField();
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
                && probeField != PROBE_HEADERS && probeField != PROBE_PATH_PARAMS
                && probeField != PROBE_QUERY_PARAMS) {
            probePrettyPrint = !probePrettyPrint;
            if (probeResponseLines != null) {
                reformatResponseBody();
            }
            return true;
        }

        // Field-specific handling
        if (probeField == PROBE_METHOD) {
            return handleProbeMethodKeyEvent(ke);
        }
        if (probeField == PROBE_PATH) {
            return handleProbePathKeyEvent(ke);
        }
        if (probeField == PROBE_PATH_PARAMS) {
            return handleProbePathParamKeyEvent(ke);
        }
        if (probeField == PROBE_QUERY_PARAMS) {
            return handleProbeQueryParamKeyEvent(ke);
        }
        if (probeField == PROBE_CONTENT_TYPE) {
            return handleProbeCyclerKeyEvent(ke, CONTENT_TYPES, PROBE_CONTENT_TYPE);
        }
        if (probeField == PROBE_ACCEPT) {
            return handleProbeCyclerKeyEvent(ke, ACCEPT_TYPES, PROBE_ACCEPT);
        }
        if (probeField == PROBE_HEADERS) {
            return handleProbeHeaderKeyEvent(ke);
        }
        if (probeField == PROBE_BODY) {
            return handleProbeBodyKeyEvent(ke);
        }
        if (probeField == PROBE_HISTORY) {
            return handleProbeHistoryKeyEvent(ke);
        }
        return true;
    }

    boolean handleMouseScroll(MouseEvent me) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            probeResponseScroll = Math.max(0, probeResponseScroll - MOUSE_SCROLL_LINES);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            probeResponseScroll += MOUSE_SCROLL_LINES;
            return true;
        }
        return false;
    }

    void handlePaste(String text) {
        if (!active || probeSending.get()) {
            return;
        }
        if (probeField == PROBE_BODY) {
            probeBodyState.insert(text);
        } else {
            TextInputState target = probeActiveTextInput();
            if (target != null) {
                FormHelper.handlePaste(text, target);
            }
        }
    }

    boolean setInputValue(String field, String value) {
        String v = value != null ? value : "";
        if ("path".equals(field)) {
            probePathState.setText(v);
            rescanPathParams();
            return true;
        }
        if ("body".equals(field)) {
            probeBodyState.setText(v);
            return true;
        }
        if ("method".equals(field)) {
            String upper = v.toUpperCase(Locale.ENGLISH);
            for (int i = 0; i < PROBE_METHODS.length; i++) {
                if (PROBE_METHODS[i].equals(upper)) {
                    probeMethodIndex = i;
                    return true;
                }
            }
            return false;
        }
        if ("content-type".equals(field)) {
            probeContentTypeIndex = findPresetIndex(CONTENT_TYPES, v);
            return true;
        }
        if ("accept".equals(field)) {
            probeAcceptIndex = findPresetIndex(ACCEPT_TYPES, v);
            return true;
        }
        return false;
    }

    // ---- Field-specific key handlers ----

    private boolean handleProbeMethodKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            probeField = PROBE_PATH;
            return true;
        }
        if (ke.isUp()) {
            goToLastProbeField();
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
        if (ke.isChar('+')) {
            addProbeQueryParamEmpty();
            return true;
        }
        return true;
    }

    private boolean handleProbePathKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            goToNextProbeFieldFrom(PROBE_PATH);
            return true;
        }
        if (ke.isUp()) {
            probeField = PROBE_METHOD;
            return true;
        }
        if (ke.isChar('+')) {
            addProbeQueryParamEmpty();
            return true;
        }
        FormHelper.handleTextInput(ke, probePathState);
        rescanPathParams();
        return true;
    }

    private boolean handleProbePathParamKeyEvent(KeyEvent ke) {
        if (probePathParams == null || probePathParams.isEmpty()) {
            goToNextProbeFieldFrom(PROBE_PATH_PARAMS);
            return true;
        }
        PathParam current = probePathParams.get(probeSelectedPathParam);

        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            if (probeSelectedPathParam < probePathParams.size() - 1) {
                probeSelectedPathParam++;
            } else {
                goToNextProbeFieldFrom(PROBE_PATH_PARAMS);
            }
            return true;
        }
        if (ke.isUp()) {
            if (probeSelectedPathParam > 0) {
                probeSelectedPathParam--;
            } else {
                probeField = PROBE_PATH;
            }
            return true;
        }
        FormHelper.handleTextInput(ke, current.input);
        return true;
    }

    private boolean handleProbeQueryParamKeyEvent(KeyEvent ke) {
        if (probeQueryParams == null || probeQueryParams.isEmpty()) {
            goToNextProbeFieldFrom(PROBE_QUERY_PARAMS);
            return true;
        }
        FormHelper.HeaderEntry current = probeQueryParams.get(probeSelectedQueryParam);
        TextInputState activeInput = probeEditingQueryKey ? current.keyInput() : current.valueInput();

        if (ke.isChar('+')) {
            addProbeQueryParamEmpty();
            return true;
        }
        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            if (probeEditingQueryKey) {
                probeEditingQueryKey = false;
            } else if (probeSelectedQueryParam < probeQueryParams.size() - 1) {
                probeSelectedQueryParam++;
                probeEditingQueryKey = true;
            } else {
                probeField = PROBE_CONTENT_TYPE;
            }
            return true;
        }
        if (ke.isUp()) {
            if (probeEditingQueryKey) {
                if (probeSelectedQueryParam > 0) {
                    probeSelectedQueryParam--;
                    probeEditingQueryKey = false;
                } else {
                    goToPrevProbeFieldFrom(PROBE_QUERY_PARAMS);
                }
            } else {
                probeEditingQueryKey = true;
            }
            return true;
        }
        if (ke.isDeleteBackward()) {
            if (probeEditingQueryKey && current.keyInput().text().isEmpty()) {
                probeQueryParams.remove(probeSelectedQueryParam);
                if (probeQueryParams.isEmpty()) {
                    probeQueryParams = null;
                    goToPrevProbeFieldFrom(PROBE_QUERY_PARAMS);
                } else if (probeSelectedQueryParam >= probeQueryParams.size()) {
                    probeSelectedQueryParam = probeQueryParams.size() - 1;
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
            if (!probeEditingQueryKey && activeInput.cursorPosition() == 0) {
                probeEditingQueryKey = true;
            } else {
                activeInput.moveCursorLeft();
            }
            return true;
        }
        if (ke.isRight()) {
            if (probeEditingQueryKey && activeInput.cursorPosition() == activeInput.text().length()) {
                probeEditingQueryKey = false;
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

    private boolean handleProbeCyclerKeyEvent(KeyEvent ke, String[] options, int field) {
        if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
            goToNextProbeFieldFrom(field);
            return true;
        }
        if (ke.isUp()) {
            goToPrevProbeFieldFrom(field);
            return true;
        }
        if (ke.isLeft()) {
            if (field == PROBE_CONTENT_TYPE) {
                probeContentTypeIndex = (probeContentTypeIndex - 1 + options.length) % options.length;
            } else {
                probeAcceptIndex = (probeAcceptIndex - 1 + options.length) % options.length;
            }
            return true;
        }
        if (ke.isRight() || ke.isChar(' ')) {
            if (field == PROBE_CONTENT_TYPE) {
                probeContentTypeIndex = (probeContentTypeIndex + 1) % options.length;
            } else {
                probeAcceptIndex = (probeAcceptIndex + 1) % options.length;
            }
            return true;
        }
        if (ke.isChar('+')) {
            addProbeHeaderEmpty();
            return true;
        }
        return true;
    }

    private boolean handleProbeBodyKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.TAB)) {
            goToNextProbeFieldFrom(PROBE_BODY);
            return true;
        }
        if (ke.isUp()) {
            if (probeBodyState.cursorRow() == 0) {
                goToPrevProbeFieldFrom(PROBE_BODY);
            } else {
                probeBodyState.moveCursorUp();
            }
            return true;
        }
        if (ke.isDown()) {
            if (probeBodyState.cursorRow() >= probeBodyState.lineCount() - 1) {
                goToNextProbeFieldFrom(PROBE_BODY);
            } else {
                probeBodyState.moveCursorDown();
            }
            return true;
        }
        if (ke.isLeft()) {
            probeBodyState.moveCursorLeft();
            return true;
        }
        if (ke.isRight()) {
            probeBodyState.moveCursorRight();
            return true;
        }
        if (ke.isHome()) {
            probeBodyState.moveCursorToLineStart();
            return true;
        }
        if (ke.isEnd()) {
            probeBodyState.moveCursorToLineEnd();
            return true;
        }
        if (ke.isDeleteBackward()) {
            probeBodyState.deleteBackward();
            return true;
        }
        if (ke.isDeleteForward()) {
            probeBodyState.deleteForward();
            return true;
        }
        if (ke.code() == KeyCode.CHAR) {
            probeBodyState.insert(ke.character());
            return true;
        }
        return true;
    }

    private boolean handleProbeHistoryKeyEvent(KeyEvent ke) {
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
        if (ke.isChar('+')) {
            addProbeQueryParamEmpty();
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
                    probeField = PROBE_ACCEPT;
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
                    probeField = PROBE_ACCEPT;
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

    // ---- Header and param helpers ----

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

    private void addProbeHeaderEmpty() {
        if (probeHeaders == null) {
            probeHeaders = new ArrayList<>();
        }
        probeHeaders.add(new FormHelper.HeaderEntry(new TextInputState(""), new TextInputState("")));
        probeField = PROBE_HEADERS;
        probeSelectedHeader = probeHeaders.size() - 1;
        probeEditingHeaderKey = true;
    }

    private void addProbeQueryParamEmpty() {
        if (probeQueryParams == null) {
            probeQueryParams = new ArrayList<>();
        }
        probeQueryParams.add(new FormHelper.HeaderEntry(new TextInputState(""), new TextInputState("")));
        probeField = PROBE_QUERY_PARAMS;
        probeSelectedQueryParam = probeQueryParams.size() - 1;
        probeEditingQueryKey = true;
    }

    private boolean hasProbeHeaders() {
        return probeHeaders != null && !probeHeaders.isEmpty();
    }

    private boolean hasPathParams() {
        return probePathParams != null && !probePathParams.isEmpty();
    }

    private boolean hasQueryParams() {
        return probeQueryParams != null && !probeQueryParams.isEmpty();
    }

    private TextInputState probeActiveTextInput() {
        if (probeField == PROBE_PATH) {
            return probePathState;
        }
        if (probeField == PROBE_PATH_PARAMS && hasPathParams()) {
            return probePathParams.get(probeSelectedPathParam).input;
        }
        if (probeField == PROBE_QUERY_PARAMS && hasQueryParams()) {
            FormHelper.HeaderEntry qp = probeQueryParams.get(probeSelectedQueryParam);
            return probeEditingQueryKey ? qp.keyInput() : qp.valueInput();
        }
        if (probeField == PROBE_HEADERS && hasProbeHeaders()) {
            FormHelper.HeaderEntry he = probeHeaders.get(probeSelectedHeader);
            return probeEditingHeaderKey ? he.keyInput() : he.valueInput();
        }
        return null;
    }

    // ---- Field navigation ----

    private void advanceProbeField() {
        goToNextProbeFieldFrom(probeField);
    }

    private void goToNextProbeFieldFrom(int from) {
        switch (from) {
            case PROBE_METHOD:
                probeField = PROBE_PATH;
                break;
            case PROBE_PATH:
                if (hasPathParams()) {
                    probeField = PROBE_PATH_PARAMS;
                    probeSelectedPathParam = 0;
                } else if (hasQueryParams()) {
                    probeField = PROBE_QUERY_PARAMS;
                    probeSelectedQueryParam = 0;
                    probeEditingQueryKey = true;
                } else {
                    probeField = PROBE_CONTENT_TYPE;
                }
                break;
            case PROBE_PATH_PARAMS:
                if (hasQueryParams()) {
                    probeField = PROBE_QUERY_PARAMS;
                    probeSelectedQueryParam = 0;
                    probeEditingQueryKey = true;
                } else {
                    probeField = PROBE_CONTENT_TYPE;
                }
                break;
            case PROBE_QUERY_PARAMS:
                probeField = PROBE_CONTENT_TYPE;
                break;
            case PROBE_CONTENT_TYPE:
                probeField = PROBE_ACCEPT;
                break;
            case PROBE_ACCEPT:
                if (hasProbeHeaders()) {
                    probeField = PROBE_HEADERS;
                    probeSelectedHeader = 0;
                    probeEditingHeaderKey = true;
                } else {
                    probeField = PROBE_BODY;
                }
                break;
            case PROBE_HEADERS:
                probeField = PROBE_BODY;
                break;
            case PROBE_BODY:
                if (!probeHistory.isEmpty()) {
                    probeField = PROBE_HISTORY;
                    probeHistoryIndex = 0;
                } else {
                    probeField = PROBE_METHOD;
                }
                break;
            case PROBE_HISTORY:
                probeField = PROBE_METHOD;
                break;
            default:
                probeField = PROBE_METHOD;
        }
    }

    private void goToPrevProbeFieldFrom(int from) {
        switch (from) {
            case PROBE_PATH:
                probeField = PROBE_METHOD;
                break;
            case PROBE_PATH_PARAMS:
                probeField = PROBE_PATH;
                break;
            case PROBE_QUERY_PARAMS:
                if (hasPathParams()) {
                    probeField = PROBE_PATH_PARAMS;
                    probeSelectedPathParam = probePathParams.size() - 1;
                } else {
                    probeField = PROBE_PATH;
                }
                break;
            case PROBE_CONTENT_TYPE:
                if (hasQueryParams()) {
                    probeField = PROBE_QUERY_PARAMS;
                    probeSelectedQueryParam = probeQueryParams.size() - 1;
                    probeEditingQueryKey = false;
                } else if (hasPathParams()) {
                    probeField = PROBE_PATH_PARAMS;
                    probeSelectedPathParam = probePathParams.size() - 1;
                } else {
                    probeField = PROBE_PATH;
                }
                break;
            case PROBE_ACCEPT:
                probeField = PROBE_CONTENT_TYPE;
                break;
            case PROBE_HEADERS:
                probeField = PROBE_ACCEPT;
                break;
            case PROBE_BODY:
                if (hasProbeHeaders()) {
                    probeField = PROBE_HEADERS;
                    probeSelectedHeader = probeHeaders.size() - 1;
                    probeEditingHeaderKey = false;
                } else {
                    probeField = PROBE_ACCEPT;
                }
                break;
            case PROBE_HISTORY:
                probeField = PROBE_BODY;
                break;
            default:
                probeField = PROBE_METHOD;
        }
    }

    private void goToLastProbeField() {
        if (!probeHistory.isEmpty()) {
            probeField = PROBE_HISTORY;
            probeHistoryIndex = 0;
        } else {
            probeField = PROBE_BODY;
        }
    }

    // ---- Path param scanning ----

    private List<PathParam> scanPathParams(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        Matcher m = PATH_PARAM_PATTERN.matcher(path);
        List<PathParam> params = null;
        while (m.find()) {
            if (params == null) {
                params = new ArrayList<>();
            }
            params.add(new PathParam(m.group(1), new TextInputState("")));
        }
        return params;
    }

    private void rescanPathParams() {
        String path = probePathState.text();
        List<PathParam> newParams = scanPathParams(path);
        if (newParams == null) {
            probePathParams = null;
            return;
        }
        if (probePathParams != null) {
            for (PathParam np : newParams) {
                for (PathParam op : probePathParams) {
                    if (np.name.equals(op.name) && !op.input.text().isEmpty()) {
                        np.input.setText(op.input.text());
                        break;
                    }
                }
            }
        }
        probePathParams = newParams;
    }

    // ---- History replay ----

    private void replayHistoryEntry(ProbeHistoryEntry entry) {
        for (int i = 0; i < PROBE_METHODS.length; i++) {
            if (PROBE_METHODS[i].equals(entry.method)) {
                probeMethodIndex = i;
                break;
            }
        }
        probePathState.setText(entry.path);
        rescanPathParams();
        if (probePathParams != null && entry.resolvedPath != null) {
            extractPathParamValues(entry.path, entry.resolvedPath);
        }
        probeQueryParams = null;
        if (entry.queryParams != null) {
            probeQueryParams = new ArrayList<>();
            for (FormHelper.HeaderEntry qp : entry.queryParams) {
                probeQueryParams.add(new FormHelper.HeaderEntry(
                        new TextInputState(qp.keyInput().text()),
                        new TextInputState(qp.valueInput().text())));
            }
        }
        probeSelectedQueryParam = 0;
        probeEditingQueryKey = true;
        probeContentTypeIndex = entry.contentTypeIndex;
        probeAcceptIndex = entry.acceptIndex;
        probeHeaders = null;
        probeSelectedHeader = 0;
        probeEditingHeaderKey = true;
        if (entry.headers != null) {
            for (FormHelper.HeaderEntry he : entry.headers) {
                addProbeHeader(he.keyInput().text(), he.valueInput().text());
            }
        }
        probeBodyState.setText(entry.body != null ? entry.body : "");
        probeField = PROBE_METHOD;
        probeViewRequest = true;
    }

    private void extractPathParamValues(String templatePath, String resolvedPath) {
        if (probePathParams == null || probePathParams.isEmpty()) {
            return;
        }
        StringBuilder regex = new StringBuilder();
        Matcher m = PATH_PARAM_PATTERN.matcher(templatePath);
        int last = 0;
        List<String> paramOrder = new ArrayList<>();
        while (m.find()) {
            regex.append(Pattern.quote(templatePath.substring(last, m.start())));
            regex.append("([^/]*)");
            paramOrder.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(templatePath.substring(last)));
        try {
            Matcher rm = Pattern.compile(regex.toString()).matcher(resolvedPath);
            if (rm.matches()) {
                for (int i = 0; i < paramOrder.size() && i < rm.groupCount(); i++) {
                    String name = paramOrder.get(i);
                    String value = rm.group(i + 1);
                    for (PathParam pp : probePathParams) {
                        if (pp.name.equals(name)) {
                            pp.input.setText(value);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore regex errors
        }
    }

    // ---- Request execution ----

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

        if (hasPathParams()) {
            for (PathParam pp : probePathParams) {
                String val = pp.input.text();
                if (!val.isEmpty()) {
                    path = path.replace("{" + pp.name + "}", val);
                }
            }
        }

        if (hasQueryParams()) {
            StringBuilder sb = new StringBuilder(path);
            char sep = path.contains("?") ? '&' : '?';
            for (FormHelper.HeaderEntry qp : probeQueryParams) {
                String k = qp.keyInput().text().trim();
                String v = qp.valueInput().text();
                if (!k.isEmpty()) {
                    sb.append(sep)
                            .append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                            .append('=')
                            .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
                    sep = '&';
                }
            }
            path = sb.toString();
        }

        String body = probeBodyState.text();
        if (body != null && body.trim().startsWith("file:")) {
            try {
                body = Files.readString(Path.of(body.trim().substring(5)));
            } catch (IOException e) {
                probeResponseStatus = "Error reading file: " + e.getMessage();
                probeResponseError = true;
                probeSending.set(false);
                return;
            }
        }
        String sendBody = body;
        String baseUrl = probeBaseUrl;

        List<FormHelper.HeaderEntry> allHeaders = new ArrayList<>();
        if (probeContentTypeIndex > 0) {
            allHeaders.add(new FormHelper.HeaderEntry(
                    new TextInputState("Content-Type"),
                    new TextInputState(CONTENT_TYPES[probeContentTypeIndex])));
        }
        if (probeAcceptIndex > 0) {
            allHeaders.add(new FormHelper.HeaderEntry(
                    new TextInputState("Accept"),
                    new TextInputState(ACCEPT_TYPES[probeAcceptIndex])));
        }
        if (hasProbeHeaders()) {
            for (FormHelper.HeaderEntry he : probeHeaders) {
                allHeaders.add(new FormHelper.HeaderEntry(
                        new TextInputState(he.keyInput().text()),
                        new TextInputState(he.valueInput().text())));
            }
        }
        List<FormHelper.HeaderEntry> hdrs = allHeaders.isEmpty() ? null : allHeaders;

        List<FormHelper.HeaderEntry> manualHeaderSnapshot = snapshotEntries(probeHeaders);
        List<FormHelper.HeaderEntry> qpSnapshot = snapshotEntries(probeQueryParams);
        int ctIdx = probeContentTypeIndex;
        int accIdx = probeAcceptIndex;
        String originalPath = probePathState.text();
        String resolvedPath = path;

        ctx.backgroundExecutor.execute(() -> {
            try {
                doProbeRequestInBackground(baseUrl, method, resolvedPath, originalPath, sendBody,
                        hdrs, manualHeaderSnapshot, qpSnapshot, ctIdx, accIdx);
            } finally {
                probeSending.set(false);
            }
        });
    }

    private void doProbeRequestInBackground(
            String baseUrl, String method, String resolvedPath, String originalPath,
            String body, List<FormHelper.HeaderEntry> allHeaders,
            List<FormHelper.HeaderEntry> manualHeaders,
            List<FormHelper.HeaderEntry> queryParams, int contentTypeIdx, int acceptIdx) {

        String url = baseUrl + resolvedPath;
        HttpHelper.HttpResult result = HttpHelper.sendRequest(url, method, body, allHeaders);

        String statusText;
        boolean error;
        List<String> headerLines;
        int httpStatus;

        if (result.error() != null) {
            statusText = "Error";
            error = true;
            httpStatus = 0;
            headerLines = new ArrayList<>();
            headerLines.add(result.error());
        } else {
            httpStatus = result.statusCode();
            statusText = String.valueOf(httpStatus);
            error = httpStatus >= 400;
            headerLines = result.headerLines();
        }

        ProbeHistoryEntry histEntry = new ProbeHistoryEntry(
                method, originalPath, resolvedPath, manualHeaders, body,
                queryParams, contentTypeIdx, acceptIdx,
                httpStatus, result.elapsed(), statusText, error);

        String finalRawBody = result.body();

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                probeResponseStatus = statusText;
                probeResponseElapsed = result.elapsed();
                probeResponseError = error;
                probeResponseHeaderLines = headerLines;
                probeResponseRawBody = finalRawBody;
                probeResponseScroll = 0;
                rebuildResponseLines();

                probeHistory.add(0, histEntry);
                if (probeHistory.size() > MAX_PROBE_HISTORY) {
                    probeHistory.remove(probeHistory.size() - 1);
                }
                probeHistoryIndex = 0;
            });
        }
    }

    // ---- Helpers ----

    private static String extractBaseUrl(String url) {
        if (url == null) {
            return "http://localhost:8080";
        }
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

    private static int findPresetIndex(String[] presets, String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        for (int i = 1; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(value) || value.contains(presets[i])) {
                return i;
            }
        }
        return 0;
    }

    private static List<FormHelper.HeaderEntry> snapshotEntries(List<FormHelper.HeaderEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        List<FormHelper.HeaderEntry> snap = new ArrayList<>();
        for (FormHelper.HeaderEntry he : entries) {
            snap.add(new FormHelper.HeaderEntry(
                    new TextInputState(he.keyInput().text()),
                    new TextInputState(he.valueInput().text())));
        }
        return snap;
    }

    private String buildFullUrl() {
        String base = probeBaseUrl != null ? probeBaseUrl : "";
        String path = probePathState.text();
        if (hasPathParams()) {
            for (PathParam pp : probePathParams) {
                String val = pp.input.text();
                if (!val.isEmpty()) {
                    path = path.replace("{" + pp.name + "}", val);
                }
            }
        }
        return base + path;
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

    // ---- Rendering ----

    void render(Frame frame, Rect area) {
        frame.renderWidget(Clear.INSTANCE, area);

        Rect inner = area;

        int pathParamCount = hasPathParams() ? probePathParams.size() : 0;
        int queryParamCount = hasQueryParams() ? probeQueryParams.size() : 0;
        int headerCount = hasProbeHeaders() ? probeHeaders.size() : 0;
        int bodyHeight = 6;
        int requestHeight = 3 // method + URL + path
                            + pathParamCount
                            + queryParamCount + (queryParamCount > 0 ? 1 : 0)
                            + 2 // content-type + accept
                            + headerCount + (headerCount > 0 ? 1 : 0)
                            + bodyHeight
                            + 3; // borders and padding
        int historyHeight = probeHistory.isEmpty() ? 3 : Math.min(4, probeHistory.size()) + 2;

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

    void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "Esc", "back");
        TuiHelper.hint(spans, "Tab", "fields");
        TuiHelper.hint(spans, "F5", "send");
        TuiHelper.hint(spans, "F4", probeViewRequest ? "response" : "request");
        TuiHelper.hint(spans, "+", "param/header");
        TuiHelper.hint(spans, "p", "pretty" + (probePrettyPrint ? " [on]" : ""));
        if (!probeHistory.isEmpty()) {
            TuiHelper.hintLast(spans, TuiIcons.HINT_SCROLL, "history");
        }
    }

    private void renderProbeRequest(Frame frame, Rect area) {
        String method = PROBE_METHODS[probeMethodIndex];
        Title title = Title.from(Line.from(
                Span.styled(" HTTP Probe ", Style.EMPTY.bold()),
                Span.styled(method, methodStyle(method).bold()),
                Span.raw(" " + probePathState.text() + " ")));

        boolean requestFocused = probeField != PROBE_HISTORY;
        Style borderStyle = requestFocused ? Style.EMPTY.fg(Theme.accent()) : Style.EMPTY;
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(borderStyle).title(title).build();
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
        String fullUrl = buildFullUrl();
        Rect urlArea = new Rect(innerX + labelW, row, fieldW, 1);
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(fullUrl, Theme.info().dim().hyperlink(fullUrl)))), urlArea);

        // Path input
        row++;
        FormHelper.renderLabel(frame, innerX, row, labelW, "Path:", probeField == PROBE_PATH);
        Rect pathArea = new Rect(innerX + labelW, row, fieldW, 1);
        if (probeField == PROBE_PATH && !probeSending.get()) {
            TextInput textInput = TextInput.builder().cursorStyle(Style.EMPTY.reversed()).build();
            // renderWithCursor (not renderStatefulWidget) so the caret is painted on the active field
            textInput.renderWithCursor(pathArea, frame.buffer(), probePathState, frame);
        } else {
            String pathText = probePathState.text();
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(pathText.isEmpty() ? "/" : pathText,
                            pathText.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY))),
                    pathArea);
        }

        // Path parameters ({xxx} placeholders)
        if (hasPathParams()) {
            for (int i = 0; i < probePathParams.size(); i++) {
                row++;
                PathParam pp = probePathParams.get(i);
                boolean isSelected = probeField == PROBE_PATH_PARAMS && probeSelectedPathParam == i;
                String label = i == 0 ? "Params:" : "";
                FormHelper.renderLabel(frame, innerX, row, labelW, label,
                        isSelected || (i == 0 && probeField == PROBE_PATH_PARAMS));

                int fieldX = innerX + labelW;
                String paramLabel = "{" + pp.name + "}: ";
                int paramLabelW = Math.min(paramLabel.length(), 15);
                Rect paramLabelArea = new Rect(fieldX, row, paramLabelW, 1);
                frame.renderWidget(Paragraph.from(Line.from(
                        Span.styled(paramLabel, isSelected ? Theme.label().bold() : Theme.label()))),
                        paramLabelArea);

                Rect paramInputArea = new Rect(fieldX + paramLabelW, row, fieldW - paramLabelW, 1);
                if (isSelected && !probeSending.get()) {
                    TextInput textInput = TextInput.builder()
                            .cursorStyle(Style.EMPTY.reversed())
                            .placeholder("value for {" + pp.name + "}")
                            .build();
                    // renderWithCursor (not renderStatefulWidget) so the caret is painted on the active field
                    textInput.renderWithCursor(paramInputArea, frame.buffer(), pp.input, frame);
                } else {
                    String val = pp.input.text();
                    frame.renderWidget(Paragraph.from(Line.from(
                            Span.styled(val.isEmpty() ? "—" : val,
                                    val.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY))),
                            paramInputArea);
                }
            }
        }

        // Query parameters
        if (hasQueryParams()) {
            row = renderKeyValueSection(frame, row, innerX, labelW, fieldW, "Query:",
                    probeQueryParams, PROBE_QUERY_PARAMS, probeSelectedQueryParam, probeEditingQueryKey);
        }

        // Content-Type cycler
        row++;
        renderCycler(frame, innerX, row, labelW, fieldW, "Content:",
                CONTENT_TYPES, probeContentTypeIndex, probeField == PROBE_CONTENT_TYPE);

        // Accept cycler
        row++;
        renderCycler(frame, innerX, row, labelW, fieldW, "Accept:",
                ACCEPT_TYPES, probeAcceptIndex, probeField == PROBE_ACCEPT);

        // Headers
        if (hasProbeHeaders()) {
            row = renderKeyValueSection(frame, row, innerX, labelW, fieldW, "Headers:",
                    probeHeaders, PROBE_HEADERS, probeSelectedHeader, probeEditingHeaderKey);
        }

        // Body input (multi-line TextArea)
        row++;
        int bodyH = 6;
        FormHelper.renderLabel(frame, innerX, row, labelW, "Body:", probeField == PROBE_BODY);
        Rect bodyArea = new Rect(innerX + labelW, row, fieldW, bodyH);
        Style bodyBorderStyle = probeField == PROBE_BODY ? Style.EMPTY.fg(Theme.accent()) : Theme.muted();
        Block bodyBlock = Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(bodyBorderStyle)
                .build();
        Rect bodyInner = bodyBlock.inner(bodyArea);
        frame.renderWidget(bodyBlock, bodyArea);
        TextArea textArea = TextArea.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("body text, JSON, XML, or file:payload.json")
                .build();
        if (probeField == PROBE_BODY && !probeSending.get()) {
            textArea.renderWithCursor(bodyInner, frame.buffer(), probeBodyState, frame);
        } else {
            textArea.render(bodyInner, frame.buffer(), probeBodyState);
        }
    }

    private void renderCycler(
            Frame frame, int x, int row, int labelW, int fieldW,
            String label, String[] options, int activeIndex, boolean selected) {

        FormHelper.renderLabel(frame, x, row, labelW, label, selected);
        Rect fieldArea = new Rect(x + labelW, row, fieldW, 1);
        String display = activeIndex == 0 ? "(none)" : options[activeIndex];
        String leftArr = selected ? TuiIcons.ARROW_LEFT + " " : "  ";
        String rightArr = selected ? " " + TuiIcons.ARROW_RIGHT : "";
        Style style = activeIndex == 0 ? Style.EMPTY.dim() : Style.EMPTY;
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(leftArr, style),
                Span.styled(display, selected ? style.bold() : style),
                Span.styled(rightArr, style))), fieldArea);
    }

    private int renderKeyValueSection(
            Frame frame, int startRow, int innerX, int labelW, int fieldW,
            String sectionLabel, List<FormHelper.HeaderEntry> entries,
            int fieldId, int selectedIdx, boolean editingKey) {

        int keyW = Math.min(20, fieldW / 3);
        int valW = fieldW - keyW - 3;
        int row = startRow;
        for (int i = 0; i < entries.size(); i++) {
            row++;
            boolean isSelected = probeField == fieldId && selectedIdx == i;
            String label = i == 0 ? sectionLabel : "";
            FormHelper.renderLabel(frame, innerX, row, labelW, label,
                    isSelected || (i == 0 && probeField == fieldId));

            FormHelper.HeaderEntry he = entries.get(i);
            int fieldX = innerX + labelW;

            Rect keyArea = new Rect(fieldX, row, keyW, 1);
            if (isSelected && editingKey && !probeSending.get()) {
                TextInput keyInput = TextInput.builder().cursorStyle(Style.EMPTY.reversed()).build();
                // renderWithCursor (not renderStatefulWidget) so the caret is painted on the active field
                keyInput.renderWithCursor(keyArea, frame.buffer(), he.keyInput(), frame);
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
            if (isSelected && !editingKey && !probeSending.get()) {
                TextInput valInput = TextInput.builder().cursorStyle(Style.EMPTY.reversed()).build();
                // renderWithCursor (not renderStatefulWidget) so the caret is painted on the active field
                valInput.renderWithCursor(valArea, frame.buffer(), he.valueInput(), frame);
            } else {
                String valText = he.valueInput().text();
                Style valStyle = valText.isEmpty() ? Style.EMPTY.dim()
                        : isSelected ? Style.EMPTY.bold() : Style.EMPTY;
                frame.renderWidget(Paragraph.from(Line.from(
                        Span.styled(valText.isEmpty() ? "<value>" : valText, valStyle))), valArea);
            }
        }
        return row;
    }

    private void renderProbeResponse(Frame frame, Rect area) {
        boolean hasResponse = probeResponseStatus != null;

        Title title;
        if (!hasResponse) {
            title = Title.from(Line.from(Span.styled(" Request ", Style.EMPTY.bold())));
        } else {
            Style reqStyle = probeViewRequest ? Style.EMPTY.bold() : Style.EMPTY.dim();
            Style resStyle = probeViewRequest ? Style.EMPTY.dim() : Style.EMPTY.bold();

            String statusSuffix = "";
            Style statusColor = Style.EMPTY;
            if (probeResponseError) {
                statusSuffix = " " + probeResponseStatus;
                statusColor = Theme.error();
            } else if ("Sending...".equals(probeResponseStatus)) {
                statusSuffix = " Sending...";
                statusColor = Theme.warning();
            } else {
                statusSuffix = " " + probeResponseStatus;
                if (probeResponseElapsed > 0) {
                    statusSuffix += " (" + probeResponseElapsed + "ms)";
                }
                statusColor = statusStyle(probeResponseStatus);
            }

            title = Title.from(Line.from(
                    Span.styled(" Request ", reqStyle),
                    Span.styled("|", Style.EMPTY.dim()),
                    Span.styled(" Response", resStyle),
                    Span.styled(statusSuffix + " ", statusColor.bold())));
        }

        if (!hasResponse || probeViewRequest) {
            List<Line> preview = buildRequestPreview();
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(preview))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                            .build(),
                    area);
            return;
        }

        if (probeResponseLines == null || probeResponseLines.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" No response content", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int totalLines = probeResponseLines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        probeResponseScroll = Math.min(probeResponseScroll, maxScroll);

        int end = Math.min(probeResponseScroll + visibleLines, totalLines);
        List<Line> lines = new ArrayList<>();
        for (int i = probeResponseScroll; i < end; i++) {
            String line = probeResponseLines.get(i);
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

        Block.Builder blockBuilder = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title);
        if (totalLines > visibleLines) {
            int pct = (int) ((probeResponseScroll + visibleLines) * 100L / totalLines);
            String scrollInfo = " " + pct + "% (" + totalLines + " lines) ";
            blockBuilder.titleBottom(Title.from(Line.from(Span.styled(scrollInfo, Style.EMPTY.dim()))));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(blockBuilder.build())
                        .build(),
                area);
    }

    private List<Line> buildRequestPreview() {
        List<Line> lines = new ArrayList<>();
        String method = PROBE_METHODS[probeMethodIndex];
        String fullUrl = buildFullUrl();

        if (hasQueryParams()) {
            StringBuilder sb = new StringBuilder(fullUrl);
            char sep = fullUrl.contains("?") ? '&' : '?';
            for (FormHelper.HeaderEntry qp : probeQueryParams) {
                String k = qp.keyInput().text().trim();
                String v = qp.valueInput().text();
                if (!k.isEmpty()) {
                    sb.append(sep).append(k).append('=').append(v);
                    sep = '&';
                }
            }
            fullUrl = sb.toString();
        }

        lines.add(Line.from(
                Span.styled(" " + method, methodStyle(method).bold()),
                Span.raw(" " + fullUrl)));
        lines.add(Line.from(Span.raw("")));

        String ct = CONTENT_TYPES[probeContentTypeIndex];
        if (!ct.isEmpty()) {
            lines.add(Line.from(
                    Span.styled(" Content-Type: ", Theme.label()),
                    Span.raw(ct)));
        }
        String acc = ACCEPT_TYPES[probeAcceptIndex];
        if (!acc.isEmpty()) {
            lines.add(Line.from(
                    Span.styled(" Accept: ", Theme.label()),
                    Span.raw(acc)));
        }
        if (hasProbeHeaders()) {
            for (FormHelper.HeaderEntry h : probeHeaders) {
                String k = h.keyInput().text().trim();
                String v = h.valueInput().text();
                if (!k.isEmpty()) {
                    lines.add(Line.from(
                            Span.styled(" " + k + ": ", Theme.label()),
                            Span.raw(v)));
                }
            }
        }

        String body = probeBodyState.text();
        if (body != null && !body.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            for (String bl : body.split("\n", -1)) {
                lines.add(Line.from(Span.raw(" " + bl)));
            }
        }

        if (lines.size() <= 1) {
            lines.add(Line.from(Span.styled(" Press F5 to send", Style.EMPTY.dim())));
        }
        return lines;
    }

    private void renderProbeHistory(Frame frame, Rect area) {
        String title = " History [" + probeHistory.size() + "] ";
        boolean historyFocused = probeField == PROBE_HISTORY;
        Style histBorderStyle = historyFocused ? Style.EMPTY.fg(Theme.accent()) : Style.EMPTY;

        if (probeHistory.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" No requests yet", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .borderStyle(histBorderStyle).title(title).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }

        int start = 0;
        if (probeHistoryIndex >= visibleLines) {
            start = probeHistoryIndex - visibleLines + 1;
        }
        int end = Math.min(start + visibleLines, probeHistory.size());

        List<Line> lines = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ProbeHistoryEntry entry = probeHistory.get(i);
            boolean selected = historyFocused && i == probeHistoryIndex;
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
                    Span.styled(entry.resolvedPath + "  ", lineStyle),
                    Span.styled(statusStr, selected ? statusStyle(statusStr) : statusStyle(statusStr).dim()),
                    Span.styled("  " + elapsedStr, Style.EMPTY.dim()),
                    Span.styled(bodySnippet, Style.EMPTY.dim())));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .borderStyle(histBorderStyle).title(title).build())
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

    // ---- JSON serialization ----

    JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.put("tab", "HTTP");
        result.put("probeMode", true);
        result.put("method", PROBE_METHODS[probeMethodIndex]);
        result.put("path", probePathState.text());
        String ct = CONTENT_TYPES[probeContentTypeIndex];
        if (!ct.isEmpty()) {
            result.put("contentType", ct);
        }
        String acc = ACCEPT_TYPES[probeAcceptIndex];
        if (!acc.isEmpty()) {
            result.put("accept", acc);
        }
        if (hasPathParams()) {
            JsonObject pp = new JsonObject();
            for (PathParam p : probePathParams) {
                pp.put(p.name, p.input.text());
            }
            result.put("pathParams", pp);
        }
        if (hasQueryParams()) {
            JsonArray qp = new JsonArray();
            for (FormHelper.HeaderEntry e : probeQueryParams) {
                JsonObject entry = new JsonObject();
                entry.put("key", e.keyInput().text());
                entry.put("value", e.valueInput().text());
                qp.add(entry);
            }
            result.put("queryParams", qp);
        }
        if (hasProbeHeaders()) {
            JsonArray hd = new JsonArray();
            for (FormHelper.HeaderEntry e : probeHeaders) {
                JsonObject entry = new JsonObject();
                entry.put("key", e.keyInput().text());
                entry.put("value", e.valueInput().text());
                hd.add(entry);
            }
            result.put("headers", hd);
        }
        String body = probeBodyState.text();
        if (body != null && !body.isEmpty()) {
            result.put("body", body);
        }
        if (probeResponseStatus != null) {
            result.put("responseStatus", probeResponseStatus);
        }
        if (probeResponseElapsed > 0) {
            result.put("responseElapsed", probeResponseElapsed);
        }
        if (probeResponseRawBody != null && !probeResponseRawBody.isEmpty()) {
            result.put("responseBody", probeResponseRawBody);
        }
        if (!probeHistory.isEmpty()) {
            JsonArray hist = new JsonArray();
            for (ProbeHistoryEntry he : probeHistory) {
                JsonObject entry = new JsonObject();
                entry.put("method", he.method);
                entry.put("path", he.path);
                entry.put("statusCode", he.statusCode);
                entry.put("elapsed", he.elapsed);
                if (he.error) {
                    entry.put("error", true);
                }
                hist.add(entry);
            }
            result.put("history", hist);
        }
        return result;
    }

    // ---- Inner records ----

    record PathParam(String name, TextInputState input) {
    }

    record ProbeHistoryEntry(
            String method, String path, String resolvedPath,
            List<FormHelper.HeaderEntry> headers, String body,
            List<FormHelper.HeaderEntry> queryParams,
            int contentTypeIndex, int acceptIndex,
            int statusCode, long elapsed,
            String statusText, boolean error) {
    }
}
