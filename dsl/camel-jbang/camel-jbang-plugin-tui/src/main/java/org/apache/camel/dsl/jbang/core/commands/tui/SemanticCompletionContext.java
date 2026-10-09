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

import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/** The expert selected by the current editor buffer, not the last saved declaration. */
record SemanticCompletionContext(String expert, boolean shorthand) {
    static SemanticCompletionContext at(List<String> lines, int row) {
        if (row < 0 || row >= lines.size()) {
            return null;
        }
        String line = lines.get(row);
        int colon = line.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String key = line.substring(0, colon).strip();
        if (!"type".equals(key) && !"operation".equals(key)) {
            return null;
        }
        // The value being completed may be an unfinished quote or placeholder.
        List<String> buffer = new ArrayList<>(lines);
        buffer.set(row, line.substring(0, colon + 1));
        try {
            Node root = new Compose(LoadSettings.builder().build()).composeString(String.join("\n", buffer)).orElse(null);
            if (root instanceof SequenceNode sequence) {
                for (Node item : sequence.getValue()) {
                    Node block = field(item, "semantic");
                    Node evaluations = field(block, "evaluation");
                    if (evaluations instanceof MappingNode mapping) {
                        for (NodeTuple entry : mapping.getValue()) {
                            Node evaluation = entry.getValueNode();
                            if (!(evaluation instanceof MappingNode fields)) {
                                continue;
                            }
                            boolean atOperation
                                    = fields.getValue().stream().anyMatch(tuple -> key.equals(text(tuple.getKeyNode()))
                                            && tuple.getKeyNode().getStartMark().orElseThrow().getLine() == row);
                            if (atOperation) {
                                Node expert = field(evaluation, "expert");
                                if (expert == null) {
                                    expert = field(block, "expert");
                                }
                                String name = expert == null ? null : text(expert);
                                if (expert != null && (name == null || name.isBlank() || !Tag.STR.equals(expert.getTag()))) {
                                    return null;
                                }
                                return new SemanticCompletionContext(name, "type".equals(key));
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException incomplete) {
            // Unreadable or ambiguous source must not select a different expert.
        }
        return null;
    }

    private static Node field(Node node, String name) {
        Node found = null;
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (name.equals(text(tuple.getKeyNode()))) {
                    if (found != null) {
                        throw new IllegalArgumentException("Duplicate field: " + name);
                    }
                    found = tuple.getValueNode();
                }
            }
        }
        return found;
    }

    private static String text(Node node) {
        return node instanceof ScalarNode scalar ? scalar.getValue() : null;
    }
}
