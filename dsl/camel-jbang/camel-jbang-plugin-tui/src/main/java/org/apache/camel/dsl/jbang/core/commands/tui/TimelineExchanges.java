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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * The exchanges of a second of the timeline (Enter on the Timeline screen, CAMEL-26691): the ones the integration kept
 * of it (the failed, handled and recovered ones, and the slowest), and the selected one in detail, with the calls it
 * made and its exception. It takes the place of the Activity tab, which listed the last exchanges without their time.
 */
final class TimelineExchanges {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private boolean open;
    private boolean detail;
    private int selected;
    private long from;
    private long to;

    boolean isOpen() {
        return open;
    }

    boolean isDetail() {
        return detail;
    }

    long from() {
        return from;
    }

    long to() {
        return to;
    }

    void open(long from, long to) {
        this.open = true;
        this.detail = false;
        this.selected = 0;
        this.from = from;
        this.to = to;
    }

    /** Esc: the detail closes first, then the list. */
    void back() {
        if (detail) {
            detail = false;
        } else {
            open = false;
        }
    }

    void close() {
        open = false;
        detail = false;
    }

    void toggleDetail() {
        detail = !detail;
    }

    void move(int delta, int count) {
        selected = Math.max(0, Math.min(Math.max(0, count - 1), selected + delta));
    }

    /**
     * A click on a line of the list selects the exchange on it; true when it was on one.
     *
     * @param line  the line clicked, from the top of the list
     * @param count the exchanges kept
     * @param rows  the lines the list has
     */
    boolean click(int line, int count, int rows) {
        if (!open || detail || line < 0 || line >= rows) {
            return false;
        }
        int i = first(count, rows) + line;
        if (i >= count) {
            return false;
        }
        selected = i;
        return true;
    }

    /** The first exchange the list shows: the list scrolls to keep the selected one in view. */
    private int first(int count, int rows) {
        int sel = Math.min(selected, count - 1);
        return Math.max(0, Math.min(sel - rows + 1, count - rows));
    }

    Timeline.Kept selected(List<Timeline.Kept> kept) {
        return kept.isEmpty() ? null : kept.get(Math.min(selected, kept.size() - 1));
    }

    /** The title of the panel: the seconds, how many exchanges were done then, and how many were kept. */
    String title(List<Timeline.Kept> kept, long done) {
        String when
                = TIME.format(Instant.ofEpochSecond(from)) + (to > from ? "–" + TIME.format(Instant.ofEpochSecond(to)) : "");
        return " Exchanges at " + when + " ─ " + done + " done, " + kept.size()
               + " kept (the failed, handled and recovered, and the slowest) ";
    }

    /** The lines of the panel, as many as fit in the given rows. */
    List<Line> lines(List<Timeline.Kept> kept, int rows) {
        List<Line> lines = new ArrayList<>();
        if (kept.isEmpty()) {
            lines.add(Line.from(Span.styled("   No exchange kept of these seconds", Theme.muted())));
            return lines;
        }
        if (detail) {
            return detailLines(selected(kept), rows);
        }
        int sel = Math.min(selected, kept.size() - 1);
        for (int i = first(kept.size(), rows); i < kept.size() && lines.size() < rows; i++) {
            lines.add(row(kept.get(i), i == sel));
        }
        return lines;
    }

    private static Line row(Timeline.Kept k, boolean selected) {
        List<Span> spans = new ArrayList<>();
        String id = ErrorsTab.shortExchangeId(k.exchangeId());
        spans.add(selected ? Span.styled(">> " + pad(id, 14), Theme.selectionBg()) : Span.raw("   " + pad(id, 14)));
        spans.add(Span.raw(pad(k.routeId() != null ? k.routeId() : "", 18)));
        spans.add(Span.styled(pad(k.outcome(), 10), style(k)));
        spans.add(Span.raw(pad(ChaosTab.shortTime(k.elapsed()), 8)));
        Timeline.Call slowest = null;
        for (Timeline.Call c : k.calls()) {
            if (slowest == null || c.elapsed() > slowest.elapsed()) {
                slowest = c;
            }
        }
        if (slowest != null) {
            spans.add(Span.styled(k.calls().size() + (k.calls().size() == 1 ? " call, " : " calls, slowest ")
                                  + ChaosTab.shortTime(slowest.elapsed()) + " " + uri(slowest.endpointUri()),
                    Theme.muted()));
        }
        if (k.chaosRule() != null) {
            spans.add(Span.raw("  " + TuiIcons.TAB_CHAOS + " " + k.chaosRule()));
        }
        return Line.from(spans);
    }

