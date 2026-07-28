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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class JfrTab extends AbstractTab {

    private static final Style LABEL = Theme.muted();
    private static final Style OK = Style.EMPTY.fg(Theme.baseFg()).bold();

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private boolean registered;
    private List<String> recordings = List.of();
    private Map<String, Boolean> events = new LinkedHashMap<>();
    private String message;
    private String errorMessage;
    private boolean dataLoaded;

    JfrTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public void onTabSelected() {
        if (!dataLoaded) {
            refreshStatus();
        }
    }

    @Override
    public void onIntegrationChanged() {
        registered = false;
        recordings = List.of();
        events = new LinkedHashMap<>();
        message = null;
        errorMessage = null;
        dataLoaded = false;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.F5)) {
            refreshStatus();
            return true;
        }
        if (ke.isCharIgnoreCase('e')) {
            toggleAll(true);
            return true;
        }
        if (ke.isCharIgnoreCase('d')) {
            toggleAll(false);
            return true;
        }
        if (ke.isCharIgnoreCase('j')) {
            generateJfc();
            return true;
        }
        return false;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && !dataLoaded) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  Loading JFR status...", LABEL))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" JFR Runtime Instrumentation ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && !dataLoaded) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  " + errorMessage, Theme.error()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" JFR Runtime Instrumentation ").build())
                            .build(),
                    area);
            return;
        }

        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(
                Span.styled("  registered: ", LABEL),
                Span.styled(String.valueOf(registered), registered ? OK : Theme.error())));
        lines.add(Line.from(Span.raw("")));
        if (recordings.isEmpty()) {
            lines.add(Line.from(Span.styled("  no active recording", LABEL)));
        } else {
            for (String recording : recordings) {
                lines.add(Line.from(Span.styled("  recording: ", LABEL), Span.styled(recording, OK)));
            }
        }
        lines.add(Line.from(Span.raw("")));
        for (Map.Entry<String, Boolean> entry : events.entrySet()) {
            boolean enabled = Boolean.TRUE.equals(entry.getValue());
            lines.add(Line.from(
                    Span.styled("  " + entry.getKey() + ": ", LABEL),
                    Span.styled(enabled ? "enabled" : "disabled", enabled ? OK : Theme.error())));
        }
        if (message != null) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled("  " + message, LABEL)));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(" JFR Runtime Instrumentation ").build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "E", "enable all");
        hint(spans, "D", "disable all");
        hint(spans, "J", "generate .jfc");
        hintLast(spans, "F5", "refresh");
    }

    private void refreshStatus() {
        if (ctx.selectedPid == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                JsonObject root = new JsonObject();
                root.put("action", "jfr");
                root.put("command", "status");

                JsonObject jo = ctx.executeAction(pid, root, 5000);
                applyStatus(jo);
            } catch (Exception e) {
                applyError("Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    private void toggleAll(boolean enable) {
        if (ctx.selectedPid == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                JsonObject root = new JsonObject();
                root.put("action", "jfr");
                root.put("command", enable ? "enable" : "disable");
                root.put("event", "all");

                JsonObject jo = ctx.executeAction(pid, root, 5000);
                String result = jo != null ? jo.getString("result") : null;
                applyMessage(result != null ? result : "no response from integration");
                refreshStatus();
            } catch (Exception e) {
                applyError("Error: " + e.getMessage());
                loading.set(false);
            }
        });
    }

    private void generateJfc() {
        if (ctx.selectedPid == null) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                JsonObject root = new JsonObject();
                root.put("action", "jfr");
                root.put("command", "jfc");

                JsonObject jo = ctx.executeAction(pid, root, 5000);
                String jfc = jo != null ? jo.getString("jfc") : null;
                if (jfc != null && ctx.openMarkdownCallback != null && ctx.runner != null) {
                    ctx.runner.runOnRenderThread(
                            () -> ctx.openMarkdownCallback.accept("JFR .jfc overlay", "```\n" + jfc + "\n```"));
                } else {
                    applyMessage(jfc != null ? jfc : "no response from integration");
                }
            } catch (Exception e) {
                applyError("Error: " + e.getMessage());
            }
        });
    }

    private void applyStatus(JsonObject jo) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (jo == null) {
                errorMessage = "No response from integration";
                dataLoaded = true;
                return;
            }
            registered = Boolean.TRUE.equals(jo.getBoolean("registered"));
            List<String> recs = new ArrayList<>();
            JsonArray recordingsArr = (JsonArray) jo.get("recordings");
            if (recordingsArr != null) {
                for (Object o : recordingsArr) {
                    JsonObject rec = (JsonObject) o;
                    recs.add(rec.getString("name") + " (state=" + rec.getString("state") + ")");
                }
            }
            recordings = recs;
            Map<String, Boolean> evts = new LinkedHashMap<>();
            JsonObject eventsObj = (JsonObject) jo.get("events");
            if (eventsObj != null) {
                eventsObj.forEach((k, v) -> evts.put(k, Boolean.TRUE.equals(v)));
            }
            events = evts;
            errorMessage = null;
            dataLoaded = true;
        });
    }

    private void applyMessage(String msg) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> message = msg);
    }

    private void applyError(String error) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> message = error);
    }

    @Override
    public SelectionContext getSelectionContext() {
        return null;
    }

    @Override
    public String description() {
        return "JFR runtime instrumentation status and live event toggling";
    }

    @Override
    public String getHelpText() {
        return """
                # JFR

                The JFR tab shows the live status of camel-jfr's runtime instrumentation:
                whether it is registered, any active Flight Recorder recording(s), and the
                current enabled/disabled state of each of the six runtime events (route,
                processor, exchange, send, failed, redelivery).

                ## Keys

                - `E` — enable all runtime events on every active recording
                - `D` — disable all runtime events on every active recording
                - `J` — generate a `.jfc` overlay with the current event selection
                - `F5` — refresh status
                - `Esc` — back

                Toggling requires at least one active recording; start one first via
                `--jfr`, `jcmd <pid> JFR.start`, or JMX.
                """;
    }
}
