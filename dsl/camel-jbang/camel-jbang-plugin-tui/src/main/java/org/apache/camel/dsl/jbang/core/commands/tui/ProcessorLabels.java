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
import java.util.List;

import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * What the processors table of the Routes tab shows of a step: the line of the route source it is on, and what it does
 * as the route structure has it (log:outbox for to[log:outbox]), with its id only when the route sets one. An id Camel
 * generates (to1, setBody2) repeats the type the table already shows.
 */
final class ProcessorLabels {

    private ProcessorLabels() {
    }

    /** The line of the source, from a source location such as orders.camel.yaml:21; null when it has none. */
    static Integer line(String source) {
        if (source == null) {
            return null;
        }
        int colon = source.lastIndexOf(':');
        if (colon < 0 || colon == source.length() - 1) {
            return null;
        }
        for (int i = colon + 1; i < source.length(); i++) {
            if (!Character.isDigit(source.charAt(i))) {
                return null;
            }
        }
        return Integer.valueOf(source.substring(colon + 1));
    }

    /**
     * Whether the route sets the id of the step: one Camel generates is the type with a counter (to1, setBody2), after
     * the node prefix of a route template or kamelet.
     */
    static boolean customId(ProcessorInfo proc) {
        String id = proc.id;
        if (id == null || id.isEmpty()) {
            return false;
        }
        String type = proc.processor;
        if (type == null) {
            return true;
        }
        if (proc.nodePrefixId != null && id.startsWith(proc.nodePrefixId)) {
            id = id.substring(proc.nodePrefixId.length());
        }
        if (!id.startsWith(type) || id.length() == type.length()) {
            return true;
        }
        for (int i = type.length(); i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the step does, from its code in the route structure without the type in front: log:outbox for
     * to[log:outbox], jackson for unmarshal[jackson], nothing for choice. Without the structure, where it sends to.
     */
    static String detail(ProcessorInfo proc, DiagramTopology.Step step) {
        String code = step != null ? step.code() : null;
        if (code == null) {
            return proc.uri != null ? TuiHelper.displayUri(proc.uri) : "";
        }
        String type = proc.processor;
        if (type != null && code.startsWith(type + "[") && code.endsWith("]")) {
            return code.substring(type.length() + 1, code.length() - 1);
        }
        return code.equals(type) ? "" : code;
    }

    /**
     * The step in at most the width: its id when the route sets one, then what it does, cut at the end with an
     * ellipsis.
     */
    static Line label(ProcessorInfo proc, DiagramTopology.Step step, int width, Style style) {
        List<Span> spans = new ArrayList<>();
        int room = Math.max(0, width);
        if (customId(proc)) {
            String id = TuiHelper.truncate(proc.id, room);
            spans.add(Span.styled(id, Theme.label().bold()));
            room -= CharWidth.of(id) + 2;
        }
        String detail = detail(proc, step);
        if (!detail.isEmpty() && room > 0) {
            spans.add(Span.styled((spans.isEmpty() ? "" : "  ") + TuiHelper.truncate(detail, room), style));
        }
        return Line.from(spans);
    }
}
