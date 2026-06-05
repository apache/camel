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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

public final class DiagramColors {

    static final Color OK_COLOR = Color.GREEN;
    static final Color FAIL_COLOR = Color.LIGHT_RED;
    static final Color EXTERNAL_COLOR = Color.CYAN;

    static final Style BORDER_STYLE = Style.EMPTY.fg(Color.WHITE);
    static final Style DASHED_BORDER_STYLE = Style.EMPTY.fg(Color.CYAN);
    static final Style SELECTION_STYLE = Style.EMPTY.bg(Color.DARK_GRAY);
    static final Style ROUTE_ID_STYLE = Style.EMPTY.fg(Color.WHITE).bold();
    static final Style FROM_LABEL_STYLE = Style.EMPTY.fg(Color.GRAY);
    static final Style METRICS_OK_STYLE = Style.EMPTY.fg(Color.GREEN);
    static final Style METRICS_FAIL_STYLE = Style.EMPTY.fg(Color.LIGHT_RED).bold();
    static final Color HIGHLIGHT_OK_COLOR = Color.GREEN;
    static final Color HIGHLIGHT_FAIL_COLOR = Color.LIGHT_RED;

    // Unicode box-drawing characters
    static final char H = '─';
    static final char V = '│';
    static final char TL = '┌';
    static final char TR = '┐';
    static final char BL = '└';
    static final char BR = '┘';
    static final char T_DOWN = '┬';
    static final char T_UP = '┴';
    static final char CROSS = '┼';
    static final char ARROW = '▼';
    static final char DASH_V = '┆';
    static final char DASH_H = '┄';

    private DiagramColors() {
    }

    public static Color getEipColor(String type) {
        if (type == null) {
            return Color.GRAY;
        }
        return switch (type) {
            case "from" -> Color.GREEN;
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> Color.CYAN;
            case "choice", "when", "otherwise" -> Color.YELLOW;
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                Color.CYAN;
            case "bean", "process", "log", "script", "delay" -> Color.MAGENTA;
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                Color.rgb(0x89, 0x57, 0xE5);
            default -> Color.GRAY;
        };
    }
}
