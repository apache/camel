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
import org.apache.camel.dsl.jbang.core.commands.tui.Theme;

public final class DiagramColors {

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

    static Style borderStyle() {
        return Style.EMPTY.fg(Theme.diagramBorder());
    }

    static Style dashedBorderStyle() {
        return Style.EMPTY.fg(Theme.diagramTo());
    }

    static Style selectionStyle() {
        return Theme.selectionBg();
    }

    static Style routeIdStyle() {
        return Style.EMPTY.fg(Theme.diagramId()).bold();
    }

    static Style fromLabelStyle() {
        return Theme.muted();
    }

    static Style metricsOkStyle() {
        return Theme.success();
    }

    static Style metricsFailStyle() {
        return Theme.error().bold();
    }

    static Color okColor() {
        return Theme.diagramFrom();
    }

    static Color failColor() {
        return Theme.error().fg().orElse(Color.LIGHT_RED);
    }

    static Color externalColor() {
        return Theme.diagramTo();
    }

    static Color highlightOkColor() {
        return Theme.diagramFrom();
    }

    static Color highlightFailColor() {
        return Theme.error().fg().orElse(Color.LIGHT_RED);
    }

    public static Color getEipColor(String type) {
        if (type == null) {
            return Theme.diagramDefault();
        }
        return switch (type) {
            case "from" -> Theme.diagramFrom();
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> Theme.diagramTo();
            case "choice", "when", "otherwise" -> Theme.diagramChoice();
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                Theme.diagramTo();
            case "bean", "process", "log", "script", "delay" -> Theme.diagramAction();
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                Theme.diagramEip();
            default -> Theme.diagramDefault();
        };
    }
}
