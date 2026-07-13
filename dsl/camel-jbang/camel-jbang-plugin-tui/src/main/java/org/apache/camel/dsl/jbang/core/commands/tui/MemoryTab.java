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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class MemoryTab extends AbstractTab {

    // Unicode block characters for gauge bar
    private static final String GAUGE_FILLED = "█";
    private static final String GAUGE_EMPTY = "░";

    private final Map<String, LinkedList<Long>> heapMemHistory;
    private int statsPanelHeight = -1;
    private final DragSplit vSplit = new DragSplit();

    MemoryTab(MonitorContext ctx, MetricsCollector metrics) {
        super(ctx);
        this.heapMemHistory = metrics.getHeapMemHistory();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isChar('g')) {
            triggerGC();
            return true;
        }
        if (ke.isChar('h')) {
            triggerHeapDump();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (vSplit.handleMouse(me, me.y())) {
            if (vSplit.isDragging()) {
                statsPanelHeight = Math.max(5, Math.min(me.y() - area.y(), area.height() - 5));
            }
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
    }

    @Override
    public void navigateDown() {
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        int statsHeight = 11;
        if (info.oldGenUsed > 0) {
            statsHeight += 2;
        }
        if (info.metaspaceUsed > 0) {
            statsHeight += 2;
        }
        if (info.threadCount > 0) {
            statsHeight += 2;
        }
        int statsH = statsPanelHeight >= 0 ? statsPanelHeight : statsHeight;
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(statsH), Constraint.fill())
                .split(area);
        vSplit.setBorderPos(chunks.get(1).y());

        renderStats(frame, chunks.get(0), info);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(chunks.get(1));

        renderSparkline(frame, vChunks.get(0), info);
        renderTimeAxis(frame, vChunks.get(1), info);
    }

    private void renderStats(Frame frame, Rect area, IntegrationInfo info) {
        List<Line> lines = new ArrayList<>();

        // Heap memory with two gauge bars (used/committed and used/max)
        if (info.heapMemUsed > 0) {
            lines.add(Line.from(
                    Span.styled("  Heap Memory", Theme.label().bold())));

            // Compute heap trend from history
            LinkedList<Long> hist = heapMemHistory.get(info.pid);
            Span trendSpan = computeTrendSpan(hist, info.heapMemCommitted);

            lines.add(Line.from(
                    Span.styled("  used:      ", Theme.muted()),
                    Span.styled(formatBytes(info.heapMemUsed), Style.EMPTY.fg(Theme.baseFg()).bold())));

            if (info.heapMemCommitted > 0) {
                long pctComm = info.heapMemUsed * 100 / info.heapMemCommitted;
                String gaugeComm = buildGaugeBar(pctComm, 30);
                Style colorComm = pctComm >= 80 ? Theme.error() : pctComm >= 60 ? Theme.warning()
                        : Theme.success();
                List<Span> commSpans = new ArrayList<>();
                commSpans.add(Span.styled("  committed: ", Theme.muted()));
                commSpans.add(Span.styled(String.format("%-10s", formatBytes(info.heapMemCommitted)), Style.EMPTY));
                commSpans.add(Span.styled(gaugeComm, colorComm));
                commSpans.add(Span.styled(String.format("  %d%%", pctComm), colorComm.bold()));
                if (info.heapMemMax <= 0 && trendSpan != null) {
                    commSpans.add(Span.raw("    "));
                    commSpans.add(trendSpan);
                }
                lines.add(Line.from(commSpans));
            }
            if (info.heapMemMax > 0) {
                long pctMax = info.heapMemUsed * 100 / info.heapMemMax;
                String gaugeMax = buildGaugeBar(pctMax, 30);
                Style colorMax = pctMax >= 80 ? Theme.error() : pctMax >= 60 ? Theme.warning()
                        : Theme.success();
                List<Span> maxSpans = new ArrayList<>();
                maxSpans.add(Span.styled("  max:       ", Theme.muted()));
                maxSpans.add(Span.styled(String.format("%-10s", formatBytes(info.heapMemMax)), Style.EMPTY));
                maxSpans.add(Span.styled(gaugeMax, colorMax));
                maxSpans.add(Span.styled(String.format("  %d%%", pctMax), colorMax.bold()));
                if (trendSpan != null) {
                    maxSpans.add(Span.raw("    "));
                    maxSpans.add(trendSpan);
                }
                lines.add(Line.from(maxSpans));
            }
        }

        // Old Gen pool
        if (info.oldGenUsed > 0) {
            long oldPct = info.oldGenMax > 0 ? info.oldGenUsed * 100 / info.oldGenMax : 0;
            String oldGauge = buildGaugeBar(oldPct, 30);
            Style oldColor = oldPct >= 80 ? Theme.error() : oldPct >= 60 ? Theme.warning()
                    : Theme.success();

            lines.add(Line.from(
                    Span.styled("  Old Gen:   ", Theme.muted()),
                    Span.styled(String.format("%-10s", formatBytes(info.oldGenUsed)), Style.EMPTY.fg(Theme.baseFg()).bold()),
                    Span.styled(oldGauge, oldColor),
                    Span.styled(String.format("  %d%%", oldPct), oldColor.bold())));
            lines.add(Line.from(
                    Span.styled("  committed: ", Theme.muted()),
                    Span.raw(formatBytes(info.oldGenCommitted)),
                    Span.styled("    max: ", Theme.muted()),
                    Span.raw(formatBytes(info.oldGenMax))));
        }

        // Non-heap memory + Metaspace
        if (info.nonHeapMemUsed > 0) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Non-Heap Memory", Theme.label().bold())));
            lines.add(Line.from(
                    Span.styled("  used:      ", Theme.muted()),
                    Span.styled(String.format("%-10s", formatBytes(info.nonHeapMemUsed)),
                            Style.EMPTY.fg(Theme.baseFg()).bold()),
                    Span.styled("  committed: ", Theme.muted()),
                    Span.raw(formatBytes(info.nonHeapMemCommitted))));
        }
        if (info.metaspaceUsed > 0) {
            lines.add(Line.from(
                    Span.styled("  Metaspace: ", Theme.muted()),
                    Span.styled(String.format("%-10s", formatBytes(info.metaspaceUsed)), Style.EMPTY.fg(Theme.baseFg()).bold()),
                    Span.styled("  committed: ", Theme.muted()),
                    Span.raw(formatBytes(info.metaspaceCommitted)),
                    info.metaspaceMax > 0
                            ? Span.styled("  max: " + formatBytes(info.metaspaceMax), Theme.muted())
                            : Span.raw("")));
        }

        // Threads
        if (info.threadCount > 0) {
            lines.add(Line.from(Span.raw("")));
            List<Span> threadSpans = new ArrayList<>();
            threadSpans.add(Span.styled("  Threads", Theme.label().bold()));
            threadSpans.add(Span.styled("  current: ", Theme.muted()));
            threadSpans.add(Span.styled(String.valueOf(info.threadCount), Style.EMPTY.fg(Theme.baseFg()).bold()));
            threadSpans.add(Span.styled("  peak: ", Theme.muted()));
            threadSpans.add(Span.raw(String.valueOf(info.peakThreadCount)));
            lines.add(Line.from(threadSpans));
        }

        // GC and class loading on the same line
        List<Span> gcSpans = new ArrayList<>();
        gcSpans.add(Span.styled("  GC: ", Theme.muted()));
        gcSpans.add(Span.raw(info.gcCollectionCount + " collections"));
        if (info.gcCollectionTime > 0) {
            gcSpans.add(Span.styled("  time: ", Theme.muted()));
            gcSpans.add(Span.raw(TimeUtils.printDuration(info.gcCollectionTime, true)));
        }
        if (info.loadedClassCount > 0) {
            gcSpans.add(Span.styled("    Classes: ", Theme.muted()));
            gcSpans.add(Span.raw(String.valueOf(info.loadedClassCount)));
        }
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(gcSpans));

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Memory ").build())
                .build();

        frame.renderWidget(paragraph, area);
    }

    private static final String[] BAR_EIGHTHS = { " ", "▁", "▂", "▃", "▄", "▅", "▆", "▇", "█" };

    private void renderSparkline(Frame frame, Rect area, IntegrationInfo info) {
        String pid = info.pid;
        LinkedList<Long> hist = heapMemHistory.get(pid);

        if (hist == null || hist.isEmpty()) {
            Paragraph p = Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled("  Collecting heap data...", Style.EMPTY.dim()))))
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Heap Usage ").build())
                    .build();
            frame.renderWidget(p, area);
            return;
        }

        // Use committed as the scale ceiling
        long observedMax = hist.stream().mapToLong(Long::longValue).max().orElse(1);
        long ceiling = info.heapMemCommitted > 0 ? info.heapMemCommitted : observedMax;
        if (observedMax > ceiling) {
            ceiling = observedMax;
        }

        String title = String.format(" Heap Usage (%s / %s committed) ", formatBytes(info.heapMemUsed), formatBytes(ceiling));

        // Render the block border first
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);

        if (inner.isEmpty() || ceiling <= 0) {
            return;
        }

        int chartW = inner.width();
        int chartH = inner.height();

        // Build data array right-aligned: latest data on the right
        long[] data = new long[chartW];
        int startIdx = Math.max(0, hist.size() - chartW);
        int dataOffset = Math.max(0, chartW - hist.size());
        for (int i = startIdx; i < hist.size(); i++) {
            data[dataOffset + (i - startIdx)] = hist.get(i);
        }

        // Render multi-row bar chart with stacked colors: green (<60%), yellow (60-80%), light red (>80%)
        Buffer buf = frame.buffer();
        // Precompute eighths thresholds for the color bands
        int greenEighths = (int) Math.round(0.6 * chartH * 8.0);
        int yellowEighths = (int) Math.round(0.8 * chartH * 8.0);
        Style greenStyle = Theme.success();
        Style yellowStyle = Theme.warning();
        Style redStyle = Theme.error();

        for (int col = 0; col < chartW; col++) {
            double ratio = (double) data[col] / ceiling;
            int totalEighths = (int) Math.round(ratio * chartH * 8.0);

            // Render from bottom to top
            for (int row = 0; row < chartH; row++) {
                int y = inner.y() + chartH - 1 - row;
                int x = inner.x() + col;
                int rowEighths = Math.min(8, Math.max(0, totalEighths - row * 8));
                if (rowEighths > 0) {
                    // Determine color based on vertical position in the bar
                    int eighthsAtRowBottom = row * 8;
                    Style style;
                    if (eighthsAtRowBottom >= yellowEighths) {
                        style = redStyle;
                    } else if (eighthsAtRowBottom >= greenEighths) {
                        style = yellowStyle;
                    } else {
                        style = greenStyle;
                    }
                    buf.setString(x, y, BAR_EIGHTHS[rowEighths], style);
                }
            }
        }
    }

    private void renderTimeAxis(Frame frame, Rect area, IntegrationInfo info) {
        LinkedList<Long> hist = heapMemHistory.get(info.pid);
        int points = hist != null ? hist.size() : 0;

        int w = area.width();
        if (w < 10) {
            return;
        }

        // The chart area has the same width; each column = 1 data point = 5 seconds
        // chartW columns map to the rightmost `chartW` points of history
        int chartW = w;
        int totalPoints = Math.min(points, chartW);
        long totalSeconds = totalPoints * 5L;

        Buffer buf = frame.buffer();
        Style dimStyle = Style.EMPTY.dim();
        int xAxisY = area.y();
        int startX = area.x();

        // "now" label at the right edge
        int nowX = startX + chartW - 3;
        if (nowX >= startX) {
            buf.setString(nowX, xAxisY, "now", dimStyle);
        }

        // Pick step interval based on total time span
        int stepSeconds;
        if (totalSeconds <= 120) {
            stepSeconds = 30;
        } else if (totalSeconds <= 300) {
            stepSeconds = 60;
        } else if (totalSeconds <= 900) {
            stepSeconds = 120;
        } else {
            stepSeconds = 300;
        }

        // Place markers from right to left at regular time intervals
        for (int s = stepSeconds; s <= totalSeconds; s += stepSeconds) {
            int col = chartW - 1 - (s / 5);
            if (col < 0) {
                break;
            }
            String label;
            if (s < 60) {
                label = "-" + s + "s";
            } else {
                label = "-" + (s / 60) + "m";
            }
            int markerX = startX + col;
            if (markerX + label.length() <= startX + chartW - 4 && markerX >= startX) {
                buf.setString(markerX, xAxisY, label, dimStyle);
            }
        }
    }

    private static Span computeTrendSpan(LinkedList<Long> hist, long heapCeiling) {
        // need at least 30 samples (~2.5 min at 5s intervals) for a meaningful trend
        if (hist == null || hist.size() < 30) {
            return null;
        }
        int size = hist.size();
        int third = size / 3;

        // average the first and last thirds to smooth out GC sawtooth noise
        long oldSum = 0;
        for (int i = 0; i < third; i++) {
            oldSum += hist.get(i);
        }
        long newSum = 0;
        for (int i = size - third; i < size; i++) {
            newSum += hist.get(i);
        }
        double oldAvg = (double) oldSum / third;
        double newAvg = (double) newSum / third;
        if (oldAvg <= 0) {
            return null;
        }

        long diff = (long) (newAvg - oldAvg);
        double change = (newAvg - oldAvg) / oldAvg;
        int pct = (int) Math.round(change * 100);
        // each sample is taken every 5 seconds (HEAP_SAMPLE_INTERVAL_MS)
        long seconds = size * 5L;
        String period = seconds >= 60 ? (seconds / 60) + "m" : seconds + "s";

        // ignore small fluctuations relative to heap capacity (at least 5M or 1% of ceiling)
        long threshold = Math.max(5 * 1024 * 1024, (long) (heapCeiling * 0.01));
        if (Math.abs(diff) < threshold) {
            return Span.styled("  → stable over last " + period, Theme.success());
        }

        if (change > 0.05) {
            return Span.styled(
                    String.format("  %s growing by %d%% (%s) over last %s", TuiIcons.ARROW_UP, pct, formatBytes(diff), period),
                    Theme.error().bold());
        } else if (change < -0.05) {
            return Span.styled(
                    String.format("  %s shrinking by %d%% (%s) over last %s", TuiIcons.ARROW_DOWN,
                            Math.abs(pct), formatBytes(Math.abs(diff)), period),
                    Theme.success());
        } else {
            return Span.styled("  " + TuiIcons.ARROW_STABLE + " stable over last " + period, Theme.success());
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "g", "gc");
        hint(spans, "h", "heap dump");
    }

    private void triggerHeapDump() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        notify("Writing heap dump...", false);
        String pid = info.pid;
        Thread t = new Thread(() -> {
            Path outputFile = ctx.getOutputFile(pid);
            PathUtils.deleteFile(outputFile);
            JsonObject root = new JsonObject();
            root.put("action", "heap-dump");
            Path actionFile = ctx.getActionFile(pid);
            PathUtils.writeTextSafely(root.toJson(), actionFile);
            JsonObject jo = TuiHelper.pollJsonResponse(outputFile, 60000);
            if (jo != null) {
                String error = jo.getString("error");
                if (error != null) {
                    notify("Heap dump failed: " + error, true);
                } else {
                    String file = jo.getString("file");
                    long size = jo.getLongOrDefault("size", 0);
                    notify("Heap dump: " + file + " (" + formatBytes(size) + ")", false);
                }
            } else {
                notify("Heap dump: no response within 60s", true);
            }
            PathUtils.deleteFile(outputFile);
        });
        t.setDaemon(true);
        t.setName("heap-dump-" + pid);
        t.start();
    }

    private void notify(String message, boolean error) {
        if (ctx.notificationCallback != null) {
            ctx.notificationCallback.accept(message, error);
        }
    }

    private void triggerGC() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.put("action", "gc");
        Path actionFile = ctx.getActionFile(info.pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    @Override
    public String description() {
        return "JVM memory usage (heap/non-heap), GC stats, and thread counts";
    }

    @Override
    public String getHelpText() {
        return """
                # Memory

                The Memory tab shows JVM memory usage, garbage collection stats, and a
                real-time heap usage chart. This helps you monitor memory consumption,
                detect memory leaks, and understand GC behavior.

                ## Heap Memory

                The JVM heap is where Java objects live. Three values matter:

                - **used**: Memory currently occupied by live objects. This value goes up as objects are created and drops when garbage collection runs
                - **committed**: Memory the JVM has reserved from the operating system. This is your working set — the JVM can use this much without requesting more from the OS. The JVM may increase committed memory up to the max limit as needed
                - **max**: The absolute upper limit set by `-Xmx`. The JVM will never use more than this. If used reaches max and GC cannot free enough space, you get an `OutOfMemoryError`

                Two gauge bars show how full the heap is:

                ```
                committed: 80 MB  ████████████████░░░░░░░░░░░░░░  53%
                max:       16 GB  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░░   2%
                ```

                The committed bar shows how much of the reserved memory is in use —
                this is the most useful indicator for day-to-day monitoring. The max
                bar shows how much headroom remains before the JVM hits its hard limit.

                ## Example Screen

                ```
                 Heap: used 55 MB / committed 80 MB / max 16 GB
                 committed: 80 MB  ████████████████░░░░░░░░░░░░░░  68%
                 max:       16 GB  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░░   0%

                 Old Gen:   used 19 MB / committed 24 MB / max 16 GB
                 Non-Heap:  used 66 MB / committed 68 MB
                 Metaspace: used 43 MB / committed 44 MB

                 GC: 19 collections, 28 ms total
                ```

                ## Old Gen

                When objects survive multiple garbage collections, they are promoted to
                the Old Generation memory pool. Old Gen stores long-lived objects like
                caches, connection pools, and singleton beans.

                **Watch for**: Old Gen usage that keeps growing over time without going
                back down after GC. This pattern often indicates a **memory leak** —
                objects being retained that should have been released.

                ## Non-Heap and Metaspace

                - **Non-Heap**: Memory outside the heap used by the JVM itself — JIT compiled code, thread stacks, internal data structures
                - **Metaspace**: A subset of non-heap where Java class metadata is stored (class definitions, method tables, constant pools). Replaced the old PermGen space in Java 8+. Metaspace grows automatically but can be limited with `-XX:MaxMetaspaceSize`

                High Metaspace usage is normal for large applications with many classes.
                It typically stays stable after startup.

                ## Garbage Collection

                - **Collections**: Total number of GC cycles. The JVM runs GC automatically when it needs to free memory. Modern collectors (G1, ZGC) run frequently with short pauses
                - **Time**: Total time spent in GC (milliseconds). High GC time relative to uptime means the JVM is spending too much time cleaning up — consider increasing heap size

                **GC key**: The key at the top identifies which GC algorithm is active
                (e.g., `G1 Young Generation`, `ZGC`). Different algorithms have
                different trade-offs between throughput and pause times.

                ## Sparkline Chart

                The chart shows heap usage over time. Color indicates how full the
                heap is relative to committed memory:

                - **Green**: below 60% — healthy, plenty of headroom
                - **Yellow**: 60-80% — getting full, GC is working harder
                - **Red**: above 80% — high pressure, risk of long GC pauses or OOM

                A sawtooth pattern (usage rises then drops sharply) is normal — it shows
                GC reclaiming memory periodically. A steadily rising baseline that
                never drops back suggests a memory leak.

                ## Keys

                - `g` — trigger garbage collection on the running integration (sends a GC request to the JVM — useful for testing if high usage is just uncollected garbage)
                - `h` — write a heap dump (.hprof) file for deep analysis with tools like Eclipse MAT or VisualVM
                - `Esc` — back
                """;
    }

    private static String buildGaugeBar(long pct, int width) {
        int filled = (int) (pct * width / 100);
        int empty = width - filled;
        return GAUGE_FILLED.repeat(Math.max(0, filled)) + GAUGE_EMPTY.repeat(Math.max(0, empty));
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Memory");
        JsonObject data = new JsonObject();
        data.put("heapMemUsed", info.heapMemUsed);
        data.put("heapMemCommitted", info.heapMemCommitted);
        data.put("heapMemMax", info.heapMemMax);
        data.put("nonHeapMemUsed", info.nonHeapMemUsed);
        data.put("nonHeapMemCommitted", info.nonHeapMemCommitted);
        if (info.oldGenUsed > 0) {
            data.put("oldGenUsed", info.oldGenUsed);
            data.put("oldGenCommitted", info.oldGenCommitted);
            data.put("oldGenMax", info.oldGenMax);
        }
        if (info.metaspaceUsed > 0) {
            data.put("metaspaceUsed", info.metaspaceUsed);
            data.put("metaspaceCommitted", info.metaspaceCommitted);
            data.put("metaspaceMax", info.metaspaceMax);
        }
        data.put("gcCollectionCount", info.gcCollectionCount);
        data.put("gcCollectionTime", info.gcCollectionTime);
        data.put("loadedClassCount", info.loadedClassCount);
        data.put("threadCount", info.threadCount);
        data.put("peakThreadCount", info.peakThreadCount);
        result.put("snapshot", data);
        return result;
    }
}
