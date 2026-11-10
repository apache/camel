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
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

/**
 * Let there be chaos ({@code L} on the Chaos screen, CAMEL-26760): the start of an exploration of the integration, by
 * hand. The rules to try are the suggestions of the integration, the most worth trying first, each with what should
 * hold despite its faults; the ones worth trying now are picked. The exploration tries the picked rules one at a time,
 * each for its share of the budget (see {@link ChaosExploreRun}).
 */
final class ChaosExploreDialog {

    /** What a key did to the dialog. */
    enum Outcome {
        NONE,
        CLOSED,
        START
    }

    /**
     * A suggestion to try.
     *
     * @param rule   its rules, separated by ; for two faults at once
     * @param expect what should hold, such as orders:failed<=0
     * @param fed    whether its route gets messages without a feed (a timer, or a route it is called from)
     */
    record Item(String route, String kind, String title, String rule, String why, String expect, boolean tried,
            boolean fed) {

        boolean combined() {
            return "combined".equals(kind);
        }

        boolean deadLetter() {
            return "dead letter channel".equals(kind);
        }
    }

    /** The budgets to pick from: an exploration by hand is short, the long ones are for camel cmd chaos-explore. */
    static final List<String> BUDGETS = List.of("2m", "5m", "10m", "15m", "30m");
    // the shortest time a rule is tried, so its faults fire and the routes have messages
    static final long MIN_SHARE = 30_000;
    // the rules picked when the dialog opens: single faults, then two at once
    private static final int PICKED_SINGLE = 3;
    private static final int PICKED_COMBINED = 1;
    private static final Pattern EXPECT = Pattern.compile("expect=(\\S+)");

    private final String name;
    private final List<Item> items = new ArrayList<>();
    private final List<Boolean> picked = new ArrayList<>();
    private int selected;
    private int budget = BUDGETS.indexOf("10m");

    ChaosExploreDialog(String name, JsonObject reply) {
        this.name = name;
        if (reply != null && reply.getCollection("suggestions") != null) {
            for (Object o : reply.getCollection("suggestions")) {
                if (o instanceof JsonObject jo && jo.getString("rule") != null) {
                    // two faults at once (CAMEL-26772): the rules are added together, separated by ;
                    List<Object> rules = jo.getCollection("rules");
                    String rule = rules != null && !rules.isEmpty()
                            ? rules.stream().map(String::valueOf).collect(Collectors.joining("; "))
                            : jo.getString("rule");
                    Matcher m = EXPECT.matcher(rule);
                    JsonObject feed = jo.getMap("feed");
                    items.add(new Item(
                            jo.getString("route"), jo.getString("kind"), jo.getString("title"), rule, jo.getString("why"),
                            m.find() ? m.group(1) : null, jo.getBooleanOrDefault("tried", false),
                            feed != null && feed.getBooleanOrDefault("fed", false)));
                }
            }
        }
        // the faults not tried yet whose routes get messages by themselves, the most worth trying first: a few single
        // ones, then two at once, as most bugs that lose messages need two failures that meet; a route without messages
        // says nothing, and a dead letter channel fails only when another fault sends it a message (the pair does)
        int singles = 0;
        int combined = 0;
        for (Item item : items) {
            boolean worth = !item.tried() && item.fed() && item.expect() != null && !item.deadLetter();
            boolean pick = worth && (item.combined() ? combined < PICKED_COMBINED : singles < PICKED_SINGLE);
            picked.add(pick);
            if (pick && item.combined()) {
                combined++;
            } else if (pick) {
                singles++;
            }
        }
    }

    List<Item> items() {
        return items;
    }

