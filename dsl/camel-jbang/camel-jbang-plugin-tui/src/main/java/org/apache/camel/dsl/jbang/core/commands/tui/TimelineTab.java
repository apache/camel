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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

/**
 * The timeline of an integration (CAMEL-26691): the last minute on one time axis, one column per second. On top the
 * cause, the errors per second by kind, and with camel-chaos the faults of each rule marked 🎲. Under it the effect, a
 * line per route of the exchanges per second, coloured by the worst that happened to them that second (failed, handled
 * by the error handler, recovered by redelivery, completed), with the seconds a chaos rule had the route down shaded.
 * The selected route is shown in detail at the bottom: its outcomes stacked per second, its mean, p95 and max time, its
 * exchanges in flight, its redelivery attempts and its slowest calls. Paused, a cursor picks a second, and Enter shows
 * the exchanges the integration kept of it (the failed and handled ones, and the slowest) with their calls. The
 * integration records all of it (the timeline dev console); a quiet integration is a calm screen.
 */
class TimelineTab extends AbstractTableTab {

    static final int[] WINDOWS = { 60, 120, 300 };
    private static final String BLOCKS = "▁▂▃▄▅▆▇█";
    private static final int CAUSE_ROWS = 4;
    private static final int DETAIL_ROWS = 4;
    private static final int DETAIL_LINES = DETAIL_ROWS + 5;
    private static final int SUFFIX = 30;
    private static final int MAX_LABEL = 18;

    private final LongSupplier clock;
    // the seconds a column has, and the characters a column is wide, as the last render worked them out
    private int per = 1;
    private int width = 1;
    private int window;
    // the second the screen stays at while paused, or -1 to follow now
    private long pausedAt = -1;
    // the column of the cursor while paused
    private int cursor = -1;
    private int routeLinesY = -1;
    private final TimelineExchanges drill = new TimelineExchanges();

    TimelineTab(MonitorContext ctx) {
        this(ctx, System::currentTimeMillis);
    }

    TimelineTab(MonitorContext ctx, LongSupplier clock) {
        super(ctx);
        this.clock = clock;
    }

    @Override
    protected boolean selectsFirstRow() {
        return true;
    }

    @Override
    protected int getRowCount() {
        return routes(ctx.findSelectedIntegration()).size();
    }

    @Override
    public void onTabSelected() {
        refreshWhileVisible();
        // nothing in the last minute (an app that worked at startup, a batch): open on the window with the last exchange
        IntegrationInfo info = ctx.findSelectedIntegration();
        long last = info != null ? ctx.timeline.lastActive(info.pid) : -1;
        if (pausedAt < 0 && window == 0 && last >= 0) {
            long ago = nowSecond() - last;
            for (int i = 0; i < WINDOWS.length && ago >= WINDOWS[window]; i++) {
                window = Math.min(WINDOWS.length - 1, window + 1);
            }
        }
        // the detail opens on the route that had the worst time in the window
        List<Timeline.RouteSeries> routes = routes(ctx.findSelectedIntegration());
        int worst = worst(routes);
        if (worst >= 0) {
            tableState.select(worst);
        }
    }

