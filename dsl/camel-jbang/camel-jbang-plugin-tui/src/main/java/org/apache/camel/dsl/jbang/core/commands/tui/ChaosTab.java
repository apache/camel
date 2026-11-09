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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

/**
 * The chaos screen (camel-chaos): whether chaos is on, the verdict of the report, the current rules with their live
 * state (the observed rate, when a rule last fired, its faults per second over the last minute as a sparkline, and the
 * effect of its faults on the exchanges), the selected rule in detail with its recent faults, and the counts per route
 * of the report. Rules are added, changed and removed from here too ({@link ChaosControl}), as with
 * {@code camel cmd chaos-*}.
 */
class ChaosTab extends AbstractTableTab {

    private static final String SPARK = "▁▂▃▄▅▆▇█";
    // the width of the sparkline in the rules table: the 60 seconds, 4 per column
    private static final int TABLE_SPARK = 15;
    private static final int ROUTES_WIDTH = 46;
    private static final int MAX_EFFECT_WIDTH = 30;

    private final ChaosControl control;

    ChaosTab(MonitorContext ctx) {
        this(ctx, new ChaosControl(ctx));
    }

    ChaosTab(MonitorContext ctx, ChaosControl control) {
        super(ctx);
        this.control = control;
    }

    ChaosControl control() {
        return control;
    }

    /** Opens the known rules for a target ({@code !} on another screen). */
    void openAt(ChaosHere.Target target) {
        control.openAt(ctx.findSelectedIntegration(), target);
    }

    /** Opens the rule typed as one line, such as a rule that makes a real error again. */
    void openRule(String rule) {
        control.openRule(ctx.findSelectedIntegration(), rule);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        ChaosInfo.Rule rule = info != null && info.chaos != null ? selectedRule(info.chaos) : null;
        if (ke.isKey(KeyCode.F8) && ke.hasShift() && control.step() == ChaosControl.Step.NONE) {
            // explain with AI what the selected rule did (CAMEL-26702)
            if (rule != null && ctx.askAiCallback != null) {
                ctx.askAiCallback.ask(ChaosAiPrompt.of(info, rule));
            }
            return true;
        }
        if (ke.isChar('t') && rule != null && rule.id != null && ctx.askAiCallback != null
                && control.step() == ChaosControl.Step.NONE) {
            // a test of the rule, so the finding runs again with the same faults (CAMEL-26725)
            ctx.askAiCallback.ask(ChaosAiPrompt.ofTest(info, rule));
            return true;
        }
        return control.handleKey(ke, info, rule);
    }

    @Override
    public void renderFKeyHints(List<Span> spans) {
        // with the global F-keys, as the AI key of a screen is an F-key of every screen that has one
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (ctx.askAiCallback != null && control.step() == ChaosControl.Step.NONE && info != null
                && info.chaos != null && selectedRule(info.chaos) != null) {
            hint(spans, "Shift+F8", "explain with AI");
        }
    }

    @Override
    public boolean handleEscape() {
        return control.escape();
    }

    @Override
    public boolean isTextInputActive() {
        return control.isTextInputActive();
    }

    @Override
    public boolean isConfirming() {
        return control.isConfirming();
    }

    @Override
    public boolean isConfirmCancelledByEsc() {
        // Esc closes the confirm of a chaos action without doing it
        return true;
    }

    @Override
    public boolean handlePaste(String text) {
        return control.handlePaste(text);
    }

    @Override
    public void navigateUp() {
        if (!control.isOpen()) {
            super.navigateUp();
        }
    }

    @Override
    public void navigateDown() {
        if (!control.isOpen()) {
            super.navigateDown();
        }
    }

