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
 * class names, and their kinds (null, payload, data or collection) when there is more than one kind, which is what
 * needs attention.
 */
public final class BodyTypes {

    /** The mark of a step reached with bodies of more than one kind. */
    public static final String MARK = TuiIcons.WARN;

    private BodyTypes() {
    }

    /** Whether the bodies were of more than one kind: a step that needs attention. */
    public static boolean mixed(List<String> kinds) {
        return kinds != null && kinds.size() > 1;
    }

    /** The simple class name of a type: LinkedHashMap, byte[]. */
    public static String simpleName(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    /** The types by their simple class names: String | LinkedHashMap. */
    public static String names(List<String> types) {
        StringJoiner sj = new StringJoiner(" | ");
        for (String type : types) {
            sj.add(simpleName(type));
        }
        return sj.toString();
    }

    /** The kinds in words: payload and data, or null, payload and data. */
    public static String kinds(List<String> kinds) {
        if (kinds.size() < 2) {
            return String.join("", kinds);
        }
        return String.join(", ", kinds.subList(0, kinds.size() - 1)) + " and " + kinds.get(kinds.size() - 1);
    }

    /**
     * The types, with a warning sign and the kinds when there is more than one kind: "⚠ String | LinkedHashMap (payload
     * and data)". Empty when the step has not been reached.
     */
    public static String label(List<String> types, List<String> kinds) {
        if (types == null || types.isEmpty()) {
            return "";
        }
        return mixed(kinds) ? MARK + " " + names(types) + " (" + kinds(kinds) + ")" : names(types);
    }

    /**
     * The types, with a warning sign when there is more than one kind, for a table where the room is short: "⚠ String |
     * LinkedHashMap". Empty when the step has not been reached.
     */
    public static String shortLabel(List<String> types, List<String> kinds) {
        if (types == null || types.isEmpty()) {
            return "";
        }
        return mixed(kinds) ? MARK + " " + names(types) : names(types);
    }

    /**
     * The types in at most the width, the most that fits of: all of them with their kinds, all of them, the first ones
     * and how many more ("⚠ byte[] +1"), the first one cut and how many more. A view that cuts them shows them all
     * elsewhere (the diagram in its Info panel).
     */
    public static String fit(List<String> types, List<String> kinds, int width) {
        if (types == null || types.isEmpty() || width <= 0) {
            return "";
        }
        String all = label(types, kinds);
        if (CharWidth.of(all) <= width) {
            return all;
        }
        String mark = mixed(kinds) ? MARK + " " : "";
        for (int n = types.size(); n >= 1; n--) {
            String more = n < types.size() ? " +" + (types.size() - n) : "";
            String text = mark + names(types.subList(0, n)) + more;
            if (CharWidth.of(text) <= width) {
                return text;
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
}