    private static List<Line> detailLines(Timeline.Kept k, int rows) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled("   " + k.exchangeId(), Theme.label()),
                Span.raw("  " + (k.routeId() != null ? k.routeId() : "")),
                Span.raw(k.fromEndpointUri() != null ? " from " + k.fromEndpointUri() : "")));
        lines.add(Line.from(Span.raw("   "), Span.styled(k.outcome(), style(k)),
                Span.raw(" in " + ChaosTab.shortTime(k.elapsed()) + " at " + TIME.format(Instant.ofEpochMilli(k.timestamp()))),
                Span.raw(k.chaosRule() != null ? "  " + TuiIcons.TAB_CHAOS + " faulted by chaos rule " + k.chaosRule() : "")));
        if (k.exception() != null && !k.failed() && !k.handled()) {
            // a recovered exchange: the failure it was redelivered for
            lines.add(Line.from(Span.styled("   recovered after: ", Theme.muted()),
                    Span.styled(k.exception(), Theme.warning())));
        } else if (k.exception() != null) {
            lines.add(Line.from(Span.styled("   " + k.exception(), Theme.error())));
        }
        if (k.calls().isEmpty()) {
            lines.add(Line.from(Span.styled("   no calls to remote endpoints", Theme.muted())));
        }
        for (Timeline.Call c : k.calls()) {
            if (lines.size() >= rows) {
                break;
            }
            lines.add(Line.from(Span.raw("   " + pad(ChaosTab.shortTime(c.elapsed()), 8)), Span.raw(uri(c.endpointUri()))));
        }
        return lines;
    }

    /**
     * The calls of the route's exchanges kept in the window, by endpoint: the slowest time and how many, the slowest
     * endpoint first.
     */
    static List<Span> slowestCalls(List<Timeline.Kept> kept, String routeId, int max) {
        Map<String, long[]> byEndpoint = new LinkedHashMap<>();
        for (Timeline.Kept k : kept) {
            if (routeId.equals(k.routeId())) {
                for (Timeline.Call c : k.calls()) {
                    long[] v = byEndpoint.computeIfAbsent(c.endpointUri(), e -> new long[2]);
                    v[0] = Math.max(v[0], c.elapsed());
                    v[1]++;
                }
            }
        }
        List<Span> spans = new ArrayList<>();
        byEndpoint.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0])).limit(max)
                .forEach(e -> {
                    if (!spans.isEmpty()) {
                        spans.add(Span.styled(" · ", Theme.muted()));
                    }
                    spans.add(Span.raw(uri(e.getKey()) + " "));
                    spans.add(Span.styled("max " + ChaosTab.shortTime(e.getValue()[0]), Theme.info()));
                });
        return spans;
    }

    /** An endpoint URI as people write it: decoded (%24%7B is ${), as the endpoint keeps it encoded. */
    static String uri(String uri) {
        if (uri == null || uri.indexOf('%') < 0) {
            return uri;
        }
        try {
            return URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return uri;
        }
    }

    private static Style style(Timeline.Kept k) {
        return k.failed() ? Theme.error()
                : k.handled() ? Style.EMPTY.fg(Theme.ACCENT) : k.redelivered() ? Theme.warning() : Theme.success();
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s.substring(0, width - 1) + " " : s + " ".repeat(width - s.length());
    }
}
