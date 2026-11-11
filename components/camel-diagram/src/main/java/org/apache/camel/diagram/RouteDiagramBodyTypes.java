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
package org.apache.camel.diagram;

import java.awt.FontMetrics;
import java.util.List;
import java.util.StringJoiner;

/**
 * The label of the body types a step has been reached with, on the arrow into the step of a route diagram image: their
 * simple class names, and their kinds (null, payload, data or collection) when there is more than one kind, which is
 * what needs attention. The same words as the diagram of the Camel CLI terminal UI.
 */
final class RouteDiagramBodyTypes {

    private RouteDiagramBodyTypes() {
    }

    /** Whether the bodies were of more than one kind: a step that needs attention. */
    static boolean mixed(List<String> kinds) {
        return kinds != null && kinds.size() > 1;
    }

    /** The simple class name of a type: LinkedHashMap, byte[]. */
    static String simpleName(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    /** The types by their simple class names: String | LinkedHashMap. */
    static String names(List<String> types) {
        StringJoiner sj = new StringJoiner(" | ");
        for (String type : types) {
            sj.add(simpleName(type));
        }
        return sj.toString();
    }

    /** The kinds in words: payload and data, or null, payload and data. */
    static String kinds(List<String> kinds) {
        if (kinds.size() < 2) {
            return String.join("", kinds);
        }
        return String.join(", ", kinds.subList(0, kinds.size() - 1)) + " and " + kinds.get(kinds.size() - 1);
    }

    /**
     * The label in at most the width, the most that fits of: all the types with their kinds when there is more than one
     * kind, all the types, the first ones and how many more ({@code byte[] +1}), the first one cut and how many more.
     * Empty when the step has not been reached.
     */
    static String fit(List<String> types, List<String> kinds, FontMetrics fm, int width) {
        if (types == null || types.isEmpty() || width <= 0) {
            return "";
        }
        if (mixed(kinds)) {
            String all = names(types) + " (" + kinds(kinds) + ")";
            if (fm.stringWidth(all) <= width) {
                return all;
            }
        }
        for (int n = types.size(); n >= 1; n--) {
            String more = n < types.size() ? " +" + (types.size() - n) : "";
            String text = names(types.subList(0, n)) + more;
            if (fm.stringWidth(text) <= width) {
                return text;
            }
        }
        String more = types.size() > 1 ? " +" + (types.size() - 1) : "";
        String name = simpleName(types.get(0));
        while (name.length() > 1 && fm.stringWidth(name + "…" + more) > width) {
            name = name.substring(0, name.length() - 1);
        }
        return name + "…" + more;
    }
}
