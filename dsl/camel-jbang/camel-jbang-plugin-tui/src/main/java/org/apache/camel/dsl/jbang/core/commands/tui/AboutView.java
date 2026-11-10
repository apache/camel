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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Padding;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.apache.camel.dsl.jbang.core.commands.ai.AboutReport;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * About an integration (Overview ▸ About, CAMEL-26700): what it does at a glance and without an AI, and what has
 * happened. A header with what it is and its purpose (the comment at the top of its main file, else the descriptions of
 * its routes), then what comes in, its routes (the routes called with direct or seda under the route that calls them)
 * and what goes out, from the analysis of its sources ({@link ProjectOverview}). Under it, the last minutes from the
 * timeline the integration records, which the Timeline tab (4) shows second by second, and what to watch.
 */
final class AboutView {

    // how often the sources are read again, to see a change
    private static final long RELOAD_MILLIS = 10_000;
    private static final int HAPPENED_SECONDS = 300;
    // how long after the last error the Watch line calms down
    private static final long WATCH_CALM_MILLIS = 60_000;
    private static final String BLOCKS = " ▁▂▃▄▅▆▇█";
    // the strips have at least this many columns, whatever the zoom, and as many as the width allows up to the most,
    // a column taking a whole number of seconds
    private static final int STRIP_COLUMNS = 60;
    private static final int MAX_STRIP_COLUMNS = 300;
    // the cause of a recovery after the strip of a route, cut to this width when the strips need the room
    private static final int CAUSE_WIDTH = 30;
    // the sparklines of In and Out, wider in a wider column
    private static final int SPARK_WIDTH = 14;
    private static final int MAX_SPARK_WIDTH = 30;
    // the strips of What happened are this many rows high at most, on a tall screen
    private static final int MAX_STRIP_ROWS = 2;
    // with this many rows to spare, the sections get room around them
    private static final int ROOMY_ROWS = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MonitorContext ctx;
    private volatile ProjectOverview.Overview overview;
    private volatile About.Model model;
    private volatile String purpose;
    private volatile String purposeFrom;
    private volatile boolean loading;
    private volatile String error;
    private Path dir;
    // the README of the project, which r opens; null when it has none
    private Path readme;
    private long loadedAt;
    // the zoom of What happened, as the windows of the Timeline tab (+ and -), and its last second when moved (← →);
    // -1 follows the last second recorded
    private int zoom = TimelineTab.WINDOWS.length - 1;
    private long stripsEnd = -1;
    // the columns of the strips as last drawn, for the title of What happened
    private int stripColumns = STRIP_COLUMNS;
    // the width of the text of the Out column, as the last render had it
    private int outWidth = 40;
    // the line of In, Routes or Out the cursor is on (↑ ↓), in the order of the columns; -1 when none is
    private int cursor = -1;
    // the column the cursor is in: 0 In, 1 Routes, 2 Out, or -1 when it is on nothing; its panel has the focus border
    private int focus = -1;
    private volatile List<Target> targets = List.of();
    private volatile List<String> targetLabels = List.of();
    // what In, Routes and Out show and where their lines are on the screen, for a click
    private Columns shown;
    private final Rect[] columnAreas = new Rect[3];

    /**
     * What a line of In, Routes or Out is about, for Enter: a route (opened on the Diagram) or an endpoint a route
     * sends to (opened on the Endpoint tab).
     *
     * @param routeId  the route
     * @param endpoint the URI of the endpoint as the sources have it, or null for a route
     */
    record Target(String routeId, String endpoint) {
    }

    AboutView(MonitorContext ctx) {
        this.ctx = ctx;
    }

    /** Reads the sources of the integration in the background, again when they may have changed. */
    private void load(IntegrationInfo info, Consumer<Runnable> onRenderThread) {
        Path d = info.directory != null ? Path.of(info.directory) : null;
        long now = System.currentTimeMillis();
        boolean same = d != null && d.equals(dir);
        if (d == null || loading || same && now - loadedAt < RELOAD_MILLIS) {
            if (d == null) {
                error = "No source directory for this integration";
            }
            return;
        }
        if (!same) {
            overview = null;
            purpose = null;
            error = null;
        }
        dir = d;
        loadedAt = now;
        loading = true;
        Thread t = new Thread(() -> {
            try {
                ProjectOverview.Overview o = ProjectOverview.analyze(d, ArchitectureView.catalog());
                About.Model m = About.of(d, o);
                Path r = About.readme(d);
                onRenderThread.accept(() -> {
                    readme = r;
                    overview = o;
                    model = m;
                    purpose = m.purpose();
                    purposeFrom = m.purposeFrom();
                    error = o.flows().isEmpty() ? "No routes found in " + d : null;
                    loading = false;
                });
            } catch (Exception e) {
                onRenderThread.accept(() -> {
                    error = "Cannot read the sources: " + e.getMessage();
                    loading = false;
                });
            }
        }, "CamelTuiAbout");
        t.setDaemon(true);
        t.start();
    }

    /**
     * The keys of What happened: + zooms in on the last trouble in the window (a failure, a handled error, a
     * redelivery, a route down, a circuit breaker open), - zooms out, and ← → move a zoomed window.
     */
    boolean handleKeyEvent(KeyEvent ke, IntegrationInfo info) {
        if (info == null) {
            return false;
        }
        if (ke.isConfirm()) {
            return open();
        }
        if (ke.isChar('r') && readme != null) {
            return openReadme(info);
        }
        if (ke.isChar('s') && dir != null) {
            // the summary of the project, as the Diagram opens it: what an AI wrote, and what its sources say
            IntegrationSummaryDoc.open(ctx, dir, null);
            return true;
        }
        long end = ctx.timeline.endSecond(info.pid);
        if (ke.isChar('+') || ke.isChar('=')) {
            if (zoom > 0 && end >= 0) {
                long trouble = lastTrouble(info, windowEnd(end), seconds());
                zoom--;
                if (trouble >= 0) {
                    // the trouble in the middle of the window, when there is time after it
                    moveTo(trouble + seconds() / 2, end);
                }
            }
            return true;
        }
        if (ke.isChar('-')) {
            if (zoom < TimelineTab.WINDOWS.length - 1) {
                zoom++;
                if (end >= 0) {
                    moveTo(windowEnd(end) + seconds() / 4, end);
                }
            }
            return true;
        }
        if ((ke.isLeft() || ke.isRight()) && end >= 0) {
            int by = Math.max(1, seconds() / 4);
            moveTo(windowEnd(end) + (ke.isLeft() ? -by : by), end);
            return true;
        }
        return false;
    }

    /** Opens the README of the project in the document viewer. */
    private boolean openReadme(IntegrationInfo info) {
        if (ctx.openMarkdownCallback == null) {
            return false;
        }
        try {
            String text = Files.readString(readme, StandardCharsets.UTF_8);
            String name = readme.getFileName().toString();
            String markdown = name.toLowerCase(Locale.ROOT).endsWith(".adoc") ? DocHelper.asciidocToMarkdown(text) : text;
            ctx.openMarkdownCallback.accept((info.name != null ? info.name : info.pid) + " - " + name, markdown);
        } catch (IOException e) {
            error = "Cannot read " + readme + ": " + e.getMessage();
        }
        return true;
    }

