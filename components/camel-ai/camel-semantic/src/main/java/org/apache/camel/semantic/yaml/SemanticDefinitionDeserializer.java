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
import org.apache.camel.semantic.SemanticAuditConfiguration;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluationBuilder;
import org.apache.camel.semantic.SemanticEvaluations;
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
                      type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$EvaluationDeclarationSchema",
                      oneOf = "declaration", required = true),
        @YamlProperty(name = "__oneOf",
                      type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$AuditDeclarationSchema",
                      oneOf = "declaration", required = true),
        @YamlProperty(name = "__oneOf",
                      type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$AuditedEvaluationDeclarationSchema",
                      oneOf = "declaration", required = true)
})
public class SemanticDefinitionDeserializer extends YamlDeserializerSupport implements ConstructNode, YamlDeserializerResolver {
    private static final Tag NUMBER = new Tag("!number");
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
        Map<String, SemanticEvaluation> definitions = new LinkedHashMap<>();
        SemanticAuditConfiguration audit = null;
        for (Node node : sequence.getValue()) {
            if (!(node instanceof MappingNode mapping)) {
                // Leave malformed entries to the route loader without replacing the resource's evaluations.
                return;
            }
            for (NodeTuple tuple : mapping.getValue()) {
                if ("semantic".equals(asText(tuple.getKeyNode()))) {
                    Declaration declaration = read(dc.getCamelContext(), tuple.getValueNode());
                    if (declaration.audit != null) {
                        if (audit != null) {
                            throw new YamlDeserializationException(
                                    tuple.getValueNode(), "Only one audit declaration is allowed");
                        }
                        audit = declaration.audit;
                    }
                    declaration.definitions.forEach((name, evaluation) -> {
                        if (definitions.putIfAbsent(name, evaluation) != null) {
                            throw new YamlDeserializationException(
                                    tuple.getValueNode(), "Duplicate semantic evaluation: " + name);
                        }
                    });
                }
            }
        }
        CamelContext context = dc.getCamelContext();
        SemanticEvaluations evaluations = definitions.isEmpty() && audit == null
                ? context.getCamelContextExtension().getContextPlugin(SemanticEvaluations.class)
                : SemanticEvaluations.get(context);
        if (evaluations != null) {
            try {
                evaluations.replace(dc.getResource(), definitions, audit);
            } catch (IllegalArgumentException e) {
                throw new YamlDeserializationException(root, e.getMessage(), e);
            }
        }
    }

    private static Declaration read(CamelContext context, Node node) {
        Map<String, Node> semantic = fields(node, "semantic declaration");
        for (String field : semantic.keySet()) {
            if (!Set.of("evaluation", "expert", "state", "audit").contains(field)) {
                throw new YamlDeserializationException(
                        semantic.get(field), "Unknown property '" + field + "' in semantic declaration");
            }
        }
        if (!semantic.containsKey("evaluation") && !semantic.containsKey("audit")) {
            throw new YamlDeserializationException(node, "Semantic declaration requires evaluation or audit");
        }
        Map<String, SemanticEvaluation> result = new LinkedHashMap<>();
        if (semantic.containsKey("evaluation")) {
            fields(semantic.get("evaluation"), "semantic evaluations").forEach((name, definition) -> {
                if (name.isBlank()) {
                    throw new YamlDeserializationException(definition, "Semantic evaluation requires a nonblank name");
                }
                Map<String, Node> values = fields(definition, "semantic evaluation '" + name + "'");
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
                    result.put(name, readEvaluation(context, name, expert, definition, values));
                } catch (IllegalArgumentException | InvalidNodeTypeException e) {
                    throw new YamlDeserializationException(
                            definition, "Invalid semantic evaluation '" + name + "': " + e.getMessage()
                                        + " (expert '" + expert + "')",
                            e);
                }
            });
        }
        return new Declaration(result, semantic.containsKey("audit") ? audit(context, semantic.get("audit")) : null);
    }

    private record Declaration(Map<String, SemanticEvaluation> definitions, SemanticAuditConfiguration audit) {
    }

    private static SemanticAuditConfiguration audit(CamelContext context, Node node) {
        try {
            return readAudit(context, node);
        } catch (IllegalArgumentException e) {
            throw new YamlDeserializationException(node, "Invalid semantic audit configuration: " + e.getMessage(), e);
        }
    }

    private static SemanticAuditConfiguration readAudit(CamelContext context, Node node) {
        Map<String, Node> values = fields(node, "semantic audit");
        if (!Set.of("enabled", "experts", "sinks", "reader", "capacity", "queueCapacity").containsAll(values.keySet())) {
            throw new YamlDeserializationException(node, "Unknown semantic audit option");
        }
        Map<String, Boolean> experts = new LinkedHashMap<>();
        if (values.containsKey("experts")) {
            fields(values.get("experts"), "audit experts").forEach((name, expert) -> {
                Map<String, Node> settings = fields(expert, "audit expert");
                if (!settings.keySet().equals(Set.of("enabled"))) {
                    throw new YamlDeserializationException(expert, "Audit expert requires only enabled");
                }
                experts.put(name, auditBoolean(context, settings.get("enabled")));
            });
        }
        List<String> sinks = values.containsKey("sinks")
                ? asSequenceNode(values.get("sinks")).getValue().stream()
                        .map(n -> context.resolvePropertyPlaceholders(asText(n))).toList()
                : List.of("memory");
        return new SemanticAuditConfiguration(
                values.containsKey("enabled") && auditBoolean(context, values.get("enabled")),
                experts, sinks, auditText(context, values, "reader", "memory"),
                auditCapacity(context, values, "capacity"), auditCapacity(context, values, "queueCapacity"));
    }

    private static int auditCapacity(CamelContext context, Map<String, Node> values, String key) {
        Node node = values.get(key);
        if (node == null) {
            return 1000;
        }
        try {
            int value = Integer.parseInt(context.resolvePropertyPlaceholders(asText(node)));
            if (value >= 1 && value <= 100000) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Report the same bound and source location for malformed and out-of-range values.
        }
        throw new YamlDeserializationException(node, "Audit '" + key + "' requires an integer between 1 and 100000");
    }

    private static boolean auditBoolean(CamelContext context, Node node) {
        String value = context.resolvePropertyPlaceholders(asText(node));
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new YamlDeserializationException(node, "Audit enabled must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static String auditText(CamelContext context, Map<String, Node> values, String name, String fallback) {
        return values.containsKey(name) ? context.resolvePropertyPlaceholders(asText(values.get(name))) : fallback;
    }

    @YamlType(properties = {
            @YamlProperty(name = "expert", type = "string"),
            @YamlProperty(name = "state", type = "string")
    })
    public static class DeclarationSchema {
    }

    @YamlType(properties = @YamlProperty(name = "evaluation",
                                         type = "map:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$EvaluationSchema",
                                         required = true))
    public static class EvaluationDeclarationSchema extends DeclarationSchema {
    }

    @YamlType(properties = @YamlProperty(name = "audit",
                                         type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$AuditSchema",
                                         required = true))
    public static class AuditDeclarationSchema extends DeclarationSchema {
    }

    @YamlType(properties = @YamlProperty(name = "audit",
                                         type = "object:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$AuditSchema",
                                         required = true))
    public static class AuditedEvaluationDeclarationSchema extends EvaluationDeclarationSchema {
    }

    @YamlType(properties = {
            @YamlProperty(name = "enabled", type = "boolean"),
            @YamlProperty(name = "experts",
                          type = "map:org.apache.camel.semantic.yaml.SemanticDefinitionDeserializer$AuditExpertSchema"),
            @YamlProperty(name = "sinks", type = "array:string"),
            @YamlProperty(name = "reader", type = "string"),
            @YamlProperty(name = "capacity", type = "integer"),
            @YamlProperty(name = "queueCapacity", type = "integer")
    })
    public static class AuditSchema {
    }

    @YamlType(properties = { @YamlProperty(name = "enabled", type = "boolean", required = true) })
    public static class AuditExpertSchema {
    }

    private static SemanticEvaluation readEvaluation(
            CamelContext context, String name, String expert, Node definition, Map<String, Node> values) {
        String description = "semantic evaluation '" + name + "'";
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
        SemanticEvaluationBuilder builder = new SemanticEvaluationBuilder().operation(operation)
                .expert(asText(values.get("expert"))).state(asText(values.get("state")));
        if (values.containsKey("parameters")) {
            fields(values.get("parameters"), "evaluation parameters")
                    .forEach((key, value) -> builder.parameter(key, value(context, value)));
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
                        builder.parameter(field, value(context, node));
                    }
                }
                default -> throw new IllegalStateException(field);
            }
        }
        return builder.build(context);
    }

    private static Object value(CamelContext context, Node node) {
        if (node instanceof MappingNode) {
            Map<String, Object> result = new LinkedHashMap<>();
            fields(node, "parameter map").forEach((key, child) -> result.put(key, value(context, child)));
            return result;
        }
        if (node instanceof SequenceNode sequence) {
            return sequence.getValue().stream().map(child -> value(context, child)).toList();
        }
        if (Tag.FLOAT.equals(node.getTag()) || NUMBER.equals(node.getTag())) {
            try {
                return new BigDecimal(context.resolvePropertyPlaceholders(asText(node)));
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
