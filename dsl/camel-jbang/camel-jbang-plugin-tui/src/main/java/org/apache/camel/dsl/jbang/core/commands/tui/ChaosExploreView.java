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
import java.util.StringJoiner;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

/**
 * The exploration on the chaos screen (let there be chaos, CAMEL-26760): an AI tries chaos on the integration for a
 * budget of time, one experiment at a time ({@code camel cmd chaos-explore}, or an agent with the MCP tools). Its
 * budget as a strip of time with the experiments on it, coloured by their verdict, the experiments as a table, and the
 * selected one in detail: its rules, expectations, what the routes did, and what the experiment could not tell.
 */
final class ChaosExploreView {

    // every other experiment in another block, so experiments that run back to back are told apart
    private static final String BAR = "█";
    private static final String BAR_NEXT = "▓";

    private ChaosExploreView() {
    }

    /**
     * Renders the exploration.
     *
     * @return the area of the table of experiments, for the clicks
     */
    static Rect render(Frame frame, Rect area, ChaosInfo.Exploration ex, TableState state, String name) {
        if (ex == null) {
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(Line.from(Span.raw("")),
                            Line.from(Span.styled("  No exploration yet. Let there be chaos: an AI tries faults on the"
                                                  + " integration by itself for a budget of time, and reports",
                                    Theme.muted())),
                            Line.from(Span.styled("  what held and what broke:  camel cmd chaos-explore "
                                                  + (name != null ? name : "<name>") + " --budget=10m",
                                    Theme.muted()))))
                    .block(block(" Explore "))
                    .build(), area);
            return null;
        }
        int tableHeight = Math.max(4, Math.min(ex.experiments().size() + 3, area.height() / 2));
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(6), Constraint.length(tableHeight), Constraint.fill())
                .split(area);
        renderBudget(frame, rows.get(0), ex);
        renderExperiments(frame, rows.get(1), ex, state);
        Integer sel = state.selected();
        ChaosInfo.Experiment selected
                = sel != null && sel >= 0 && sel < ex.experiments().size() ? ex.experiments().get(sel) : null;
        renderDetail(frame, rows.get(2), selected);
        return rows.get(1);
    }

    // ---- the budget, as a strip of time ----

    private static void renderBudget(Frame frame, Rect area, ChaosInfo.Exploration ex) {
        List<Span> first = new ArrayList<>();
        first.add(Span.raw(" "));
        if (ex.running()) {
            first.add(Span.styled("exploring", Theme.warning().bold()));
            first.add(Span.raw(" " + ChaosTab.shortTime(ex.elapsed()) + " of " + ChaosTab.shortTime(ex.budget())));
        } else {
            first.add(Span.styled("finished", Theme.muted().bold()));
            first.add(Span.raw(("deadline".equals(ex.finishedBy()) ? " at its deadline" : "")
                               + (ex.ran() > 0 ? " after " + ChaosTab.shortTime(ex.ran()) : "")
                               + " (budget " + ChaosTab.shortTime(ex.budget()) + ")"));
        }
        first.add(Span.styled("   focus ", Theme.muted()));
        first.add(Span.raw(ex.focus() != null ? ex.focus() : "all routes"));
        first.add(Span.styled("   experiments ", Theme.muted()));
        first.add(Span.raw(String.valueOf(ex.experiments().size())));
        long pass = ex.count("PASS");
        long fail = ex.count("FAIL");
        long inconclusive = ex.count("INCONCLUSIVE");
        long running = ex.experiments().size() - pass - fail - inconclusive;
        first.add(Span.raw("  "));
        first.add(Span.styled(pass + " pass", Theme.success()));
        first.add(Span.raw(" · "));
        first.add(Span.styled(fail + " fail", fail > 0 ? Theme.error() : Style.EMPTY));
        if (inconclusive > 0) {
            first.add(Span.raw(" · "));
            first.add(Span.styled(inconclusive + " inconclusive", Theme.muted()));
        }
        if (running > 0) {
            first.add(Span.raw(" · "));
            first.add(Span.styled(running + " running", Theme.warning()));
        }
        int width = Math.max(10, area.width() - 4);
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(first));
        lines.add(numbers(ex, width));
        lines.add(strip(ex, width));
        lines.add(scale(ex, width));
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .block(block(" Let there be chaos "))
                .build(), area);
    }

    /** The number of each experiment above where it began, when there is room for it. */
    static Line numbers(ChaosInfo.Exploration ex, int width) {
        char[] row = new char[width];
        Arrays.fill(row, ' ');
        for (ChaosInfo.Experiment e : ex.experiments()) {
            String n = String.valueOf(e.number());
            int col = column(ex, e.at(), width);
            boolean free = col + n.length() <= width;
            for (int i = 0; free && i < n.length(); i++) {
                free = row[col + i] == ' ' && (col + i == 0 || row[col + i - 1] == ' ' || i > 0);
            }
            if (free) {
                n.getChars(0, n.length(), row, col);
            }
        }
        return Line.from(Span.raw(" "), Span.styled(new String(row), Theme.muted()));
    }

    /**
     * The budget as a strip, a column per slice of time: an experiment as a bar in the colour of its verdict, the time
     * gone by as a line, the time left as dots.
     */
    static Line strip(ChaosInfo.Exploration ex, int width) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" "));
        long now = ex.elapsed();
        for (int c = 0; c < width; c++) {
            long from = ex.budget() * c / width;
            long to = ex.budget() * (c + 1) / width;
            ChaosInfo.Experiment hit = null;
            for (ChaosInfo.Experiment e : ex.experiments()) {
                if (e.at() < to && e.at() + Math.max(e.elapsed(), 1) > from) {
                    hit = e;
                }
            }
            if (hit != null) {
                spans.add(Span.styled(hit.number() % 2 == 1 ? BAR : BAR_NEXT, verdictStyle(hit)));
            } else if (from < now) {
                spans.add(Span.styled("─", Theme.muted()));
            } else {
                spans.add(Span.styled("·", Theme.muted()));
            }
        }
        return Line.from(spans);
    }

    private static Line scale(ChaosInfo.Exploration ex, int width) {
        String end = ChaosTab.shortTime(ex.budget());
        StringBuilder sb = new StringBuilder("0");
        while (sb.length() < width - end.length()) {
            sb.append(' ');
        }
        sb.append(end);
        return Line.from(Span.raw(" "), Span.styled(sb.toString(), Theme.muted()));
    }

    private static int column(ChaosInfo.Exploration ex, long at, int width) {
        if (ex.budget() <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(width - 1, at * width / ex.budget()));
    }

    // ---- the experiments ----

    private static void renderExperiments(Frame frame, Rect area, ChaosInfo.Exploration ex, TableState state) {
        if (ex.experiments().isEmpty()) {
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(" No experiment yet", Theme.muted()))))
                    .block(block(" Experiments [0] "))
                    .build(), area);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (ChaosInfo.Experiment e : ex.experiments()) {
            rows.add(Row.from(
                    Cell.from(Span.styled(String.valueOf(e.number()), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(clock(e.at()), Theme.muted())),
                    Cell.from(Span.styled(verdict(e), verdictStyle(e).bold())),
                    Cell.from(e.hypothesis() != null ? e.hypothesis() : String.join("; ", e.rules())),
                    Cell.from(Span.styled(evidence(e), Theme.muted()))));
        }
        Style bold = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("#", bold)),
                        Cell.from(Span.styled("AT", bold)),
                        Cell.from(Span.styled("VERDICT", bold)),
                        Cell.from(Span.styled("HYPOTHESIS", bold)),
                        Cell.from(Span.styled("EVIDENCE", bold))))
                .widths(
                        Constraint.length(3),
                        Constraint.length(6),
                        Constraint.length(13),
                        Constraint.fill(3),
                        Constraint.fill(2))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(block(" Experiments [" + ex.experiments().size() + "] "))
                .build();
        frame.renderStatefulWidget(table, area, state);
    }

    /** The verdict as the table shows it: an open experiment is running. */
    static String verdict(ChaosInfo.Experiment e) {
        if (e.open()) {
            return "running";
        }
        return e.verdict() != null ? e.verdict() : "-";
    }

    /** The evidence in a few words: what decided it, or what it could not tell. */
    static String evidence(ChaosInfo.Experiment e) {
        StringJoiner sj = new StringJoiner("; ");
        // what it could not tell first, as the column cuts the rest
        if (e.reason() != null && e.reason().startsWith("its faults never fired")) {
            sj.add("the faults never fired");
        }
        if (e.lostAtDlc() > 0) {
            sj.add(e.lostAtDlc() + " lost at the dead letter channel");
        }
        for (String x : e.expectations()) {
            // orders:failed<=0: unmet, 5 failed -> 5 failed in orders
            int colon = x.indexOf(": ");
            int comma = x.indexOf(", ", colon + 2);
            String route = x.contains(":") ? x.substring(0, x.indexOf(':')) : "";
            if (comma > 0) {
                String value = x.substring(comma + 2);
                sj.add(value + (route.isEmpty() || value.contains(route) ? "" : " in " + route));
            }
        }
        e.alsoFailed().forEach((route, n) -> sj.add("also " + n + " failed in " + route));
        return sj.toString();
    }

    // ---- the selected experiment ----

    private static void renderDetail(Frame frame, Rect area, ChaosInfo.Experiment e) {
        if (e == null) {
            frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.raw(""))))
                    .block(block(" Experiment ")).build(), area);
            return;
        }
        Style label = Theme.muted();
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" " + verdict(e), verdictStyle(e).bold()),
                Span.raw("  " + (e.hypothesis() != null ? e.hypothesis() : "")),
                Span.styled("   at " + clock(e.at()) + (e.open() ? ", running for " : ", ran ")
                            + ChaosTab.shortTime(e.elapsed()) + ", " + e.faults()
                            + (e.faults() == 1 ? " fault" : " faults"),
                        label)));
        if (e.reason() != null) {
            lines.add(Line.from(Span.styled(" " + e.reason(), Theme.warning())));
        }
        lines.add(Line.from(Span.raw("")));
        for (String r : e.rules()) {
            lines.add(Line.from(Span.styled(" rule      ", label), Span.raw(r)));
        }
        for (String x : e.expectations()) {
            boolean unmet = x.contains(": unmet,");
            lines.add(Line.from(Span.styled(" expect    ", label),
                    Span.styled(x, unmet ? Theme.error() : x.contains(": met,") ? Theme.success() : Theme.warning())));
        }
        if (e.lostAtDlc() > 0) {
            lines.add(Line.from(Span.styled(" lost      ", label),
                    Span.styled(e.lostAtDlc() + (e.lostAtDlc() == 1 ? " message" : " messages")
                                + " at the dead letter channel, handled there without being delivered",
                            Theme.error())));
        }
        if (!e.alsoFailed().isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            e.alsoFailed().forEach((route, n) -> sj.add(route + " " + n));
            lines.add(Line.from(Span.styled(" also      ", label),
                    Span.styled("failed, in no expectation: " + sj, Theme.warning())));
        }
        for (String r : e.routes()) {
            lines.add(Line.from(Span.styled(" route     ", label), Span.raw(r)));
        }
        if (e.note() != null) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" note      ", label), Span.raw(e.note())));
        }
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .block(block(" Experiment " + e.number() + " "))
                .build(), area);
    }

    // ---- helpers ----

    private static Style verdictStyle(ChaosInfo.Experiment e) {
        if (e.open()) {
            return Theme.warning();
        }
        return switch (e.verdict() != null ? e.verdict() : "") {
            case "PASS" -> Theme.success();
            case "FAIL" -> Theme.error();
            default -> Theme.muted();
        };
    }

    /** A time after the start of the exploration, as a clock: 2:05. */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private static Block block(String title) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build();
    }
}