    /** The lines the cursor can be on, for an agent (tui_get_state): the route, or the endpoint and its route. */
    SelectionContext selectionContext() {
        List<String> items = targetLabels;
        return new SelectionContext("list", items, cursor < items.size() ? cursor : -1, items.size(), "About");
    }

    /**
     * About the integration as data, for an agent (tui_get_table): what the screen shows, with what happened over the
     * window of What happened.
     */
    JsonObject tableJson(IntegrationInfo info) {
        JsonObject result = new JsonObject();
        result.put("tab", "Overview");
        result.put("view", "About");
        result.put("integration", info.name != null ? info.name : info.pid);
        result.put("pid", info.pid);
        About.Model m = model;
        if (m == null) {
            result.put("loading", error != null ? error : "reading the sources");
            return result;
        }
        if (m.purpose() != null) {
            result.put("purpose", m.purpose());
            result.put("purposeFrom", m.purposeFrom());
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        info.routes.forEach(r -> {
            if (r.routeId != null) {
                totals.put(r.routeId, r.total);
            }
        });
        JsonArray in = new JsonArray();
        for (About.In e : m.in()) {
            JsonObject jo = new JsonObject();
            jo.put("label", Placeholders.resolve(e.label(), info));
            jo.put("kind", e.kind());
            jo.put("routeId", e.route());
            putTotalAndRate(jo, info, e.route(), totals);
            in.add(jo);
        }
        result.put("in", in);
        JsonArray routes = new JsonArray();
        for (About.RouteLine r : m.routes()) {
            JsonObject jo = new JsonObject();
            jo.put("routeId", r.routeId());
            jo.put("depth", r.depth());
            if (r.how() != null) {
                jo.put("calledHow", r.how());
            }
            jo.put("from", r.from());
            jo.put("steps", r.steps());
            if (totals.get(r.routeId()) != null) {
                jo.put("exchangesTotal", totals.get(r.routeId()));
            }
            routes.add(jo);
        }
        result.put("routes", routes);
        JsonArray out = new JsonArray();
        for (About.Out o : m.out()) {
            JsonObject jo = new JsonObject();
            jo.put("uri", o.uri());
            jo.put("category", o.category());
            jo.put("routeId", o.route());
            Long hits = outHits(info, o.uri(), o.route());
            if (hits != null) {
                jo.put("sends", hits);
            }
            out.add(jo);
        }
        result.put("out", out);
        long last = ctx.timeline.endSecond(info.pid);
        if (last >= 0) {
            long end = windowEnd(last);
            int seconds = seconds();
            JsonObject happened = new JsonObject();
            happened.put("seconds", seconds);
            happened.put("endSecond", end);
            happened.put("following", stripsEnd < 0);
            JsonArray lines = new JsonArray();
            for (Timeline.RouteSeries r : ctx.timeline.routes(info.pid, routeOrder(info), end, seconds)) {
                JsonObject jo = new JsonObject();
                jo.put("routeId", r.route());
                jo.put("exchanges", r.total(r.completed()) + r.total(r.recovered()) + r.total(r.handled())
                                    + r.total(r.failed()));
                long[] worst = r.total(r.failed()) > 0 ? r.failed() : r.total(r.handled()) > 0 ? r.handled()
                        : r.total(r.recovered()) > 0 ? r.recovered() : null;
                if (worst != null) {
                    jo.put("worst", worst == r.failed() ? "failed" : worst == r.handled() ? "handled" : "recovered");
                    jo.put("worstCount", r.total(worst));
                    jo.put("worstSecondsAgo", last - lastAt(worst, end, seconds));
                    if (worst == r.recovered()) {
                        String from = recoveredFrom(info, r.route(), end - seconds + 1, end);
                        if (from != null) {
                            jo.put("recoveredFrom", from);
                        }
                    }
                }
                if (r.openSeconds() > 0) {
                    jo.put("circuitBreakerOpenSeconds", r.openSeconds());
                }
                if (r.total(r.down()) > 0) {
                    jo.put("downSeconds", r.total(r.down()));
                }
                lines.add(jo);
            }
            happened.put("routes", lines);
            JsonArray causes = new JsonArray();
            for (Timeline.CauseSeries c : ctx.timeline.causes(info.pid, end, seconds)) {
                long total = Arrays.stream(c.values()).sum();
                if (total > 0) {
                    JsonObject jo = new JsonObject();
                    jo.put("label", c.label());
                    if (c.rule() != null) {
                        jo.put("chaosRule", c.rule());
                    }
                    jo.put("count", total);
                    causes.add(jo);
                }
            }
            happened.put("causes", causes);
            result.put("whatHappened", happened);
        }
        Target t = selected();
        if (t != null) {
            JsonObject sel = new JsonObject();
            sel.put("routeId", t.routeId());
            if (t.endpoint() != null) {
                sel.put("endpoint", t.endpoint());
            }
            result.put("selected", sel);
        }
        result.put("keys", "v all integrations, Up/Down select a line, Enter opens it, +/- zoom, Left/Right move, 4 Timeline,"
                           + (readme != null ? " r the README," : "") + " s the summary");
        return result;
    }

    private void putTotalAndRate(JsonObject jo, IntegrationInfo info, String routeId, Map<String, Long> totals) {
        if (routeId == null) {
            return;
        }
        if (totals.get(routeId) != null) {
            jo.put("exchangesTotal", totals.get(routeId));
        }
        long[] perSecond = routePerSecond(info, routeId);
        if (perSecond != null) {
            jo.put("rate", rate(perSecond));
        }
    }

    /** ↑ ↓: the cursor moves over the lines of In, Routes and Out that are about a route or an endpoint. */
    void moveCursor(int delta) {
        int n = targets.size();
        if (n == 0) {
            cursor = -1;
        } else if (cursor < 0) {
            cursor = delta > 0 ? 0 : n - 1;
        } else {
            cursor = Math.max(0, Math.min(n - 1, cursor + delta));
        }
    }

    /**
     * A click on In, Routes or Out puts the cursor on the entry clicked: its name, or the line under it (its exchanges,
     * or the route that sends to it). False when the click was on none.
     */
    boolean click(int x, int y) {
        Columns c = shown;
        if (c == null) {
            return false;
        }
        List<List<Line>> lines = List.of(c.in(), c.routes(), c.out());
        List<List<Target>> columns = List.of(c.inTargets(), c.routeTargets(), c.outTargets());
        int before = 0;
        for (int i = 0; i < columns.size(); i++) {
            List<Target> col = columns.get(i);
            Rect r = columnAreas[i];
            if (r != null && r.contains(x, y)) {
                int at = y - r.y();
                if (at > 0 && at < col.size() && col.get(at) == null && col.get(at - 1) != null
                        && !lines.get(i).get(at).rawContent().isBlank()) {
                    at--;
                }
                if (at >= col.size() || col.get(at) == null) {
                    return false;
                }
                cursor = before + count(col.subList(0, at));
                return true;
            }
            before += count(col);
        }
        return false;
    }

    private static int count(List<Target> targets) {
        return (int) targets.stream().filter(Objects::nonNull).count();
    }

    /** The line the cursor is on, or null. */
    Target selected() {
        List<Target> t = targets;
        return cursor >= 0 && cursor < t.size() ? t.get(cursor) : null;
    }

    /** Enter: opens what the cursor is on; false when it is on nothing. */
    boolean open() {
        Target t = selected();
        if (t == null || ctx.openFromAbout == null) {
            return false;
        }
        ctx.openFromAbout.accept(t);
        return true;
    }

    private int seconds() {
        return TimelineTab.WINDOWS[zoom];
    }

    private long windowEnd(long end) {
        return stripsEnd >= 0 ? Math.min(stripsEnd, end) : end;
    }

    /** Moves the window to end at the given second, as far as there are seconds recorded; at the end it follows. */
    private void moveTo(long second, long end) {
        long first = Math.max(0, end - Timeline.SECONDS + 1);
        long to = Math.max(first + seconds() - 1, second);
        stripsEnd = to >= end ? -1 : to;
    }

    /** The last second in the window that a route had trouble in, or -1. */
    private long lastTrouble(IntegrationInfo info, long windowEnd, int seconds) {
        long last = -1;
        for (Timeline.RouteSeries r : ctx.timeline.routes(info.pid, routeOrder(info), windowEnd, seconds)) {
            for (int i = seconds - 1; i >= 0; i--) {
                if (r.failed()[i] > 0 || r.handled()[i] > 0 || r.recovered()[i] > 0 || r.attempts()[i] > 0
                        || r.down()[i] > 0 || r.open()[i] > 0) {
                    last = Math.max(last, windowEnd - seconds + 1 + i);
                    break;
                }
            }
        }
        return last;
    }

    /**
     * The routes in the order of the Routes panel, where a route comes after the one that calls it, then those it does
     * not have; the integration lists its routes by id, which puts a called route before its caller.
     */
    private List<String> routeOrder(IntegrationInfo info) {
        List<String> running = info.routes.stream().map(r -> r.routeId).filter(id -> id != null).toList();
        List<String> order = new ArrayList<>();
        About.Model m = model;
        if (m != null) {
            for (About.RouteLine r : m.routes()) {
                if (running.contains(r.routeId()) && !order.contains(r.routeId())) {
                    order.add(r.routeId());
                }
            }
        }
        for (String id : running) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    /**
     * In, Routes and Out: their lines, and what each line is about (null for a line about nothing, as a sparkline).
     */
    private record Columns(
            List<Line> in, List<Line> routes, List<Line> out, List<Target> inTargets, List<Target> routeTargets,
            List<Target> outTargets) {

        int rows() {
            return Math.max(1, Math.max(in.size(), Math.max(routes.size(), out.size())));
        }
    }

    void render(Frame frame, Rect area, IntegrationInfo info) {
        // the sources are read in the background, and what they say is set on the render thread
        load(info, ctx.runner != null ? ctx.runner::runOnRenderThread : Runnable::run);
        ProjectOverview.Overview o = overview;
        About.Model m = model;
        int width = area.width();
        // the text of the Out column: its 30 % of the width, less the border, padding and the space before it
        outWidth = Math.max(0, width * 30 / 100 - 6);
        List<Line> head = head(info, o, width);
        int spark = sparkWidth(width);
        Columns columns = columns(o, m, info, spark, false);
        List<Line> status = status(info, width);
        List<Line> strips = routeStrips(info, width - 2, false, 1);
        int compact = head.size() + 2 + columns.rows() + 2 + status.size() + 1 + (strips.isEmpty() ? 0 : strips.size() + 2);
        // a tall screen: room around the sections, a blank line between the entries of In and Out, and between the
        // routes of What happened when there is room for that too
        boolean roomy = area.height() - compact >= ROOMY_ROWS;
        if (roomy) {
            head.add(1, Line.from(Span.raw("")));
            columns = columns(o, m, info, spark, true);
            // the routes, and the axis under them
            int routes = Math.max(0, strips.size() - 1);
            boolean spaced = area.height() - compact - ROOMY_ROWS >= routes - 1;
            int used = head.size() + 2 + 1 + columns.rows() + 4 + 1 + status.size() + 1
                       + (routes == 0 ? 0 : routes + 1 + (spaced ? routes - 1 : 0) + 4) + 2;
            // the rows left over make the strips taller, keeping a little room under the keys
            int tall = routes == 0 ? 1 : Math.max(1, Math.min(MAX_STRIP_ROWS, 1 + (area.height() - used - 2) / routes));
            strips = routeStrips(info, width - 4, spaced, tall);
        }
        select(columns, m, info);
        String name = " " + (info.name != null ? info.name : info.pid) + " ";
        if (roomy) {
            renderRoomy(frame, area, info, name, head, columns, status, strips);
        } else {
            status.add(next());
            renderCompact(frame, area, info, name, head, columns, status, strips);
        }
    }

    private void renderCompact(
            Frame frame, Rect area, IntegrationInfo info, String name, List<Line> head, Columns columns,
            List<Line> status, List<Line> strips) {
        int headHeight = head.size() + 2;
        int available = Math.max(3, area.height() - headHeight - status.size());
        int columnHeight = Math.min(columns.rows() + 2, available);
        int statusHeight = status.size();
        int happenedHeight = strips.isEmpty()
                ? 0 : Math.max(0, Math.min(strips.size() + 2, area.height() - headHeight - columnHeight - statusHeight));
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(headHeight), Constraint.length(columnHeight), Constraint.length(statusHeight),
                        Constraint.length(happenedHeight), Constraint.fill())
                .split(area);
        frame.renderWidget(Paragraph.builder().text(Text.from(head)).block(block(name, Padding.NONE, false)).build(),
                rows.get(0));
        renderColumns(frame, rows.get(1), columns, Padding.NONE, 0, focus);
        frame.renderWidget(Paragraph.builder().text(Text.from(status)).build(), rows.get(2));
        if (happenedHeight > 2) {
            frame.renderWidget(Paragraph.builder().text(Text.from(strips))
                    .block(block(happenedTitle(info), Padding.NONE, false)).build(), rows.get(3));
        }
    }

    /**
     * A tall screen: a blank line between the sections and inside the panels, the panels apart, the status lines in
     * line with what the panels have, and the keys under What happened.
     */
    private void renderRoomy(
            Frame frame, Rect area, IntegrationInfo info, String name, List<Line> head, Columns columns,
            List<Line> status, List<Line> strips) {
        int happenedHeight = strips.isEmpty() ? 0 : strips.size() + 4;
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(head.size() + 2), Constraint.length(1), Constraint.length(columns.rows() + 4),
                        Constraint.length(1), Constraint.length(status.size()), Constraint.length(1),
                        Constraint.length(happenedHeight), Constraint.length(1), Constraint.length(1), Constraint.fill())
                .split(area);
        frame.renderWidget(Paragraph.builder().text(Text.from(head)).block(block(name, Padding.horizontal(1), false)).build(),
                rows.get(0));
        renderColumns(frame, rows.get(2), columns, Padding.uniform(1), 1, focus);
        frame.renderWidget(Paragraph.builder().text(Text.from(status)).build(), indent(rows.get(4)));
        if (happenedHeight > 2) {
            frame.renderWidget(Paragraph.builder().text(Text.from(strips))
                    .block(block(happenedTitle(info), Padding.uniform(1), false)).build(), rows.get(6));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(next())).build(), indent(rows.get(8)));
    }

    /** The area without the two columns the panels have before their text: their border and their padding. */
    private static Rect indent(Rect r) {
        return new Rect(r.x() + 2, r.y(), Math.max(0, r.width() - 2), r.height());
    }

    private void renderColumns(Frame frame, Rect area, Columns columns, Padding padding, int spacing, int focus) {
        // by hand: the layout leaves the last column short of the edge when spaced
        int w0 = area.width() * 28 / 100;
        int w1 = area.width() * 42 / 100;
        int w2 = Math.max(0, area.width() - w0 - w1 - 2 * spacing);
        List<Rect> cols = List.of(new Rect(area.x(), area.y(), w0, area.height()),
                new Rect(area.x() + w0 + spacing, area.y(), w1, area.height()),
                new Rect(area.x() + w0 + w1 + 2 * spacing, area.y(), w2, area.height()));
        List<List<Line>> texts = List.of(columns.in(), columns.routes(), columns.out());
        String[] titles = { " In ", " Routes ", " Out " };
        for (int i = 0; i < titles.length; i++) {
            Block b = block(titles[i], padding, focus == i);
            columnAreas[i] = b.inner(cols.get(i));
            frame.renderWidget(Paragraph.builder().text(Text.from(texts.get(i))).block(b).build(), cols.get(i));
        }
    }

    /** What the integration is, and its purpose: the comment at the top of its main file. */
    private List<Line> head(IntegrationInfo info, ProjectOverview.Overview o, int width) {
        String p = purpose;
        String pFrom = purposeFrom;
        List<Line> head = new ArrayList<>();
        head.add(Line.from(Span.raw(" "), Span.raw(meta(info, o)), StatusAge.stale(info)
                ? Span.styled("  ✗ no status", Theme.error())
                : Span.styled(info.vanishing ? "  ○ stopped" : "  ● running",
                        info.vanishing ? Theme.muted() : Theme.success())));
        if (p != null) {
            for (String l : wrap(p, Math.max(20, width - 8), 2)) {
                head.add(Line.from(Span.styled(" " + l, Style.EMPTY.bold())));
            }
            head.add(Line.from(Span.styled(" " + pFrom, Theme.muted())));
        } else if (o != null) {
            head.add(Line.from(Span.styled(" No description yet: a comment at the top of " + mainFile(o)
                                           + " or a README says what it does, or ask the AI (Shift+F8 on a route)",
                    Theme.muted())));
        } else {
            head.add(Line.from(Span.styled(error != null ? " " + error : " Reading the sources…", Theme.muted())));
        }
        return head;
    }

    /** The lines the cursor goes over, down In, then Routes, then Out; the line it is on in the selection colour. */
    private void select(Columns c, About.Model m, IntegrationInfo info) {
        List<Target> all = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> inLabels = new ArrayList<>();
        if (m != null) {
            m.in().stream().filter(e -> e.route() != null).forEach(e -> inLabels.add(Placeholders.resolve(e.label(), info)));
        }
        String[] names = { "in", "route", "out" };
        List<List<Target>> columns = List.of(c.inTargets(), c.routeTargets(), c.outTargets());
        for (int i = 0; i < columns.size(); i++) {
            int n = 0;
            for (Target t : columns.get(i)) {
                if (t != null) {
                    all.add(t);
                    // what an agent sees of the line: its column, and what it is about
                    labels.add(names[i] + ": " + (i == 0 && n < inLabels.size() ? inLabels.get(n) + " → " + t.routeId()
                            : t.endpoint() != null ? t.endpoint() + " from " + t.routeId() : t.routeId()));
                    n++;
                }
            }
        }
        targets = all;
        targetLabels = labels;
        shown = c;
        if (cursor >= all.size()) {
            cursor = all.size() - 1;
        }
        focus = -1;
        for (int i = 0, before = 0; i < columns.size() && cursor >= 0; i++) {
            int n = count(columns.get(i));
            if (cursor < before + n) {
                focus = i;
                break;
            }
            before += n;
        }
        highlight(c.in(), c.inTargets(), c.routes(), c.routeTargets(), c.out(), c.outTargets());
    }

    /**
     * A line per route over the last 5 minutes, a column of 5 seconds coloured by the worst that happened to its
     * exchanges then (failed, handled, recovered, completed), as the Timeline tab colours its seconds.
     */
    private String happenedTitle(IntegrationInfo info) {
        int seconds = seconds();
        String window = seconds % 60 == 0 ? seconds / 60 + "m" : seconds + "s";
        long end = ctx.timeline.endSecond(info.pid);
        String when = stripsEnd >= 0 && end >= 0
                ? window + " to " + TIME.format(Instant.ofEpochSecond(windowEnd(end))) : "last " + window;
        String keys = zoom == 0 ? "- zoom out" : zoom < TimelineTab.WINDOWS.length - 1 ? "+ zoom in, - out" : "+ zoom in";
        if (seconds < Timeline.SECONDS) {
            keys += ", ← → move";
        }
        return " What happened ─ " + when + ", a column is " + seconds / stripColumns + "s ─ " + keys
               + ", 4 for each second ";
    }

    /**
     * A strip per route, {@code tall} rows high (the blocks stacked, so a strip of 2 rows has 16 levels), with a blank
     * line between the routes when spaced, and the time axis under them.
     */
    private List<Line> routeStrips(IntegrationInfo info, int width, boolean spaced, int tall) {
        List<Line> lines = new ArrayList<>();
        long last = ctx.timeline.endSecond(info.pid);
        if (last < 0) {
            return lines;
        }
        long end = windowEnd(last);
        int seconds = seconds();
        List<String> order = routeOrder(info);
        // the names, then one space: every line starts its columns at the same place
        int label = Math.min(18, Math.max(8, order.stream().mapToInt(String::length).max().orElse(8))) + 4;
        List<Timeline.RouteSeries> series = ctx.timeline.routes(info.pid, order, end, seconds);
        // what the line of a route has after its strip (its exchanges, an open circuit breaker, the worst that
        // happened, with the cause of a recovery up to CAUSE_WIDTH) takes the room it needs, and the strips the rest
        int notes = 0;
        for (Timeline.RouteSeries r : series) {
            notes = Math.max(notes, width(stripNotes(info, r, last, end, seconds, CAUSE_WIDTH)));
        }
        int columns = stripColumns(seconds, width - label - notes - 1);
        // the cause gets what the strips leave too
        int causeRoom = CAUSE_WIDTH + Math.max(0, width - label - notes - 1 - columns);
        stripColumns = columns;
        int per = seconds / columns;
        // the columns that end before the integration started are blank, as the sparklines have them, where a dot is
        // a time it ran and did nothing (uptime is the time it started)
        long startOffset = info.uptime > 0 ? info.uptime / 1000 - (end - seconds + 1) : 0;
        int before = (int) Math.max(0, Math.min(columns, startOffset / per));
        for (Timeline.RouteSeries r : series) {
            if (spaced && !lines.isEmpty()) {
                lines.add(Line.from(Span.raw("")));
            }
            String name = r.route().length() > label - 3 ? r.route().substring(0, label - 4) + "…" : r.route();
            long max = 1;
            // completed, recovered, handled, failed, down, redelivery attempts: the height is the exchanges done, the
            // attempts only colour the column (an exchange retried many times is still one exchange)
            long[][] col = new long[columns][7];
            for (int i = 0; i < seconds; i++) {
                long[] c = col[i / per];
                c[0] += Math.max(0, r.completed()[i]);
                c[1] += Math.max(0, r.recovered()[i]);
                c[2] += Math.max(0, r.handled()[i]);
                c[3] += Math.max(0, r.failed()[i]);
                c[4] += Math.max(0, r.down()[i]);
                c[5] += Math.max(0, r.attempts()[i]);
                c[6] = Math.max(c[6], r.open()[i]);
            }
            for (long[] c : col) {
                max = Math.max(max, c[0] + c[1] + c[2] + c[3]);
            }
            List<List<Span>> rows = stripCells(col, max, tall, before);
            // the name and the notes on the bottom row, where the blocks stand
            for (int row = 0; row < tall; row++) {
                List<Span> spans = new ArrayList<>();
                spans.add(Span.raw(row == tall - 1
                        ? "   " + name + " ".repeat(Math.max(1, label - 3 - name.length())) : " ".repeat(label)));
                spans.addAll(rows.get(row));
                if (row == tall - 1) {
                    spans.addAll(stripNotes(info, r, last, end, seconds, causeRoom));
                }
                lines.add(Line.from(spans));
            }
        }
        // the time axis: the first second of the window and the last
        String from = TIME.format(Instant.ofEpochSecond(end - seconds + 1));
        String to = stripsEnd >= 0 ? TIME.format(Instant.ofEpochSecond(end)) : "now";
        lines.add(Line.from(Span.styled(" ".repeat(label) + from
                                        + " ".repeat(Math.max(1, columns - from.length() - to.length())) + to,
                Theme.muted())));
        return lines;
    }

    /**
     * The cells of a strip, row by row from the top: a column coloured by the worst that happened in it, an outage and
     * an open circuit breaker the full height, nothing a dot at the bottom, and the first columns blank (from before
     * the integration started).
     */
    private static List<List<Span>> stripCells(long[][] col, long max, int tall, int before) {
        List<List<Span>> rows = new ArrayList<>();
        for (int row = 0; row < tall; row++) {
            rows.add(new ArrayList<>());
        }
        for (int k = 0; k < col.length; k++) {
            long[] c = col[k];
            long t = c[0] + c[1] + c[2] + c[3];
            for (int row = 0; row < tall; row++) {
                boolean bottom = row == tall - 1;
                Span cell;
                if (c[4] > 0) {
                    cell = Span.styled("░", Theme.error());
                } else if (c[6] > 0) {
                    // a circuit breaker of the route was open (half open dim), as on the Timeline
                    cell = Span.styled("▒", c[6] >= 2 ? Theme.notice() : Theme.muted());
                } else if (t == 0 && c[5] == 0) {
                    cell = bottom && k >= before ? Span.styled("·", Theme.muted()) : Span.raw(" ");
                } else {
                    int level = (int) Math.max(1, Math.min(8L * tall, Math.ceil(t * 8.0 * tall / max)));
                    int here = Math.max(0, Math.min(8, level - (tall - 1 - row) * 8));
                    Style style = c[3] > 0 ? Theme.error()
                            : c[2] > 0 ? Style.EMPTY.fg(Theme.ACCENT)
                            : c[1] > 0 || c[5] > 0 ? Theme.warning() : Theme.success();
                    cell = Span.styled(String.valueOf(BLOCKS.charAt(here)), style);
                }
                rows.get(row).add(cell);
            }
        }
        return rows;
    }

    /**
     * What comes after the strip of a route: its exchanges, the seconds its circuit breaker was open, and the worst
     * that happened, when it last did, and for a recovery the failure it recovered from (a failed or handled one has
     * its cause on the Last 5m line and on the Timeline).
     */
    private List<Span> stripNotes(IntegrationInfo info, Timeline.RouteSeries r, long last, long end, int seconds, int room) {
        List<Span> spans = new ArrayList<>();
        long done = r.total(r.completed()) + r.total(r.recovered()) + r.total(r.handled()) + r.total(r.failed());
        spans.add(Span.styled("  " + done, Theme.success()));
        if (r.openSeconds() > 0) {
            spans.add(Span.styled("  ⚡ open " + r.openSeconds() + "s " + ago(last - lastAt(r.open(), end, seconds)),
                    Theme.notice()));
        }
        long[] worst = r.total(r.failed()) > 0 ? r.failed() : r.total(r.handled()) > 0 ? r.handled()
                : r.total(r.recovered()) > 0 ? r.recovered() : null;
        if (worst != null) {
            String what = worst == r.failed() ? " failed" : worst == r.handled() ? " handled" : " recovered";
            Style style = worst == r.failed() ? Theme.error()
                    : worst == r.handled() ? Style.EMPTY.fg(Theme.ACCENT) : Theme.warning();
            long at = lastAt(worst, end, seconds);
            spans.add(Span.styled("  " + r.total(worst) + what, style));
            spans.add(Span.styled(" " + ago(last - at), Theme.muted()));
            String cause = worst == r.recovered() ? recoveredFrom(info, r.route(), end - seconds + 1, end) : null;
            if (cause != null && room > 10) {
                spans.add(Span.styled(" · ", Theme.muted()));
                spans.add(Span.styled(cause.length() > room ? cause.substring(0, room - 1) + "…" : cause,
                        Theme.warning()));
            }
        }
        return spans;
    }

    private static int width(List<Span> spans) {
        int w = 0;
        for (Span span : spans) {
            w += CharWidth.of(span.content());
        }
        return w;
    }

    /**
     * The columns of the strips: the most that fit the room, up to {@value #MAX_STRIP_COLUMNS}, with a whole number of
     * seconds in each; at least {@value #STRIP_COLUMNS}.
     */
    static int stripColumns(int seconds, int room) {
        int best = Math.min(STRIP_COLUMNS, seconds);
        for (int c = best + 1; c <= Math.min(MAX_STRIP_COLUMNS, Math.min(seconds, room)); c++) {
            if (seconds % c == 0) {
                best = c;
            }
        }
        return best;
    }

    /** The last second of the window with a value, or the end. */
    private static long lastAt(long[] values, long end, int seconds) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] > 0) {
                return end - seconds + 1 + i;
            }
        }
        return end;
    }

    private static String ago(long seconds) {
        return seconds > 0 ? ChaosTab.shortTime(seconds * 1000) + " ago" : "now";
    }

    /**
     * The failure the last recovered exchange of the route was redelivered for, from the exchanges the integration
     * kept; as a route called by another keeps its exchanges under the caller, any recovered exchange of the window
     * will do.
     */
    private String recoveredFrom(IntegrationInfo info, String routeId, long from, long to) {
        String any = null;
        for (Timeline.Kept k : ctx.timeline.exchanges(info.pid, from, to)) {
            if (k.redelivered() && !k.failed() && !k.handled() && k.exception() != null) {
                if (routeId.equals(k.routeId())) {
                    any = k.exception();
                } else if (any == null) {
                    any = k.exception();
                }
            }
        }
        return any;
    }

    private static String meta(IntegrationInfo info, ProjectOverview.Overview o) {
        StringBuilder sb = new StringBuilder();
        if (info.camelVersion != null) {
            sb.append("Camel ").append(info.camelVersion);
        }
        if (info.platform != null) {
            sb.append(" · ").append(info.platform);
        }
        if (info.profile != null) {
            sb.append(" · ").append(info.profile);
        }
        if (o != null) {
            sb.append(" · ").append(o.files().size()).append(o.files().size() == 1 ? " file" : " files");
            if (o.files().size() == 1) {
                sb.append(" (").append(o.files().get(0)).append(')');
            }
        }
        sb.append(" · ").append(info.routes.size()).append(info.routes.size() == 1 ? " route" : " routes");
        if (info.uptime > 0) {
            // the time it started
            sb.append(" · up ").append(TimeUtils.printSince(info.uptime));
        }
        return sb.toString();
    }

    /** The file of the first route, where its comment goes: not a file that only declares beans. */
    private static String mainFile(ProjectOverview.Overview o) {
        return o.flows().stream().map(ProjectRoutes.Route::file).filter(f -> f != null).findFirst()
                .orElse(o.files().isEmpty() ? "the main file" : o.files().get(0));
    }

    // ---- In, Routes, Out ----

    /** The line the cursor is on, in the selection colour. */
    private void highlight(
            List<Line> in, List<Target> inTargets, List<Line> routes, List<Target> routeTargets, List<Line> out,
            List<Target> outTargets) {
        int n = 0;
        List<List<Line>> columns = List.of(in, routes, out);
        List<List<Target>> columnTargets = List.of(inTargets, routeTargets, outTargets);
        for (int c = 0; c < columns.size(); c++) {
            List<Target> t = columnTargets.get(c);
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i) != null && n++ == cursor) {
                    List<Span> spans = new ArrayList<>();
                    for (Span s : columns.get(c).get(i).spans()) {
                        spans.add(Span.styled(s.content(), s.style().patch(Theme.selectionBg())));
                    }
                    columns.get(c).set(i, Line.from(spans));
                    return;
                }
            }
        }
    }

    private Columns columns(ProjectOverview.Overview o, About.Model m, IntegrationInfo info, int spark, boolean spaced) {
        Columns c = new Columns(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        if (o != null && m != null) {
            columns(m, info, spark, spaced, c);
        }
        return c;
    }

    private void columns(About.Model m, IntegrationInfo info, int spark, boolean spaced, Columns c) {
        List<Line> in = c.in();
        List<Line> routes = c.routes();
        List<Line> out = c.out();
        List<Target> inTargets = c.inTargets();
        List<Target> routeTargets = c.routeTargets();
        List<Target> outTargets = c.outTargets();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (RouteInfo r : info.routes) {
            if (r.routeId != null) {
                totals.put(r.routeId, r.total);
            }
        }
        for (About.In e : m.in()) {
            if (spaced && !in.isEmpty()) {
                in.add(Line.from(Span.raw("")));
                inTargets.add(null);
            }
            Long total = e.route() != null ? totals.get(e.route()) : null;
            // with the values of its properties: the period or the schedule it runs with, its profile applied
            in.add(Line.from(Span.raw(" " + icon(e.kind()) + " "),
                    Span.styled(Placeholders.resolve(e.label(), info), Style.EMPTY.bold()),
                    Span.styled(total != null ? "  " + total : "", Theme.success())));
            inTargets.add(e.route() != null ? new Target(e.route(), null) : null);
            // the exchanges of the route it starts over the last minutes, and the rate now
            long[] perSecond = e.route() != null ? routePerSecond(info, e.route()) : null;
            if (perSecond != null) {
                in.add(sparkLine("   ", perSecond, spark, StatusAge.stale(info)));
                inTargets.add(null);
            }
        }
        if (in.isEmpty()) {
            in.add(Line.from(Span.styled(" nothing from outside", Theme.muted())));
            inTargets.add(null);
        }

        for (About.RouteLine r : m.routes()) {
            List<Span> spans = new ArrayList<>();
            // ↳ a call that waits for the route, ⇢ a hand-off that does not
            String how = r.how() == null ? "" : "call".equals(r.how()) ? "↳ " : "⇢ ";
            spans.add(Span.raw(" " + "  ".repeat(r.depth()) + how));
            spans.add(Span.styled(r.routeId(), Style.EMPTY.bold()));
            spans.add(Span.styled("  " + r.from() + " → " + r.steps() + (r.steps() == 1 ? " step" : " steps"),
                    Theme.muted()));
            Long total = totals.get(r.routeId());
            if (total != null) {
                spans.add(Span.styled("  " + total, Theme.success()));
            }
            routes.add(Line.from(spans));
            routeTargets.add(new Target(r.routeId(), null));
        }

        // and the ones the integration sent to that its routes do not name (a toD, a recipient list)
        List<About.Out> outs = new ArrayList<>(m.out());
        outs.addAll(ObservedOuts.of(info, m.out()));
        for (About.Out s : outs) {
            if (spaced && !out.isEmpty()) {
                out.add(Line.from(Span.raw("")));
                outTargets.add(null);
            }
            Long hits = outHits(info, s.uri(), s.route());
            out.add(Line.from(Span.raw(" " + icon(s.category()) + " "), Span.styled(shortUri(s.uri()), Style.EMPTY.bold()),
                    Span.styled(hits != null ? "  " + hits : "", Theme.success())));
            outTargets.add(new Target(s.route(), s.uri()));
            List<Span> from = new ArrayList<>();
            from.add(Span.styled("    from " + s.route(), Theme.muted()));
            // the exchanges of the route that sends to it, as In has them: the integration records the last
            // minutes, where a history of the sends would start with the TUI
            long[] perSecond = s.route() != null ? routePerSecond(info, s.route()) : null;
            if (perSecond != null) {
                // the sparkline in what "    from <route>  " and the rate leave of the column
                int room = outWidth - 9 - s.route().length() - 2 - 8;
                from.addAll(sparkLine("  ", perSecond, Math.max(4, Math.min(spark, room)), StatusAge.stale(info))
                        .spans());
            }
            out.add(Line.from(from));
            outTargets.add(null);
        }
        if (out.isEmpty()) {
            out.add(Line.from(Span.styled(" nothing outside the integration", Theme.muted())));
            outTargets.add(null);
        }
    }

    /** The exchanges done per second by the route over What happened, or null when nothing is recorded. */
    private long[] routePerSecond(IntegrationInfo info, String routeId) {
        long end = ctx.timeline.endSecond(info.pid);
        if (end < 0) {
            return null;
        }
        List<Timeline.RouteSeries> series = ctx.timeline.routes(info.pid, List.of(routeId), end, HAPPENED_SECONDS);
        for (Timeline.RouteSeries r : series) {
            if (routeId.equals(r.route())) {
                long[] done = new long[HAPPENED_SECONDS];
                for (int i = 0; i < done.length; i++) {
                    done[i] = Math.max(0, r.completed()[i]) + Math.max(0, r.recovered()[i]) + Math.max(0, r.handled()[i])
                              + Math.max(0, r.failed()[i]);
                }
                return done;
            }
        }
        return null;
    }

    /**
     * The values as a sparkline and the rate now, or only idle when there was nothing; the rate is not known when the
     * integration wrote no status of late.
     */
    private static Line sparkLine(String indent, long[] values, int width, boolean noStatus) {
        if (Arrays.stream(values).allMatch(v -> v <= 0)) {
            return Line.from(Span.raw(indent), Span.styled(noStatus ? "-" : "idle", Theme.muted()));
        }
        return Line.from(Span.raw(indent), Span.styled(spark(values, width), Theme.info()),
                Span.styled("  " + (noStatus ? "-" : rate(values)), Theme.muted()));
    }

    /** The sparklines of In fill its column, between {@value #SPARK_WIDTH} and {@value #MAX_SPARK_WIDTH} wide. */
    private static int sparkWidth(int width) {
        // the column less its border and padding, the indent and the rate
        return Math.max(SPARK_WIDTH, Math.min(MAX_SPARK_WIDTH, width * 28 / 100 - 4 - 3 - 8));
    }

    /** The rate now: the mean of the last 10 seconds, or idle. */
    static String rate(long[] values) {
        int n = Math.min(10, values.length);
        long sum = 0;
        for (int i = values.length - n; i < values.length; i++) {
            sum += Math.max(0, values[i]);
        }
        double r = n > 0 ? (double) sum / n : 0;
        return r == 0 ? "idle" : r >= 10 ? String.format(Locale.ROOT, "%.0f/s", r) : String.format(Locale.ROOT, "%.1f/s", r);
    }

    /**
     * How many times the route sent to the endpoint, from the endpoint statistics of the integration; null when the
     * integration has no such endpoint (a URI with a placeholder, or no statistics).
     */
    static Long outHits(IntegrationInfo info, String uri, String route) {
        String base = baseUri(uri);
        Long answer = null;
        for (EndpointInfo ep : info.endpoints) {
            if ("out".equals(ep.direction) && (route == null || route.equals(ep.routeId))
                    && ObservedOuts.matches(base, baseUri(ep.uri))) {
                answer = (answer != null ? answer : 0) + ep.hits;
            }
        }
        return answer;
    }

    /** The URI without its options, and the same with or without the // after the scheme. */
    static String baseUri(String uri) {
        return AboutReport.baseUri(uri);
    }

    private static String icon(String kind) {
        String k = kind != null ? kind.toLowerCase(Locale.ROOT) : "";
        if (k.contains("file") || k.contains("storage") || k.equals("ftp") || k.equals("sftp") || k.equals("s3")) {
            return "📁";
        }
        if (k.contains("kafka") || k.contains("jms") || k.contains("amqp") || k.contains("mqtt") || k.equals("seda")) {
            return "📨";
        }
        return switch (k) {
            case "schedule" -> "⏱";
            case "http" -> "🌐";
            case "file" -> "📁";
            case "messaging" -> "📨";
            case "database" -> "🗄";
            case "mail" -> "✉";
            default -> "•";
        };
    }

    private static String shortUri(String uri) {
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        return q > 0 ? uri.substring(0, q) : uri;
    }

    // ---- what has happened, and what to watch ----

    /** The last minutes, and what to watch. */
    private List<Line> status(IntegrationInfo info, int width) {
        List<Line> lines = new ArrayList<>();
        lines.add(happened(info, width));
        List<Span> watch = new ArrayList<>();
        watch.add(Span.styled(" Watch    ", Theme.muted()));
        List<Span> noStatus = StatusAge.watch(info);
        if (!noStatus.isEmpty()) {
            // its numbers are of when it stopped writing its status
            watch.addAll(noStatus);
        } else if (info.errorCount > 0) {
            // errors that stopped a minute ago are history: the line says so, and calms down. A minute, not the 5 of
            // Last 5m and the strips (which still show them): a fix that reloads should not stay red for long
            long lastError = lastError(info);
            boolean old = lastError > 0 && System.currentTimeMillis() - lastError > WATCH_CALM_MILLIS;
            String count = info.errorCount + (info.errorCount == 1 ? " error" : " errors");
            Style style = old ? Theme.muted() : Theme.error();
            if (old) {
                watch.add(Span.styled("no errors in the last minute", Theme.success()));
                watch.add(Span.styled(" · " + count + " before", style));
            } else {
                watch.add(Span.styled("✗ " + count, style));
            }
            // the kinds that happened most, when there are more than one
            List<IntegrationInfo.ErrorKind> kinds = info.errorKinds;
            if (kinds.size() > 1) {
                for (int i = 0; i < Math.min(3, kinds.size()); i++) {
                    IntegrationInfo.ErrorKind k = kinds.get(i);
                    watch.add(Span.styled(i == 0 ? ": " : " · ", Theme.muted()));
                    watch.add(Span.styled(simpleName(k.exceptionType()) + " ×" + k.count(), style));
                }
                if (kinds.size() > 3) {
                    watch.add(Span.styled(" …", Theme.muted()));
                }
            } else if (kinds.size() == 1) {
                watch.add(Span.styled(": ", Theme.muted()));
                watch.add(Span.styled(simpleName(kinds.get(0).exceptionType()), style));
            }
            if (lastError > 0) {
                watch.add(Span.styled(" · the last " + TimeUtils.printSince(lastError) + " ago", Theme.muted()));
            }
            // the key to the Errors tab, as the Next line has its keys (a bare 9 read as a second count of errors)
            watch.add(Span.raw("   "));
            watch.add(Span.styled(" 9 ", Theme.hintKey()));
            watch.add(Span.raw(" show errors"));
        } else {
            watch.add(Span.styled("no errors", Theme.success()));
        }
        long stopped = info.routes.stream().filter(r -> r.state != null && !"Started".equals(r.state)).count();
        if (stopped > 0) {
            watch.add(Span.raw(" · "));
            watch.add(Span.styled(stopped + (stopped == 1 ? " route not started" : " routes not started"),
                    Theme.warning()));
        }
        long inflight = info.routes.stream().mapToLong(r -> r.inflight).sum();
        if (inflight > 0) {
            watch.add(Span.raw(" · " + inflight + " in flight"));
        }
        lines.add(Line.from(watch));
        return lines;
    }

    /**
     * When the newest error happened (epoch millis), 0 when not known: as the integration says it (Camel 4.24), else
     * from the errors the Errors tab loaded.
     */
    private static long lastError(IntegrationInfo info) {
        long last = info.lastErrorTimestamp;
        for (ErrorInfo e : info.errors) {
            last = Math.max(last, e.timestamp);
        }
        return last;
    }

    private static String simpleName(String type) {
        if (type == null) {
            return "error";
        }
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    /** How each route that another one sends to is reached: call (direct) or a hand-off (seda); none for the others. */
    private Map<String, String> howCalled() {
        Map<String, String> how = new LinkedHashMap<>();
        About.Model m = model;
        if (m != null) {
            m.routes().stream().filter(r -> r.how() != null).forEach(r -> how.put(r.routeId(), r.how()));
        }
        return how;
    }

    /** Where to go next: the README and the summary of the project too. */
    private Line next() {
        List<Span> spans = new ArrayList<>(
                List.of(Span.styled(" Next     ", Theme.muted()), Span.styled(" 4 ", Theme.hintKey()),
                        Span.raw(" what happened, per second  "), Span.styled(" 5 ", Theme.hintKey()),
                        Span.raw(" diagram  "), Span.styled(" 2 ", Theme.hintKey()), Span.raw(" source  ")));
        if (readme != null) {
            spans.add(Span.styled(" r ", Theme.hintKey()));
            spans.add(Span.raw(" readme  "));
        }
        if (dir != null) {
            spans.add(Span.styled(" s ", Theme.hintKey()));
            spans.add(Span.raw(" summary  "));
        }
        spans.add(Span.styled(" v ", Theme.hintKey()));
        spans.add(Span.raw(" all integrations"));
        return Line.from(spans);
    }

    /**
     * The last minutes from the timeline the integration records: how many exchanges, how they ended, the errors, and
     * when it was busy, as one line. The Timeline tab (4) has them second by second.
     */
    private Line happened(IntegrationInfo info, int width) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Last 5m  ", Theme.muted()));
        long end = ctx.timeline.endSecond(info.pid);
        if (end < 0) {
            spans.add(Span.styled("nothing recorded yet (the timeline dev console records it, as camel run does)",
                    Theme.muted()));
            return Line.from(spans);
        }
        List<String> order = info.routes.stream().map(r -> r.routeId).filter(id -> id != null).toList();
        List<Timeline.RouteSeries> series = ctx.timeline.routes(info.pid, order, end, HAPPENED_SECONDS);
        long[] perSecond = new long[HAPPENED_SECONDS];
        long completed = 0;
        long recovered = 0;
        long handled = 0;
        long failed = 0;
        // what went wrong on a hand-off, to an exchange already counted
        long handedOff = 0;
        Map<String, String> how = howCalled();
        for (Timeline.RouteSeries r : series) {
            String called = how.get(r.route());
            if ("call".equals(called)) {
                // a route called with direct is part of the exchange of its caller, which gets its failure too
                continue;
            }
            if (called == null) {
                completed += r.total(r.completed());
                recovered += r.total(r.recovered());
                for (int i = 0; i < HAPPENED_SECONDS; i++) {
                    perSecond[i] += Math.max(0, r.completed()[i]) + Math.max(0, r.recovered()[i])
                                    + Math.max(0, r.handled()[i]) + Math.max(0, r.failed()[i]);
                }
            }
            // one handed off (seda) carries the same message on: only what went wrong there, which its sender does not see
            handled += r.total(r.handled());
            failed += r.total(r.failed());
            if (called != null) {
                handedOff += r.total(r.handled()) + r.total(r.failed());
            }
        }
        spans.add(Span.styled(spark(perSecond, 30), Theme.info()));
        long done = completed + recovered + handled + failed - handedOff;
        spans.add(Span.styled("  " + done, Theme.success()));
        spans.add(Span.raw(done == 1 ? " exchange" : " exchanges"));
        if (recovered > 0) {
            spans.add(Span.styled(" · " + recovered + " recovered", Theme.warning()));
        }
        if (handled > 0) {
            spans.add(Span.styled(" · " + handled + " handled", Style.EMPTY.fg(Theme.ACCENT)));
        }
        if (failed > 0) {
            spans.add(Span.styled(" · " + failed + " failed", Theme.error()));
        }
        // the cause that happened most, with when it last did
        Timeline.CauseSeries top = null;
        long topTotal = 0;
        int last = -1;
        for (Timeline.CauseSeries c : ctx.timeline.causes(info.pid, end, HAPPENED_SECONDS)) {
            long t = 0;
            for (long v : c.values()) {
                t += v;
            }
            if (t > topTotal) {
                top = c;
                topTotal = t;
                for (int i = c.values().length - 1; i >= 0; i--) {
                    if (c.values()[i] > 0) {
                        last = i;
                        break;
                    }
                }
            }
        }
        if (top != null) {
            long ago = HAPPENED_SECONDS - 1 - last;
            spans.add(Span.raw(" · "));
            spans.add(Span.styled((top.rule() != null ? "🎲 " : "") + top.label() + " ×" + topTotal, Theme.error()));
            spans.add(Span.styled(" (last " + (ago > 0 ? ChaosTab.shortTime(ago * 1000) + " ago" : "now") + ")",
                    Theme.muted()));
        }
        if (done == 0) {
            long lastActive = ctx.timeline.lastActive(info.pid);
            spans.add(Span.styled(lastActive >= 0
                    ? "  (the last exchange " + ChaosTab.shortTime((end - lastActive) * 1000) + " ago)"
                    : "  (quiet)", Theme.muted()));
        }
        // what the routes that something starts did since the integration started, as their counters say
        Set<String> entry = new LinkedHashSet<>();
        ProjectOverview.Overview o = overview;
        if (o != null) {
            o.entryPoints().forEach(e -> entry.add(e.route()));
        }
        long total = 0;
        long totalFailed = 0;
        long totalHandled = 0;
        for (RouteInfo r : info.routes) {
            if (entry.isEmpty() || entry.contains(r.routeId)) {
                total += r.total;
                totalFailed += r.failed;
                totalHandled += r.failuresHandled;
            }
        }
        if (total > done) {
            String since = "  ·  since it started: " + total + (total == 1 ? " exchange" : " exchanges")
                           + (totalHandled > 0 ? ", " + totalHandled + " handled" : "")
                           + (totalFailed > 0 ? ", " + totalFailed + " failed" : "");
            // only when it fits: the line is about the last minutes first
            int used = spans.stream().mapToInt(sp -> sp.content().length()).sum();
            if (used + since.length() < width - 1) {
                spans.add(Span.styled(since, Theme.muted()));
            }
        }
        return Line.from(spans);
    }

    /** The values as a sparkline of the given width, a character taking the sum of its seconds. */
    static String spark(long[] values, int width) {
        long[] col = new long[width];
        long max = 0;
        for (int i = 0; i < values.length; i++) {
            int c = (int) ((long) i * width / values.length);
            col[c] += values[i];
        }
        for (long v : col) {
            max = Math.max(max, v);
        }
        StringBuilder sb = new StringBuilder(width);
        for (long v : col) {
            int level = max == 0 || v == 0 ? 0 : (int) Math.max(1, Math.round(v * 8.0 / max));
            sb.append(BLOCKS.charAt(level));
        }
        return sb.toString();
    }

    // ---- the purpose line ----

    static List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String word : text.split(" ")) {
            if (sb.length() + word.length() + 1 > width && !sb.isEmpty()) {
                lines.add(sb.toString());
                sb.setLength(0);
                if (lines.size() == maxLines) {
                    String lastLine = lines.remove(maxLines - 1);
                    lines.add(lastLine.length() > width - 1 ? lastLine.substring(0, width - 1) + "…" : lastLine + " …");
                    return lines;
                }
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(word);
        }
        if (!sb.isEmpty()) {
            lines.add(sb.toString());
        }
        return lines;
    }

    /** A panel: the one the cursor is in has the focus border, as the panels of the other screens. */
    private Block block(String title, Padding padding, boolean focused) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).borderStyle(ctx.paneBorder(focused))
                .title(title).padding(padding).build();
    }
}
