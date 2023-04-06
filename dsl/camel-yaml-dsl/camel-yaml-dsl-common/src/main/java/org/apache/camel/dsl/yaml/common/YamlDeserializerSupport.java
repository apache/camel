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
package org.apache.camel.dsl.yaml.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.exception.InvalidEnumException;
import org.apache.camel.dsl.yaml.common.exception.InvalidNodeTypeException;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedNodeTypeException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.model.Block;
import org.apache.camel.model.OutputNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

public class YamlDeserializerSupport {

    protected YamlDeserializerSupport() {
    }

    public static Class<?> asClass(String val) throws ClassNotFoundException {
        return Class.forName(val);
    }

    public static Class<?>[] asClassArray(Node node) throws YamlDeserializationException {
        if (node == null) {
            return null;
        }

        String val = asText(node);
        String[] values = val.split(" ");
        Class<?>[] cls = new Class<?>[values.length];
        for (int i = 0; i < values.length; i++) {
            String name = values[i];
            try {
                cls[i] = asClass(name);
            } catch (ClassNotFoundException e) {
                throw new YamlDeserializationException(node, "Cannot load class " + name);
            }
        }
        return cls;
    }

    public static byte[] asByteArray(String val) {
        return Base64.getDecoder().decode(val);
    }

    public static List<String> asStringList(String val) {
        return Arrays.asList(val.split(" "));
    }

    public static Set<String> asStringSet(String val) {
        return CollectionHelper.createSetContaining(val.split(" "));
    }

    public static byte[] asByteArray(Node node) {
        if (node == null) {
            return null;
        }

        return asByteArray(asText(node));
    }

    public static Class<?> asClass(Node node) {
        if (node == null) {
            return null;
        }

        String val = asText(node);
        try {
            return Class.forName(val);
        } catch (ClassNotFoundException e) {
            throw new YamlDeserializationException(node, "Cannot load class: " + val);
        }
    }

    public static List<String> asStringList(Node node) {
        if (node == null) {
            return null;
        }

        final List<String> answer;

        if (node.getNodeType() == NodeType.SCALAR) {
            answer = asStringList(asText(node));
        } else if (node.getNodeType() == NodeType.SEQUENCE) {
            answer = new ArrayList<>();
            for (Node item : asSequenceNode(node).getValue()) {
                answer.add(asText(item));
            }
        } else {
            throw new UnsupportedNodeTypeException(node);
        }

        return answer;
    }

    public static Set<String> asStringSet(Node node) {
        if (node == null) {
            return null;
        }

        final Set<String> answer;

        if (node.getNodeType() == NodeType.SCALAR) {
            answer = asStringSet(asText(node));
        } else if (node.getNodeType() == NodeType.SEQUENCE) {
            answer = new LinkedHashSet<>();
            for (Node item : asSequenceNode(node).getValue()) {
                answer.add(asText(item));
            }
        } else {
            throw new UnsupportedNodeTypeException(node);
        }

        return answer;
    }

    public static String asText(Node node) throws YamlDeserializationException {
        if (node == null) {
            return null;
        }
        if (node.getNodeType() != NodeType.SCALAR) {
            throw new InvalidNodeTypeException(node, NodeType.SCALAR);
        }

        return ((ScalarNode) node).getValue();
    }

    public static <T> T asEnum(Node node, Class<T> type) throws YamlDeserializationException {
        if (node == null) {
            return null;
        }
        if (node.getNodeType() != NodeType.SCALAR) {
            throw new InvalidNodeTypeException(node, NodeType.SCALAR);
        }

        String text = ((ScalarNode) node).getValue();
        return enumConverter(node, type, text);
    }

