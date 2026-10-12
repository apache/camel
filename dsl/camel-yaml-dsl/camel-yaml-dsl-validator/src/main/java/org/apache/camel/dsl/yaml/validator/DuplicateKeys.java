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
package org.apache.camel.dsl.yaml.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

/**
 * CAMEL-25560: a key given twice in one mapping. YAML keeps only the last of the two (the tree the validator reads
 * does, and the YAML DSL when it loads the route), so the first is lost without a word: an edit that adds a message:
 * under a log: and leaves the old one runs with the old one. The YAML nodes still have both, with their lines.
 */
final class DuplicateKeys {

    private DuplicateKeys() {
    }

    /** One message for each key that repeats a key of its mapping, in the order of the file; empty for no YAML. */
    static List<String> find(Node root) {
        List<String> answer = new ArrayList<>();
        walk(root, null, answer);
        return answer;
    }

    private static void walk(Node node, String owner, List<String> answer) {
        if (node instanceof SequenceNode seq) {
            for (Node item : seq.getValue()) {
                walk(item, owner, answer);
            }
        } else if (node instanceof MappingNode map) {
            Map<String, NodeTuple> seen = new HashMap<>();
            for (NodeTuple t : map.getValue()) {
                String key = t.getKeyNode() instanceof ScalarNode s ? s.getValue() : null;
                // <<: is a merge key, which YAML allows more than once
                if (key != null && !"<<".equals(key)) {
                    // each one against the one before it, so with a key three times both lost ones are named
                    NodeTuple previous = seen.put(key, t);
                    if (previous != null) {
                        answer.add(message(key, owner, previous, t));
                    }
                }
                walk(t.getValueNode(), key != null ? key : owner, answer);
            }
        }
    }

    private static String message(String key, String owner, NodeTuple previous, NodeTuple again) {
        int before = line(previous.getKeyNode());
        int line = line(again.getKeyNode());
        String where = owner != null ? "this " + owner + ":" : "one mapping";
        String lost = "line " + line + ": " + key + ": is given twice in " + where + " (lines " + before + " and " + line
                      + "); YAML keeps only the last one, so the " + key + ": at line " + before + " is lost";
        // XML and Java write one when for each branch of a choice; YAML has one when: key with the branches as its list
        if (previous.getValueNode() instanceof SequenceNode && again.getValueNode() instanceof SequenceNode) {
            return lost + ": " + key + ": takes a list, so write " + key + ": once and put the - items of both under it";
        }
        return lost + ": write " + key + ": once, with the value you mean";
    }

    private static int line(Node node) {
        return node.getStartMark().map(mark -> mark.getLine() + 1).orElse(0);
    }
}
