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
import dev.tamboui.layout.Padding;
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

    // the mean time sparkline is at least this many milliseconds high: below it, a time is noise
    static final long MIN_MEAN_SCALE_MILLIS = 10;
    static final int[] WINDOWS = { 60, 120, 300 };
    private static final String BLOCKS = "▁▂▃▄▅▆▇█";
    private static final int CAUSE_ROWS = 4;
    private static final int DETAIL_ROWS = 4;
    private static final int DETAIL_LINES = DETAIL_ROWS + 5;
    private static final int SUFFIX = 30;
    private static final int MAX_LABEL = 18;
    // a tall screen (this many rows to spare) has room around the panels, and its charts and strips grow
    static final int ROOMY_ROWS = 12;
    // the rows the room around takes: the padding of both panels, a blank line in each, and the line between them
    private static final int ROOMY_EXTRA = 9;
    private static final int MAX_CHART_ROWS = 6;
    private static final int MAX_STRIP_ROWS = 2;

    private final LongSupplier clock;
    // the seconds a column has, and the characters a column is wide, as the last render worked them out
    private int per = 1;
    private int width = 1;
    private int window;
    // the second the screen stays at while paused, or -1 to follow now
    private long pausedAt = -1;
    // the column of the cursor while paused
    private int cursor = -1;
    // where the strips of the routes are, for the clicks: the first line, the lines from one route to the next, the
    // lines of a strip
    private int routeTop = -1;
    private int routePitch = 1;
    private int routeStrip = 1;
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
        if (routeTop < 0 || !me.isClick() || lastTableArea == null || !lastTableArea.contains(me.x(), me.y())) {
            return false;
        }
        // a click on a strip of a route selects it, not on the blank line between two routes
        int dy = me.y() - routeTop;
        int i = dy / routePitch;
        if (dy >= 0 && dy % routePitch < routeStrip && i < getRowCount()) {
            tableState.select(i);
            return true;
        }
        return false;
    }

    /**
     * How the screen is laid out: compact, or on a tall screen with room around the panels, the charts this many rows
     * high, the strips of the routes this many rows high and a blank line between them.
     */
    record Room(boolean roomy, int causeRows, int detailRows, int strip, boolean spaced) {
        static final Room COMPACT = new Room(false, CAUSE_ROWS, DETAIL_ROWS, 1, false);
    }

    /**
     * The room the screen has: the rows to spare go first to the room around the panels, then a blank line between the
     * routes, then taller strips, and the rest to the charts (a line kept free at the bottom).
     */
    static Room room(int height, int routes, boolean anyCause) {
        int compact = (anyCause ? CAUSE_ROWS + 1 : 1) + routes + 4 + DETAIL_LINES + 2;
        int spare = height - compact;
        if (spare < ROOMY_ROWS) {
            return Room.COMPACT;
        }
        spare -= ROOMY_EXTRA + 1;
        boolean spaced = routes > 1 && spare >= routes - 1;
        if (spaced) {
            spare -= routes - 1;
        }
        int strip = 1;
        if (routes > 0) {
            strip = Math.min(MAX_STRIP_ROWS, 1 + spare / routes);
            spare -= (strip - 1) * routes;
        }
        int cause = anyCause ? Math.min(MAX_CHART_ROWS - CAUSE_ROWS, spare / 2) : 0;
        int detail = Math.min(MAX_CHART_ROWS - DETAIL_ROWS, spare - cause);
        return new Room(true, CAUSE_ROWS + cause, DETAIL_ROWS + detail, strip, spaced);
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
        Room room = room(area.height(), routes.size(), anyCause);
        // on a tall screen: padding in the panels, a blank line after the cause, after the legend and in the detail,
        // and a line between the panels
        int gap = room.roomy() ? 1 : 0;
        int causeHeight = anyCause ? room.causeRows() + 1 : 1;
        int routeHeight = routes.size() * room.strip() + (room.spaced() ? Math.max(0, routes.size() - 1) : 0);
        // the cause, the legend of the routes, a strip per route and the time axis, in a border
        int detailHeight = DETAIL_LINES + 2 + room.detailRows() - DETAIL_ROWS + 4 * gap;
        int topHeight = Math.max(6,
                Math.min(area.height() - detailHeight - gap, causeHeight + Math.max(1, routeHeight) + 4 + 4 * gap));
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(topHeight), Constraint.length(gap), Constraint.length(detailHeight),
                        Constraint.fill())
                .split(area);
        int inner = Math.max(20, area.width() - 2 - 2 * gap);
        int label = Math.min(MAX_LABEL,
                Math.max(11, routes.stream().mapToInt(r -> r.route().length()).max().orElse(0))) + 4;
        // a second is 1 to 3 characters wide as the screen allows, or a character is several seconds
        int space = Math.max(10, inner - label - SUFFIX - 1);
        int per = space >= seconds() ? 1 : (int) Math.ceil((double) seconds() / space);
        width = per == 1 ? Math.max(1, Math.min(3, space / seconds())) : 1;
        this.per = per;
        int columns = (seconds() + per - 1) / per * width;
        if (cursor >= 0) {
            // the window or the width changed since the cursor was put
            cursor = Math.min(cursor, columnCount() - 1);
        }

        List<Line> lines = new ArrayList<>();
        renderCauses(lines, causes, anyCause, info, columns, room.causeRows(), label);
        blank(lines, room.roomy());
        lines.add(legend(routes, label));
        blank(lines, room.roomy());
        Integer sel = tableState.selected();
        int routeLinesY = lines.size();
        for (int i = 0; i < routes.size(); i++) {
            blank(lines, room.spaced() && i > 0);
            lines.addAll(routeLines(routes.get(i), sel != null && sel == i, columns, per, label, room.strip()));
        }
        if (routes.isEmpty()) {
            lines.add(Line.from(Span.styled(pad("", label) + "no routes yet", Theme.muted())));
        }
        lines.add(axis(columns, per, label));
        Rect top = rows.get(0);
        // the lines of the routes, for the clicks
        routeTop = top.y() + 1 + gap + routeLinesY;
        routePitch = room.strip() + (room.spaced() ? 1 : 0);
        routeStrip = room.strip();
        lastTableArea = new Rect(top.x(), routeTop, top.width(), Math.max(1, routeHeight));
        Padding padding = room.roomy() ? Padding.uniform(1) : Padding.NONE;
        frame.renderWidget(
                Paragraph.builder().text(Text.from(lines)).block(block(title(info), padding, !drill.isOpen())).build(), top);

        if (drill.isOpen()) {
            List<Timeline.Kept> kept = keptAtCursor();
            frame.renderWidget(Paragraph.builder().text(Text.from(drill.lines(kept, detailHeight - 2 - 2 * gap)))
                    .block(block(drill.title(kept, ctx.timeline.done(info.pid, drill.from(), drill.to())), padding, true))
                    .build(),
                    rows.get(2));
            return;
        }
        Timeline.RouteSeries selected = sel != null && sel >= 0 && sel < routes.size() ? routes.get(sel) : null;
        renderDetail(frame, rows.get(2), selected, columns, label, info, room);
    }

    private static void blank(List<Line> lines, boolean when) {
        if (when) {
            lines.add(Line.from(Span.raw("")));
        }
    }

    /** The legend of the strips of the routes. */
    private static Line legend(List<Timeline.RouteSeries> routes, int label) {
        boolean anyOpen = routes.stream().anyMatch(r -> r.total(r.open()) > 0);
        return Line.from(Span.raw(pad("", label)), Span.styled("Routes  ", Theme.label()),
                Span.styled("█", Theme.success()), Span.raw(" completed  "),
                Span.styled("█", Theme.warning()), Span.raw(" recovered  "),
                Span.styled("█", handledStyle()), Span.raw(" handled  "),
                Span.styled("█", Theme.error()), Span.raw(" failed  "),
                Span.styled("░", Theme.error()), Span.raw(" down"),
                Span.raw(anyOpen ? "  " : ""), Span.styled(anyOpen ? "▒" : "", Theme.notice()),
                Span.raw(anyOpen ? " circuit breaker open" : ""),
                Span.raw(routes.stream().anyMatch(r -> Arrays.stream(r.events()).anyMatch(e -> e != null))
                        ? "   ▶ started  ■ stopped  ↻ reloaded" : ""));
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
            int rows, int label) {
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
        lines.addAll(stacked(values, styles, rows, columns, label));
    }

    private static Style causeStyle(int i) {
        Style[] palette = {
                Theme.error(), Style.EMPTY.fg(Theme.ACCENT), Theme.notice(), Theme.info(), Theme.warning(),
                Style.EMPTY.fg(Theme.diagramTo()) };
        return palette[i % palette.length];
    }

    // ---- the routes ----

    /**
     * The strip of a route, {@code strip} rows high (the blocks stacked, so a strip of 2 rows has 16 levels), its name
     * and its summary on the bottom row, where the blocks stand.
     */
    private List<Line> routeLines(Timeline.RouteSeries r, boolean selected, int columns, int per, int label, int strip) {
        String name = r.route().length() > label - 3 ? r.route().substring(0, label - 4) + "…" : r.route();
        long[] completed = cols(r.completed(), false);
        long[] recovered = cols(r.recovered(), false);
        long[] handled = cols(r.handled(), false);
        long[] failed = cols(r.failed(), false);
        long[] down = cols(r.down(), true);
        long[] open = cols(r.open(), true);
        long[] attempts = cols(r.attempts(), false);
        long max = 1;
        for (int c = 0; c < columns; c++) {
            max = Math.max(max, total(completed[c], recovered[c], handled[c], failed[c]));
        }
        String[] events = eventsOf(r.events(), per);
        List<List<Span>> rows = new ArrayList<>();
        for (int row = 0; row < strip; row++) {
            boolean bottom = row == strip - 1;
            List<Span> spans = new ArrayList<>();
            String text = bottom ? (selected ? ">> " : "   ") + name : "";
            spans.add(selected ? Span.styled(pad(text, label), Theme.selectionBg()) : Span.raw(pad(text, label)));
            rows.add(spans);
        }
        for (int c = 0; c < columns; c++) {
            long t = total(completed[c], recovered[c], handled[c], failed[c]);
            String event = c / width < events.length && c % width == 0 ? events[c / width] : null;
            Style style = failed[c] > 0 ? Theme.error()
                    : handled[c] > 0 ? handledStyle()
                    : recovered[c] > 0 || attempts[c] > 0 ? Theme.warning() : Theme.success();
            int level = t == 0 ? 0 : (int) Math.max(1, Math.min(8L * strip, Math.ceil(t * 8.0 * strip / max)));
            for (int row = 0; row < strip; row++) {
                boolean bottom = row == strip - 1;
                Span cell;
                if (event != null) {
                    // what happened to the route then, in place of its exchanges
                    cell = bottom ? eventMark(event) : Span.raw(" ");
                } else if (down[c] > 0) {
                    cell = Span.styled("░", Theme.error());
                } else if (open[c] > 0) {
                    // the circuit breaker was open (half open dim): the calls went to the fallback, or failed fast
                    cell = Span.styled("▒", open[c] >= 2 ? Theme.notice() : Theme.muted());
                } else if (completed[c] < 0) {
                    cell = Span.raw(" ");
                } else if (t == 0) {
                    cell = bottom ? Span.styled("·", Theme.muted()) : Span.raw(" ");
                } else {
                    int here = Math.max(0, Math.min(8, level - (strip - 1 - row) * 8));
                    cell = here == 0 ? Span.raw(" ") : Span.styled(String.valueOf(BLOCKS.charAt(here - 1)), style);
                }
                rows.get(row).add(cell);
            }
        }
        List<Span> last = rows.get(strip - 1);
        last.add(Span.raw("  "));
        last.addAll(summary(r));
        return rows.stream().map(Line::from).toList();
    }

    /** The event of each column of several seconds: the last in the column. */
    static String[] eventsOf(String[] seconds, int per) {
        String[] answer = new String[(seconds.length + per - 1) / per];
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] != null) {
                answer[i / per] = seconds[i];
            }
        }
        return answer;
    }

    /** The mark of what happened to a route: ▶ started, ■ stopped, ↻ reloaded, ✕ removed, ! restart failed. */
    static Span eventMark(String event) {
        return switch (event) {
            case "started" -> Span.styled("▶", Theme.success().bold());
            case "stopped" -> Span.styled("■", Theme.warning().bold());
            case "reloaded" -> Span.styled("↻", Theme.info().bold());
            case "removed" -> Span.styled("✕", Theme.muted());
            default -> Span.styled("!", Theme.error().bold());
        };
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
        if (r.openSeconds() > 0) {
            spans.add(Span.styled("⚡ open " + r.openSeconds() + "s ", Theme.notice()));
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
        long pausedAgo = pausedAt >= 0 ? Math.max(0, nowSecond() - pausedAt) : 0;
        String end = pausedAgo > 0 ? "-" + ChaosTab.shortTime(pausedAgo * 1000) : "now";
        // a label the cursor is on is left out: the time of the cursor is at the right of the axis
        int from = cursor >= 0 ? cursor * width : -1;
        int to = cursor >= 0 ? from + width : -1;
        label(axis, 0, start, from, to);
        label(axis, columns / 2 - middle.length() / 2, middle, from, to);
        label(axis, columns - end.length(), end, from, to);
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

    private static void label(char[] axis, int at, String s, int from, int to) {
        if (at >= to || at + s.length() <= from) {
            put(axis, at, s);
        }
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
            Frame frame, Rect area, Timeline.RouteSeries r, int columns, int label, IntegrationInfo info, Room room) {
        List<Line> lines = new ArrayList<>();
        Padding padding = room.roomy() ? Padding.uniform(1) : Padding.NONE;
        if (r == null) {
            frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.raw(""))))
                    .block(block(" Route ", padding, false)).build(), area);
            return;
        }
        List<long[]> values = List.of(cols(r.completed(), false), cols(r.recovered(), false),
                cols(r.handled(), false), cols(r.failed(), false));
        List<Style> styles = List.of(Theme.success(), Theme.warning(), handledStyle(), Theme.error());
        int rows = Math.max(2, Math.min(room.detailRows(), area.height() - 7 - (room.roomy() ? 4 : 0)));
        lines.addAll(stacked(values, styles, rows, columns, label));
        blank(lines, room.roomy());

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
        // relative to twice the average, so a steady time is a calm line and a slow second stands out; at least
        // MIN_MEAN_SCALE_MILLIS, so a millisecond of jitter on a fast route is not a full bar
        long scale = n > 0 ? meanScale(maxMean, sumMean / n) : 0;
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
        blank(lines, room.roomy());

        List<Span> totals = new ArrayList<>();
        totals.add(Span.raw(pad("", label)));
        addTotal(totals, r.total(r.completed()), "completed", Theme.success());
        addTotal(totals, r.total(r.recovered()), "recovered by redelivery", Theme.warning());
        addTotal(totals, r.total(r.handled()), "handled by the error handler", handledStyle());
        addTotal(totals, r.total(r.failed()), "failed", Theme.error());
        lines.add(Line.from(totals));
        String title = " " + r.route() + " ─ per second ";
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block(title, padding, false)).build(), area);
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
    /** The height of the mean time sparkline: twice the average or the slowest second, at least 10 ms. */
    static long meanScale(long maxMean, long avg) {
        return Math.max(MIN_MEAN_SCALE_MILLIS, Math.max(maxMean, 2 * avg));
    }

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

    /** A panel: the one the keys go to has the focus border (the routes, or the exchanges of a second when open). */
    private Block block(String title, Padding padding, boolean focused) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).borderStyle(ctx.paneBorder(focused))
                .title(title).padding(padding).build();
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
        return "A timeline of the integration: per second the errors by exception (else the WARN and ERROR lines of the log),"
               + " what each route did, when a route started, stopped or reloaded, and the seconds a circuit breaker was"
               + " open, with chaos rules as a layer; paused, the exchanges of a second";
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
            row.put("circuitBreakerOpenSeconds", r.openSeconds());
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
            // 1 a second a chaos rule had the route down; 2 a circuit breaker of the route open, 1 half open
            perSecond.put("down", array(r.down()));
            perSecond.put("circuitBreaker", array(r.open()));
            // what happened to the route: started, stopped, reloaded, removed, restartFailed, and the second
            JsonArray events = new JsonArray();
            long first = endSecond() - seconds() + 1;
            for (int i = 0; i < r.events().length; i++) {
                if (r.events()[i] != null) {
                    JsonObject ev = new JsonObject();
                    ev.put("event", r.events()[i]);
                    ev.put("second", first + i);
                    events.add(ev);
                }
            }
            if (!events.isEmpty()) {
                row.put("events", events);
            }
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
