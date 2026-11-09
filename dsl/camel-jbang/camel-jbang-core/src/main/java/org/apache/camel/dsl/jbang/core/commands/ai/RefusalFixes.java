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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CAMEL-26758: the fixes the refusals of a write gave as exact text, made by the write tool when the model sends the
 * same refused content a third time. A local model that does not see the problem in its own lines sends them again and
 * again, also when the refusal says which lines to write: in r28 to r30 of the local-model benchmark, 13 of the 14 step
 * runs with 4 or more such sends failed, and contracts-rest-code-first step 1 sent ...')][] 15 times in two runs of
 * r30, told each time to write the jsonpath without the []. A repair per shape ({@link LenientRepairs}) moved the loop
 * to the next mistake. Two forms of exact text end a message, and only those are applied:
 * <ul>
 * <li>the block form, {@code Write line 7 as:} or {@code Write lines 48-51 as:} (or {@code Write line 45 as these 2
 * lines:}), and then the lines with their indentation;</li>
 * <li>the line form, {@code line 49: ... write the line as expression: "$.lines"}: the line without its indentation,
 * which is kept.</li>
 * </ul>
 * A problem without such text is left to the check that follows: the caller validates the result again, and writes it
 * only when no problem is left. So a problem that came from a line a fix rewrote goes with it (an unclosed quote also
 * gives a Simple syntax error on its line), and any other keeps the write refused, with nothing written: the model
 * never goes on from a file that is neither its own text nor the file on disk.
 */
final class RefusalFixes {

    /** The line a message starts with: "line 49: ...", "Line 46: ...", "line 37 (" - doCatch:"): ...". */
    private static final Pattern LEADING_LINE = Pattern.compile("^[Ll]ine (\\d+)\\b");
    /** The block form; its lines are the rest of the message. */
    private static final Pattern BLOCK = Pattern.compile(
            "Write (?:line (\\d+)|lines (\\d+)-(\\d+)) as(?: these (\\d+) lines)?:\\n");
    private static final String LINE_FORM = "write the line as ";
    /** A key of a YAML mapping: expression of expression: "x", to of - to: x. */
    private static final Pattern KEY = Pattern.compile("^([\\w.-]+):(?:\\s|$)");

    private RefusalFixes() {
    }

    /** The lines from (0-based) to to (exclusive) written as the message said. */
    private record Fix(int from, int to, List<String> written, String description) {
    }

    /**
     * The content with the fixes the problems gave as exact text made, and what was done, one line each ("line 49:
     * written as the refusals said: ..."); null when no problem gives exact text that fits the content, or when two
     * fixes are of the same lines.
     */
    static LenientRepairs.Result apply(String content, List<String> errors) {
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        List<Fix> fixes = new ArrayList<>();
        for (String error : errors) {
            Fix fix = fixOf(error, lines);
            if (fix != null && !fixes.contains(fix)) {
                fixes.add(fix);
            }
        }
        if (fixes.isEmpty()) {
            return null;
        }
        // from the bottom, so the line numbers of the ones above stay right
        fixes.sort(Comparator.comparingInt(Fix::from).reversed());
        int below = Integer.MAX_VALUE;
        for (Fix f : fixes) {
            if (f.to() > below) {
                return null;
            }
            below = f.from();
        }
        List<String> applied = new ArrayList<>();
        boolean moved = false;
        for (Fix f : fixes) {
            List<String> range = lines.subList(f.from(), f.to());
            moved |= range.size() != f.written().size();
            range.clear();
            range.addAll(f.written());
            applied.add(0, f.description());
        }
        return new LenientRepairs.Result(String.join("\n", lines), applied, moved);
    }

    private static Fix fixOf(String error, List<String> lines) {
        Matcher block = BLOCK.matcher(error);
        if (block.find()) {
            boolean one = block.group(1) != null;
            int first = Integer.parseInt(one ? block.group(1) : block.group(2));
            int last = one ? first : Integer.parseInt(block.group(3));
            List<String> written = List.of(error.substring(block.end()).split("\n", -1));
            // "as these 2 lines": one line becomes several
            int count = block.group(4) != null ? Integer.parseInt(block.group(4)) : last - first + 1;
            if (first < 1 || last < first || last > lines.size() || written.size() != count
                    || block.group(4) != null && !one
                    // a long block is shown in part, with "(and the 3 lines after them, ...)" (IndentLines)
                    || written.stream().anyMatch(l -> l.startsWith("(and the "))) {
                return null;
            }
            return new Fix(
                    first - 1, last, written,
                    (one ? "line " + first : "lines " + first + "-" + last) + ": written as the refusals said");
        }
        Matcher lead = LEADING_LINE.matcher(error);
        int at = error.lastIndexOf(LINE_FORM);
        if (at < 0 || !lead.find()) {
            return null;
        }
        int line = Integer.parseInt(lead.group(1));
        String text = error.substring(at + LINE_FORM.length()).strip();
        if (line < 1 || line > lines.size() || text.isEmpty() || text.contains("\n")
                || !sameShape(lines.get(line - 1), text)) {
            return null;
        }
        String old = lines.get(line - 1);
        return new Fix(
                line - 1, line, List.of(old.substring(0, LenientRepairs.indent(old)) + text),
                "line " + line + ": written as the refusals said: " + text);
    }

    /**
     * The line to write is of the line it replaces: the same key, and a list item when that one is; a message that went
     * on after the line to write would else give a line that is not the one meant.
     */
    private static boolean sameShape(String old, String text) {
        String o = old.strip();
        if (o.startsWith("- ") != text.startsWith("- ")) {
            return false;
        }
        Matcher k = KEY.matcher(o.startsWith("- ") ? o.substring(2).stripLeading() : o);
        if (!k.find()) {
            return true;
        }
        Matcher t = KEY.matcher(text.startsWith("- ") ? text.substring(2).stripLeading() : text);
        return t.find() && t.group(1).equals(k.group(1));
    }
}
