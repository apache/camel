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
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class HeapHistogramTab extends AbstractTableTab {

    private static final String[] FILTER_LABELS = { "all", "non-jdk", "camel" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private int filter;
    private List<HeapEntry> allEntries = Collections.emptyList();
    private long totalInstances;
    private long totalBytes;
    private String lastPid;
    private List<ClasspathTab.JarEntry> classpathEntries = Collections.emptyList();

    HeapHistogramTab(MonitorContext ctx) {
        super(ctx, "className", "instances", "bytes");
        sortIndex = 2;
        sort = "bytes";
    }

    @Override
    protected int getRowCount() {
        return sortedEntries().size();
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allEntries = Collections.emptyList();
            classpathEntries = Collections.emptyList();
        }
        if (allEntries.isEmpty()) {
            loadHeapHistogram();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allEntries = Collections.emptyList();
        classpathEntries = Collections.emptyList();
        lastPid = null;
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isCharIgnoreCase('f')) {
            filter = (filter + 1) % FILTER_LABELS.length;
            tableState.select(0);
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            loadHeapHistogram();
            return true;
        }
        return false;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (loading.get() && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(
                                    Line.from(Span.styled(" Loading heap histogram...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Heap Histogram ").build())
                            .build(),
                    area);
            return;
        }

        List<HeapEntry> visible = sortedEntries();

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(60), Constraint.fill())
                .split(area);
        renderTable(frame, chunks.get(0), visible);
        renderDetail(frame, chunks.get(1), visible);
    }

    private void renderTable(Frame frame, Rect area, List<HeapEntry> visible) {
        List<Row> rows = new ArrayList<>();
        for (HeapEntry e : visible) {
            rows.add(Row.from(
                    rightCell(String.valueOf(e.num), 6),
                    Cell.from(Span.styled(e.className != null ? e.className : "", Style.EMPTY.fg(Theme.accent()))),
                    rightCell(formatNumber(e.instances), 14),
                    rightCell(formatBytes(e.bytes), 14)));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No data", 4));
        }

        long visibleInstances = 0;
        long visibleBytes = 0;
        for (HeapEntry e : visible) {
            visibleInstances += e.instances;
            visibleBytes += e.bytes;
        }

        String title = String.format(" Heap Histogram [%d] instances:%s bytes:%s filter:%s ",
                visible.size(), formatNumber(visibleInstances), formatBytes(visibleBytes),
                FILTER_LABELS[filter]);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 6, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("CLASS NAME", "className"), sortStyle("className"))),
                        rightCell(sortLabel("INSTANCES", "instances"), 14, sortStyle("instances")),
                        rightCell(sortLabel("BYTES", "bytes"), 14, sortStyle("bytes"))))
                .widths(
                        Constraint.length(6),
                        Constraint.fill(),
                        Constraint.length(14),
                        Constraint.length(15))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderDetail(Frame frame, Rect area, List<HeapEntry> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a class to see details", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        HeapEntry entry = visible.get(sel);
        String className = entry.className != null ? entry.className : "";
        String pkg = extractPackage(className);

        List<Line> lines = new ArrayList<>();

        // Class info
        lines.add(Line.from(
                Span.styled("  Class:      ", Theme.muted()),
                Span.styled(className, Style.EMPTY.fg(Theme.accent()))));
        lines.add(Line.from(
                Span.styled("  Package:    ", Theme.muted()),
                Span.styled(pkg.isEmpty() ? "(none)" : pkg,
                        pkg.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY.fg(Theme.baseFg()))));
        lines.add(Line.from(
                Span.styled("  Instances:  ", Theme.muted()),
                Span.styled(formatNumber(entry.instances), Style.EMPTY.fg(Theme.baseFg())),
                Span.styled("          Bytes: ", Theme.muted()),
                Span.styled(formatBytes(entry.bytes), Style.EMPTY.fg(Theme.baseFg()))));

        // Package summary
        if (!pkg.isEmpty()) {
            long pkgInstances = 0;
            long pkgBytes = 0;
            int pkgClasses = 0;
            for (HeapEntry e : allEntries) {
                if (e.className != null && extractPackage(e.className).equals(pkg)) {
                    pkgInstances += e.instances;
                    pkgBytes += e.bytes;
                    pkgClasses++;
                }
            }
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Package Summary ", Theme.muted()),
                    Span.styled("(" + pkg + ")", Style.EMPTY.dim())));
            lines.add(Line.from(
                    Span.styled("    Classes: ", Theme.muted()),
                    Span.styled(formatNumber(pkgClasses), Style.EMPTY.fg(Theme.baseFg())),
                    Span.styled("     Instances: ", Theme.muted()),
                    Span.styled(formatNumber(pkgInstances), Style.EMPTY.fg(Theme.baseFg())),
                    Span.styled("     Bytes: ", Theme.muted()),
                    Span.styled(formatBytes(pkgBytes), Style.EMPTY.fg(Theme.baseFg()))));
        }

        // JAR info
        ClasspathTab.JarEntry jar = findJar(className);
        if (jar != null) {
            lines.add(Line.from(Span.raw("")));
            if (jar.groupId() != null) {
                lines.add(Line.from(
                        Span.styled("  JAR:        ", Theme.muted()),
                        Span.styled(jar.groupId() + ":" + jar.artifactId() + ":" + jar.version(),
                                Theme.success())));
            } else {
                lines.add(Line.from(
                        Span.styled("  JAR:        ", Theme.muted()),
                        Span.styled(jar.display(), Theme.success())));
            }
            if (jar.fullPath() != null) {
                String path = jar.fullPath();
                int maxW = area.width() - 18;
                if (maxW > 0 && path.length() > maxW) {
                    path = "..." + path.substring(path.length() - maxW + 3);
                }
                lines.add(Line.from(
                        Span.styled("                ", Style.EMPTY),
                        Span.styled(path, Style.EMPTY.dim())));
            }
        } else if (isBuiltinClass(className)) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  JAR:        ", Theme.muted()),
                    Span.styled("JDK (built-in)", Theme.success())));
        }

        String title = " Detail: " + TuiHelper.truncate(className, area.width() - 14) + " ";
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "f", "filter [" + FILTER_LABELS[filter] + "]");
        hintLast(spans, "F5", "refresh");
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<HeapEntry> visible = sortedEntries();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(e -> e.className != null ? e.className : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Heap Histogram");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<HeapEntry> entries = sortedEntries();
        if (entries.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Heap Histogram");
        JsonArray rows = new JsonArray();
        for (HeapEntry e : entries) {
            JsonObject row = new JsonObject();
            row.put("num", e.num);
            row.put("className", e.className);
            row.put("instances", e.instances);
            row.put("bytes", e.bytes);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", allEntries.size());
        result.put("totalInstances", totalInstances);
        result.put("totalBytes", totalBytes);
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    @Override
    public String description() {
        return "Class-level heap memory analysis showing instance counts, byte usage, package summary, and JAR origin per class";
    }

    @Override
    public String getHelpText() {
        return """
                # Heap Histogram

                The Heap Histogram tab shows class-level memory usage in the JVM heap,
                similar to `jcmd <pid> GC.class_histogram`. Each row represents a class
                with the number of live instances and total bytes consumed.

                This is useful for diagnosing memory leaks, finding unexpected object
                retention, and understanding which classes dominate heap usage.

                ## Table Columns

                - **#** — Rank by bytes (from the raw JVM histogram)
                - **CLASS NAME** — Fully qualified class name. Array types use JVM notation (e.g., `[B` = byte array, `[Ljava.lang.Object;` = Object array)
                - **INSTANCES** — Number of live instances of this class on the heap
                - **BYTES** — Total bytes consumed by all instances of this class

                The title bar shows total classes, instances, and bytes for the current filter.

                ## Detail Panel

                The detail panel below the table shows additional context for the selected class:

                - **Class** — Full class name, package, instance count and bytes
                - **Package Summary** — Total classes, instances, and bytes for all classes in the same package
                - **JAR** — The Maven artifact (groupId:artifactId:version) and file path of the JAR containing the class. JDK classes show "JDK (built-in)"

                ## Filter Modes

                - **all** (default) — Show all classes
                - **non-jdk** — Exclude JDK classes (java.*, javax.*, jdk.*, sun.*, com.sun.*, arrays)
                - **camel** — Show only classes from `org.apache.camel` packages

                ## What To Look For

                - **Large byte counts at the top**: Normal for byte arrays and char arrays — these back Strings and buffers
                - **Unexpected classes with high counts**: May indicate a memory leak
                - **Growing instance counts on refresh**: Press F5 repeatedly to spot classes whose counts keep growing
                - **Package summary**: Use the detail panel to see total memory for an entire package
                - **JAR origin**: Identify which dependency owns the memory-heavy classes

                ## Keys

                | Key | Action |
                |-----|--------|
                | Up/Down | Select class |
                | s | Cycle sort column (className, instances, bytes) |
                | S | Reverse sort order |
                | f | Toggle filter (all / non-jdk / camel) |
                | F5 | Refresh heap histogram |
                | PgUp/PgDn | Scroll by page |
                | Esc | Back |
                """;
    }

    private List<HeapEntry> sortedEntries() {
        List<HeapEntry> result = new ArrayList<>();
        for (HeapEntry e : allEntries) {
            if (filter == 1 && isJavaClass(e)) {
                continue;
            }
            if (filter == 2 && !isCamelClass(e)) {
                continue;
            }
            result.add(e);
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "instances" -> Long.compare(b.instances, a.instances);
                case "className" -> compareStr(a.className, b.className);
                default -> Long.compare(b.bytes, a.bytes);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private static boolean isJavaClass(HeapEntry e) {
        if (e.className == null) {
            return false;
        }
        return e.className.startsWith("java.")
                || e.className.startsWith("javax.")
                || e.className.startsWith("jdk.")
                || e.className.startsWith("sun.")
                || e.className.startsWith("com.sun.")
                || e.className.startsWith("[");
    }

    private static boolean isCamelClass(HeapEntry e) {
        return e.className != null && e.className.contains("org.apache.camel");
    }

    private static boolean isBuiltinClass(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.")
                || className.startsWith("[");
    }

    private static String extractPackage(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        if (className.startsWith("[")) {
            // array type — extract element class if object array
            int idx = className.indexOf('L');
            if (idx >= 0) {
                String element = className.substring(idx + 1).replace(";", "");
                int dot = element.lastIndexOf('.');
                return dot >= 0 ? element.substring(0, dot) : "";
            }
            return "";
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(0, dot) : "";
    }

    private ClasspathTab.JarEntry findJar(String className) {
        if (classpathEntries.isEmpty() || className == null || className.isEmpty()) {
            return null;
        }
        String pkg = extractPackage(className);
        if (pkg.isEmpty()) {
            return null;
        }
        // try progressively shorter prefixes of the package against groupId
        ClasspathTab.JarEntry best = null;
        int bestLen = -1;
        for (ClasspathTab.JarEntry jar : classpathEntries) {
            if (jar.groupId() == null) {
                continue;
            }
            String gid = jar.groupId();
            if (pkg.startsWith(gid) && gid.length() > bestLen) {
                best = jar;
                bestLen = gid.length();
            }
        }
        return best;
    }

    private void loadHeapHistogram() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                loadHeapHistogramInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadHeapHistogramInBackground(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "heap-histogram");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            return;
        }

        long ti = jo.getLongOrDefault("totalInstances", 0);
        long tb = jo.getLongOrDefault("totalBytes", 0);

        JsonArray arr = (JsonArray) jo.get("classes");
        if (arr == null) {
            return;
        }

        List<HeapEntry> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject ej = (JsonObject) arr.get(i);
            HeapEntry entry = new HeapEntry();
            entry.num = ej.getIntegerOrDefault("num", 0);
            entry.className = ej.getString("className");
            entry.instances = ej.getLongOrDefault("instances", 0);
            entry.bytes = ej.getLongOrDefault("bytes", 0);
            result.add(entry);
        }

        // load classpath if not already loaded
        List<ClasspathTab.JarEntry> cpEntries = classpathEntries;
        if (cpEntries.isEmpty()) {
            cpEntries = loadClasspathEntries(pid);
        }

        List<ClasspathTab.JarEntry> finalCp = cpEntries;
        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allEntries = result;
                totalInstances = ti;
                totalBytes = tb;
                classpathEntries = finalCp;
                lastPid = pid;
            });
        }
    }

    private List<ClasspathTab.JarEntry> loadClasspathEntries(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject action = new JsonObject();
        action.put("action", "jvm");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(action.toJson(), actionFile);

        JsonObject response = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (response == null) {
            return Collections.emptyList();
        }

        Object cp = response.get("classpath");
        List<String> paths = new ArrayList<>();
        if (cp instanceof JsonArray jArr) {
            for (Object item : jArr) {
                paths.add(String.valueOf(item));
            }
        }

        if (paths.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClasspathTab.JarEntry> parsed = new ArrayList<>();
        for (String path : paths) {
            parsed.add(ClasspathTab.parseJarEntry(path));
        }
        return parsed;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String formatNumber(long num) {
        return String.format(java.util.Locale.US, "%,d", num);
    }

    static class HeapEntry {
        int num;
        String className;
        long instances;
        long bytes;
    }
}