    public static Map<String, Object> asMap(Node node) {
        if (node == null) {
            return null;
        }

        final MappingNode mn = asMappingNode(node);
        final Map<String, Object> answer = new HashMap<>();

        for (NodeTuple tuple : mn.getValue()) {
            final String key = asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            switch (val.getNodeType()) {
                case SCALAR:
                    answer.put(StringHelper.dashToCamelCase(key), asText(val));
                    break;
                case MAPPING:
                    answer.put(StringHelper.dashToCamelCase(key), asMap(val));
                    break;
                default:
                    throw new UnsupportedNodeTypeException(node);
            }
        }

        return answer;
    }

    public static Map<String, Object> asScalarMap(Node node) {
        if (node == null) {
            return null;
        }

        final MappingNode mn = asMappingNode(node);
        final Map<String, Object> answer = new HashMap<>();

        for (NodeTuple tuple : mn.getValue()) {
            final String key = asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            switch (val.getNodeType()) {
                case SCALAR:
                    answer.put(StringHelper.dashToCamelCase(key), asText(val));
                    break;
                default:
                    throw new InvalidNodeTypeException(node, NodeType.SCALAR);
            }
        }

        return answer;
    }

    public static boolean asBoolean(Node node) throws YamlDeserializationException {
        return Boolean.parseBoolean(asText(node));
    }

    public static int asInt(Node node) throws YamlDeserializationException {
        return Integer.parseInt(asText(node));
    }

    public static long asLong(Node node) throws YamlDeserializationException {
        return Long.parseLong(asText(node));
    }

    public static double asDouble(Node node) throws YamlDeserializationException {
        return Double.parseDouble(asText(node));
    }

    public static MappingNode asMappingNode(Node node) throws YamlDeserializationException {
        if (node == null) {
            return null;
        }

        if (node.getNodeType() != NodeType.MAPPING) {
            throw new InvalidNodeTypeException(node, NodeType.MAPPING);
        }

        return (MappingNode) node;
    }

    public static SequenceNode asSequenceNode(Node node) throws YamlDeserializationException {
        if (node == null) {
            return null;
        }

        if (node.getNodeType() != NodeType.SEQUENCE) {
            throw new InvalidNodeTypeException(node, NodeType.SEQUENCE);
        }

        return (SequenceNode) node;
    }

    public static boolean isSequenceNode(Node node) {
        if (node == null) {
            return false;
        }

        return node.getNodeType() == NodeType.SEQUENCE;
    }

    public static <T> List<T> asFlatList(Node node, Class<T> type) throws YamlDeserializationException {
        List<T> answer = new ArrayList<>();
        asFlatCollection(node, type, answer);

        return answer;
    }

    public static <T> List<T> asList(Node node, Class<T> type) throws YamlDeserializationException {
        List<T> answer = new ArrayList<>();
        asCollection(node, type, answer);

        return answer;
    }

    public static <T> Set<T> asFlatSet(Node node, Class<T> type) throws YamlDeserializationException {
        Set<T> answer = new HashSet<>();
        asFlatCollection(node, type, answer);

        return answer;
    }

    public static <T> void asFlatCollection(Node node, Class<T> type, Collection<T> collection)
            throws YamlDeserializationException {
        asCollection(node, type, collection, true);
    }

    public static <T> void asCollection(Node node, Class<T> type, Collection<T> collection)
            throws YamlDeserializationException {
        asCollection(node, type, collection, false);
    }

