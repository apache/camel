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
import java.util.Set;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;

class RouteTreePreview {

    private static final Set<String> STRUCTURAL_TYPES = Set.of(
            "choice", "multicast", "split", "filter", "doTry", "doCatch", "doFinally",
            "loadBalance", "recipientList", "circuitBreaker", "onFallback",
            "when", "otherwise", "loop", "pipeline", "saga", "step",
            "aggregate", "resequence", "routingSlip", "dynamicRouter",
            "throttle", "threads", "onException", "onCompletion",
            "intercept", "interceptFrom", "interceptSendToEndpoint");

    private RouteTreePreview() {
    }

    static List<Line> buildTree(LayoutRoute layout, int maxLines, int maxWidth) {
        if (layout == null || layout.nodes.isEmpty()) {
            return List.of(Line.from(Span.styled("(no structure)", Style.EMPTY.dim())));
        }

        TreeNode root = layout.nodes.get(0).treeNode;
        if (root == null) {
            return List.of(Line.from(Span.styled("(no structure)", Style.EMPTY.dim())));
        }

        List<Line> result = new ArrayList<>();
        // Render root node (the "from" node)
        addLine(result, " ", root, maxLines, maxWidth);
        // Render children
        addChildren(root.children, " ", result, maxLines, maxWidth);
        return result;
    }

    private static void addChildren(
            List<TreeNode> children, String parentIndent,
            List<Line> result, int maxLines, int maxWidth) {
        for (int i = 0; i < children.size(); i++) {
            if (result.size() >= maxLines) {
                return;
            }
            boolean last = (i == children.size() - 1);

            if (result.size() >= maxLines - 1 && !last) {
                result.add(Line.from(Span.styled(parentIndent + "...", Style.EMPTY.dim())));
                return;
            }

            String connector = last ? "└─" : "├─";
            String childCont = last ? "  " : "│ ";

            TreeNode child = children.get(i);
            addLine(result, parentIndent + connector, child, maxLines, maxWidth);
            addChildren(child.children, parentIndent + childCont, result, maxLines, maxWidth);
        }
    }

    private static void addLine(
            List<Line> result, String prefix, TreeNode node, int maxLines, int maxWidth) {
        if (result.size() >= maxLines) {
            return;
        }
        String label = buildLabel(node);

        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(prefix, Style.EMPTY.fg(Color.DARK_GRAY)));
        spans.add(styledLabel(node.info.type, truncate(label, maxWidth - prefix.length())));
        result.add(Line.from(spans));
    }

    private static Span styledLabel(String type, String text) {
        if ("from".equals(type)) {
            return Span.styled(text, Style.EMPTY.fg(Color.YELLOW));
        } else if (STRUCTURAL_TYPES.contains(type)) {
            return Span.styled(text, Style.EMPTY.fg(Color.CYAN));
        }
        return Span.raw(text);
    }

    private static String buildLabel(TreeNode node) {
        String type = node.info.type;
        if (type == null) {
            return "?";
        }

        String code = node.info.code;
        if (code != null && !code.isEmpty()) {
            if (code.startsWith(type + "[") || code.startsWith(type + "(")) {
                return code;
            }
            if (STRUCTURAL_TYPES.contains(type)) {
                return type;
            }
            return type + ": " + code;
        }
        return type;
    }

    private static String truncate(String text, int maxLen) {
        if (maxLen <= 0) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        if (maxLen <= 3) {
            return text.substring(0, maxLen);
        }
        return text.substring(0, maxLen - 3) + "...";
    }
}
