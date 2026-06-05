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
import java.util.Collections;
import java.util.List;
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
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class StartupTab implements MonitorTab {

    private static final Style LABEL = Style.EMPTY.dim();
    private static final Style VALUE = Style.EMPTY.fg(Color.WHITE).bold();
    private static final Style HEADER = Style.EMPTY.fg(Color.YELLOW).bold();

    private final MonitorContext ctx;
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private List<StartupStep> steps = Collections.emptyList();
    private int scrollOffset;
    private long totalDuration;
    private long maxDuration;
    private long minDurationColor;
    private long maxDurationColor;
    private String errorMessage;
    private boolean dataLoaded;

    StartupTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        if (!dataLoaded) {
            loadStartupData();
        }
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isUp()) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        if (ke.isDown()) {
            scrollOffset++;
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            scrollOffset = Math.max(0, scrollOffset - 20);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            scrollOffset += 20;
            return true;
        }
        if (ke.isHome()) {
            scrollOffset = 0;
            return true;
        }
        if (ke.isEnd()) {
            scrollOffset = Integer.MAX_VALUE;
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            loadStartupData();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
        scrollOffset = Math.max(0, scrollOffset - 1);
    }

    @Override
    public void navigateDown() {
        scrollOffset++;
    }

    @Override
    public void onIntegrationChanged() {
        steps = Collections.emptyList();
        scrollOffset = 0;
        errorMessage = null;
        dataLoaded = false;
        loadStartupData();
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && steps.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  Loading startup data...", LABEL))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Startup Timeline ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && steps.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  " + errorMessage, Style.EMPTY.fg(Color.LIGHT_RED)))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Startup Timeline ").build())
                            .build(),
                    area);
            return;
        }

        if (steps.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(
                                            "  No startup data available. The integration may not have a startup recorder enabled.",
                                            LABEL))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Startup Timeline ").build())
                            .build(),
                    area);
            return;
        }

        String title = String.format(" Startup Timeline — Total: %dms, Steps: %d ", totalDuration, steps.size());
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (inner.height() < 1 || inner.width() < 10) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, steps.size() - visibleLines);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int end = Math.min(scrollOffset + visibleLines, steps.size());

        int barMaxWidth = Math.max(10, inner.width() - 30);

        List<Line> lines = new ArrayList<>();
        for (int i = scrollOffset; i < end; i++) {
            lines.add(renderStep(steps.get(i), barMaxWidth));
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), hChunks.get(0));

        if (steps.size() > visibleLines) {
            scrollbarState
                    .contentLength(steps.size())
                    .viewportContentLength(visibleLines)
                    .position(scrollOffset);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
        }
    }

    private Line renderStep(StartupStep step, int maxBarWidth) {
        String indent = "  ".repeat(step.level);

        boolean isRoot = step.level == 0;
        Style bandStyle = isRoot ? LABEL : colorForDuration(step.duration);

        double ratio = maxDuration > 0 ? (double) step.duration / maxDuration : 0;
        int barWidth = Math.max(1, (int) Math.round(ratio * maxBarWidth));
        String bar = "█".repeat(barWidth);

        String durationStr = step.duration + "ms";
        String label = step.name != null ? step.name : "";
        if (step.description != null && !step.description.isEmpty() && !step.description.equals(label)) {
            label += " " + step.description;
        }

        int pad = Math.max(1, 8 - durationStr.length());

        return Line.from(
                Span.raw(indent),
                Span.styled(bar, bandStyle),
                Span.raw(" ".repeat(pad)),
                Span.styled(durationStr, isRoot ? LABEL : VALUE),
                Span.styled("  " + label, LABEL));
    }

    private Style colorForDuration(long duration) {
        return TuiHelper.colorForDuration(duration, minDurationColor, maxDurationColor);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "scroll");
        hint(spans, "PgUp/Dn", "page");
        hintLast(spans, "F5", "reload");
    }

    private void loadStartupData() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                Path outputFile = ctx.getOutputFile(pid);
                PathUtils.deleteFile(outputFile);

                JsonObject root = new JsonObject();
                root.put("action", "startup-recorder");

                Path actionFile = ctx.getActionFile(pid);
                PathUtils.writeTextSafely(root.toJson(), actionFile);

                JsonObject jo = pollJsonResponse(outputFile, 5000);
                PathUtils.deleteFile(outputFile);

                if (jo == null) {
                    applyResult(Collections.emptyList(), "No response from integration");
                    return;
                }

                JsonArray stepsArr = (JsonArray) jo.get("steps");
                if (stepsArr == null || stepsArr.isEmpty()) {
                    applyResult(Collections.emptyList(), "No startup steps available");
                    return;
                }

                List<StartupStep> parsed = new ArrayList<>();
                for (Object o : stepsArr) {
                    JsonObject sj = (JsonObject) o;
                    StartupStep s = new StartupStep();
                    s.id = sj.getIntegerOrDefault("id", 0);
                    s.parentId = sj.getIntegerOrDefault("parentId", 0);
                    s.level = sj.getIntegerOrDefault("level", 0);
                    s.name = sj.getString("name");
                    s.type = sj.getString("type");
                    s.description = sj.getString("description");
                    s.beginTime = TuiHelper.objToLong(sj.get("beginTime"));
                    s.duration = TuiHelper.objToLong(sj.get("duration"));
                    parsed.add(s);
                }

                applyResult(parsed, null);
            } catch (Exception e) {
                applyResult(Collections.emptyList(), "Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    private void applyResult(List<StartupStep> parsed, String error) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            steps = parsed;
            errorMessage = error;
            dataLoaded = true;

            if (!steps.isEmpty()) {
                totalDuration = steps.stream().mapToLong(s -> s.duration).max().orElse(0);
                maxDuration = totalDuration;
                minDurationColor
                        = steps.stream().filter(s -> s.level > 0).mapToLong(s -> s.duration).min().orElse(0);
                maxDurationColor
                        = steps.stream().filter(s -> s.level > 0).mapToLong(s -> s.duration).max().orElse(0);
            }
        });
    }

    @Override
    public SelectionContext getSelectionContext() {
        return null;
    }

    static class StartupStep {
        int id;
        int parentId;
        int level;
        String name;
        String type;
        String description;
        long beginTime;
        long duration;
    }

    @Override
    public String getHelpText() {
        return """
                # Startup

                The Startup tab shows a timeline of how the integration started up.
                Each step is displayed with a horizontal bar proportional to its
                duration, helping you visually identify which phases took the most time.

                This is useful for optimizing startup time — for example, finding
                components that take a long time to initialize, routes that are slow
                to start, or external connections that delay readiness.

                ## Timeline Display

                Each line shows one startup step with:

                - **Bar** — Horizontal bar proportional to duration. Longer bars = slower steps. Color ranges from green (fast) through yellow to red (slowest steps)
                - **Duration** — Time in milliseconds this step took
                - **Name** — What this step does (e.g., component initialization, route startup)

                Steps are indented to show parent-child relationships — a route startup
                step contains sub-steps for each processor initialization.

                ## Example Screen

                ```
                 ████████████████████████████████████████  1234ms  Starting CamelContext
                   ██████████████████                      620ms   Start Routes
                     ████████████                          412ms   timer-to-log
                       ████                                150ms   Create Component: timer
                       ██                                  80ms    Create Component: log
                     ████                                  140ms   seda-consumer
                   ████████                                280ms   Start Consumers
                     ██████                                210ms   timer://hello
                     ██                                    70ms    seda://queue
                ```

                In this example, the total startup took 1234ms. The slowest phase
                was starting routes (620ms), with `timer-to-log` taking 412ms due
                to component initialization. This helps you focus optimization
                efforts on the right areas.

                ## What Causes Slow Startup

                Common reasons for slow startup:

                - **Component initialization**: Some components connect to external systems during startup (e.g., Kafka broker discovery, database connection pool warmup)
                - **Route compilation**: Routes with many processors or complex EIP patterns take longer to compile
                - **Bean creation**: Heavy beans with complex initialization logic
                - **Class loading**: Large applications with many dependencies

                ## Startup Recorder

                This tab requires the startup recorder to be enabled. Camel JBang
                enables it by default in `dev` profile. For production deployments,
                enable it with `camel.main.startup-recorder=true`.

                The recorder captures timing data during startup and freezes after
                the context is fully started — the data does not change during runtime.

                ## Keys

                - `Up/Down` — scroll through steps
                - `PgUp/PgDn` — scroll by page
                - `Home/End` — jump to top/end
                - `F5` — reload startup data
                - `Esc` — back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (steps.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Startup");
        JsonArray rows = new JsonArray();
        for (StartupStep s : steps) {
            JsonObject row = new JsonObject();
            row.put("id", s.id);
            row.put("parentId", s.parentId);
            row.put("level", s.level);
            row.put("name", s.name);
            row.put("type", s.type);
            if (s.description != null) {
                row.put("description", s.description);
            }
            row.put("beginTime", s.beginTime);
            row.put("duration", s.duration);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", steps.size());
        return result;
    }
}