    private static <T> void asCollection(Node node, Class<T> type, Collection<T> collection, boolean flat)
            throws YamlDeserializationException {
        if (node.getNodeType() != NodeType.SEQUENCE) {
            throw new InvalidNodeTypeException(node, NodeType.SEQUENCE);
        }

        YamlDeserializationContext dc = getDeserializationContext(node);
        if (dc == null) {
            throw new YamlDeserializationException(node, "Unable to find constructor for node");
        }

        for (Node element : asSequenceNode(node).getValue()) {
            final Node val = setDeserializationContext(element, dc);
            final T instance;

            if (flat) {
                instance = asType(val, type);
            } else {
                ConstructNode cn = dc.mandatoryResolve(val);
                instance = type.cast(cn.construct(val));
            }

            collection.add(instance);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T asType(Node node, Class<T> type) throws YamlDeserializationException {
        YamlDeserializationContext resolver
                = (YamlDeserializationContext) node.getProperty(YamlDeserializationContext.class.getName());
        if (resolver == null) {
            throw new YamlDeserializationException(node, "Unable to find YamlConstructor");
        }
        ConstructNode construct = resolver.resolve(type);
        if (construct == null) {
            throw new YamlDeserializationException(node, "Unable to determine constructor for type: " + type.getName());
        }

        return (T) construct.construct(node);
    }

    public static String asEndpoint(Node node) {
        return asEndpoint(
                asMappingNode(node));
    }

    public static String asEndpoint(MappingNode node) {
        final YamlDeserializationContext dc = getDeserializationContext(node);
        final CamelContext cc = dc.getCamelContext();

        String uri = null;
        Map<String, Object> properties = null;

        for (NodeTuple tuple : node.getValue()) {
            final String key = YamlDeserializerSupport.asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            switch (key) {
                case "uri":
                    uri = YamlDeserializerSupport.asText(val);
                    break;
                case "properties":
                    properties = YamlDeserializerSupport.asScalarMap(tuple.getValueNode());
                    break;
                default:
                    throw new UnsupportedFieldException(node, key);
            }
        }

        return YamlSupport.createEndpointUri(cc, node, uri, properties);
    }

    public static YamlDeserializationContext getDeserializationContext(Node node) {
        return (YamlDeserializationContext) node.getProperty(YamlDeserializationContext.class.getName());
    }

    public static Node setDeserializationContext(Node node, YamlDeserializationContext context) {
        node.setProperty(YamlDeserializationContext.class.getName(), context);
        return node;
    }

    public static Map<String, Object> parseParameters(NodeTuple node) {
        Node value = node.getValueNode();
        final YamlDeserializationContext dc = getDeserializationContext(value);
        return asScalarMap(value);
    }

    public static void setSteps(Block target, Node node) {
        final YamlDeserializationContext dc = getDeserializationContext(node);
        setStepsFlowMode(target, node);
    }

    private static void setStepsFlowMode(Block target, Node node) {
        Block block = target;
        for (ProcessorDefinition<?> definition : asFlatList(node, ProcessorDefinition.class)) {
            block.addOutput(definition);
            // flow mode
            if (definition instanceof OutputNode) {
                if (ObjectHelper.isEmpty(definition.getOutputs())) {
                    block = definition;
                }
            }
        }
    }

    public static Node nodeAt(Node root, String pointer) {
        if (ObjectHelper.isEmpty(pointer)) {
            return root;
        }

        MappingNode mn = asMappingNode(root);
        for (String path : pointer.split("/")) {
            for (NodeTuple child : mn.getValue()) {
                if (child.getKeyNode() instanceof ScalarNode) {
                    ScalarNode scalar = (ScalarNode) child.getKeyNode();
                    if (scalar.getValue().equals(path)) {
                        String next = pointer.substring(path.length() + 1);
                        return ObjectHelper.isEmpty(next)
                                ? child.getValueNode()
                                : nodeAt(child.getValueNode(), next);
                    }
                }
            }
        }

        return null;
    }

    public static <T> T enumConverter(Node node, Class<T> type, String value) {
        if (type.isEnum()) {
            String text = value;
            Class<Enum<?>> enumClass = (Class<Enum<?>>) type;

            // we want to match case-insensitive for enums
            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(text)) {
                    return type.cast(enumValue);
                }
            }

            // add support for using dash or camel cased to common used upper cased underscore style for enum constants
            text = StringHelper.asEnumConstantValue(text);
            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(text)) {
                    return type.cast(enumValue);
                }
            }

            throw new InvalidEnumException(node, type, value);
        }

        return null;
    }
}