    @Override
    protected boolean selectsFirstRow() {
        return true;
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null && info.chaos != null ? info.chaos.rules.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        ChaosInfo chaos = info.chaos;
        if (chaos == null || !chaos.enabled) {
            String why = chaos == null
                    ? "camel-chaos is not in this integration: run it with camel run --chaos"
                    : chaos.reason != null ? chaos.reason : "Chaos is off";
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(Line.from(Span.raw("")), Line.from(Span.styled("  " + why, Theme.muted()))))
                    .block(block(" Chaos "))
                    .build(), area);
            return;
        }
        int tableHeight = Math.max(5, Math.min(chaos.rules.size() + 3, area.height() / 3));
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(4), Constraint.length(tableHeight), Constraint.fill())
                .split(area);
        renderVerdict(frame, rows.get(0), chaos);
        renderRules(frame, rows.get(1), chaos, info.name);
        List<Rect> bottom = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(ROUTES_WIDTH))
                .split(rows.get(2));
        renderDetail(frame, bottom.get(0), info, selectedRule(chaos));
        renderRoutes(frame, bottom.get(1), chaos);
        control.render(frame, area);
    }

    private ChaosInfo.Rule selectedRule(ChaosInfo chaos) {
        Integer sel = tableState.selected();
        return sel != null && sel >= 0 && sel < chaos.rules.size() ? chaos.rules.get(sel) : null;
    }

    // ---- the verdict ----

    private static void renderVerdict(Frame frame, Rect area, ChaosInfo chaos) {
        boolean fail = "FAIL".equals(chaos.verdict);
        List<Span> first = new ArrayList<>();
        first.add(Span.raw(" "));
        first.add(Span.styled(chaos.verdict != null ? chaos.verdict : "-",
                (fail ? Theme.error() : Theme.success()).bold()));
        first.add(Span.raw("   "));
        first.add(Span.styled("lost ", Theme.muted()));
        first.add(Span.styled(String.valueOf(chaos.lost), chaos.lost > 0 ? Theme.error() : Style.EMPTY));
        long met = 0;
        long unmet = 0;
        long pending = 0;
        long waiting = 0;
        for (ChaosInfo.Rule r : chaos.rules) {
            if ("met".equals(r.expectOutcome)) {
                met++;
            } else if ("unmet".equals(r.expectOutcome)) {
                unmet++;
            } else if ("pending".equals(r.expectOutcome)) {
                pending++;
            }
            if ("waiting".equals(r.state)) {
                waiting++;
            }
        }
        if (met + unmet + pending > 0) {
            first.add(Span.styled("   expectations ", Theme.muted()));
            first.add(Span.styled(met + " met", Theme.success()));
            first.add(Span.raw(" · "));
            first.add(Span.styled(unmet + " unmet", unmet > 0 ? Theme.error() : Style.EMPTY));
            first.add(Span.raw(" · "));
            first.add(Span.styled(pending + " pending", pending > 0 ? Theme.warning() : Style.EMPTY));
        }
        first.add(Span.styled("   faults ", Theme.muted()));
        first.add(Span.raw(String.valueOf(chaos.faultsLastMinute())));
        first.add(Span.styled(" in the last minute", Theme.muted()));
        first.add(Span.styled("   rules ", Theme.muted()));
        first.add(Span.raw((chaos.activeRules() - waiting) + " active"
                           + (waiting > 0 ? " · " + waiting + " waiting" : "")));
        // the seed is in the title; the reason of a failed verdict, or what the verdict waits for
        Line second;
        if (!chaos.reasons.isEmpty()) {
            second = Line.from(Span.styled(" " + TuiIcons.CROSS + " " + chaos.reasons.get(0)
                                           + (chaos.reasons.size() > 1 ? " (+" + (chaos.reasons.size() - 1) + " more)" : ""),
                    Theme.error()));
        } else if (!chaos.isFinal) {
            second = Line.from(Span.styled(" not final: an expectation is pending", Theme.muted()));
        } else {
            second = Line.from(Span.raw(""));
        }
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(Line.from(first), second))
                .block(block(" Chaos ─ on ─ seed " + chaos.seed + " "))
                .build(), area);
    }

    // ---- the rules ----

    private void renderRules(Frame frame, Rect area, ChaosInfo chaos, String name) {
        List<Row> rows = new ArrayList<>();
        for (ChaosInfo.Rule r : chaos.rules) {
            rows.add(Row.from(
                    Cell.from(Span.styled(r.id + (r.version > 1 ? " v" + r.version : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(r.state != null ? r.state : "", stateStyle(r.state))),
                    Cell.from(target(r)),
                    Cell.from(r.faultShort()),
                    Cell.from(rate(r)),
                    rightCell(String.valueOf(r.fired), 5),
                    rightCell(r.lastFiredAgo >= 0 ? shortTime(r.lastFiredAgo) : "-", 6),
                    Cell.from(Span.styled(sparkline(r.faultsPerSecond, TABLE_SPARK), Theme.warning())),
                    Cell.from(Line.from(effect(r))),
                    Cell.from(Span.styled(note(r), Theme.muted()))));
        }
        if (rows.isEmpty()) {
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(
                            " No chaos rules: add one with camel cmd chaos-add " + (name != null ? name : "<name>")
                                                          + " --rule='...'",
                            Theme.muted()))))
                    .block(block(" Rules [0] "))
                    .build(), area);
            return;
        }
        Style bold = Style.EMPTY.bold();
        // EFFECT is as wide as its counts need ("62↩ 1…" was cut), TARGET and FAULT share the rest
        int effectWidth = CharWidth.of("EFFECT");
        for (ChaosInfo.Rule r : chaos.rules) {
            effectWidth = Math.max(effectWidth, CharWidth.of(Line.from(effect(r)).rawContent()));
        }
        // one more, so the counts do not run into the note
        effectWidth = Math.min(effectWidth + 1, MAX_EFFECT_WIDTH);
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("ID", bold)),
                        Cell.from(Span.styled("STATE", bold)),
                        Cell.from(Span.styled("TARGET", bold)),
                        Cell.from(Span.styled("FAULT", bold)),
                        Cell.from(Span.styled("RATE", bold)),
                        rightCell("FIRED", 5, bold),
                        rightCell("LAST", 6, bold),
                        Cell.from(Span.styled("FAULTS/S", bold)),
                        Cell.from(Span.styled("EFFECT", bold)),
                        Cell.from(Span.styled("NOTE", bold))))
                .widths(
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.fill(7),
                        Constraint.fill(5),
                        Constraint.length(16),
                        Constraint.length(5),
                        Constraint.length(6),
                        Constraint.length(TABLE_SPARK),
                        Constraint.length(effectWidth),
                        Constraint.fill(4))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(block(" Rules [" + chaos.rules.size() + "] "))
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, table, chaos.rules.size());
    }

    /**
     * The target of a rule for the table: the URI pattern of an {@code endpoint=} target (the most common one), else
     * the kind and the pattern, such as {@code route poller}, and the route it is narrowed to
     * ({@code http:* in orders}).
     */
    static String target(ChaosInfo.Rule r) {
        String t = r.target().replace(" in=", " in ");
        if (t.startsWith("endpoint=")) {
            return t.substring(9);
        }
        int eq = t.indexOf('=');
        return eq > 0 ? t.substring(0, eq) + " " + t.substring(eq + 1) : t;
    }

    /**
     * The observed rate next to the rate of the rule, such as {@code 24% of 25%}, {@code 1 in 4} or {@code 5s in 30s}.
     */
    static String rate(ChaosInfo.Rule r) {
        if (r.lifecycleAction != null) {
            return r.lifecycleRepeat > 1 ? "round " + r.lifecycleRound + " of " + r.lifecycleRepeat : "";
        }
        String option = r.option("rate");
        String target;
        if (option != null && option.startsWith("burst:")) {
            target = option.substring(6).replace("/", " in ");
        } else if (option != null && option.startsWith("every:")) {
            target = "1 in " + option.substring(6);
        } else if (r.rate >= 0 && r.rate < 1) {
            target = percent(r.rate);
        } else {
            return "all";
        }
        return r.observedRate >= 0 ? percent(r.observedRate) + " of " + target : target;
    }

    /** The effect of the faults of a rule, compact: completed, recovered, handled, failed and in flight. */
    static List<Span> effect(ChaosInfo.Rule r) {
        List<Span> spans = new ArrayList<>();
        if (r.faulted <= 0) {
            // a poll rule faults no exchange: its effect is on the polls (CAMEL-26699)
            if (r.firedAtPoll() > 0) {
                spans.add(Span.styled(r.firedAtPoll() + (r.firedAtPoll() == 1 ? " poll " : " polls ") + pollWord(r),
                        r.faultShort().startsWith("error") ? Theme.error() : Theme.warning()));
            }
            return spans;
        }
        addCount(spans, r.completed, "✓", Theme.success());
        addCount(spans, r.recovered, "↩", Theme.warning());
        addCount(spans, r.handled, "⇩", Theme.error());
        addCount(spans, r.failed, TuiIcons.CROSS, Theme.error().bold());
        // not … , which reads as a cut text
        addCount(spans, r.inFlight, "◷", Theme.muted());
        return spans;
    }

    /** What a poll rule did to the polls: an error fails them, a drop skips them. */
    private static String pollWord(ChaosInfo.Rule r) {
        return r.faultShort().startsWith("error") ? "failed" : "skipped";
    }

    private static void addCount(List<Span> spans, long count, String mark, Style style) {
        if (count > 0) {
            if (!spans.isEmpty()) {
                spans.add(Span.raw(" "));
            }
            spans.add(Span.styled(count + mark, style));
        }
    }

    /** What a person should know about a rule besides its counts: when it starts or ends, its bursts, its route. */
    static String note(ChaosInfo.Rule r) {
        if (r.lifecycleState != null) {
            // the route is the target of the rule; between the rounds of repeat= the route is back
            if (r.lifecycleRound > 0 && r.lifecycleActsIn >= 0) {
                return "next round in " + countdown(r.lifecycleActsIn);
            }
            StringBuilder sb = new StringBuilder(r.lifecycleState);
            if (r.lifecycleActsIn >= 0) {
                sb.append(", acts in ").append(countdown(r.lifecycleActsIn));
            } else if (r.lifecycleBackIn >= 0) {
                sb.append(", back in ").append(countdown(r.lifecycleBackIn));
            }
            if (r.lifecycleReason != null) {
                sb.append(": ").append(r.lifecycleReason);
            }
            return sb.toString();
        }
        List<String> parts = new ArrayList<>();
        if (r.startsIn >= 0) {
            parts.add("starts in " + countdown(r.startsIn));
        }
        if (r.burstEndsIn >= 0) {
            parts.add("burst, " + countdown(r.burstEndsIn) + " left");
        } else if (r.nextBurstIn >= 0) {
            parts.add("next burst in " + countdown(r.nextBurstIn));
        }
        if (r.nextFireIn > 0) {
            // a count of matches (rate=every:N), not a time
            parts.add("fires again in " + r.nextFireIn + (r.nextFireIn == 1 ? " match" : " matches"));
        }
        if (r.endsIn >= 0) {
            parts.add("ends in " + countdown(r.endsIn));
        }
        if (r.skipped > 0) {
            parts.add("skipped " + r.skipped);
        }
        if (r.lostAtDlq > 0) {
            parts.add(r.lostAtDlq + " lost at the DLQ");
        }
        return String.join(", ", parts);
    }

    // ---- the selected rule ----

    private static void renderDetail(Frame frame, Rect area, IntegrationInfo info, ChaosInfo.Rule r) {
        ChaosInfo chaos = info.chaos;
        if (r == null) {
            frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.raw(""))))
                    .block(block(" Rule ")).build(), area);
            return;
        }
        Style label = Theme.muted();
        List<Line> lines = new ArrayList<>();
        int sparkWidth = Math.max(10, Math.min(60, area.width() - 20));
        lines.add(Line.from(Span.styled(" Faults/s  ", label),
                Span.styled(sparkline(r.faultsPerSecond, sparkWidth), Theme.warning()),
                Span.styled("  " + r.faultsLastMinute + " in 60s", label)));
        List<Span> rate = new ArrayList<>();
        rate.add(Span.styled(" Rate      ", label));
        rate.add(Span.raw(rate(r)));
        if (r.lastFiredAgo >= 0) {
            rate.add(Span.styled("   last fired ", label));
            rate.add(Span.raw(shortTime(r.lastFiredAgo) + " ago"));
        }
        rate.add(Span.styled("   matches ", label));
        rate.add(Span.raw(String.valueOf(r.matches)));
        String note = note(r);
        if (!note.isEmpty()) {
            rate.add(Span.styled("   " + note, label));
        }
        lines.add(Line.from(rate));
        if (r.faulted > 0) {
            lines.add(effectBar(r, Math.max(10, Math.min(40, area.width() - 40))));
            lines.add(Line.from(effectWords(r)));
        } else if (r.firedAtPoll() > 0) {
            lines.add(Line.from(Span.styled(" Effect    ", label), Span.styled(
                    r.firedAtPoll() + (r.firedAtPoll() == 1 ? " poll " : " polls ") + pollWord(r)
                                                                               + ": the consumer polls again on its schedule",
                    Theme.warning())));
        }
        addImpact(lines, info, chaos, r);
        if (r.expectRoute != null) {
            String spec = r.expectSpec != null ? r.expectSpec
                    : "completed".equals(r.expectKind) && ">=".equals(r.expectOp)
                            ? r.expectRoute + ":" + r.expectCount
                    : r.expectRoute + ":" + r.expectKind + r.expectOp + r.expectCount;
            Style outcome = switch (r.expectOutcome != null ? r.expectOutcome : "") {
                case "met" -> Theme.success().bold();
                case "unmet" -> Theme.error().bold();
                case "pending" -> Theme.warning();
                default -> Theme.muted();
            };
            List<Span> expect = new ArrayList<>();
            expect.add(Span.styled(" Expect    ", label));
            expect.add(Span.raw(spec + "  "));
            expect.add(Span.styled(r.expectOutcome != null ? r.expectOutcome.toUpperCase(Locale.ENGLISH) : "-", outcome));
            // a time (meanTime, maxTime) is in milliseconds
            boolean time = "meanTime".equals(r.expectKind) || "maxTime".equals(r.expectKind);
            expect.add(Span.raw("  " + (time ? shortTime(r.expectValue) : String.valueOf(r.expectValue)) + " "
                                + r.expectKind));
            if (r.expectTimeLeft >= 0 && "pending".equals(r.expectOutcome)) {
                expect.add(Span.styled("  " + shortTime(r.expectTimeLeft) + " left", label));
            }
            lines.add(Line.from(expect));
        }
        for (Map.Entry<String, Long> skip : r.skippedReasons.entrySet()) {
            lines.add(Line.from(Span.styled(" Skipped   ", label),
                    Span.styled(skip.getValue() + ": " + skip.getKey(), Theme.warning())));
        }
        boolean first = true;
        for (Map.Entry<ChaosInfo.RecentFault, Integer> e : recentOf(chaos, r).entrySet()) {
            ChaosInfo.RecentFault f = e.getKey();
            lines.add(Line.from(
                    Span.styled(first ? " Recent    " : "           ", label),
                    Span.styled(String.format("%-6s", f.ago() >= 0 ? shortTime(f.ago()) : ""), label),
                    Span.styled("🎲 ", Theme.warning()),
                    Span.raw(f.fault() != null ? f.fault() : ""),
                    Span.styled(where(f), label),
                    Span.styled(e.getValue() > 1 ? "  ×" + e.getValue() : "", Theme.warning())));
            first = false;
        }
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .block(block(" " + r.id + " ─ " + r.text + " "))
                .build(), area);
    }

    /**
     * The recent faults of a rule, the latest first, with how many times each exchange was faulted: a redelivery is
     * faulted again, and is shown once with its count ({@code ×3}), not once per attempt.
     */
    static Map<ChaosInfo.RecentFault, Integer> recentOf(ChaosInfo chaos, ChaosInfo.Rule r) {
        Map<ChaosInfo.RecentFault, Integer> answer = new LinkedHashMap<>();
        Map<String, ChaosInfo.RecentFault> byExchange = new LinkedHashMap<>();
        for (ChaosInfo.RecentFault f : chaos.recentFaults) {
            if (r.id == null || !r.id.equals(f.rule())) {
                continue;
            }
            ChaosInfo.RecentFault seen = f.exchangeId() != null ? byExchange.get(f.exchangeId()) : null;
            if (seen != null) {
                answer.merge(seen, 1, Integer::sum);
            } else {
                if (f.exchangeId() != null) {
                    byExchange.put(f.exchangeId(), f);
                }
                answer.put(f, 1);
            }
        }
        return answer;
    }

    /**
     * The routes the faults of the rule were in, with their times and throughput: what a delay costs, which the effect
     * does not say (the exchanges complete), is in the times of the route.
     */
    private static void addImpact(List<Line> lines, IntegrationInfo info, ChaosInfo chaos, ChaosInfo.Rule r) {
        Set<String> routes = new LinkedHashSet<>();
        for (ChaosInfo.RecentFault f : chaos.recentFaults) {
            if (r.id != null && r.id.equals(f.rule()) && f.route() != null) {
                routes.add(f.route());
            }
        }
        boolean first = true;
        for (String routeId : routes) {
            RouteInfo route = info.routes.stream().filter(ri -> routeId.equals(ri.routeId)).findFirst().orElse(null);
            if (route == null || route.total <= 0) {
                continue;
            }
            StringBuilder sb = new StringBuilder(routeId).append("  mean ").append(route.meanTime).append("ms");
            if (route.p99Time >= 0) {
                sb.append(" · p95 ").append(route.p95Time).append("ms · p99 ").append(route.p99Time).append("ms");
            } else {
                sb.append(" · max ").append(route.maxTime).append("ms");
            }
            if (route.throughput != null && !route.throughput.isEmpty()) {
                sb.append(" · ").append(route.throughput).append(" msg/s");
            }
            lines.add(Line.from(Span.styled(first ? " Routes    " : "           ", Theme.muted()), Span.raw(sb.toString())));
            first = false;
        }
    }

    private static String where(ChaosInfo.RecentFault f) {
        StringBuilder sb = new StringBuilder();
        if (f.hook() != null) {
            sb.append(" at ").append(f.hook());
            if (f.where() != null) {
                sb.append(' ').append(f.where());
            }
        }
        if (f.route() != null) {
            sb.append(" · route ").append(f.route());
        }
        if (f.exchangeId() != null) {
            String id = f.exchangeId();
            // the start and the end of the id, as the Activity tab shortens it
            sb.append(" · ").append(id.length() > 16 ? id.substring(0, 6) + "…" + id.substring(id.length() - 4) : id);
        }
        return sb.toString();
    }

    /** The effect as a bar: completed, recovered, handled, failed, and in flight, in proportion. */
    private static Line effectBar(ChaosInfo.Rule r, int width) {
        long total = Math.max(1, r.faulted);
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Effect    ", Theme.muted()));
        int used = 0;
        long[] counts = { r.completed, r.recovered, r.handled, r.failed, r.inFlight };
        Style[] styles = { Theme.success(), Theme.warning(), Theme.error(), Theme.error().bold(), Theme.muted() };
        for (int i = 0; i < counts.length; i++) {
            int n = (int) Math.round((double) counts[i] * width / total);
            if (counts[i] > 0 && n == 0) {
                n = 1;
            }
            n = Math.min(n, width - used);
            if (n > 0) {
                spans.add(Span.styled((i == counts.length - 1 ? "░" : "█").repeat(n), styles[i]));
                used += n;
            }
        }
        spans.add(Span.styled("  " + r.faulted + (r.faulted == 1 ? " exchange" : " exchanges"), Theme.muted()));
        return Line.from(spans);
    }

    private static List<Span> effectWords(ChaosInfo.Rule r) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw("           "));
        addWords(spans, r.completed, "completed", Theme.success());
        addWords(spans, r.recovered, "recovered by redelivery", Theme.warning());
        addWords(spans, r.handled, "handled by the error handler", Theme.error());
        addWords(spans, r.failed, "failed", Theme.error().bold());
        addWords(spans, r.inFlight, "in flight", Theme.muted());
        return spans;
    }

    private static void addWords(List<Span> spans, long count, String words, Style style) {
        if (count > 0) {
            if (spans.size() > 1) {
                spans.add(Span.styled(" · ", Theme.muted()));
            }
            spans.add(Span.styled(count + " " + words, style));
        }
    }

    // ---- the routes ----

    private static void renderRoutes(Frame frame, Rect area, ChaosInfo chaos) {
        List<Row> rows = new ArrayList<>();
        for (ChaosInfo.RouteCounts c : chaos.routes) {
            // a consumer that is a stub (camel run --stub) does not read a kept message again; the panel is narrow, so
            // the component it stands in for is left to the report
            Span name = Span.styled(c.route() != null ? c.route() : "", Style.EMPTY.fg(Theme.accent()));
            Line route = c.stubbedConsumer() != null ? Line.from(name, Span.styled(" (stub)", Theme.muted())) : Line.from(name);
            rows.add(Row.from(
                    Cell.from(route),
                    rightCell(String.valueOf(c.completed()), 6),
                    rightCell(String.valueOf(c.failed()), 5, c.failed() > 0 ? Theme.error() : Style.EMPTY),
                    rightCell(String.valueOf(c.failedKept()), 5),
                    rightCell(String.valueOf(c.handled()), 5, c.handled() > 0 ? Theme.warning() : Style.EMPTY),
                    rightCell(String.valueOf(c.redeliveries()), 6)));
        }
        if (rows.isEmpty()) {
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(" No routes counted yet", Theme.muted()))))
                    .block(block(" Routes "))
                    .build(), area);
            return;
        }
        Style bold = Style.EMPTY.bold();
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("ROUTE", bold)),
                        rightCell("DONE", 6, bold),
                        rightCell("FAIL", 5, bold),
                        rightCell("KEPT", 5, bold),
                        rightCell("HAND", 5, bold),
                        rightCell("REDEL", 6, bold)))
                .widths(
                        Constraint.fill(),
                        Constraint.length(6),
                        Constraint.length(5),
                        Constraint.length(5),
                        Constraint.length(5),
                        Constraint.length(6))
                .block(block(" Routes "))
                .build();
        frame.renderStatefulWidget(table, area, new TableState());
    }

    // ---- the badge ----

    /**
     * The badge of the chaos screen, in the More menu and the title bar: how many rules inject faults now, and FAIL
     * when the verdict failed; null when chaos is off or there is nothing to say.
     */
    static String badge(IntegrationInfo info) {
        if (info == null || info.chaos == null || !info.chaos.enabled) {
            return null;
        }
        long active = info.chaos.activeRules();
        boolean fail = "FAIL".equals(info.chaos.verdict);
        if (active == 0 && !fail) {
            return null;
        }
        String rules = active == 1 ? "1 rule" : active + " rules";
        return active > 0 ? rules + (fail ? " · FAIL" : "") : "FAIL";
    }

    /**
     * The badge in the title bar, on every screen: faults are injected on purpose, so a failure made by chaos is not
     * debugged as a real one. Empty when there is no badge.
     */
    static List<Span> headerBadge(IntegrationInfo info) {
        String badge = badge(info);
        if (badge == null) {
            return List.of();
        }
        Style style = (badge.endsWith("FAIL") ? Theme.error() : Theme.warning()).bold();
        return List.of(Span.styled(" " + TuiIcons.TAB_CHAOS + " chaos " + badge + " ", style), Span.raw(" "));
    }

    // ---- helpers ----

    /**
     * The values as a sparkline of the width: several values share a column (the largest of them), and the height is
     * relative to the largest value; a column with no fault is blank.
     */
    static String sparkline(long[] values, int width) {
        if (values == null || values.length == 0 || width <= 0) {
            return "";
        }
        int per = Math.max(1, (int) Math.ceil((double) values.length / width));
        int columns = (values.length + per - 1) / per;
        long[] cols = new long[columns];
        long max = 0;
        for (int i = 0; i < values.length; i++) {
            cols[i / per] = Math.max(cols[i / per], values[i]);
            max = Math.max(max, values[i]);
        }
        StringBuilder sb = new StringBuilder();
        for (long v : cols) {
            if (v <= 0 || max == 0) {
                sb.append(' ');
            } else {
                int level = (int) Math.min(SPARK.length() - 1, Math.max(0, (v * SPARK.length() - 1) / max));
                sb.append(SPARK.charAt(level));
            }
        }
        return sb.toString();
    }

    /**
     * The time until something happens, in whole seconds rounded up: {@code back in 1s}, not {@code back in 903ms},
     * which a narrow note column cuts to {@code 903m} (read as minutes).
     */
    static String countdown(long ms) {
        return ms <= 0 ? "0s" : shortTime((ms + 999) / 1000 * 1000);
    }

    /** A time for a column: 800ms, 2s, 1m4s, 2h3m. */
    static String shortTime(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        long s = ms / 1000;
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            return (s / 60) + "m" + (s % 60 > 0 ? (s % 60) + "s" : "");
        }
        return (s / 3600) + "h" + ((s % 3600) / 60 > 0 ? ((s % 3600) / 60) + "m" : "");
    }

    private static String percent(double share) {
        double p = share * 100;
        return p >= 10 || p == Math.rint(p)
                ? String.format(Locale.US, "%.0f%%", p) : String.format(Locale.US, "%.1f%%", p);
    }

    private static Style stateStyle(String state) {
        return switch (state != null ? state : "") {
            case "active" -> Theme.warning();
            case "waiting" -> Theme.info();
            default -> Theme.muted();
        };
    }

    private static Block block(String title) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build();
    }

    @Override
    public void renderFooter(List<Span> spans) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.chaos == null || !info.chaos.enabled) {
            hint(spans, "Esc", "back");
            return;
        }
        control.renderFooter(spans, !info.chaos.rules.isEmpty());
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.chaos == null || info.chaos.rules.isEmpty()) {
            return null;
        }
        List<String> items = info.chaos.rules.stream().map(r -> r.id + ": " + r.text).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Chaos rules");
    }

    @Override
    public String description() {
        return "The chaos rules of camel-chaos with their live state, and the report";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("chaos");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.chaos == null) {
            return null;
        }
        ChaosInfo chaos = info.chaos;
        JsonObject result = new JsonObject();
        result.put("tab", "Chaos");
        result.put("enabled", chaos.enabled);
        if (chaos.reason != null) {
            result.put("reason", chaos.reason);
        }
        result.put("verdict", chaos.verdict);
        result.put("final", chaos.isFinal);
        result.put("lost", chaos.lost);
        result.put("reasons", new JsonArray(chaos.reasons));
        JsonArray rows = new JsonArray();
        for (ChaosInfo.Rule r : chaos.rules) {
            JsonObject row = new JsonObject();
            row.put("id", r.id);
            row.put("version", r.version);
            row.put("rule", r.text);
            row.put("state", r.state);
            row.put("rate", rate(r));
            row.put("matches", r.matches);
            row.put("fired", r.fired);
            row.put("faultsLastMinute", r.faultsLastMinute);
            if (r.lastFiredAgo >= 0) {
                row.put("lastFiredAgo", r.lastFiredAgo);
            }
            if (r.faulted > 0) {
                JsonObject effect = new JsonObject();
                effect.put("faulted", r.faulted);
                effect.put("completed", r.completed);
                effect.put("recovered", r.recovered);
                effect.put("handled", r.handled);
                effect.put("failed", r.failed);
                effect.put("inFlight", r.inFlight);
                row.put("effect", effect);
            }
            if (r.expectOutcome != null) {
                row.put("expectation", r.expectOutcome);
            }
            row.put("note", note(r));
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", chaos.rules.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
