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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

/**
 * The line of the YAML text a JSON pointer of a schema error points at (/0/route/from/steps/0/to/uri), so the error can
 * be reported on its line as the other checks are, and marked on that line in an editor.
 */
public final class YamlPointerLines {

    /** The property a schema error is about: "property 'logLevel' is not defined in the schema". */
    private static final Pattern PROPERTY = Pattern.compile("property '([^']+)'");

    private YamlPointerLines() {
    }

    /** The root node of the YAML text, or null when it does not parse. */
    public static Node root(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            LoadSettings settings = LoadSettings.builder().build();
            Composer composer = new Composer(settings, new ParserImpl(settings, new StreamReader(settings, content)));
            return composer.getSingleNode().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The 1-based line the pointer points at, or the line of the property the message names when the pointer is the
     * mapping it is not allowed in; 0 when it cannot be found.
     */
    public static int line(Node root, String pointer, String message) {
        if (root == null || pointer == null) {
            return 0;
        }
        Node node = root;
        for (String segment : pointer.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            String key = segment.replace("~1", "/").replace("~0", "~");
            node = child(node, key);
            if (node == null) {
                return 0;
            }
        }
        if (message != null && node instanceof MappingNode) {
            // an unknown property: the line of its key rather than of the mapping it is in
            Matcher m = PROPERTY.matcher(message);
            if (m.find()) {
                Node keyNode = key((MappingNode) node, m.group(1));
                if (keyNode != null) {
                    node = keyNode;
                }
            }
        }
        return node.getStartMark().map(mark -> mark.getLine() + 1).orElse(0);
    }

    private static Node child(Node node, String key) {
        if (node instanceof SequenceNode seq) {
            try {
                int index = Integer.parseInt(key);
                return index >= 0 && index < seq.getValue().size() ? seq.getValue().get(index) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                if (t.getKeyNode() instanceof ScalarNode s && key.equals(s.getValue())) {
                    return t.getValueNode();
                }
            }
        }
        return null;
    }

    private static Node key(MappingNode map, String key) {
        for (NodeTuple t : map.getValue()) {
            if (t.getKeyNode() instanceof ScalarNode s && key.equals(s.getValue())) {
                return t.getKeyNode();
            }
        }
        return null;
    }
}
