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
package org.apache.camel.semantic.yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

/** Named semantic declarations are installed in a resource-wide pass before route references are resolved. */
@YamlIn
@YamlType(nodes = "semantic", properties = {
        @YamlProperty(name = "question",
                      type = "map:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$QuestionSchema",
                      required = true)
})
public class SemanticDefinitionDeserializer extends YamlDeserializerSupport implements ConstructNode, YamlDeserializerResolver {
    private static final Set<String> FIELDS
            = Set.of("type", "instructions", "state", "criteria", "threshold", "uncertainty", "uncertaintyPolicy");

    @Override
    public ConstructNode resolve(String id) {
        return "semantic".equals(id) ? this : null;
    }

    @Override
    public Object construct(Node node) {
        read(node);
        // Registration happens once for the entire resource, including declarations after routes.
        return (CamelContextCustomizer) context -> {
        };
    }

    @Override
    public void preParse(YamlDeserializationContext dc, Node root) {
        if (!(root instanceof SequenceNode sequence)) {
            return;
        }
        Map<String, SemanticQuestion> definitions = new LinkedHashMap<>();
        for (Node node : sequence.getValue()) {
            for (NodeTuple tuple : asMappingNode(node).getValue()) {
                if ("semantic".equals(asText(tuple.getKeyNode()))) {
                    read(tuple.getValueNode()).forEach((name, question) -> {
                        if (definitions.putIfAbsent(name, question) != null) {
                            throw new IllegalArgumentException("Duplicate semantic question: " + name);
                        }
                    });
                }
            }
        }
        CamelContext context = dc.getCamelContext();
        SemanticQuestions questions = definitions.isEmpty()
                ? context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class)
                : SemanticQuestions.get(context);
        if (questions != null) {
            questions.replace(dc.getResource(), definitions);
        }
    }

    private static Map<String, SemanticQuestion> read(Node node) {
        Map<String, Node> semantic = fields(node);
        if (!semantic.keySet().equals(Set.of("question"))) {
            throw new IllegalArgumentException("Semantic declaration requires only question");
        }
        Map<String, SemanticQuestion> result = new LinkedHashMap<>();
        fields(semantic.get("question")).forEach((name, definition) -> {
            Map<String, Node> values = fields(definition);
            if (!FIELDS.containsAll(values.keySet())) {
                throw new IllegalArgumentException("Unknown property in semantic question: " + name);
            }
            String typeName = asText(values.get("type"));
            if (typeName == null) {
                throw new IllegalArgumentException("Semantic question type is required: " + name);
            }
            SemanticQuestion.Type type = SemanticQuestion.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
            Map<String, String> criteria = new LinkedHashMap<>();
            List<String> levels = List.of();
            if (values.containsKey("criteria")) {
                if (type == SemanticQuestion.Type.SCORE) {
                    levels = asSequenceNode(values.get("criteria")).getValue().stream().map(YamlDeserializerSupport::asText)
                            .toList();
                } else {
                    fields(values.get("criteria")).forEach((key, value) -> criteria.put(key, asText(value)));
                }
            }
            if (type != SemanticQuestion.Type.BOOLEAN && (values.containsKey("threshold") || values.containsKey("uncertainty")
                    || values.containsKey("uncertaintyPolicy"))) {
                throw new IllegalArgumentException("Threshold and uncertainty policy require a boolean question: " + name);
            }
            SemanticQuestion.UncertaintyPolicy policy = values.containsKey("uncertaintyPolicy")
                    ? SemanticQuestion.UncertaintyPolicy
                            .valueOf(asText(values.get("uncertaintyPolicy")).replace('-', '_').toUpperCase(Locale.ROOT))
                    : SemanticQuestion.UncertaintyPolicy.FAIL;
            result.put(name, new SemanticQuestion(
                    type, asText(values.get("instructions")), asText(values.get("state")),
                    criteria, levels, number(values, name, "threshold", 0.5), number(values, name, "uncertainty", 0), policy));
        });
        return result;
    }

    private static double number(Map<String, Node> values, String question, String name, double fallback) {
        if (!values.containsKey(name)) {
            return fallback;
        }
        Node node = values.get(name);
        String raw = asText(node);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new YamlDeserializationException(
                    node,
                    "Invalid numeric value for '" + name + "' in semantic question '" + question + "': " + raw, e);
        }
    }

    private static Map<String, Node> fields(Node node) {
        Map<String, Node> result = new LinkedHashMap<>();
        for (NodeTuple tuple : asMappingNode(node).getValue()) {
            String name = asText(tuple.getKeyNode());
            if (result.putIfAbsent(name, tuple.getValueNode()) != null) {
                throw new IllegalArgumentException("Duplicate semantic declaration key: " + name);
            }
        }
        return result;
    }

    @YamlType(properties = {
            @YamlProperty(name = "__oneOf",
                          type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$BooleanSchema",
                          oneOf = "kind", required = true),
            @YamlProperty(name = "__oneOf",
                          type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$ChoiceSchema",
                          oneOf = "kind", required = true),
            @YamlProperty(name = "__oneOf",
                          type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$ScoreSchema",
                          oneOf = "kind", required = true)
    })
    public static class QuestionSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "type", type = "enum:boolean", required = true),
            @YamlProperty(name = "instructions", type = "string", required = true),
            @YamlProperty(name = "state", type = "string"),
            @YamlProperty(name = "criteria", type = "map:string"),
            @YamlProperty(name = "threshold", type = "number"),
            @YamlProperty(name = "uncertainty", type = "number"),
            @YamlProperty(name = "uncertaintyPolicy", type = "enum:fail,non-match")
    })
    public static class BooleanSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "type", type = "enum:choice", required = true),
            @YamlProperty(name = "instructions", type = "string", required = true),
            @YamlProperty(name = "state", type = "string"),
            @YamlProperty(name = "criteria", type = "map:string", required = true)
    })
    public static class ChoiceSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "type", type = "enum:score", required = true),
            @YamlProperty(name = "instructions", type = "string", required = true),
            @YamlProperty(name = "state", type = "string"),
            @YamlProperty(name = "criteria", type = "array:string", required = true)
    })
    public static class ScoreSchema {
    }
}
