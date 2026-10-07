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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.exception.InvalidNodeTypeException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestionBuilder;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/** Named semantic declarations are installed in a resource-wide pass before route references are resolved. */
@YamlIn
@YamlType(nodes = "semantic", properties = {
        @YamlProperty(name = "__oneOf",
                      type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$QuestionBlockSchema",
                      oneOf = "declarations", required = true),
        @YamlProperty(name = "__oneOf",
                      type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$EvaluationBlockSchema",
                      oneOf = "declarations", required = true)
})
public class SemanticDefinitionDeserializer extends YamlDeserializerSupport implements ConstructNode, YamlDeserializerResolver {
    private static final Set<String> FIELDS
            = Set.of("type", "operation", "expert", "parameters", "instructions", "state", "criteria", "threshold",
                    "uncertainty", "uncertaintyPolicy");

    @Override
    public ConstructNode resolve(String id) {
        return "semantic".equals(id) ? this : null;
    }

    @Override
    public Object construct(Node node) {
        read(getDeserializationContext(node).getCamelContext(), node);
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
            if (!(node instanceof MappingNode mapping)) {
                // Leave malformed entries to the route loader without replacing the resource's questions.
                return;
            }
            for (NodeTuple tuple : mapping.getValue()) {
                if ("semantic".equals(asText(tuple.getKeyNode()))) {
                    read(dc.getCamelContext(), tuple.getValueNode()).forEach((name, question) -> {
                        if (definitions.putIfAbsent(name, question) != null) {
                            throw new YamlDeserializationException(
                                    tuple.getValueNode(), "Duplicate semantic question: " + name);
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
            try {
                questions.replace(dc.getResource(), definitions);
            } catch (IllegalArgumentException e) {
                throw new YamlDeserializationException(root, e.getMessage(), e);
            }
        }
    }

    private static Map<String, SemanticQuestion> read(CamelContext context, Node node) {
        Map<String, Node> semantic = fields(node, "semantic declaration");
        for (String field : semantic.keySet()) {
            if (!Set.of("question", "evaluation", "expert", "state").contains(field)) {
                throw new YamlDeserializationException(
                        semantic.get(field), "Unknown property '" + field + "' in semantic declaration");
            }
        }
        if (semantic.containsKey("question") == semantic.containsKey("evaluation")) {
            throw new YamlDeserializationException(node, "Semantic declaration requires exactly one of question or evaluation");
        }
        String declaration = semantic.containsKey("evaluation") ? "evaluation" : "question";
        Map<String, SemanticQuestion> result = new LinkedHashMap<>();
        fields(semantic.get(declaration), "semantic evaluations").forEach((name, definition) -> {
            if (name.isBlank()) {
                throw new YamlDeserializationException(definition, "Semantic question requires a nonblank name");
            }
            Map<String, Node> values = fields(definition, "semantic question '" + name + "'");
            for (String common : List.of("expert", "state")) {
                if (!values.containsKey(common) && semantic.containsKey(common)) {
                    values.put(common, semantic.get(common));
                }
            }
            String expert = "default/automatic";
            try {
                if (values.containsKey("expert")) {
                    expert = "invalid expert reference";
                    expert = asText(values.get("expert"));
                }
                result.put(name, readQuestion(context, name, expert, definition, values));
            } catch (IllegalArgumentException | InvalidNodeTypeException e) {
                throw new YamlDeserializationException(
                        definition, "Invalid semantic question '" + name + "': " + e.getMessage()
                                    + " (expert '" + expert + "')",
                        e);
            }
        });
        return result;
    }

    private static SemanticQuestion readQuestion(
            CamelContext context, String name, String expert, Node definition, Map<String, Node> values) {
        String description = "semantic question '" + name + "'";
        String expertContext = " (expert '" + expert + "')";
        values.forEach((field, value) -> {
            if (!FIELDS.contains(field)) {
                throw new YamlDeserializationException(
                        value, "Unknown property '" + field + "' in " + description + expertContext);
            }
        });
        if (values.containsKey("type") == values.containsKey("operation")) {
            throw new YamlDeserializationException(
                    definition, "Specify exactly one operation or type: " + name + expertContext);
        }
        String operation = values.containsKey("operation")
                ? asText(values.get("operation"))
                : asText(values.get("type")).toLowerCase(Locale.ROOT);
        SemanticQuestionBuilder builder = new SemanticQuestionBuilder().operation(operation)
                .expert(asText(values.get("expert"))).state(asText(values.get("state")));
        if (values.containsKey("parameters")) {
            fields(values.get("parameters"), "evaluation parameters")
                    .forEach((key, value) -> builder.parameter(key, value(value)));
        }
        for (String field : List.of("instructions", "criteria", "threshold", "uncertainty", "uncertaintyPolicy")) {
            Node node = values.get(field);
            if (node == null) {
                continue;
            }
            switch (field) {
                case "instructions" -> builder.instructions(asText(node));
                case "threshold", "uncertainty" -> {
                    try {
                        builder.parameter(field, Double.valueOf(context.resolvePropertyPlaceholders(asText(node))));
                    } catch (NumberFormatException invalid) {
                        throw new YamlDeserializationException(
                                node,
                                "Invalid numeric value for '" + field + "' in " + description + expertContext);
                    }
                }
                case "uncertaintyPolicy" -> builder.uncertaintyPolicy(asText(node));
                case "criteria" -> {
                    if (node instanceof MappingNode) {
                        Map<String, String> criteria = new LinkedHashMap<>();
                        fields(node, "criteria").forEach((key, child) -> criteria.put(key, asText(child)));
                        builder.parameter(field, criteria);
                    } else if (node instanceof SequenceNode sequence) {
                        builder.parameter(field,
                                sequence.getValue().stream().map(SemanticDefinitionDeserializer::asText).toList());
                    } else {
                        builder.parameter(field, value(node));
                    }
                }
                default -> throw new IllegalStateException(field);
            }
        }
        return builder.build(context);
    }

    private static Object value(Node node) {
        if (node instanceof MappingNode) {
            Map<String, Object> result = new LinkedHashMap<>();
            fields(node, "parameter map").forEach((key, child) -> result.put(key, value(child)));
            return result;
        }
        if (node instanceof SequenceNode sequence) {
            return sequence.getValue().stream().map(SemanticDefinitionDeserializer::value).toList();
        }
        if (Tag.FLOAT.equals(node.getTag())) {
            try {
                return new BigDecimal(asText(node));
            } catch (NumberFormatException invalid) {
                throw new YamlDeserializationException(node, "Invalid numeric parameter");
            }
        }
        return new StandardConstructor(LoadSettings.builder().build()).constructSingleDocument(Optional.of(node));
    }

    private static Map<String, Node> fields(Node node, String description) {
        Map<String, Node> result = new LinkedHashMap<>();
        for (NodeTuple tuple : asMappingNode(node).getValue()) {
            String name = asText(tuple.getKeyNode());
            if (result.putIfAbsent(name, tuple.getValueNode()) != null) {
                throw new YamlDeserializationException(tuple.getKeyNode(), "Duplicate key '" + name + "' in " + description);
            }
        }
        return result;
    }

    @YamlType(properties = {
            @YamlProperty(name = "expert", type = "string"),
            @YamlProperty(name = "state", type = "string")
    })
    public static class BlockSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "question",
                          type = "map:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$EvaluationSchema",
                          required = true)
    })
    public static class QuestionBlockSchema extends BlockSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "evaluation",
                          type = "map:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$EvaluationSchema",
                          required = true)
    })
    public static class EvaluationBlockSchema extends BlockSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "__oneOf",
                          type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$MapCriteriaSchema",
                          oneOf = "criteria", required = true),
            @YamlProperty(name = "__oneOf",
                          type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$ListCriteriaSchema",
                          oneOf = "criteria", required = true)
    })
    public static class EvaluationSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "operation", type = "string"),
            @YamlProperty(name = "type", type = "string"),
            @YamlProperty(name = "expert", type = "string"),
            @YamlProperty(name = "state", type = "string"),
            @YamlProperty(name = "parameters", type = "object"),
            @YamlProperty(name = "instructions", type = "string"),
            @YamlProperty(name = "criteria", type = "map:string"),
            @YamlProperty(name = "threshold", type = "number"),
            @YamlProperty(name = "uncertainty", type = "number"),
            @YamlProperty(name = "uncertaintyPolicy", type = "string")
    })
    public static class MapCriteriaSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "criteria", type = "array:string", required = true)
    })
    public static class ListCriteriaSchema extends MapCriteriaSchema {
    }
}