    /** The route that failed the most, else was handled, recovered or down the most, else the busiest; -1 if none. */
    static int worst(List<Timeline.RouteSeries> routes) {
        int best = -1;
        long[] bestScore = null;
        for (int i = 0; i < routes.size(); i++) {
            Timeline.RouteSeries r = routes.get(i);
            long[] score = {
                    r.total(r.failed()), r.total(r.handled()), r.total(r.recovered()) + r.total(r.down()),
                    r.total(r.completed()) };
            if (bestScore == null || compare(score, bestScore) > 0) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    private static int compare(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return Long.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    @Override
    public void refreshWhileVisible() {
        // the whole timeline once, when the screen first shows an integration (each refresh reads the last seconds)
        DataRefreshService data = ctx.dataService;
        List<Long> pids = data != null ? data.selectedPidAsList() : List.of();
        if (!pids.isEmpty()) {
            data.refreshTimelineData(pids);
        }
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.F8) && ke.hasShift()) {
            // explain with AI: the story of cause and effect of the window
            IntegrationInfo info = ctx.findSelectedIntegration();
            if (info != null && ctx.askAiCallback != null) {
                ctx.askAiCallback.ask(TimelineAiPrompt.of(info, ctx.timeline.causes(info.pid, endSecond(), seconds()),
                        routes(info), seconds()));
            }
            return true;
        }
        if (ke.isChar('w')) {
            window = (window + 1) % WINDOWS.length;
            return true;
        }
        if (ke.isChar(' ')) {
            if (pausedAt < 0) {
                pause();
            } else {
                follow();
            }
            return true;
        }
        if (ke.isConfirm()) {
            // the exchanges of the second at the cursor, or the selected one in detail
            if (drill.isOpen()) {
                drill.toggleDetail();
            } else {
                if (pausedAt < 0) {
                    pause();
                }
                drill.open(cursorFrom(), cursorTo());
            }
            return true;
        }
        if (pausedAt >= 0 && (ke.isLeft() || ke.isRight())) {
            int last = columnCount() - 1;
            if (ke.hasShift()) {
                shift(ke.isLeft() ? -Math.max(5, seconds() / 4) : Math.max(5, seconds() / 4));
            } else if (ke.isLeft()) {
                if (cursor > 0) {
                    cursor--;
                } else {
                    shift(-per);
                }
            } else if (cursor < last) {
                cursor++;
            } else {
                shift(per);
            }
            if (drill.isOpen()) {
                drill.open(cursorFrom(), cursorTo());
            }
            return true;
        }
        return false;
    }

    private void pause() {
        pausedAt = nowSecond();
        cursor = columnCount() - 1;
    }

    private void follow() {
        pausedAt = -1;
        cursor = -1;
        drill.close();
    }

    /** Moves the paused window, between the first second recorded and now. */
    private void shift(long by) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        long first = info != null ? ctx.timeline.firstSecond(info.pid) : -1;
        long now = nowSecond();
        long earliest = first >= 0 ? Math.min(now, first + seconds() - 1) : now;
        pausedAt = Math.max(earliest, Math.min(now, pausedAt + by));
    }

    private int columnCount() {
        return (seconds() + per - 1) / per;
    }

    /** The first second of the column at the cursor. */
    long cursorFrom() {
        return endSecond() - seconds() + 1 + (long) Math.max(0, cursor) * per;
    }

    long cursorTo() {
        return Math.min(endSecond(), cursorFrom() + per - 1);
    }

    @Override
    public void navigateUp() {
        if (drill.isOpen() && !drill.isDetail()) {
            drill.move(-1, keptAtCursor().size());
        } else {
            super.navigateUp();
        }
    }

    @Override
    public void navigateDown() {
        if (drill.isOpen() && !drill.isDetail()) {
            drill.move(1, keptAtCursor().size());
        } else {
            super.navigateDown();
        }
    }

