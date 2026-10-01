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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fix of a problem the validation reports, when the problem says it: an option that is a typo of another (Did you
 * mean: [period]), an enum value of the wrong case, a to that needs toD, a Simple expression where a property
 * placeholder is meant. The fix is a replacement of text on the line of the problem, which an editor applies with one
 * key and an agent applies as an edit, whatever the DSL (YAML, Java, XML) the line is written in.
 * <p/>
 * A fix is only given when it is certain: one suggestion, a value that matches but for its case or a letter or two.
 */
public final class QuickFixes {

    /**
     * A replacement on the line of a problem.
     *
     * @param label   what the fix does, such as "peroid → period"
     * @param oldText the text on the line to replace (its first occurrence)
     * @param newText what it is replaced with
     */
    public record Fix(String label, String oldText, String newText) {

        /** The line with the fix applied, or null when the line does not have the text to replace. */
        public String apply(String line) {
            int i = line != null ? line.indexOf(oldText) : -1;
            return i < 0 ? null : line.substring(0, i) + newText + line.substring(i + oldText.length());
        }
    }

    private static final Pattern UNKNOWN_OPTION
            = Pattern.compile("Unknown option '([^']+)'\\. Did you mean: \\[([^\\],]+)\\]");
    private static final Pattern UNKNOWN_PROPERTY
            = Pattern.compile("property '([^']+)' is not defined in the schema.*did you mean '([^']+)'\\?");
    private static final Pattern INVALID_ENUM
            = Pattern.compile("Invalid enum value '([^']*)' for option '([^']+)'\\. Possible values: \\[([^\\]]*)\\]");
    private static final Pattern INVALID_BOOLEAN = Pattern.compile("Invalid boolean value '([^']*)' for option '([^']+)'");
    private static final Pattern SIMPLE_AS_PLACEHOLDER = Pattern.compile(
            "([\\w.-]+)=(\\$\\{[^}]*\\}) is a Simple expression, which an endpoint option is not evaluated as.*\\{\\{([^}]+)\\}\\}");
    private static final Pattern DYNAMIC_TO = Pattern.compile("holds an expression \\(\\$\\{");

    private QuickFixes() {
    }

    /**
     * The fix of a problem.
     *
     * @param  message the message of the problem, with or without its "Line N: " in front
     * @param  line    the text of the line the problem is on
     * @return         the fix, or null when the problem has none or the line does not have what it names
     */
    public static Fix fixFor(String message, String line) {
        if (message == null || line == null) {
            return null;
        }
        Fix fix = find(message, line);
        return fix != null && fix.apply(line) != null ? fix : null;
    }

    private static Fix find(String message, String line) {
        Matcher m = UNKNOWN_OPTION.matcher(message);
        if (m.find()) {
            return rename(line, m.group(1), m.group(2).trim());
        }
        m = UNKNOWN_PROPERTY.matcher(message);
        if (m.find()) {
            return rename(line, m.group(1), m.group(2));
        }
        m = INVALID_ENUM.matcher(message);
        if (m.find()) {
            List<String> choices = new ArrayList<>();
            for (String c : m.group(3).split(",")) {
                if (!c.isBlank()) {
                    choices.add(c.trim());
                }
            }
            return value(line, m.group(2), m.group(1), closest(m.group(1), choices));
        }
        m = INVALID_BOOLEAN.matcher(message);
        if (m.find()) {
            return value(line, m.group(2), m.group(1), closest(m.group(1), List.of("true", "false")));
        }
        m = SIMPLE_AS_PLACEHOLDER.matcher(message);
        if (m.find()) {
            String placeholder = "{{" + m.group(3) + "}}";
            return new Fix(m.group(2) + " → " + placeholder, m.group(2), placeholder);
        }
        if (DYNAMIC_TO.matcher(message).find() && message.contains("toD")) {
            // the step itself: .to( in Java, <to in XML, to: in YAML
            for (String[] form : new String[][] { { ".to(", ".toD(" }, { "<to ", "<toD " }, { "to:", "toD:" } }) {
                int i = line.indexOf(form[0]);
                if (i >= 0 && (form[0].startsWith(".") || form[0].startsWith("<") || isYamlKeyAt(line, i))) {
                    return new Fix("to → toD", form[0], form[1]);
                }
            }
        }
        return null;
    }

    /** An option written name= in a uri or name: as a YAML key, renamed. */
    private static Fix rename(String line, String from, String to) {
        if (line.contains(from + "=")) {
            return new Fix(from + " → " + to, from + "=", to + "=");
        }
        int i = line.indexOf(from + ":");
        if (i >= 0 && isYamlKeyAt(line, i)) {
            return new Fix(from + " → " + to, from + ":", to + ":");
        }
        return null;
    }

    /** The value of an option, written name=value in a uri or name: value as a YAML key, replaced. */
    private static Fix value(String line, String option, String from, String to) {
        if (to == null || to.equals(from)) {
            return null;
        }
        for (String sep : new String[] { "=", ": ", ":" }) {
            String old = option + sep + from;
            if (line.contains(old)) {
                return new Fix(from + " → " + to, old, option + sep + to);
            }
        }
        return null;
    }

    /** Whether the text at the index starts a YAML key: at the start of the line, after its indent or a "- ". */
    private static boolean isYamlKeyAt(String line, int i) {
        String before = line.substring(0, i).strip();
        return before.isEmpty() || before.equals("-");
    }

    /**
     * The choice the value is meant to be: the one equal but for its case, else the one a letter or two away when only
     * one is; null when it is not clear.
     */
    static String closest(String value, List<String> choices) {
        for (String c : choices) {
            if (c.equalsIgnoreCase(value)) {
                return c;
            }
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String c : choices) {
            int d = distance(value.toLowerCase(Locale.ROOT), c.toLowerCase(Locale.ROOT));
            if (d < bestDistance) {
                best = c;
                bestDistance = d;
                tie = false;
            } else if (d == bestDistance) {
                tie = true;
            }
        }
        int allowed = Math.max(1, Math.min(2, value.length() / 3));
        return best != null && !tie && bestDistance <= allowed ? best : null;
    }

    /** The Levenshtein distance of two words. */
    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
