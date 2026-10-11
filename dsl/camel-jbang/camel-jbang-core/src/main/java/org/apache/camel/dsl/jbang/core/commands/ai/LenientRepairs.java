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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CAMEL-25532: the YAML shapes a model writes for a Camel route that have one reading, repaired before a write is
 * refused for them: an option at the column of its EIP, a plain value where an expression goes, a list where a map goes
 * (and the other way round), a list item or a key one column off, an unquoted placeholder, an enum value in another
 * case, and the names models use for an option that has another name (from the benchmark traces). Each repair is driven
 * by the validator's message for that shape and changes only the lines it names; a name is never guessed by similarity.
 * The caller validates the result again, and keeps it only when no new problem is left.
 */
final class LenientRepairs {

    /** The repaired content and what was done, one line each ("line 13: steps: indented under split:"). */
    record Result(String content, List<String> applied) {
    }

    /** A repair of one place of the content; a move changes the lines above it, so it goes alone. */
    private record Repair(int line, boolean move, String description, Action action) {
    }

    @FunctionalInterface
    private interface Action {
        boolean apply(List<String> lines);
    }

    private LenientRepairs() {
    }

    private static final Pattern OPTION_AT_EIP = Pattern.compile(
            "(\\w+): lines up with (\\w+):, so it is read as a second key of the - item; it is an option of \\2:");
    private static final Pattern PLAIN_VALUE = Pattern.compile(
            "^Line (\\d+): (\\w+): a plain value \\((.*)\\) found, an expression expected");
    private static final Pattern PLAIN_EXPRESSION = Pattern.compile(
            "^Line (\\d+): expression: a plain value \\((.*)\\) found, a language expected: .*write expression:"
                                                                    + " \\{(\\w+): \\{expression: \"(.*)\"\\}\\}");
    private static final Pattern ENUM = Pattern.compile(
            "^Line (\\d+): (\\w+): does not have a value in the enumeration \\[([^\\]]*)\\]");
    private static final Pattern LIST_FOR_MAP = Pattern.compile(
            "^Line (\\d+): (otherwise|onFallback|doFinally): array found, object expected");
    private static final Pattern MAP_FOR_LIST = Pattern.compile(
            "^Line (\\d+): (get|post|put|delete|patch|head): object found, array expected");
    private static final Pattern UNKNOWN_PROPERTY = Pattern.compile(
            "^Line (\\d+): (\\w+): property '(\\w+)' is not defined in the schema");
    private static final Pattern NO_ERROR_HANDLER = Pattern.compile(
            "^Line (\\d+): noErrorHandler: boolean found, object expected");
    private static final Pattern BEFORE_ROUTES = Pattern.compile(
            "^Line (\\d+): (\\w+) must come before the routes: move this entry above the first - route: \\(line (\\d+)\\)");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "^line (\\d+): .* without quotes is read as a YAML map .*write the line as (.+)$");
    private static final Pattern BRACES = Pattern.compile(
            "^Line (\\d+): .*\\((\\{[^}]*\\}) without quotes is a YAML map");
    private static final Pattern ITEM_COLUMN = Pattern.compile(
            "^line (\\d+): this list item starts in column (\\d+), but the list that starts at line \\d+ has its items in"
                                                               + " column (\\d+)");
    private static final Pattern KEY_COLUMN = Pattern.compile(
            "^line (\\d+): (\\S+) starts in column (\\d+), but the keys of the mapping that starts at line \\d+ are in"
                                                              + " column (\\d+)");

    /** How many columns a list item or key may be off to be moved: a slip, not another structure. */
    private static final int MAX_SHIFT = 2;

    /** body, header.foo, exchangeProperty.x, body.size(): a name the Simple language reads, written without ${ }. */
    private static final Pattern BARE_NAME = Pattern.compile("^[A-Za-z_]\\w*(\\.[\\w\\[\\]()]+)*$");

    private static boolean isBareName(String value) {
        String v = value.strip();
        return BARE_NAME.matcher(v).matches() && !"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)
                && !"null".equals(v);
    }

    /** The names models write for an option, by EIP, and the option's name (seen in the benchmark traces). */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("setHeader.headerName", "name"), Map.entry("setHeader.header", "name"),
            Map.entry("setProperty.propertyName", "name"), Map.entry("setProperty.property", "name"),
            Map.entry("setVariable.variableName", "name"), Map.entry("setVariable.variable", "name"),
            Map.entry("removeHeader.headerName", "name"), Map.entry("removeProperty.propertyName", "name"),
            Map.entry("removeVariable.variableName", "name"),
            Map.entry("log.level", "loggingLevel"), Map.entry("log.logLevel", "loggingLevel"),
            Map.entry("log.loggerName", "logName"),
            Map.entry("onException.exceptionType", "exception"), Map.entry("onException.exceptions", "exception"));

    /** The repairs for the messages that have one, applied to the content; null when none applies. */
    static Result repair(String content, List<String> errors) {
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        List<Repair> repairs = new ArrayList<>();
        for (String error : errors) {
            repairs.addAll(repairsFor(error, lines));
        }
        if (repairs.isEmpty()) {
            return null;
        }
        // a move changes the lines above it: alone, when nothing else is left to repair
        List<Repair> local = repairs.stream().filter(r -> !r.move()).toList();
        List<Repair> todo = local.isEmpty() ? List.of(repairs.get(0)) : new ArrayList<>(local);
        // from the bottom, so the line numbers of the ones above stay right; one repair per line
        todo = todo.stream().sorted(Comparator.comparingInt(Repair::line).reversed()).toList();
        Set<Integer> done = new HashSet<>();
        List<String> applied = new ArrayList<>();
        for (Repair r : todo) {
            if (done.add(r.line()) && r.action().apply(lines)) {
                applied.add(0, r.description());
            }
        }
        return applied.isEmpty() ? null : new Result(String.join("\n", lines), applied);
    }

    private static List<Repair> repairsFor(String error, List<String> lines) {
        List<Repair> answer = new ArrayList<>();
        Matcher m;
        if (error.contains("lines up with") && error.startsWith("Line ")) {
            int line = leadingLine(error);
            Matcher o = OPTION_AT_EIP.matcher(error);
            while (o.find()) {
                String key = o.group(1);
                String eip = o.group(2);
                answer.add(new Repair(
                        line, false, "line " + line + ": " + key + ": indented two spaces, as an option of " + eip + ":",
                        ls -> indentOption(ls, line - 1, key)));
            }
            return answer;
        }
        if ((m = PLAIN_EXPRESSION.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String language = m.group(3);
            String text = m.group(4).replace("\\\"", "\"");
            if (!"simple".equals(language) && m.group(2).equals(text) && isBareName(text)) {
                // expression: body - the text "body" or ${body}: not one reading (r9L-2 split the text "body")
                return answer;
            }
            answer.add(new Repair(
                    line, false, "line " + line + ": expression: written as " + language + ":",
                    ls -> wrapExpression(ls, line - 1, "expression", language, text)));
        } else if ((m = PLAIN_VALUE.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String key = m.group(2);
            String value = m.group(3);
            if (isBareName(value)) {
                return answer;
            }
            String language = value.contains("${") ? "simple" : "constant";
            answer.add(new Repair(
                    line, false, "line " + line + ": " + key + ": written as " + language + ":",
                    ls -> wrapExpression(ls, line - 1, key, language, null)));
        } else if ((m = ENUM.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String key = m.group(2);
            List<String> choices = new ArrayList<>();
            Matcher q = Pattern.compile("\"([^\"]+)\"").matcher(m.group(3));
            while (q.find()) {
                choices.add(q.group(1));
            }
            answer.add(new Repair(
                    line, false, "line " + line + ": " + key + ": the value in the case of the enumeration",
                    ls -> enumCase(ls, line - 1, key, choices)));
        } else if ((m = LIST_FOR_MAP.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String key = m.group(2);
            answer.add(new Repair(
                    line, false, "line " + line + ": " + key + ": its steps put under steps:",
                    ls -> listUnderSteps(ls, line - 1, key)));
        } else if ((m = MAP_FOR_LIST.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String key = m.group(2);
            answer.add(new Repair(
                    line, false, "line " + line + ": " + key + ": written as a list item",
                    ls -> mapAsListItem(ls, line - 1, key)));
        } else if ((m = NO_ERROR_HANDLER.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            answer.add(new Repair(
                    line, false, "line " + line + ": noErrorHandler: {}",
                    ls -> replaceValue(ls, line - 1, "noErrorHandler", "true", "{}")));
        } else if ((m = UNKNOWN_PROPERTY.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String eip = m.group(2);
            String property = m.group(3);
            String alias = ALIASES.get(eip + "." + property);
            if (alias != null) {
                answer.add(new Repair(
                        line, false, "line " + line + ": " + property + ": written as " + alias + ":",
                        ls -> rename(ls, line - 1, property, alias)));
            } else if (("marshal".equals(eip) || "unmarshal".equals(eip)) && "jackson".equals(property)) {
                answer.add(new Repair(
                        line, false, "line " + line + ": jackson: written as json: with library: Jackson",
                        ls -> jacksonAsJson(ls, line - 1)));
            }
        } else if ((m = BEFORE_ROUTES.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            int route = Integer.parseInt(m.group(3));
            String entry = m.group(2);
            answer.add(new Repair(
                    line, true, "line " + line + ": - " + entry + ": moved above the first - route:",
                    ls -> moveEntry(ls, line - 1, route - 1)));
        } else if ((m = PLACEHOLDER.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String written = m.group(2);
            answer.add(new Repair(
                    line, false, "line " + line + ": the placeholder quoted",
                    ls -> replaceLine(ls, line - 1, written)));
        } else if ((m = BRACES.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            String braces = m.group(2);
            answer.add(new Repair(
                    line, false, "line " + line + ": " + braces + " quoted",
                    ls -> quoteBraces(ls, line - 1, braces)));
        } else if ((m = ITEM_COLUMN.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            int delta = Integer.parseInt(m.group(3)) - Integer.parseInt(m.group(2));
            if (Math.abs(delta) > MAX_SHIFT) {
                return answer; // further off is another structure, not a slip
            }
            answer.add(new Repair(
                    line, false, "line " + line + ": the list item moved to the column of the list",
                    ls -> shiftBlock(ls, line - 1, delta)));
        } else if ((m = KEY_COLUMN.matcher(error)).find()) {
            int line = Integer.parseInt(m.group(1));
            int delta = Integer.parseInt(m.group(4)) - Integer.parseInt(m.group(3));
            if (Math.abs(delta) > MAX_SHIFT) {
                return answer;
            }
            answer.add(new Repair(
                    line, false, "line " + line + ": " + m.group(2)
                                 + " moved to the column of the keys of its mapping",
                    ls -> shiftBlock(ls, line - 1, delta)));
        }
        return answer;
    }

    private static int leadingLine(String error) {
        int colon = error.indexOf(':');
        try {
            return Integer.parseInt(error.substring(5, colon).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the repairs
    // ---------------------------------------------------------------------------------------------------------------

    static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static boolean valid(List<String> lines, int index) {
        return index >= 0 && index < lines.size();
    }

    /**
     * The lines of the block that starts at {@code start}: the lines below it that are more indented, and the items of
     * a list at its own column when the key has no value on its line (YAML allows the items of a key's list there).
     */
    static int blockEnd(List<String> lines, int start) {
        int col = indent(lines.get(start));
        String head = lines.get(start).strip();
        boolean keyWithoutValue = head.endsWith(":");
        int end = start + 1;
        while (end < lines.size()) {
            String l = lines.get(end);
            if (l.isBlank()) {
                end++;
                continue;
            }
            int i = indent(l);
            if (i > col || (keyWithoutValue && i == col && l.strip().startsWith("- ") && !head.startsWith("- "))) {
                end++;
                continue;
            }
            break;
        }
        while (end > start + 1 && lines.get(end - 1).isBlank()) {
            end--;
        }
        return end;
    }

    /** steps: written at the column of - split: is indented two spaces, with its lines (CAMEL-25529 says which). */
    private static boolean indentOption(List<String> lines, int itemIndex, String key) {
        if (!valid(lines, itemIndex)) {
            return false;
        }
        String item = lines.get(itemIndex);
        int dash = item.indexOf("- ");
        if (dash < 0 || dash != indent(item)) {
            return false;
        }
        int col = dash + 2;
        int itemEnd = blockEnd(lines, itemIndex);
        for (int j = itemIndex + 1; j < itemEnd; j++) {
            String l = lines.get(j);
            if (!l.isBlank() && indent(l) == col && l.strip().startsWith(key + ":")) {
                int end = blockEnd(lines, j);
                for (int k = j; k < end; k++) {
                    if (!lines.get(k).isBlank()) {
                        lines.set(k, "  " + lines.get(k));
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static final Pattern KEY_VALUE = Pattern.compile("^(\\s*)(-\\s+)?([\\w.-]+):\\s*(.*?)\\s*$");

    /** handled: true as handled: {constant: {expression: "true"}}; expression: "${body}" as simple. */
    private static boolean wrapExpression(List<String> lines, int index, String key, String language, String text) {
        if (!valid(lines, index)) {
            return false;
        }
        Matcher m = KEY_VALUE.matcher(lines.get(index));
        if (!m.matches() || !key.equals(m.group(3)) || m.group(4).isEmpty()) {
            return false;
        }
        String raw = m.group(4);
        String value;
        if (text != null && !raw.startsWith("\"") && !raw.startsWith("'")) {
            value = quote(text); // expression: constant 404: the text without its language
        } else {
            value = raw.startsWith("\"") || raw.startsWith("'") ? raw : quote(raw);
        }
        int keyCol = m.group(1).length() + (m.group(2) != null ? m.group(2).length() : 0);
        String pad = " ".repeat(keyCol + 2);
        lines.set(index, m.group(1) + (m.group(2) != null ? m.group(2) : "") + key + ":");
        lines.add(index + 1, pad + language + ":");
        lines.add(index + 2, pad + "  expression: " + value);
        return true;
    }

    static String quote(String text) {
        if (!text.contains("\"")) {
            return "\"" + text + "\"";
        }
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** library: jackson as library: Jackson, when one value of the enumeration has that name in another case. */
    private static boolean enumCase(List<String> lines, int index, String key, List<String> choices) {
        if (!valid(lines, index)) {
            return false;
        }
        Matcher m = KEY_VALUE.matcher(lines.get(index));
        if (!m.matches() || !key.equals(m.group(3))) {
            return false;
        }
        String value = m.group(4).replace("\"", "").replace("'", "");
        List<String> same = choices.stream().filter(c -> c.equalsIgnoreCase(value)).toList();
        if (same.size() != 1) {
            return false;
        }
        lines.set(index, m.group(1) + (m.group(2) != null ? m.group(2) : "") + key + ": " + same.get(0));
        return true;
    }

    /** The key line above a line of its block: the nearest line above, less indented, that is that key. */
    private static int keyAbove(List<String> lines, int index, String key) {
        int col = valid(lines, index) ? indent(lines.get(index)) : Integer.MAX_VALUE;
        for (int j = index - 1; j >= 0; j--) {
            String l = lines.get(j);
            if (l.isBlank()) {
                continue;
            }
            int i = indent(l);
            String s = l.strip();
            if (i <= col && (s.equals(key + ":") || s.equals("- " + key + ":"))) {
                return j;
            }
            if (i < col) {
                return -1;
            }
        }
        return -1;
    }

    /** otherwise: [- log: ...] as otherwise: {steps: [- log: ...]}. */
    private static boolean listUnderSteps(List<String> lines, int itemIndex, String key) {
        int k = keyAbove(lines, itemIndex, key);
        if (k < 0) {
            return false;
        }
        int end = blockEnd(lines, k);
        int keyCol = indent(lines.get(k)) + (lines.get(k).strip().startsWith("- ") ? 2 : 0);
        for (int j = k + 1; j < end; j++) {
            if (!lines.get(j).isBlank()) {
                lines.set(j, "  " + lines.get(j));
            }
        }
        lines.add(k + 1, " ".repeat(keyCol + 2) + "steps:");
        return true;
    }

    /** post: {path: ..., to: ...} as post: [- path: ..., to: ...]. */
    private static boolean mapAsListItem(List<String> lines, int firstIndex, String key) {
        int k = keyAbove(lines, firstIndex, key);
        if (k < 0) {
            return false;
        }
        int end = blockEnd(lines, k);
        boolean first = true;
        for (int j = k + 1; j < end; j++) {
            String l = lines.get(j);
            if (l.isBlank()) {
                continue;
            }
            if (first) {
                int i = indent(l);
                lines.set(j, l.substring(0, i) + "- " + l.substring(i));
                first = false;
            } else {
                lines.set(j, "  " + l);
            }
        }
        return !first;
    }

    private static boolean replaceValue(List<String> lines, int index, String key, String from, String to) {
        if (!valid(lines, index)) {
            return false;
        }
        Matcher m = KEY_VALUE.matcher(lines.get(index));
        if (!m.matches() || !key.equals(m.group(3)) || !from.equalsIgnoreCase(m.group(4))) {
            return false;
        }
        lines.set(index, m.group(1) + (m.group(2) != null ? m.group(2) : "") + key + ": " + to);
        return true;
    }

    /** headerName: x as name: x; exceptionType: X as exception: [- X]. */
    private static boolean rename(List<String> lines, int index, String from, String to) {
        if (!valid(lines, index)) {
            return false;
        }
        Matcher m = KEY_VALUE.matcher(lines.get(index));
        if (!m.matches() || !from.equals(m.group(3))) {
            return false;
        }
        String head = m.group(1) + (m.group(2) != null ? m.group(2) : "");
        String value = m.group(4);
        if ("exception".equals(to) && !value.isEmpty() && !value.startsWith("[")) {
            // onException's exception is a list of class names
            int keyCol = head.length();
            lines.set(index, head + "exception:");
            lines.add(index + 1, " ".repeat(keyCol + 2) + "- " + value);
            return true;
        }
        lines.set(index, head + to + ":" + (value.isEmpty() ? "" : " " + value));
        return true;
    }

    /** unmarshal: {jackson: {}} as unmarshal: {json: {library: Jackson}}. */
    private static boolean jacksonAsJson(List<String> lines, int index) {
        if (!valid(lines, index)) {
            return false;
        }
        Matcher m = KEY_VALUE.matcher(lines.get(index));
        if (!m.matches() || !"jackson".equals(m.group(3))) {
            return false;
        }
        String head = m.group(1) + (m.group(2) != null ? m.group(2) : "");
        String value = m.group(4);
        if (!value.isEmpty() && !"{}".equals(value)) {
            return false; // options written inline: not this
        }
        int end = blockEnd(lines, index);
        for (int j = index + 1; j < end; j++) {
            if (lines.get(j).strip().startsWith("library:")) {
                return false;
            }
        }
        lines.set(index, head + "json:");
        lines.add(index + 1, " ".repeat(head.length() + 2) + "library: Jackson");
        return true;
    }

    /** - onException: below the routes moved above the first - route:, as the runtime wants it. */
    private static boolean moveEntry(List<String> lines, int index, int routeIndex) {
        if (!valid(lines, index) || !valid(lines, routeIndex) || routeIndex >= index
                || !lines.get(index).startsWith("- ")) {
            return false;
        }
        int end = blockEnd(lines, index);
        List<String> block = new ArrayList<>(lines.subList(index, end));
        lines.subList(index, end).clear();
        // the blank line it leaves behind, when there are two now
        if (valid(lines, index) && valid(lines, index - 1) && lines.get(index).isBlank() && lines.get(index - 1).isBlank()) {
            lines.remove(index);
        }
        block.add("");
        lines.addAll(routeIndex, block);
        return true;
    }

    /** The line the validator says to write, at the indentation the line has. */
    private static boolean replaceLine(List<String> lines, int index, String written) {
        if (!valid(lines, index)) {
            return false;
        }
        String l = lines.get(index);
        lines.set(index, l.substring(0, indent(l)) + written.strip());
        return true;
    }

    /** path: {sku} as path: "{sku}". */
    private static boolean quoteBraces(List<String> lines, int index, String braces) {
        if (!valid(lines, index)) {
            return false;
        }
        String l = lines.get(index);
        int at = l.indexOf(": " + braces);
        if (at < 0 || l.contains("\"" + braces) || l.contains("'" + braces)) {
            return false;
        }
        lines.set(index, l.substring(0, at + 2) + "\"" + braces + "\"" + l.substring(at + 2 + braces.length()));
        return true;
    }

    /** A list item or a key one column off, moved with its lines to the column the validator names. */
    private static boolean shiftBlock(List<String> lines, int index, int delta) {
        if (!valid(lines, index) || delta == 0) {
            return false;
        }
        int end = blockEnd(lines, index);
        if (delta < 0) {
            for (int j = index; j < end; j++) {
                String l = lines.get(j);
                if (!l.isBlank() && indent(l) < -delta) {
                    return false;
                }
            }
        }
        for (int j = index; j < end; j++) {
            String l = lines.get(j);
            if (l.isBlank()) {
                continue;
            }
            lines.set(j, delta > 0 ? " ".repeat(delta) + l : l.substring(-delta));
        }
        return true;
    }
}