    /** The picked rules, in the order of the list. */
    List<Item> picked() {
        List<Item> answer = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (picked.get(i)) {
                answer.add(items.get(i));
            }
        }
        return answer;
    }

    String budget() {
        return BUDGETS.get(budget);
    }

    long budgetMillis() {
        return TimeUtils.toMilliSeconds(budget());
    }

    /** The time each picked rule is tried, or 0 when none is picked. */
    long share() {
        int count = picked().size();
        return count == 0 ? 0 : budgetMillis() / count;
    }

    /** Why the exploration cannot start, or null when it can. */
    String cannotStart() {
        if (picked().isEmpty()) {
            return "Pick a rule to try (Space)";
        }
        if (share() < MIN_SHARE) {
            return "Too many rules for " + budget() + ": pick fewer, or a longer budget";
        }
        return null;
    }

    Outcome handleKey(KeyEvent ke) {
        if (ke.isCancel()) {
            return Outcome.CLOSED;
        }
        if (ke.isUp() && selected > 0) {
            selected--;
        } else if (ke.isDown() && selected < items.size() - 1) {
            selected++;
        } else if (ke.isKey(KeyCode.LEFT) && budget > 0) {
            budget--;
        } else if (ke.isKey(KeyCode.RIGHT) && budget < BUDGETS.size() - 1) {
            budget++;
        } else if (ke.isChar(' ') && !items.isEmpty()) {
            picked.set(selected, !picked.get(selected));
        } else if (ke.isConfirm() && cannotStart() == null) {
            return Outcome.START;
        }
        return Outcome.NONE;
    }

    void render(Frame frame, Rect area) {
        int width = Math.min(area.width() - 4, 150);
        int height = Math.min(area.height() - 2, Math.max(16, items.size() + 10));
        Rect popup = DialogHelper.centered(area, width, height);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block outer = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).borderStyle(Theme.warning())
                .title(" Let there be chaos: " + name + " ").build();
        frame.renderWidget(outer, popup);
        Rect inner = outer.inner(popup);
        if (items.isEmpty()) {
            frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(List.of(Line.empty(),
                    Line.from(Span.styled(" Nothing to try: the routes call nothing remote, and have no input from"
                                          + " outside, dead letter channel or hand-off to another route",
                            Theme.muted())))))
                    .build(),
                    inner);
            return;
        }
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(2), Constraint.fill(), Constraint.length(3))
                .split(inner);
        renderBudget(frame, rows.get(0));
        List<Rect> cols = Layout.horizontal()
                .constraints(Constraint.length(Math.min(76, rows.get(1).width() / 2)), Constraint.fill())
                .split(rows.get(1));
        renderList(frame, cols.get(0));
        renderDetail(frame, cols.get(1));
        String why = cannotStart();
        frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(
                Line.from(Span.styled(why != null
                        ? " " + why
                        : " The rules are tried one at a time on " + name + ", each ends with PASS or FAIL. Every rule"
                          + " is removed when the budget ends, and the routes are not changed.",
                        why != null ? Theme.warning() : Theme.muted()))))
                .block(Block.builder().borders(EnumSet.of(Borders.TOP)).borderStyle(Theme.border()).build()).build(),
                rows.get(2));
    }

    private void renderBudget(Frame frame, Rect area) {
        int count = picked().size();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Budget  ", Theme.label()));
        spans.add(Span.styled("◂ " + budget() + " ▸", Style.EMPTY.bold()));
        spans.add(Span.styled(count == 0
                ? "   no rule picked"
                : "   " + count + (count == 1 ? " rule" : " rules") + ", " + ChaosTab.shortTime(share()) + " each",
                Theme.muted()));
        frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(spans))).build(), area);
    }

    private void renderList(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" Rules to try, the most worth trying first", Style.EMPTY.bold())));
        int selectedLine = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean sel = i == selected;
            if (sel) {
                selectedLine = lines.size();
            }
            Style style = sel ? Theme.selectionBg().bold() : Style.EMPTY;
            List<Span> spans = new ArrayList<>();
            spans.add(Span.styled((sel ? " > " : "   ") + (picked.get(i) ? "[x] " : "[ ] ") + item.title(), style));
            spans.add(Span.styled("  " + item.route(), Theme.muted()));
            if (item.tried()) {
                spans.add(Span.styled("  tried", Theme.success()));
            }
            if (!item.fed()) {
                spans.add(Span.styled("  needs messages", Theme.warning()));
            }
            lines.add(Line.from(spans));
        }
        // keep the selected one in sight
        int scroll = Math.max(0, selectedLine - (area.height() - 2));
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).scroll(scroll)
                .block(Block.builder().borders(EnumSet.of(Borders.RIGHT)).borderStyle(Theme.border()).build()).build(), area);
    }

    private void renderDetail(Frame frame, Rect area) {
        Item item = items.get(selected);
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" " + item.title(), Style.EMPTY.bold()),
                Span.styled("  route " + item.route() + " · " + item.kind(), Theme.muted())));
        lines.add(Line.empty());
        lines.add(Line.from(Span.raw(" " + item.why())));
        lines.add(Line.empty());
        lines.add(Line.from(Span.styled(" " + item.rule(), Theme.warning())));
        if (item.expect() != null) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(" Should hold: ", Theme.label()), Span.raw(item.expect())));
        }
        if (item.tried()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(" A rule of this kind was tried in route " + item.route() + " already",
                    Theme.success())));
        }
        if (!item.fed()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled(" Route " + item.route() + " gets its messages from outside: send it some"
                                            + " while the rule runs, or the verdict says nothing (INCONCLUSIVE)",
                    Theme.warning())));
        }
        frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(lines)).build(), area);
    }

    void renderFooter(List<Span> spans) {
        if (!items.isEmpty()) {
            TuiHelper.hint(spans, "↑↓", "choose");
            TuiHelper.hint(spans, "Space", "pick");
            TuiHelper.hint(spans, "←→", "budget");
            if (cannotStart() == null) {
                TuiHelper.hint(spans, "Enter", "start");
            }
        }
        TuiHelper.hint(spans, "Esc", "cancel");
    }
}
