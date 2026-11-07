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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;
import org.apache.camel.util.TimeUtils;

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
    private static final String BLOCKS = " ▁▂▃▄▅▆▇█";
    // the strips have this many columns, whatever the zoom
    private static final int STRIP_COLUMNS = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MonitorContext ctx;
    private volatile ProjectOverview.Overview overview;
    private volatile String purpose;
    private volatile String purposeFrom;
    private volatile boolean loading;
    private volatile String error;
    private Path dir;
    private long loadedAt;
    // the zoom of What happened, as the windows of the Timeline tab (+ and -), and its last second when moved (← →);
    // -1 follows the last second recorded
    private int zoom = TimelineTab.WINDOWS.length - 1;
    private long stripsEnd = -1;
    // the line of In, Routes or Out the cursor is on (↑ ↓), in the order of the columns; -1 when none is
    private int cursor = -1;
    private volatile List<Target> targets = List.of();

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
                String[] p = purpose(d, o);
                onRenderThread.accept(() -> {
                    overview = o;
                    purpose = p != null ? p[0] : null;
                    purposeFrom = p != null ? p[1] : null;
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

    private static List<String> routeOrder(IntegrationInfo info) {
        return info.routes.stream().map(r -> r.routeId).filter(id -> id != null).toList();
    }

    void render(Frame frame, Rect area, IntegrationInfo info) {
        load(info, Runnable::run);
        ProjectOverview.Overview o = overview;
        int width = area.width();
        List<Line> head = new ArrayList<>();
        head.add(Line.from(Span.raw(" "), Span.raw(meta(info, o)),
                Span.styled(info.vanishing ? "  ○ stopped" : "  ● running",
                        info.vanishing ? Theme.muted() : Theme.success())));
        if (purpose != null) {
            for (String l : wrap(purpose, Math.max(20, width - 6), 2)) {
                head.add(Line.from(Span.styled(" " + l, Style.EMPTY.bold())));
            }
            head.add(Line.from(Span.styled(" " + purposeFrom, Theme.muted())));
        } else if (o != null) {
            head.add(Line.from(Span.styled(" No description yet: a comment at the top of " + mainFile(o)
                                           + " says what it does, or ask the AI (Shift+F8 on a route)",
                    Theme.muted())));
        } else {
            head.add(Line.from(Span.styled(error != null ? " " + error : " Reading the sources…", Theme.muted())));
        }

        List<Line> in = new ArrayList<>();
        List<Line> routes = new ArrayList<>();
        List<Line> out = new ArrayList<>();
        List<Target> inTargets = new ArrayList<>();
        List<Target> routeTargets = new ArrayList<>();
        List<Target> outTargets = new ArrayList<>();
        if (o != null) {
            columns(o, info, in, routes, out, inTargets, routeTargets, outTargets);
        }
        // the cursor goes down In, then Routes, then Out
        List<Target> all = new ArrayList<>();
        for (List<Target> column : List.of(inTargets, routeTargets, outTargets)) {
            for (Target t : column) {
                if (t != null) {
                    all.add(t);
                }
            }
        }
        targets = all;
        if (cursor >= all.size()) {
            cursor = all.size() - 1;
        }
        highlight(in, inTargets, routes, routeTargets, out, outTargets);
        int columnRows = Math.max(1, Math.max(in.size(), Math.max(routes.size(), out.size())));
        List<Line> strips = strips(info, width);
        int headHeight = head.size() + 2;
        int available = Math.max(3, area.height() - headHeight - strips.size());
        int columnHeight = Math.min(columnRows + 2, available);

        List<Line> routeStrips = routeStrips(info, width);
        int stripsHeight = strips.size();
        int happenedHeight = routeStrips.isEmpty()
                ? 0 : Math.max(0, Math.min(routeStrips.size() + 2, area.height() - headHeight - columnHeight - stripsHeight));
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(headHeight), Constraint.length(columnHeight), Constraint.length(stripsHeight),
                        Constraint.length(happenedHeight), Constraint.fill())
                .split(area);
        if (happenedHeight > 2) {
            frame.renderWidget(Paragraph.builder().text(Text.from(routeStrips))
                    .block(block(happenedTitle(info))).build(), rows.get(3));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(head))
                .block(block(" " + (info.name != null ? info.name : info.pid) + " ")).build(), rows.get(0));
        List<Rect> cols = Layout.horizontal()
                .constraints(Constraint.percentage(28), Constraint.percentage(42), Constraint.percentage(30))
                .split(rows.get(1));
        frame.renderWidget(Paragraph.builder().text(Text.from(in)).block(block(" In ")).build(), cols.get(0));
        frame.renderWidget(Paragraph.builder().text(Text.from(routes)).block(block(" Routes ")).build(), cols.get(1));
        frame.renderWidget(Paragraph.builder().text(Text.from(out)).block(block(" Out ")).build(), cols.get(2));
        frame.renderWidget(Paragraph.builder().text(Text.from(strips)).build(), rows.get(2));
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
        return " What happened ─ " + when + ", a column is " + seconds / STRIP_COLUMNS + "s ─ " + keys
               + ", 4 for each second ";
    }

    private List<Line> routeStrips(IntegrationInfo info, int width) {
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
        int per = seconds / STRIP_COLUMNS;
        int columns = STRIP_COLUMNS;
        for (Timeline.RouteSeries r : ctx.timeline.routes(info.pid, order, end, seconds)) {
            List<Span> spans = new ArrayList<>();
            String name = r.route().length() > label - 3 ? r.route().substring(0, label - 4) + "…" : r.route();
            spans.add(Span.raw("   " + name + " ".repeat(Math.max(1, label - 3 - name.length()))));
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
            for (long[] c : col) {
                long t = c[0] + c[1] + c[2] + c[3];
                if (c[4] > 0) {
                    spans.add(Span.styled("░", Theme.error()));
                } else if (c[6] > 0) {
                    // a circuit breaker of the route was open (half open dim), as on the Timeline
                    spans.add(Span.styled("▒", c[6] >= 2 ? Theme.notice() : Theme.muted()));
                } else if (t == 0 && c[5] == 0) {
                    spans.add(Span.styled("·", Theme.muted()));
                } else {
                    int level = (int) Math.max(1, Math.min(8, Math.ceil(t * 8.0 / max)));
                    Style style = c[3] > 0 ? Theme.error()
                            : c[2] > 0 ? Style.EMPTY.fg(Theme.ACCENT)
                            : c[1] > 0 || c[5] > 0 ? Theme.warning() : Theme.success();
                    spans.add(Span.styled(String.valueOf(BLOCKS.charAt(level)), style));
                }
            }
            long done = r.total(r.completed()) + r.total(r.recovered()) + r.total(r.handled()) + r.total(r.failed());
            spans.add(Span.styled("  " + done, Theme.success()));
            if (r.openSeconds() > 0) {
                spans.add(Span.styled("  ⚡ open " + r.openSeconds() + "s " + ago(last - lastAt(r.open(), end, seconds)),
                        Theme.notice()));
            }
            // the worst that happened, when it last did, and for a recovery the failure it recovered from (a failed or
            // handled one has its cause on the Last 5m line and on the Timeline)
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
                if (cause != null) {
                    int room = width - label - columns - 40;
                    if (room > 10) {
                        spans.add(Span.styled(" · ", Theme.muted()));
                        spans.add(Span.styled(cause.length() > room ? cause.substring(0, room - 1) + "…" : cause,
                                Theme.warning()));
                    }
                }
            }
            lines.add(Line.from(spans));
        }
        // the time axis: the first second of the window and the last
        String from = TIME.format(Instant.ofEpochSecond(end - seconds + 1));
        String to = stripsEnd >= 0 ? TIME.format(Instant.ofEpochSecond(end)) : "now";
        lines.add(Line.from(Span.styled(" ".repeat(label) + from
                                        + " ".repeat(Math.max(1, columns - from.length() - to.length())) + to,
                Theme.muted())));
        return lines;
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

    private static String mainFile(ProjectOverview.Overview o) {
        return o.files().isEmpty() ? "the main file" : o.files().get(0);
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

    private static void columns(
            ProjectOverview.Overview o, IntegrationInfo info, List<Line> in, List<Line> routes, List<Line> out,
            List<Target> inTargets, List<Target> routeTargets, List<Target> outTargets) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (RouteInfo r : info.routes) {
            if (r.routeId != null) {
                totals.put(r.routeId, r.total);
            }
        }
        for (ProjectOverview.EntryPoint e : o.entryPoints()) {
            Long total = e.route() != null ? totals.get(e.route()) : null;
            // a consumer by the kind of system it consumes from (its scheme)
            String kind = "consumer".equals(e.kind()) && e.label().indexOf(':') > 0
                    ? e.label().substring(0, e.label().indexOf(':')) : e.kind();
            in.add(Line.from(Span.raw(" " + icon(kind) + " "), Span.styled(e.label(), Style.EMPTY.bold()),
                    Span.styled(total != null ? "  " + total : "", Theme.success())));
            inTargets.add(e.route() != null ? new Target(e.route(), null) : null);
        }
        if (in.isEmpty()) {
            in.add(Line.from(Span.styled(" nothing from outside", Theme.muted())));
            inTargets.add(null);
        }

        // the routes that something else in the integration calls go under their caller
        Map<String, List<ProjectOverview.Link>> calledBy = new LinkedHashMap<>();
        Set<String> called = new LinkedHashSet<>();
        for (ProjectOverview.Link l : o.links()) {
            if (("call".equals(l.kind()) || "async".equals(l.kind())) && !l.from().equals(l.to())) {
                calledBy.computeIfAbsent(l.from(), k -> new ArrayList<>()).add(l);
                called.add(l.to());
            }
        }
        Set<String> shown = new LinkedHashSet<>();
        for (ProjectRoutes.Route r : o.flows()) {
            if (!called.contains(r.key())) {
                routeLines(o, r, 0, null, calledBy, totals, shown, routes, routeTargets);
            }
        }
        // what is left: routes called only by routes in a cycle
        for (ProjectRoutes.Route r : o.flows()) {
            if (!shown.contains(r.key())) {
                routeLines(o, r, 0, null, calledBy, totals, shown, routes, routeTargets);
            }
        }

        Set<String> systems = new LinkedHashSet<>();
        for (ProjectOverview.SystemUse s : o.systems()) {
            if ("in".equals(s.direction())) {
                continue;
            }
            String key = s.uri() + "|" + s.direction();
            if (systems.add(key)) {
                Long hits = outHits(info, s.uri(), s.route());
                out.add(Line.from(Span.raw(" " + icon(s.category()) + " "), Span.styled(shortUri(s.uri()), Style.EMPTY.bold()),
                        Span.styled("  " + s.direction(), Theme.muted()),
                        Span.styled(hits != null ? "  " + hits : "", Theme.success())));
                outTargets.add(new Target(s.route(), s.uri()));
                out.add(Line.from(Span.styled("    from " + s.route(), Theme.muted())));
                outTargets.add(null);
            }
        }
        if (out.isEmpty()) {
            out.add(Line.from(Span.styled(" nothing outside the integration", Theme.muted())));
            outTargets.add(null);
        }
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
                    && base.equals(baseUri(ep.uri))) {
                answer = (answer != null ? answer : 0) + ep.hits;
            }
        }
        return answer;
    }

    /** The URI without its options, and the same with or without the // after the scheme. */
    static String baseUri(String uri) {
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        String answer = q >= 0 ? uri.substring(0, q) : uri;
        answer = answer.replaceFirst("^([a-zA-Z0-9+.-]+):/{0,2}", "$1:");
        return answer.endsWith("/") ? answer.substring(0, answer.length() - 1) : answer;
    }

    private static void routeLines(
            ProjectOverview.Overview o, ProjectRoutes.Route r, int depth, String how,
            Map<String, List<ProjectOverview.Link>> calledBy, Map<String, Long> totals, Set<String> shown,
            List<Line> lines, List<Target> targets) {
        if (!shown.add(r.key()) || depth > 6) {
            return;
        }
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" " + "  ".repeat(depth) + (how != null ? how + " " : "")));
        spans.add(Span.styled(r.id() != null ? r.id() : r.key(), Style.EMPTY.bold()));
        String from = r.from() != null ? r.from().scheme() : r.kind();
        spans.add(Span.styled("  " + from + " → " + r.steps() + (r.steps() == 1 ? " step" : " steps"), Theme.muted()));
        Long total = totals.get(r.id());
        if (total != null) {
            spans.add(Span.styled("  " + total, Theme.success()));
        }
        lines.add(Line.from(spans));
        targets.add(new Target(r.id() != null ? r.id() : r.key(), null));
        for (ProjectOverview.Link l : calledBy.getOrDefault(r.key(), List.of())) {
            ProjectRoutes.Route target = o.route(l.to());
            if (target != null) {
                // ↳ a call that waits for the route, ⇢ a hand-off that does not
                routeLines(o, target, depth + 1, "call".equals(l.kind()) ? "↳" : "⇢", calledBy, totals, shown, lines,
                        targets);
            }
        }
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

    private List<Line> strips(IntegrationInfo info, int width) {
        List<Line> lines = new ArrayList<>();
        lines.add(happened(info, width));
        List<Span> watch = new ArrayList<>();
        watch.add(Span.styled(" Watch    ", Theme.muted()));
        if (info.errorCount > 0) {
            watch.add(Span.styled("✗ " + info.errorCount + (info.errorCount == 1 ? " error" : " errors"), Theme.error()));
            watch.add(Span.styled(" (9 Errors)", Theme.muted()));
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
        lines.add(Line.from(Span.styled(" Next     ", Theme.muted()), Span.styled(" 4 ", Theme.hintKey()),
                Span.raw(" what happened, per second  "), Span.styled(" 5 ", Theme.hintKey()),
                Span.raw(" diagram  "), Span.styled(" 2 ", Theme.hintKey()), Span.raw(" source  "),
                Span.styled(" v ", Theme.hintKey()), Span.raw(" all integrations")));
        return lines;
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
        for (Timeline.RouteSeries r : series) {
            completed += r.total(r.completed());
            recovered += r.total(r.recovered());
            handled += r.total(r.handled());
            failed += r.total(r.failed());
            for (int i = 0; i < HAPPENED_SECONDS; i++) {
                perSecond[i] += Math.max(0, r.completed()[i]) + Math.max(0, r.recovered()[i])
                                + Math.max(0, r.handled()[i]) + Math.max(0, r.failed()[i]);
            }
        }
        spans.add(Span.styled(spark(perSecond, 30), Theme.info()));
        long done = completed + recovered + handled + failed;
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
            spans.add(Span.styled("  ·  since it started: " + total + (total == 1 ? " exchange" : " exchanges")
                                  + (totalHandled > 0 ? ", " + totalHandled + " handled" : "")
                                  + (totalFailed > 0 ? ", " + totalFailed + " failed" : ""),
                    Theme.muted()));
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

    /**
     * What the integration does, in the words of its author: the comment at the top of a route file (the files in the
     * order of the analysis), else the descriptions of the routes that something outside starts. Returns the text and
     * where it comes from, or null.
     */
    static String[] purpose(Path dir, ProjectOverview.Overview o) {
        for (String file : o.files()) {
            try {
                String text = leadingComment(file, Files.readString(dir.resolve(file), StandardCharsets.UTF_8));
                if (text != null) {
                    return new String[] { text, "from the comment at the top of " + file };
                }
            } catch (IOException e) {
                // an unreadable file has no comment
            }
        }
        List<String> descriptions = new ArrayList<>();
        Set<String> entryRoutes = new LinkedHashSet<>();
        o.entryPoints().forEach(e -> entryRoutes.add(e.route()));
        for (ProjectRoutes.Route r : o.flows()) {
            if (r.hasDescription() && entryRoutes.contains(r.key())) {
                descriptions.add(r.description());
            }
        }
        for (ProjectRoutes.Route r : o.flows()) {
            if (r.hasDescription() && !entryRoutes.contains(r.key()) && descriptions.size() < 2) {
                descriptions.add(r.description());
            }
        }
        if (!descriptions.isEmpty()) {
            return new String[] {
                    String.join(" ", descriptions.subList(0, Math.min(2, descriptions.size()))),
                    "from the descriptions of the routes" };
        }
        return null;
    }

    /**
     * The comment a file starts with: the {@code #} lines of YAML, the Javadoc or {@code //} lines before the class of
     * Java, the first {@code <!-- -->} of XML. A license header is no description.
     */
    static String leadingComment(String file, String content) {
        String text = null;
        if (file.endsWith(".yaml") || file.endsWith(".yml")) {
            StringBuilder sb = new StringBuilder();
            for (String line : content.lines().toList()) {
                String l = line.strip();
                if (l.startsWith("#")) {
                    sb.append(l.replaceFirst("^#+\\s?", "")).append(' ');
                } else if (!l.isEmpty() || !sb.isEmpty()) {
                    break;
                }
            }
            text = sb.toString();
        } else if (file.endsWith(".java")) {
            int cls = content.indexOf(" class ");
            String before = cls > 0 ? content.substring(0, cls) : content;
            int doc = before.lastIndexOf("/**");
            if (doc >= 0) {
                int end = before.indexOf("*/", doc);
                text = end > 0 ? before.substring(doc + 3, end).replaceAll("(?m)^\\s*\\*\\s?", " ") : null;
            } else {
                StringBuilder sb = new StringBuilder();
                for (String line : before.lines().toList()) {
                    String l = line.strip();
                    if (l.startsWith("//") && !l.startsWith("///") && !l.startsWith("//DEPS")
                            && !l.startsWith("//JAVA")) {
                        sb.append(l.substring(2).strip()).append(' ');
                    }
                }
                text = sb.toString();
            }
        } else if (file.endsWith(".xml")) {
            int start = content.indexOf("<!--");
            int end = start >= 0 ? content.indexOf("-->", start) : -1;
            text = end > 0 ? content.substring(start + 4, end) : null;
        }
        if (text == null) {
            return null;
        }
        // a Javadoc tag ends the description
        int tag = text.indexOf(" @");
        if (tag > 0) {
            text = text.substring(0, tag);
        }
        text = text.replaceAll("\\s+", " ").strip();
        if (text.isEmpty() || text.contains("Licensed to the Apache") || text.contains("SPDX-License")) {
            return null;
        }
        return text.length() > 300 ? text.substring(0, 297) + "…" : text;
    }

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

    private static Block block(String title) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build();
    }
}
