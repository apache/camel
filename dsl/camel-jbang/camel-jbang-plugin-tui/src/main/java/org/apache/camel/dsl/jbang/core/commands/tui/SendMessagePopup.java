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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;

class SendMessagePopup {

    private static final int FIELD_ROUTE = 0;
    private static final int FIELD_BODY = 1;
    private static final int FIELD_MODE = 2;
    private static final int FIELD_COUNT = 3;

    private boolean visible;
    private boolean sending;
    private String pid;
    private String integrationName;
    private List<RouteInfo> routes;
    private int selectedRouteIndex;
    private final TextInputState bodyState = new TextInputState("");
    private int selectedField = FIELD_BODY;
    private boolean inOut;
    private String resultMessage;
    private boolean resultError;

    boolean isVisible() {
        return visible;
    }

    void open(MonitorContext ctx, String pid, String name, List<RouteInfo> routes, String preSelectRouteId) {
        if (pid == null || routes == null || routes.isEmpty()) {
            return;
        }
        this.pid = pid;
        this.integrationName = name;
        this.routes = new ArrayList<>(routes);
        this.selectedRouteIndex = findSmartDefault(preSelectRouteId);
        this.bodyState.clear();
        this.selectedField = FIELD_BODY;
        this.inOut = false;
        this.resultMessage = null;
        this.resultError = false;
        this.sending = false;
        this.visible = true;
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (sending) {
            return true;
        }
        if (ke.isCancel()) {
            close();
            return true;
        }
        if (ke.isConfirm()) {
            return true;
        }
        if (selectedField == FIELD_BODY) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                selectedField = FIELD_MODE;
                return true;
            }
            if (ke.isUp()) {
                if (routes.size() > 1) {
                    selectedField = FIELD_ROUTE;
                }
                return true;
            }
            handleTextInput(ke);
            return true;
        }
        if (selectedField == FIELD_ROUTE) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                selectedField = FIELD_BODY;
                return true;
            }
            if (ke.isUp()) {
                selectedField = FIELD_MODE;
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
        if (selectedField == FIELD_MODE) {
            if (ke.isKey(KeyCode.TAB) || ke.isDown()) {
                if (routes.size() > 1) {
                    selectedField = FIELD_ROUTE;
                } else {
                    selectedField = FIELD_BODY;
                }
                return true;
            }
            if (ke.isUp()) {
                selectedField = FIELD_BODY;
                return true;
            }
            if (ke.isLeft() || ke.isRight() || ke.code() == KeyCode.CHAR) {
                inOut = !inOut;
                return true;
            }
            return true;
        }
        return true;
    }

    void doSend(MonitorContext ctx, ScheduledExecutorService scheduler) {
        if (!visible || sending || ctx == null || scheduler == null) {
            return;
        }
        sending = true;
        resultMessage = "Sending...";
        resultError = false;

        String body = bodyState.text();
        RouteInfo route = routes.get(selectedRouteIndex);
        String endpoint = route.routeId;
        String mep = inOut ? "InOut" : "InOnly";
        String targetPid = pid;

        scheduler.execute(() -> {
            try {
                Path outputFile = ctx.getOutputFile(targetPid);
                PathUtils.deleteFile(outputFile);

                JsonObject root = new JsonObject();
                root.put("action", "send");
                root.put("endpoint", endpoint);
                root.put("body", body);
                root.put("exchangePattern", mep);
                root.put("pollTimeout", 20000);
                root.put("poll", false);

                Path actionFile = ctx.getActionFile(targetPid);
                PathUtils.writeTextSafely(root.toJson(), actionFile);

                JsonObject response = MonitorContext.pollJsonResponse(outputFile, 25000);
                PathUtils.deleteFile(outputFile);

                if (response != null) {
                    String status = response.getString("status");
                    Object elapsed = response.get("elapsed");
                    if ("success".equals(status)) {
                        String msg = "Sent (" + elapsed + "ms)";
                        JsonObject message = response.getMap("message");
                        if (inOut && message != null) {
                            String replyBody = objToString(message.get("body"));
                            if (replyBody != null && !replyBody.isEmpty()) {
                                msg += " - Reply: " + truncate(replyBody, 40);
                            }
                        }
                        resultMessage = msg;
                        resultError = false;
                    } else {
                        JsonObject exception = response.getMap("exception");
                        if (exception != null) {
                            String exMsg = exception.getString("message");
                            resultMessage = "Error: " + (exMsg != null ? truncate(exMsg, 50) : status);
                        } else {
                            resultMessage = "Error: " + (status != null ? status : "unknown");
                        }
                        resultError = true;
                    }
                } else {
                    resultMessage = "No response from integration";
                    resultError = true;
                }
            } catch (Exception e) {
                resultMessage = "Error: " + e.getMessage();
                resultError = true;
            } finally {
                sending = false;
            }
        });
    }

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }

        int popupW = Math.min(62, area.width() - 4);
        int popupH = routes.size() > 1 ? 14 : 12;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        String title = " Send Message";
        if (integrationName != null) {
            title += " - " + truncate(integrationName, 20);
        }
        title += " ";
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .titleBottom(Title.from(Line.from(
                        Span.styled(" Enter", MonitorContext.HINT_KEY_STYLE),
                        Span.raw(" send │"),
                        Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE),
                        Span.raw(" close "))))
                .build();
        frame.renderWidget(block, popup);

        int innerX = popup.left() + 2;
        int innerW = popup.width() - 4;
        int labelW = 8;
        int fieldW = innerW - labelW;
        int row = popup.top() + 1;

        // Route selector (only if multiple routes)
        if (routes.size() > 1) {
            row++;
            renderLabel(frame, innerX, row, labelW, "Route:", selectedField == FIELD_ROUTE);
            RouteInfo ri = routes.get(selectedRouteIndex);
            String routeDisplay = ri.routeId + " (" + truncateUri(ri.from, fieldW - ri.routeId.length() - 6) + ")";
            String arrow = selectedField == FIELD_ROUTE ? "◀ " : "  ";
            String arrowR = selectedField == FIELD_ROUTE ? " ▶" : "  ";
            Style routeStyle = selectedField == FIELD_ROUTE ? Style.EMPTY.bold() : Style.EMPTY;
            Rect routeArea = new Rect(innerX + labelW, row, fieldW, 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(arrow, routeStyle),
                    Span.styled(routeDisplay, routeStyle),
                    Span.styled(arrowR, routeStyle))), routeArea);
        }

        // Body input
        row += 2;
        renderLabel(frame, innerX, row, labelW, "Body:", selectedField == FIELD_BODY);
        Rect bodyArea = new Rect(innerX + labelW, row, fieldW, 1);
        if (selectedField == FIELD_BODY && !sending) {
            TextInput textInput = TextInput.builder()
                    .cursorStyle(Style.EMPTY.reversed())
                    .build();
            frame.renderStatefulWidget(textInput, bodyArea, bodyState);
        } else {
            String text = bodyState.text();
            Style style = text.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY;
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(text.isEmpty() ? "—" : text, style))), bodyArea);
        }

        // Mode toggle
        row += 2;
        renderLabel(frame, innerX, row, labelW, "Mode:", selectedField == FIELD_MODE);
        Rect modeArea = new Rect(innerX + labelW, row, fieldW, 1);
        Style inOnlyStyle = !inOut ? Style.EMPTY.bold().reversed() : Style.EMPTY.dim();
        Style inOutStyle = inOut ? Style.EMPTY.bold().reversed() : Style.EMPTY.dim();
        frame.renderWidget(Paragraph.from(Line.from(
                Span.styled(" InOnly ", inOnlyStyle),
                Span.raw("  "),
                Span.styled(" InOut ", inOutStyle))), modeArea);

        // Result line
        if (resultMessage != null) {
            row += 2;
            Style resultStyle = resultError
                    ? Style.EMPTY.fg(Color.LIGHT_RED)
                    : Style.EMPTY.fg(Color.GREEN);
            Rect resultArea = new Rect(innerX, row, innerW, 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(resultMessage, resultStyle))), resultArea);
        }
    }

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

    private void handleTextInput(KeyEvent ke) {
        if (ke.isDeleteBackward()) {
            bodyState.deleteBackward();
        } else if (ke.isDeleteForward()) {
            bodyState.deleteForward();
        } else if (ke.isLeft()) {
            bodyState.moveCursorLeft();
        } else if (ke.isRight()) {
            bodyState.moveCursorRight();
        } else if (ke.isHome()) {
            bodyState.moveCursorToStart();
        } else if (ke.isEnd()) {
            bodyState.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR) {
            bodyState.insert(ke.character());
        }
    }

    private void renderLabel(Frame frame, int x, int y, int w, String label, boolean selected) {
        Style style = selected ? Style.EMPTY.bold() : Style.EMPTY.dim();
        Rect labelArea = new Rect(x, y, w, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(label, style))), labelArea);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String truncateUri(String uri, int max) {
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        String clean = q > 0 ? uri.substring(0, q) : uri;
        return truncate(clean, max);
    }

    private static String objToString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }
}
