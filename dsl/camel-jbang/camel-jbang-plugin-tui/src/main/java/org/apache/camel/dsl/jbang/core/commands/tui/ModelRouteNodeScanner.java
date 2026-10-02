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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.LineNumberAware;
import org.apache.camel.NamedNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteModel;
import org.apache.camel.dsl.jbang.core.commands.tui.YamlRouteNodeScanner.EntryKind;
import org.apache.camel.dsl.jbang.core.commands.tui.YamlRouteNodeScanner.NodeEntry;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.WhenDefinition;

/**
 * The nodes of the Java and XML DSL routes of a file for Go to Node (Ctrl+G) of the Source tab, as the YAML scanner
 * gives them for a YAML file. The file is read into the Camel model as the route checks read it: a Java source by the
 * Java DSL parser, without compiling or running it.
 */
final class ModelRouteNodeScanner {

    private static final int MAX_DEPTH = 50;

    private ModelRouteNodeScanner() {
    }

    /**
     * The nodes of the routes of a Java or XML DSL file; empty when the file is neither or cannot be read.
     *
     * @param javaSources the Java sources of the project by path, for the constants of other classes
     */
    static List<NodeEntry> scan(
            String filePath, String fileName, String content, Map<String, Supplier<String>> javaSources,
            CamelCatalog catalog) {
        RouteModel model;
        try {
            model = RouteModel.read(fileName, content, catalog, javaSources);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (model == null) {
            return List.of();
        }
        List<NodeEntry> answer = new ArrayList<>();
        for (RouteDefinition r : model.routes()) {
            if (r.getInput() == null || r.getInput().getUri() == null) {
                continue;
            }
            String fromUri = DiagramSupport.stripQueryParams(JavaRouteScanner.uri(r.getInput().getUri()));
            int fromLine = line(r.getInput(), line(r, 0));
            String routeId = YamlRouteNodeScanner.resolveRouteId(r.getRouteId(), fromUri);
            answer.add(new NodeEntry(EntryKind.ROUTE, routeId, fromUri, "route", fromUri, filePath, fromLine, 0, fromLine));
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ProcessorDefinition<?> p : r.getOutputs()) {
                walk(p, routeId, filePath, fromLine, 1, answer, seen);
            }
        }
        return answer;
    }

    private static void walk(
            NamedNode p, String routeId, String filePath, int fromLine, int depth, List<NodeEntry> answer,
            Set<Object> seen) {
        if (p == null || depth > MAX_DEPTH || !seen.add(p)) {
            return;
        }
        answer.add(new NodeEntry(
                EntryKind.PROCESSOR, routeId, null, p.getShortName(), label(p), filePath, line(p, fromLine), depth,
                fromLine));
        for (NamedNode child : children(p)) {
            walk(child, routeId, filePath, fromLine, depth + 1, answer, seen);
        }
    }

    /** The nodes under a node: the when and otherwise of a choice are nodes of their own, as the diagram shows them. */
    private static List<NamedNode> children(NamedNode p) {
        List<NamedNode> answer = new ArrayList<>();
        if (p instanceof ChoiceDefinition choice) {
            answer.addAll(choice.getWhenClauses());
            if (choice.getOtherwise() != null) {
                answer.add(choice.getOtherwise());
            }
        } else if (p instanceof WhenDefinition when) {
            answer.addAll(when.getOutputs());
        } else if (p instanceof OtherwiseDefinition otherwise) {
            answer.addAll(otherwise.getOutputs());
        } else if (p instanceof ProcessorDefinition<?> pd) {
            answer.addAll(pd.getOutputs());
            if (pd instanceof TryDefinition t) {
                answer.addAll(t.getCatchClauses());
                if (t.getFinallyClause() != null) {
                    answer.add(t.getFinallyClause());
                }
            }
        }
        return answer;
    }

    /** The label of a node without its EIP name: filter[simple{...}] gives simple{...}. */
    static String label(NamedNode p) {
        if (p instanceof OtherwiseDefinition || p instanceof ChoiceDefinition) {
            // the when and otherwise of a choice are listed under it
            return "";
        }
        String label;
        try {
            label = p.getLabel();
        } catch (RuntimeException e) {
            return "";
        }
        if (label == null) {
            return "";
        }
        String name = p.getShortName();
        if (label.startsWith(name + "[") && label.endsWith("]")) {
            label = label.substring(name.length() + 1, label.length() - 1);
        }
        return label.replace('\n', ' ');
    }

    /** The line of a node from 0, or the fallback when the model does not know it. */
    private static int line(Object node, int fallback) {
        if (node instanceof LineNumberAware n && n.getLineNumber() > 0) {
            return n.getLineNumber() - 1;
        }
        return fallback;
    }
}
