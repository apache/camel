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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class StartupTab extends AbstractTab {

    private static final int MOUSE_SCROLL_LINES = 3;
    private static final Style LABEL = Theme.muted();
    private static final Style VALUE = Style.EMPTY.fg(Theme.baseFg()).bold();

    private final ScrollbarState scrollbarState = new ScrollbarState();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private volatile List<StartupStep> steps = Collections.emptyList();
    private int scrollOffset;
    // s: the steps in the order they started (a waterfall), or the slowest first
    private boolean slowestFirst;
    private long totalDuration;
    private long maxDuration;
    private long minDurationColor;
    private long maxDurationColor;
    private volatile String errorMessage;
    private volatile boolean dataLoaded;

    StartupTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public void onTabSelected() {
        if (!dataLoaded) {
            loadStartupData();
        }
    }

    @Override
    public boolean ensureDataLoaded() {
        onTabSelected();
        return true;
    }

    @Override
    public String dataLoadError() {
        if (!dataLoaded) {
            return null;
        }
        if (errorMessage != null) {
            return errorMessage;
        }
        return steps.isEmpty() ? "No startup data available for the selected integration" : null;
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
        if (ke.isCharIgnoreCase('s')) {
            slowestFirst = !slowestFirst;
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            scrollOffset = Math.max(0, scrollOffset - MOUSE_SCROLL_LINES);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            scrollOffset += MOUSE_SCROLL_LINES;
            return true;
        }
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Startup Timeline ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && steps.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  " + errorMessage, Theme.error()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Startup Timeline ").build())
                            .build(),
                    area);
            return;
        }

        String title = String.format(" Startup Timeline — Total: %dms, Steps: %d%s ", totalDuration, steps.size(),
                slowestFirst ? ", slowest first" : "");
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
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

        List<StartupStep> ordered = ordered(steps, slowestFirst);
        // the time the steps span: a bar starts where its step started, as long as it took
        long t0 = steps.stream().mapToLong(st -> st.beginTime).filter(b -> b > 0).min().orElse(0);
        long t1 = steps.stream().mapToLong(st -> st.beginTime + st.duration).max().orElse(0);
        long span = t0 > 0 ? Math.max(1, t1 - t0) : Math.max(1, maxDuration);
        int labelWidth = Math.min(inner.width() / 2,
                steps.stream().mapToInt(st -> 2 * st.level + CharWidth.of(label(st))).max().orElse(10) + 2);
        int barMaxWidth = Math.max(10, inner.width() - 9 - labelWidth - 1);

        List<Line> lines = new ArrayList<>();
        for (int i = scrollOffset; i < end; i++) {
            lines.add(renderStep(ordered.get(i), labelWidth, barMaxWidth, t0, span));
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

    /**
     * A step: how long it took, what it is (indented by its level), and its bar on the time line of the startup, from
     * where it started (without begin times, as an older Camel has them, from the left).
     */
    private Line renderStep(StartupStep step, int labelWidth, int maxBarWidth, long t0, long span) {
        boolean isRoot = step.level == 0;
        Style bandStyle = isRoot ? LABEL : colorForDuration(step.duration);
        int offset = t0 > 0 && step.beginTime > 0
                ? (int) Math.min(maxBarWidth - 1, Math.round((double) (step.beginTime - t0) / span * maxBarWidth)) : 0;
        int barWidth = Math.max(1, Math.min(maxBarWidth - offset,
                (int) Math.round((double) step.duration / span * maxBarWidth)));
        String what = TuiHelper.truncate("  ".repeat(step.level) + label(step), labelWidth);
        return Line.from(
                Span.styled(String.format("%7dms ", step.duration), isRoot ? LABEL : VALUE),
                Span.styled(what + " ".repeat(Math.max(1, labelWidth - CharWidth.of(what))), LABEL),
                Span.raw(" ".repeat(offset)),
                Span.styled("█".repeat(barWidth), bandStyle));
    }

    static String label(StartupStep step) {
        String name = step.name != null ? step.name : "";
        String description = step.description != null ? step.description : "";
        if (name.isEmpty() || description.equals(name)) {
            // a step with no name (Init CamelContext) is its description alone, without a leading space
            return name.isEmpty() ? description : name;
        }
        return description.isEmpty() ? name : name + " " + description;
    }

    /**
     * The steps in the order they started, each under its own parent (depth-first by parent id), or the slowest first.
     */
    static List<StartupStep> ordered(List<StartupStep> steps, boolean slowestFirst) {
        List<StartupStep> ordered = new ArrayList<>(steps);
        if (slowestFirst) {
            ordered.sort(Comparator.comparingLong((StartupStep st) -> st.duration).reversed());
        } else if (hasIds(steps)) {
            ordered = depthFirst(steps);
        } else if (steps.stream().anyMatch(st -> st.beginTime > 0)) {
            ordered.sort(Comparator.comparingLong((StartupStep st) -> st.beginTime).thenComparingInt(st -> st.level));
        }
        return ordered;
    }

    /** Whether every step has its own id, so the steps can be put under their parents. */
    private static boolean hasIds(List<StartupStep> steps) {
        Set<Integer> ids = new HashSet<>();
        for (StartupStep st : steps) {
            if (st.id <= 0 || !ids.add(st.id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Each step followed by its children: the recorder gives the ids in the order the steps begin, so ordering by id
     * keeps the begin order even where two steps began in the same millisecond, and a step whose parent is not in the
     * list is a root.
     */
    private static List<StartupStep> depthFirst(List<StartupStep> steps) {
        Set<Integer> ids = new HashSet<>();
        steps.forEach(st -> ids.add(st.id));
        List<StartupStep> byId = new ArrayList<>(steps);
        byId.sort(Comparator.comparingInt(st -> st.id));
        Map<Integer, List<StartupStep>> children = new HashMap<>();
        List<StartupStep> roots = new ArrayList<>();
        for (StartupStep st : byId) {
            if (st.parentId != st.id && ids.contains(st.parentId)) {
                children.computeIfAbsent(st.parentId, k -> new ArrayList<>()).add(st);
            } else {
                roots.add(st);
            }
        }
        List<StartupStep> answer = new ArrayList<>(steps.size());
        Set<Integer> visited = new HashSet<>();
        Deque<StartupStep> stack = new ArrayDeque<>();
        for (StartupStep root : roots) {
            stack.push(root);
            while (!stack.isEmpty()) {
                StartupStep st = stack.pop();
                if (visited.add(st.id)) {
                    answer.add(st);
                    List<StartupStep> kids = children.getOrDefault(st.id, List.of());
                    for (int i = kids.size() - 1; i >= 0; i--) {
                        stack.push(kids.get(i));
                    }
                }
            }
        }
        if (answer.size() < byId.size()) {
            // steps in a parent loop: after the others, in id order
            byId.stream().filter(st -> !visited.contains(st.id)).forEach(answer::add);
        }
        return answer;
    }

    /**
     * The time the startup took: from the first step to the end of the last one, or (without begin times, as an older
     * Camel has them) the longest step.
     */
    static long totalDuration(List<StartupStep> steps) {
        long t0 = steps.stream().mapToLong(st -> st.beginTime).filter(b -> b > 0).min().orElse(0);
        if (t0 > 0) {
            return steps.stream().filter(st -> st.beginTime > 0).mapToLong(st -> st.beginTime + st.duration).max()
                    .orElse(t0)
                   - t0;
        }
        return steps.stream().mapToLong(s -> s.duration).max().orElse(0);
    }

    private Style colorForDuration(long duration) {
        return TuiHelper.colorForDuration(duration, minDurationColor, maxDurationColor);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", slowestFirst ? "start order" : "slowest first");
        hintLast(spans, "PgUp/Dn", "page");
    }

    private void loadStartupData() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                JsonObject root = new JsonObject();
                root.put("action", "startup-recorder");

                JsonObject jo = ctx.executeAction(pid, root, 5000);

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
                totalDuration = totalDuration(steps);
                maxDuration = steps.stream().mapToLong(s -> s.duration).max().orElse(0);
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
    public String description() {
        return "Startup step recorder showing initialization timing";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("startup");
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
