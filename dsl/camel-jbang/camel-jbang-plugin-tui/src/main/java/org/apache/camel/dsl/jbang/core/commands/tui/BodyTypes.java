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

import java.util.List;
import java.util.StringJoiner;

import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;

/**
 * The body types a step has been reached with, across the messages, as words for the views (CAMEL-25750): their simple
 * class names, the formats of the payloads from their Content-Type (CAMEL-27468), such as {@code byte[] · json}, and
 * their kinds (null, payload, data or collection) when there is more than one kind, which is what needs attention. A
 * step reached with payloads of more than one format is marked as one of more than one kind is.
 */
public final class BodyTypes {

    /** The mark of a step reached with bodies of more than one kind, or payloads of more than one format. */
    public static final String MARK = TuiIcons.WARN;

    /** Between the types and the formats: byte[] · json. */
    public static final String FORMAT_SEPARATOR = " · ";

    /**
     * A message without a body, which the app gives as the type "null" (MessageHelper): the word null alone reads as a
     * class name.
     */
    public static final String NO_BODY = "(no body)";

    private BodyTypes() {
    }

    /** Whether the bodies were of more than one kind: a step that needs attention. */
    public static boolean mixed(List<String> kinds) {
        return kinds != null && kinds.size() > 1;
    }

    /** Whether the payloads were of more than one format, such as json and xml. */
    public static boolean mixedFormats(List<String> formats) {
        return formats != null && formats.size() > 1;
    }

    /** Whether the step is marked: bodies of more than one kind, or payloads of more than one format. */
    public static boolean marked(List<String> kinds, List<String> formats) {
        return mixed(kinds) || mixedFormats(formats);
    }

    /** The simple class name of a type: LinkedHashMap, byte[], or (no body). */
    public static String simpleName(String type) {
        return "null".equals(type) ? NO_BODY : type.substring(type.lastIndexOf('.') + 1);
    }

    /** The types by their simple class names: String | LinkedHashMap. */
    public static String names(List<String> types) {
        StringJoiner sj = new StringJoiner(" | ");
        for (String type : types) {
            sj.add(simpleName(type));
        }
        return sj.toString();
    }

    /** The formats: json, or xml | csv; empty when there is none. */
    public static String formats(List<String> formats) {
        return formats == null || formats.isEmpty() ? "" : String.join(" | ", formats);
    }

    /** The types and the formats after them: byte[] · json, or String when there is no format. */
    public static String names(List<String> types, List<String> formats) {
        String f = formats(formats);
        return f.isEmpty() ? names(types) : names(types) + FORMAT_SEPARATOR + f;
    }

    /** The kinds in words: payload and data, or no body, payload and data. */
    public static String kinds(List<String> kinds) {
        List<String> words = kinds.stream().map(k -> "null".equals(k) ? "no body" : k).toList();
        return and(words);
    }

    /** Words joined as a sentence: a, b and c. */
    public static String and(List<String> words) {
        if (words.size() < 2) {
            return String.join("", words);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }

    /**
     * The types, with a warning sign and the kinds when there is more than one kind: "⚠ String | LinkedHashMap (payload
     * and data)". Empty when the step has not been reached.
     */
    public static String label(List<String> types, List<String> kinds) {
        return label(types, kinds, List.of());
    }

    /**
     * The types and the formats, with a warning sign when there is more than one kind or format, and the kinds when
     * there is more than one kind: "byte[] · json", "⚠ String · xml | csv", "⚠ String | LinkedHashMap · json (payload
     * and data)". Empty when the step has not been reached.
     */
    public static String label(List<String> types, List<String> kinds, List<String> formats) {
        if (types == null || types.isEmpty()) {
            return "";
        }
        String text = names(types, formats);
        if (mixed(kinds)) {
            text += " (" + kinds(kinds) + ")";
        }
        return marked(kinds, formats) ? MARK + " " + text : text;
    }

    /**
     * The types, with a warning sign when there is more than one kind, for a table where the room is short: "⚠ String |
     * LinkedHashMap". Empty when the step has not been reached.
     */
    public static String shortLabel(List<String> types, List<String> kinds) {
        return shortLabel(types, kinds, List.of());
    }

    /**
     * The types and the formats, with a warning sign when there is more than one kind or format, for a table where the
     * room is short: "⚠ String | LinkedHashMap · json". Empty when the step has not been reached.
     */
    public static String shortLabel(List<String> types, List<String> kinds, List<String> formats) {
        if (types == null || types.isEmpty()) {
            return "";
        }
        String text = names(types, formats);
        return marked(kinds, formats) ? MARK + " " + text : text;
    }

    /**
     * The types in at most the width, the most that fits of: all of them with their kinds, all of them, the first ones
     * and how many more ("⚠ byte[] +1"), the first one cut and how many more. A view that cuts them shows them all
     * elsewhere (the diagram in its Info panel).
     */
    public static String fit(List<String> types, List<String> kinds, int width) {
        return fit(types, kinds, List.of(), marked(kinds, List.of()), width);
    }

    /**
     * The types and the formats in at most the width, as {@link #fit(List, List, int)}, with the formats after the
     * types: they go before the types when the room is short ("byte[] +1 · json", then "byte[] +1").
     */
    public static String fit(List<String> types, List<String> kinds, List<String> formats, int width) {
        return fit(types, kinds, formats, marked(kinds, formats), width);
    }

    /**
     * The types and the formats in at most the width, with the mark when the caller says so: a line of a source has the
     * types and formats of all its steps, and is marked when one of them is.
     */
    public static String fit(List<String> types, List<String> kinds, List<String> formats, boolean marked, int width) {
        if (types == null || types.isEmpty() || width <= 0) {
            return "";
        }
        String mark = marked ? MARK + " " : "";
        String suffix = formats(formats);
        suffix = suffix.isEmpty() ? "" : FORMAT_SEPARATOR + suffix;
        String all = mark + names(types) + suffix + (mixed(kinds) ? " (" + kinds(kinds) + ")" : "");
        if (CharWidth.of(all) <= width) {
            return all;
        }
        for (String end : suffix.isEmpty() ? List.of("") : List.of(suffix, "")) {
            for (int n = types.size(); n >= 1; n--) {
                String more = n < types.size() ? " +" + (types.size() - n) : "";
                String text = mark + names(types.subList(0, n)) + more + end;
                if (CharWidth.of(text) <= width) {
                    return text;
                }
            }
        }
        String more = types.size() > 1 ? " +" + (types.size() - 1) : "";
        int room = width - CharWidth.of(mark) - CharWidth.of(more);
        if (room < 2) {
            // no room for a name: the mark alone says the step needs attention
            return mark.isEmpty() ? "" : MARK;
        }
        return mark + CharWidth.truncateWithEllipsis(simpleName(types.get(0)), room, "…", CharWidth.TruncatePosition.END)
               + more;
    }

    /** The style of the types: a warning for more than one kind, muted otherwise. */
    public static Style style(List<String> kinds) {
        return mixed(kinds) ? Theme.warning() : Theme.muted();
    }

    /** The style of the types: a warning for more than one kind or format, muted otherwise. */
    public static Style style(List<String> kinds, List<String> formats) {
        return marked(kinds, formats) ? Theme.warning() : Theme.muted();
    }
}