    private List<Timeline.Kept> keptAtCursor() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return List.of();
        }
        // the rule of an exchange that no longer has the chaos property, from the recent faults of the chaos report
        return ctx.timeline.exchanges(info.pid, drill.from(), drill.to()).stream()
                .map(k -> k.chaosRule() != null ? k : k.withChaosRule(ChaosMarks.ruleOf(ctx, info, k.exchangeId(), null)))
                .toList();
    }

    @Override
    public boolean handleEscape() {
        if (drill.isOpen()) {
            drill.back();
            return true;
        }
        if (pausedAt >= 0) {
            follow();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (routeLinesY < 0 || lastTableArea == null) {
            return false;
        }
        return handleTableClick(me, lastTableArea, tableState, getRowCount());
    }

    int seconds() {
        return WINDOWS[window];
    }

    boolean isPaused() {
        return pausedAt >= 0;
    }

    /** The last second the integration reported, or else the last whole second (the current one is still counting). */
    private long nowSecond() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        long end = info != null ? ctx.timeline.endSecond(info.pid) : -1;
        return end >= 0 ? end : clock.getAsLong() / 1000 - 1;
    }

    private long endSecond() {
        return pausedAt >= 0 ? pausedAt : nowSecond();
    }

    private List<Timeline.RouteSeries> routes(IntegrationInfo info) {
        if (info == null) {
            return List.of();
        }
        List<String> order = info.routes.stream().map(r -> r.routeId).filter(id -> id != null).toList();
        return ctx.timeline.routes(info.pid, order, endSecond(), seconds());
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<Timeline.RouteSeries> routes = routes(info);
        List<Timeline.CauseSeries> causes = ctx.timeline.causes(info.pid, endSecond(), seconds());
        boolean anyCause = causes.stream().anyMatch(c -> sum(c.values()) > 0);
        int causeHeight = anyCause ? CAUSE_ROWS + 1 : 1;
        // the cause, the legend of the routes, a line per route and the time axis, in a border
        int detailHeight = DETAIL_LINES + 2;
        int topHeight = Math.max(6, Math.min(area.height() - detailHeight, causeHeight + routes.size() + 4));
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(topHeight), Constraint.length(detailHeight), Constraint.fill())
                .split(area);
        int inner = Math.max(20, area.width() - 2);
        int label = Math.min(MAX_LABEL,
                Math.max(11, routes.stream().mapToInt(r -> r.route().length()).max().orElse(0))) + 3;
        // a second is 1 to 3 characters wide as the screen allows, or a character is several seconds
        int room = Math.max(10, inner - label - SUFFIX - 1);
        int per = room >= seconds() ? 1 : (int) Math.ceil((double) seconds() / room);
        width = per == 1 ? Math.max(1, Math.min(3, room / seconds())) : 1;
        this.per = per;
        int columns = (seconds() + per - 1) / per * width;
        if (cursor >= 0) {
            // the window or the width changed since the cursor was put
            cursor = Math.min(cursor, columnCount() - 1);
        }

        List<Line> lines = new ArrayList<>();
        renderCauses(lines, causes, anyCause, info, columns, per, label);
        lines.add(Line.from(Span.raw(pad("", label)), Span.styled("Routes  ", Theme.label()),
                Span.styled("█", Theme.success()), Span.raw(" completed  "),
                Span.styled("█", Theme.warning()), Span.raw(" recovered  "),
                Span.styled("█", handledStyle()), Span.raw(" handled  "),
                Span.styled("█", Theme.error()), Span.raw(" failed  "),
                Span.styled("░", Theme.error()), Span.raw(" down")));
        Integer sel = tableState.selected();
        routeLinesY = lines.size();
        for (int i = 0; i < routes.size(); i++) {
            lines.add(routeLine(routes.get(i), sel != null && sel == i, columns, per, label));
        }
        if (routes.isEmpty()) {
            lines.add(Line.from(Span.styled(pad("", label) + "no routes yet", Theme.muted())));
        }
        lines.add(axis(columns, per, label));
        Rect top = rows.get(0);
        // the rows of the routes, as a table with a header line of two would have them, for the clicks
        lastTableArea = new Rect(top.x(), top.y() + 1 + routeLinesY - 2, top.width(), routes.size() + 2);
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block(title(info))).build(), top);

        if (drill.isOpen()) {
            List<Timeline.Kept> kept = keptAtCursor();
            frame.renderWidget(Paragraph.builder().text(Text.from(drill.lines(kept, DETAIL_LINES)))
                    .block(block(drill.title(kept, ctx.timeline.done(info.pid, drill.from(), drill.to())))).build(),
                    rows.get(1));
            return;
        }
        Timeline.RouteSeries selected = sel != null && sel >= 0 && sel < routes.size() ? routes.get(sel) : null;
        renderDetail(frame, rows.get(1), selected, columns, per, label, info);

    }

    private String title(IntegrationInfo info) {
        String span = seconds() >= 120 ? (seconds() / 60) + "m" : seconds() + "s";
        StringBuilder sb = new StringBuilder(" Timeline ─ ").append(info.name != null ? info.name : info.pid)
                .append(" ─ last ").append(span).append(' ');
        long last = ctx.timeline.lastActive(info.pid);
        long quiet = nowSecond() - last;
        if (pausedAt < 0 && last >= 0 && quiet >= seconds()) {
            // a quiet window: when the last exchange was, out of the window
            sb.append("─ the last exchange ").append(ChaosTab.shortTime(quiet * 1000)).append(" ago ");
        } else if (pausedAt < 0 && last < 0 && ctx.timeline.endSecond(info.pid) >= 0) {
            sb.append("─ no exchange in the last 5m ");
        }
        if (pausedAt >= 0) {
            long ago = nowSecond() - pausedAt;
            sb.append("─ paused").append(ago > 0 ? ", " + ChaosTab.shortTime(ago * 1000) + " ago " : " ");
        }
        return sb.toString();
    }

    // ---- the cause ----

    private void renderCauses(
            List<Line> lines, List<Timeline.CauseSeries> causes, boolean anyCause, IntegrationInfo info, int columns,
            int per, int label) {
        List<Span> legend = new ArrayList<>();
        legend.add(Span.raw(pad("", label)));
        legend.add(Span.styled("Errors/s  ", Theme.label()));
        List<long[]> values = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        int colour = 0;
        for (Timeline.CauseSeries c : causes) {
            if (sum(c.values()) == 0) {
                continue;
            }
            Style style = causeStyle(colour++);
            values.add(cols(c.values(), false));
            styles.add(style);
            legend.add(Span.styled("█ ", style));
            legend.add(Span.raw((c.rule() != null ? "🎲 " : "") + c.label() + "  "));
        }
        if (!anyCause) {
            boolean seen = ctx.timeline.errorsSeen(info.pid);
            legend.add(Span.styled(seen ? "none" : "reading the errors…", Theme.muted()));
            if (info.chaos != null && info.chaos.enabled) {
                legend.add(Span.styled("  · 🎲 no chaos fault", Theme.muted()));
            }
            lines.add(Line.from(legend));
            return;
        }
        lines.add(Line.from(legend));
        lines.addAll(stacked(values, styles, CAUSE_ROWS, columns, label));
    }

    private static Style causeStyle(int i) {
        Style[] palette = {
                Theme.error(), Style.EMPTY.fg(Theme.ACCENT), Theme.notice(), Theme.info(), Theme.warning(),
                Style.EMPTY.fg(Theme.diagramTo()) };
        return palette[i % palette.length];
    }

    // ---- the routes ----

    private Line routeLine(Timeline.RouteSeries r, boolean selected, int columns, int per, int label) {
        List<Span> spans = new ArrayList<>();
        String name = r.route().length() > label - 3 ? r.route().substring(0, label - 4) + "…" : r.route();
        spans.add(selected
                ? Span.styled(pad(">> " + name, label), Theme.selectionBg())
                : Span.raw(pad("   " + name, label)));
        long[] completed = cols(r.completed(), false);
        long[] recovered = cols(r.recovered(), false);
        long[] handled = cols(r.handled(), false);
        long[] failed = cols(r.failed(), false);
        long[] down = cols(r.down(), true);
        long[] attempts = cols(r.attempts(), false);
        long max = 1;
        for (int c = 0; c < columns; c++) {
            max = Math.max(max, total(completed[c], recovered[c], handled[c], failed[c]));
        }
        for (int c = 0; c < columns; c++) {
            long t = total(completed[c], recovered[c], handled[c], failed[c]);
            if (down[c] > 0) {
                spans.add(Span.styled("░", Theme.error()));
            } else if (completed[c] < 0) {
                spans.add(Span.raw(" "));
            } else if (t == 0) {
                spans.add(Span.styled("·", Theme.muted()));
            } else {
                int level = (int) Math.max(1, Math.min(8, Math.ceil(t * 8.0 / max)));
                Style style = failed[c] > 0 ? Theme.error()
                        : handled[c] > 0 ? handledStyle()
                        : recovered[c] > 0 || attempts[c] > 0 ? Theme.warning() : Theme.success();
                spans.add(Span.styled(String.valueOf(BLOCKS.charAt(level - 1)), style));
            }
        }
        spans.add(Span.raw("  "));
        spans.addAll(summary(r));
        return Line.from(spans);
    }

    /** The rate of the route over the window, and the worst that happened to its exchanges. */
    private List<Span> summary(Timeline.RouteSeries r) {
        List<Span> spans = new ArrayList<>();
        long done = r.total(r.completed()) + r.total(r.recovered()) + r.total(r.handled()) + r.total(r.failed());
        int known = 0;
        for (long v : r.completed()) {
            if (v >= 0) {
                known++;
            }
        }
        if (known == 0) {
            spans.add(Span.styled("no data yet", Theme.muted()));
            return spans;
        }
        if (r.total(r.down()) > 0) {
            spans.add(Span.styled("down " + r.total(r.down()) + "s ", Theme.error()));
        }
        spans.add(Span.raw(rate(done, known)));
        if (r.total(r.failed()) > 0) {
            spans.add(Span.styled("  " + r.total(r.failed()) + " failed", Theme.error()));
        } else if (r.total(r.handled()) > 0) {
            spans.add(Span.styled("  " + r.total(r.handled()) + " handled", handledStyle()));
        } else if (r.total(r.recovered()) > 0) {
            spans.add(Span.styled("  " + r.total(r.recovered()) + " recovered", Theme.warning()));
        } else if (r.total(r.attempts()) > 0) {
            spans.add(Span.styled("  " + r.total(r.attempts()) + " redeliveries", Theme.warning()));
        } else if (done == 0) {
            spans.add(Span.styled("  idle", Theme.muted()));
        }
        return spans;
    }

    static String rate(long count, int seconds) {
        double perSecond = seconds > 0 ? (double) count / seconds : 0;
        if (count == 0) {
            return "0/s";
        }
        return perSecond >= 10 ? String.format(Locale.US, "%.0f/s", perSecond)
                : perSecond >= 0.1 ? String.format(Locale.US, "%.1f/s", perSecond) : count + " in " + seconds + "s";
    }

    private Line axis(int columns, int per, int label) {
        char[] axis = new char[columns];
        Arrays.fill(axis, ' ');
        String start = "-" + axisTime(seconds());
        String middle = "-" + axisTime(seconds() / 2);
        String end = "now";
        put(axis, 0, start);
        put(axis, columns / 2 - middle.length() / 2, middle);
        put(axis, columns - end.length(), pausedAt >= 0 ? "" : end);
        if (pausedAt >= 0) {
            long ago = Math.max(0, nowSecond() - pausedAt);
            String at = ago > 0 ? "-" + ChaosTab.shortTime(ago * 1000) : end;
            put(axis, columns - at.length(), at);
        }
        if (cursor >= 0) {
            // the cursor under its column, the time of it at the right of the axis
            for (int i = 0; i < width; i++) {
                int at = cursor * width + i;
                if (at < axis.length) {
                    axis[at] = '▲';
                }
            }
        }
        String text = new String(axis);
        if (per > 1) {
            text += "  " + per + "s a column";
        }
        if (cursor >= 0) {
            long ago = nowSecond() - cursorTo();
            return Line.from(Span.raw(pad("", label)), Span.styled(text, Theme.muted()),
                    Span.styled("  ▲ " + (ago > 0 ? ChaosTab.shortTime(ago * 1000) + " ago" : "now"), Theme.label()),
                    Span.styled(drill.isOpen() ? "" : "  Enter: its exchanges", Theme.muted()));
        }
        return Line.from(Span.raw(pad("", label)), Span.styled(text, Theme.muted()));
    }

    /** A time on the axis: 60s, 30s, 2m, 2m30s. */
    static String axisTime(int seconds) {
        return seconds <= 90 ? seconds + "s" : ChaosTab.shortTime(seconds * 1000L);
    }

    private static void put(char[] axis, int at, String s) {
        for (int i = 0; i < s.length() && at + i < axis.length; i++) {
            if (at + i >= 0) {
                axis[at + i] = s.charAt(i);
            }
        }
    }

    // ---- the selected route ----

    private void renderDetail(
            Frame frame, Rect area, Timeline.RouteSeries r, int columns, int per, int label, IntegrationInfo info) {
        List<Line> lines = new ArrayList<>();
        if (r == null) {
            frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.raw(""))))
                    .block(block(" Route ")).build(), area);
            return;
        }
        List<long[]> values = List.of(cols(r.completed(), false), cols(r.recovered(), false),
                cols(r.handled(), false), cols(r.failed(), false));
        List<Style> styles = List.of(Theme.success(), Theme.warning(), handledStyle(), Theme.error());
        int rows = Math.max(2, Math.min(DETAIL_ROWS, area.height() - 7));
        lines.addAll(stacked(values, styles, rows, columns, label));

        long[] mean = cols(r.mean(), true);
        long maxMean = 0;
        long sumMean = 0;
        int n = 0;
        for (int i = 0; i < r.mean().length; i++) {
            long m = r.mean()[i];
            // the average of the seconds that had exchanges, not of the idle ones
            if (m >= 0 && r.completed()[i] + r.recovered()[i] + r.handled()[i] > 0) {
                maxMean = Math.max(maxMean, m);
                sumMean += m;
                n++;
            }
        }
        long p95 = 0;
        long max = 0;
        for (int i = 0; i < r.p95().length; i++) {
            p95 = Math.max(p95, r.p95()[i]);
            max = Math.max(max, r.max()[i]);
        }
        List<Span> meanLine = new ArrayList<>();
        meanLine.add(Span.styled(pad("   mean time", label), Theme.label()));
        // relative to twice the average, so a steady time is a calm line and a slow second stands out
        long scale = n > 0 ? Math.max(maxMean, 2 * sumMean / n) : 0;
        meanLine.add(Span.styled(spark(mean, scale), Theme.info()));
        meanLine.add(Span.raw("  " + (n > 0 ? "avg " + ChaosTab.shortTime(sumMean / n) : "no exchange")));
        if (max > 0) {
            // of the exchanges that came into the route, in their slowest second
            meanLine.add(Span.raw(" p95 " + ChaosTab.shortTime(p95) + " max " + ChaosTab.shortTime(max)));
        }
        lines.add(Line.from(meanLine));

        long[] inflight = cols(r.inflight(), true);
        long maxInflight = 0;
        long last = 0;
        for (long v : r.inflight()) {
            if (v >= 0) {
                maxInflight = Math.max(maxInflight, v);
                last = v;
            }
        }
        lines.add(Line.from(Span.styled(pad("   in flight", label), Theme.label()),
                Span.styled(spark(inflight, maxInflight), Theme.notice()),
                Span.raw("  now " + last + (maxInflight > last ? ", max " + maxInflight : ""))));

        long[] attempts = cols(r.attempts(), false);
        long maxAttempts = 0;
        for (long v : attempts) {
            maxAttempts = Math.max(maxAttempts, v);
        }
        lines.add(Line.from(Span.styled(pad("   redelivery", label), Theme.label()),
                Span.styled(spark(attempts, maxAttempts), Theme.warning()),
                Span.raw("  " + r.total(r.attempts()) + " attempts")));

        List<Span> calls = TimelineExchanges.slowestCalls(
                ctx.timeline.exchanges(info.pid, endSecond() - seconds() + 1, endSecond()), r.route(), 3);
        List<Span> callLine = new ArrayList<>();
        callLine.add(Span.styled(pad("   calls", label), Theme.label()));
        if (calls.isEmpty()) {
            callLine.add(Span.styled("no remote calls in the exchanges kept", Theme.muted()));
        } else {
            callLine.addAll(calls);
        }
        lines.add(Line.from(callLine));

        List<Span> totals = new ArrayList<>();
        totals.add(Span.raw(pad("", label)));
        addTotal(totals, r.total(r.completed()), "completed", Theme.success());
        addTotal(totals, r.total(r.recovered()), "recovered by redelivery", Theme.warning());
        addTotal(totals, r.total(r.handled()), "handled by the error handler", handledStyle());
        addTotal(totals, r.total(r.failed()), "failed", Theme.error());
        lines.add(Line.from(totals));
        String title = " " + r.route() + " ─ per second ";
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block(title)).build(), area);
    }

    private static void addTotal(List<Span> spans, long count, String words, Style style) {
        spans.add(Span.styled(String.valueOf(count), count > 0 ? style : Theme.muted()));
        spans.add(Span.raw(" " + words + "   "));
    }

    // ---- drawing ----

    /**
     * Columns of values stacked, the first series at the bottom, scaled to the highest column, with eighths of a cell
     * at the top of each column and the scale at the left.
     */
    static List<Line> stacked(List<long[]> series, List<Style> styles, int rows, int columns, int label) {
        long[] totals = new long[columns];
        long max = 0;
        for (long[] s : series) {
            for (int c = 0; c < columns && c < s.length; c++) {
                totals[c] += Math.max(0, s[c]);
            }
        }
        for (long t : totals) {
            max = Math.max(max, t);
        }
        List<Line> lines = new ArrayList<>();
        int cells = rows * 8;
        for (int row = rows - 1; row >= 0; row--) {
            List<Span> spans = new ArrayList<>();
            String scale = row == rows - 1 && max > 0 ? String.valueOf(max) : row == 0 ? "0" : "";
            spans.add(Span.styled(padLeft(scale, label - 2) + " ┤", Theme.muted()));
            for (int c = 0; c < columns; c++) {
                long t = totals[c];
                int height = max == 0 || t == 0 ? 0 : (int) Math.max(1, Math.round((double) t * cells / max));
                int filled = Math.max(0, Math.min(8, height - row * 8));
                if (filled == 0) {
                    spans.add(Span.raw(" "));
                    continue;
                }
                // the series at the top of what is filled in this cell
                int at = row * 8 + filled - 1;
                long cum = 0;
                Style style = styles.get(styles.size() - 1);
                for (int k = 0; k < series.size(); k++) {
                    long v = c < series.get(k).length ? Math.max(0, series.get(k)[c]) : 0;
                    cum += v;
                    if (v > 0 && Math.round((double) cum * height / t) > at) {
                        style = styles.get(k);
                        break;
                    }
                }
                spans.add(Span.styled(String.valueOf(BLOCKS.charAt(filled - 1)), style));
            }
            lines.add(Line.from(spans));
        }
        return lines;
    }

    /** A sparkline of values (-1 is unknown, blank), relative to the highest. */
    static String spark(long[] values, long max) {
        StringBuilder sb = new StringBuilder();
        for (long v : values) {
            if (v < 0) {
                sb.append(' ');
            } else if (max <= 0 || v == 0) {
                sb.append('▁');
            } else {
                sb.append(BLOCKS.charAt((int) Math.max(0, Math.min(7, Math.ceil(v * 8.0 / max) - 1))));
            }
        }
        return sb.toString();
    }

    /** The seconds as the columns of the chart: several seconds a column, or a second several characters wide. */
    private long[] cols(long[] seconds, boolean highest) {
        long[] columns = columnsOf(seconds, per, highest);
        if (width == 1) {
            return columns;
        }
        long[] wide = new long[columns.length * width];
        for (int i = 0; i < wide.length; i++) {
            wide[i] = columns[i / width];
        }
        return wide;
    }

    /**
     * The seconds as columns of several seconds each: the sum of the counts, or the highest value of a level (a mean,
     * the in flight count). A column with no known second is -1.
     */
    static long[] columnsOf(long[] seconds, int per, boolean highest) {
        int columns = (seconds.length + per - 1) / per;
        long[] answer = new long[columns];
        Arrays.fill(answer, -1);
        for (int i = 0; i < seconds.length; i++) {
            long v = seconds[i];
            if (v < 0) {
                continue;
            }
            int c = i / per;
            answer[c] = answer[c] < 0 ? v : highest ? Math.max(answer[c], v) : answer[c] + v;
        }
        return answer;
    }

    private static long total(long... values) {
        long sum = 0;
        for (long v : values) {
            sum += Math.max(0, v);
        }
        return sum;
    }

    private static long sum(long[] values) {
        return total(values);
    }

    private static Style handledStyle() {
        return Style.EMPTY.fg(Theme.ACCENT);
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }

    private static String padLeft(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    private static Block block(String title) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build();
    }

    @Override
    public void renderFKeyHints(List<Span> spans) {
        // with the global F-keys, as the AI key of a screen is an F-key of every screen that has one
        if (ctx.askAiCallback != null && ctx.findSelectedIntegration() != null) {
            hint(spans, "Shift+F8", "explain with AI");
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "↑↓", "route");
        hint(spans, "w", "window " + (seconds() >= 120 ? seconds() / 60 + "m" : seconds() + "s"));
        hint(spans, "Space", pausedAt >= 0 ? "follow" : "pause");
        if (pausedAt >= 0) {
            hint(spans, "←→", "second");
            hint(spans, "Shift+←→", "back and forth");
        }
        hint(spans, "Enter", drill.isOpen() ? (drill.isDetail() ? "list" : "exchange") : "exchanges");
        hint(spans, "Esc", drill.isOpen() || pausedAt >= 0 ? "close" : "back");
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<Timeline.RouteSeries> routes = routes(ctx.findSelectedIntegration());
        if (routes.isEmpty()) {
            return null;
        }
        List<String> items = routes.stream().map(Timeline.RouteSeries::route).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Routes");
    }

    @Override
    public String description() {
        return "A timeline of the integration: errors and route outcomes per second, with chaos rules as a layer";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("timeline");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Timeline");
        result.put("seconds", seconds());
        result.put("endSecond", endSecond());
        result.put("paused", pausedAt >= 0);
        result.put("order", "oldest first, one value per second; -1 is a second the integration did not report");
        JsonArray causes = new JsonArray();
        for (Timeline.CauseSeries c : ctx.timeline.causes(info.pid, endSecond(), seconds())) {
            JsonObject jo = new JsonObject();
            jo.put("label", c.label());
            if (c.rule() != null) {
                jo.put("chaosRule", c.rule());
            }
            jo.put("total", sum(c.values()));
            jo.put("perSecond", array(c.values()));
            causes.add(jo);
        }
        result.put("causes", causes);
        JsonArray rows = new JsonArray();
        for (Timeline.RouteSeries r : routes(info)) {
            JsonObject row = new JsonObject();
            row.put("route", r.route());
            row.put("completed", r.total(r.completed()));
            row.put("recovered", r.total(r.recovered()));
            row.put("handled", r.total(r.handled()));
            row.put("failed", r.total(r.failed()));
            row.put("downSeconds", r.total(r.down()));
            row.put("redeliveryAttempts", r.total(r.attempts()));
            JsonObject perSecond = new JsonObject();
            perSecond.put("completed", array(r.completed()));
            perSecond.put("recovered", array(r.recovered()));
            perSecond.put("handled", array(r.handled()));
            perSecond.put("failed", array(r.failed()));
            perSecond.put("inflight", array(r.inflight()));
            perSecond.put("meanMillis", array(r.mean()));
            perSecond.put("p95Millis", array(r.p95()));
            perSecond.put("maxMillis", array(r.max()));
            perSecond.put("redeliveryAttempts", array(r.attempts()));
            row.put("perSecond", perSecond);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", rows.size());
        if (drill.isOpen()) {
            JsonArray exchanges = new JsonArray();
            for (Timeline.Kept k : keptAtCursor()) {
                JsonObject jo = new JsonObject();
                jo.put("exchangeId", k.exchangeId());
                jo.put("routeId", k.routeId());
                jo.put("outcome", k.outcome());
                jo.put("elapsed", k.elapsed());
                if (k.exception() != null) {
                    jo.put("exception", k.exception());
                }
                if (k.chaosRule() != null) {
                    jo.put("chaosRule", k.chaosRule());
                }
                JsonArray calls = new JsonArray();
                k.calls().forEach(
                        c -> calls.add(new JsonObject(Map.of("endpointUri", c.endpointUri(), "elapsed", c.elapsed()))));
                jo.put("calls", calls);
                exchanges.add(jo);
            }
            result.put("exchangesFrom", drill.from());
            result.put("exchangesTo", drill.to());
            result.put("exchanges", exchanges);
        }
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    private static JsonArray array(long[] values) {
        JsonArray a = new JsonArray();
        for (long v : values) {
            a.add(v);
        }
        return a;
    }
}
