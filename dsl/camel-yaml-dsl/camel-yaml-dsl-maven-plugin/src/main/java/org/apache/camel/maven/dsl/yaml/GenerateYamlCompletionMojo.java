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
package org.apache.camel.maven.dsl.yaml;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Generates a YAML DSL completion tree JSON from the canonical schema and catalog metadata. The completion tree is a
 * generic, editor-agnostic data structure that maps every valid YAML DSL position to its children with types,
 * descriptions, required flags, defaults, and enum values.
 */
@Mojo(
      name = "generate-yaml-completion",
      inheritByDefault = false,
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true,
      requiresProject = false)
public class GenerateYamlCompletionMojo extends AbstractMojo {

    private static final String EXPRESSION_DEFINITION = "org.apache.camel.model.language.ExpressionDefinition";
    private static final String PROCESSOR_DEFINITION
            = "org.apache.camel.model.ProcessorDefinition";

    @Parameter(required = true)
    private File canonicalSchemaFile;

    @Parameter(required = true)
    private File outputFile;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private CamelCatalog catalog;

    // map from FQ definition name → short node name
    private final Map<String, String> defToNodeName = new HashMap<>();
    // map from short node name → FQ definition name
    private final Map<String, String> nodeNameToDef = new HashMap<>();

    @Override
    public void execute() throws MojoFailureException {
        if (!canonicalSchemaFile.exists()) {
            throw new MojoFailureException("Canonical schema not found: " + canonicalSchemaFile);
        }

        catalog = new DefaultCamelCatalog();

        try {
            JsonNode schema = mapper.readTree(canonicalSchemaFile);
            JsonNode items = schema.get("items");
            JsonNode definitions = items.get("definitions");
            JsonNode topLevelProps = items.get("properties");

            // build name mappings from definitions, using YAML property names where available
            buildNameMappings(definitions, topLevelProps);

            ObjectNode root = mapper.createObjectNode();
            ObjectNode nodes = mapper.createObjectNode();
            root.set("nodes", nodes);

            // collect which FQ definitions are step-capable (children of ProcessorDefinition)
            Set<String> stepDefinitions = new java.util.HashSet<>();
            JsonNode processorDef = definitions.get(PROCESSOR_DEFINITION);
            if (processorDef != null && processorDef.has("properties")) {
                processorDef.get("properties").fields().forEachRemaining(e -> {
                    String ref = resolveRefFq(e.getValue());
                    if (ref != null) {
                        stepDefinitions.add(ref);
                    }
                });
            }

            // collect which FQ definitions are top-level elements
            Set<String> topLevelDefinitions = new java.util.HashSet<>();
            topLevelProps.fields().forEachRemaining(e -> {
                String ref = resolveRefFq(e.getValue());
                if (ref != null) {
                    topLevelDefinitions.add(ref);
                }
            });

            // root node — top-level elements
            buildRootNode(nodes, topLevelProps);

            // steps node — valid EIP names inside steps: blocks
            buildStepsNode(nodes, definitions);

            // expression node — valid language names inside expression: blocks
            buildExpressionNode(nodes, definitions);

            // all other definition nodes (EIPs, languages, data formats, etc.)
            buildDefinitionNodes(nodes, definitions, stepDefinitions, topLevelDefinitions);

            // write output
            StringWriter sw = new StringWriter();
            mapper.writeValue(sw, root);
            FileUtil.updateFile(outputFile.toPath(), sw.toString());
            getLog().info("Generated YAML DSL completion tree: " + outputFile);

        } catch (IOException e) {
            throw new MojoFailureException("Failed to generate completion tree", e);
        }
    }

    private void buildNameMappings(JsonNode definitions, JsonNode topLevelProps) {
        // build a map from FQ definition name → YAML property name from the canonical schema
        Map<String, String> fqToYamlName = new HashMap<>();
        collectYamlPropertyNames(fqToYamlName, definitions.get(PROCESSOR_DEFINITION));
        if (topLevelProps != null) {
            topLevelProps.fields().forEachRemaining(e -> {
                String ref = resolveRefFq(e.getValue());
                if (ref != null) {
                    fqToYamlName.putIfAbsent(ref, e.getKey());
                }
            });
        }

        Iterator<String> fieldNames = definitions.fieldNames();
        while (fieldNames.hasNext()) {
            String fqName = fieldNames.next();
            // prefer the YAML property name from the canonical schema over the derived class name
            String shortName = fqToYamlName.getOrDefault(fqName, deriveShortName(fqName));
            defToNodeName.put(fqName, shortName);
            nodeNameToDef.put(shortName, fqName);
        }
    }

    private void collectYamlPropertyNames(Map<String, String> fqToYamlName, JsonNode definition) {
        if (definition == null || !definition.has("properties")) {
            return;
        }
        definition.get("properties").fields().forEachRemaining(e -> {
            String ref = resolveRefFq(e.getValue());
            if (ref != null) {
                fqToYamlName.putIfAbsent(ref, e.getKey());
            }
        });
    }

    private String deriveShortName(String fqName) {
        String className = fqName.substring(fqName.lastIndexOf('.') + 1);

        // special cases
        if (className.equals("ExpressionDefinition")) {
            return "expression";
        }
        if (className.equals("ProcessorDefinition")) {
            return "steps";
        }

        // strip common suffixes
        if (className.endsWith("Expression")) {
            String name = decapitalize(className.substring(0, className.length() - "Expression".length()));
            return ensureUnique(name, "lang");
        }
        if (className.endsWith("DataFormat")) {
            String name = decapitalize(className.substring(0, className.length() - "DataFormat".length()));
            return ensureUnique(name, "df");
        }
        if (className.endsWith("Definition")) {
            return decapitalize(className.substring(0, className.length() - "Definition".length()));
        }

        return decapitalize(className);
    }

    private String ensureUnique(String name, String suffix) {
        if (nodeNameToDef.containsKey(name)) {
            return name + "-" + suffix;
        }
        return name;
    }

    private void buildRootNode(ObjectNode nodes, JsonNode topLevelProps) {
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("title", "Root");
        rootNode.put("description", "Top-level YAML DSL elements");
        rootNode.put("listChildren", true);

        ArrayNode children = mapper.createArrayNode();
        Iterator<Map.Entry<String, JsonNode>> fields = topLevelProps.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode prop = entry.getValue();

            ObjectNode child = mapper.createObjectNode();
            child.put("name", name);
            child.put("type", "object");
            putIfPresent(child, "description", prop, "description");
            putIfPresent(child, "title", prop, "title");

            String ref = resolveRef(prop);
            if (ref != null) {
                child.put("ref", ref);
            }

            // enrich from catalog
            enrichChildFromCatalog(child, name);

            children.add(child);
        }
        rootNode.set("children", children);
        nodes.set("root", rootNode);
    }

    private void buildStepsNode(ObjectNode nodes, JsonNode definitions) {
        JsonNode processorDef = definitions.get(PROCESSOR_DEFINITION);
        if (processorDef == null) {
            return;
        }

        ObjectNode stepsNode = mapper.createObjectNode();
        stepsNode.put("title", "Steps");
        stepsNode.put("description", "Processing steps that can be used inside a route");
        stepsNode.put("listChildren", true);

        ArrayNode children = mapper.createArrayNode();
        JsonNode props = processorDef.get("properties");
        if (props != null) {
            // sort by name for consistent output
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            props.fields().forEachRemaining(entries::add);
            entries.sort(Comparator.comparing(Map.Entry::getKey));

            for (Map.Entry<String, JsonNode> entry : entries) {
                String name = entry.getKey();
                JsonNode prop = entry.getValue();

                ObjectNode child = mapper.createObjectNode();
                child.put("name", name);
                child.put("type", "object");
                putIfPresent(child, "description", prop, "description");
                putIfPresent(child, "title", prop, "title");

                String ref = resolveRef(prop);
                if (ref != null) {
                    child.put("ref", ref);
                }

                enrichChildFromCatalog(child, name);
                children.add(child);
            }
        }
        stepsNode.set("children", children);
        nodes.set("steps", stepsNode);
    }

    private void buildExpressionNode(ObjectNode nodes, JsonNode definitions) {
        JsonNode exprDef = definitions.get(EXPRESSION_DEFINITION);
        if (exprDef == null) {
            return;
        }

        ObjectNode exprNode = mapper.createObjectNode();
        exprNode.put("title", "Expression");
        exprNode.put("description", "Expression languages for evaluating expressions");
        exprNode.put("label", "language");

        ArrayNode children = mapper.createArrayNode();
        JsonNode props = exprDef.get("properties");
        if (props != null) {
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            props.fields().forEachRemaining(entries::add);
            entries.sort(Comparator.comparing(Map.Entry::getKey));

            for (Map.Entry<String, JsonNode> entry : entries) {
                String name = entry.getKey();
                JsonNode prop = entry.getValue();

                ObjectNode child = mapper.createObjectNode();
                child.put("name", name);
                child.put("type", "object");

                String ref = resolveRef(prop);
                if (ref != null) {
                    child.put("ref", ref);
                }

                // enrich from language catalog
                LanguageModel langModel = catalog.languageModel(name);
                if (langModel != null) {
                    child.put("title", langModel.getTitle());
                    child.put("description", langModel.getDescription());
                    if (langModel.getLabel() != null) {
                        child.put("label", langModel.getLabel());
                    }
                    if (langModel.isDeprecated()) {
                        child.put("deprecated", true);
                        if (langModel.getDeprecationNote() != null) {
                            child.put("deprecationNote", langModel.getDeprecationNote());
                        }
                    }
                }

                children.add(child);
            }
        }
        exprNode.set("children", children);
        nodes.set("expression", exprNode);
    }

    private void buildDefinitionNodes(
            ObjectNode nodes, JsonNode definitions,
            Set<String> stepDefinitions, Set<String> topLevelDefinitions) {
        Iterator<Map.Entry<String, JsonNode>> fields = definitions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fqName = entry.getKey();
            JsonNode def = entry.getValue();

            // skip already-handled special definitions
            if (PROCESSOR_DEFINITION.equals(fqName) || EXPRESSION_DEFINITION.equals(fqName)) {
                continue;
            }

            String nodeName = defToNodeName.get(fqName);
            if (nodeName == null) {
                continue;
            }

            // skip if already generated (via special handling)
            if (nodes.has(nodeName)) {
                continue;
            }

            ObjectNode node = mapper.createObjectNode();
            enrichNodeMetadata(node, nodeName, fqName, def);

            // mark nodes that appear as list items (in steps or at root level)
            if (stepDefinitions.contains(fqName) || topLevelDefinitions.contains(fqName)) {
                node.put("isListItem", true);
            }

            ArrayNode children = buildChildrenFromDefinition(def, fqName);
            node.set("children", children);

            nodes.set(nodeName, node);
        }
    }

    private void enrichNodeMetadata(ObjectNode node, String nodeName, String fqName, JsonNode def) {
        putIfPresent(node, "title", def, "title");
        putIfPresent(node, "description", def, "description");

        if (def.has("deprecated") && def.get("deprecated").asBoolean()) {
            node.put("deprecated", true);
        }

        // enrich from catalog
        EipModel eipModel = catalog.eipModel(nodeName);
        if (eipModel != null) {
            node.put("title", eipModel.getTitle());
            node.put("description", eipModel.getDescription());
            if (eipModel.getLabel() != null) {
                node.put("label", eipModel.getLabel());
            }
            if (eipModel.getAliases() != null && !eipModel.getAliases().isEmpty()) {
                ArrayNode aliases = mapper.createArrayNode();
                eipModel.getAliases().forEach(aliases::add);
                node.set("aliases", aliases);
            }
            if (eipModel.isInput()) {
                node.put("input", true);
            }
            if (eipModel.isOutput()) {
                node.put("output", true);
            }
            if (eipModel.isDeprecated()) {
                node.put("deprecated", true);
                if (eipModel.getDeprecationNote() != null) {
                    node.put("deprecationNote", eipModel.getDeprecationNote());
                }
            }
        }

        // check for language model
        LanguageModel langModel = catalog.languageModel(nodeName);
        if (langModel != null) {
            node.put("title", langModel.getTitle());
            node.put("description", langModel.getDescription());
            if (langModel.getLabel() != null) {
                node.put("label", langModel.getLabel());
            }
        }

        // check for data format model
        DataFormatModel dfModel = catalog.dataFormatModel(nodeName);
        if (dfModel != null) {
            node.put("title", dfModel.getTitle());
            node.put("description", dfModel.getDescription());
            if (dfModel.getLabel() != null) {
                node.put("label", dfModel.getLabel());
            }
        }
    }

    private ArrayNode buildChildrenFromDefinition(JsonNode def, String fqName) {
        // collect children with their index for sorting
        List<ObjectNode> childList = new ArrayList<>();
        JsonNode props = def.get("properties");
        if (props == null) {
            return mapper.createArrayNode();
        }

        // look up catalog model for enriched option metadata
        String nodeName = defToNodeName.get(fqName);
        Map<String, BaseOptionModel> catalogOptions = loadCatalogOptions(nodeName);

        Iterator<Map.Entry<String, JsonNode>> propFields = props.fields();
        while (propFields.hasNext()) {
            Map.Entry<String, JsonNode> propEntry = propFields.next();
            String propName = propEntry.getKey();
            JsonNode prop = propEntry.getValue();

            ObjectNode child = mapper.createObjectNode();
            child.put("name", propName);

            // determine type
            String ref = resolveRef(prop);
            if (ref != null) {
                // object reference
                if (prop.has("type") && "array".equals(prop.get("type").asText())) {
                    child.put("type", "array");
                } else {
                    child.put("type", "object");
                }
                child.put("ref", ref);
            } else if (prop.has("type")) {
                String type = prop.get("type").asText();
                if (prop.has("enum")) {
                    child.put("type", "enum");
                    ArrayNode enumValues = mapper.createArrayNode();
                    prop.get("enum").forEach(e -> enumValues.add(e.asText()));
                    child.set("enum", enumValues);
                } else {
                    child.put("type", type);
                }
            }

            putIfPresent(child, "description", prop, "description");
            putIfPresent(child, "title", prop, "title");

            if (prop.has("default")) {
                child.put("default", prop.get("default").asText());
            }

            // check required from schema
            if (def.has("required")) {
                for (JsonNode req : def.get("required")) {
                    if (propName.equals(req.asText())) {
                        child.put("required", true);
                        break;
                    }
                }
            }

            // enrich from catalog option metadata
            BaseOptionModel catalogOpt = catalogOptions.get(propName);
            if (catalogOpt != null) {
                child.put("displayName", catalogOpt.getDisplayName());
                child.put("kind", catalogOpt.getKind());
                child.put("index", catalogOpt.getIndex());
                if (catalogOpt.getGroup() != null) {
                    child.put("group", catalogOpt.getGroup());
                }
                if (catalogOpt.getLabel() != null && !catalogOpt.getLabel().isEmpty()) {
                    child.put("label", catalogOpt.getLabel());
                }
                if (catalogOpt.isRequired()) {
                    child.put("required", true);
                }
                if (catalogOpt.isSecret()) {
                    child.put("secret", true);
                }
                if (catalogOpt.isDeprecated()) {
                    child.put("deprecated", true);
                    if (catalogOpt.getDeprecationNote() != null) {
                        child.put("deprecationNote", catalogOpt.getDeprecationNote());
                    }
                }
                if (catalogOpt.getDefaultValue() != null) {
                    child.put("default", String.valueOf(catalogOpt.getDefaultValue()));
                }
                if (catalogOpt.getEnums() != null && !catalogOpt.getEnums().isEmpty()) {
                    child.put("type", "enum");
                    ArrayNode enumValues = mapper.createArrayNode();
                    catalogOpt.getEnums().forEach(enumValues::add);
                    child.set("enum", enumValues);
                }
            }

            childList.add(child);
        }

        // sort by index (if available), then by name
        childList.sort(Comparator
                .<ObjectNode, Integer> comparing(n -> n.has("index") ? n.get("index").asInt() : Integer.MAX_VALUE)
                .thenComparing(n -> n.get("name").asText()));

        ArrayNode children = mapper.createArrayNode();
        childList.forEach(children::add);
        return children;
    }

    private Map<String, BaseOptionModel> loadCatalogOptions(String nodeName) {
        Map<String, BaseOptionModel> optMap = new LinkedHashMap<>();
        if (nodeName == null) {
            return optMap;
        }

        // try EIP model
        EipModel eipModel = catalog.eipModel(nodeName);
        if (eipModel != null) {
            for (EipModel.EipOptionModel opt : eipModel.getOptions()) {
                optMap.put(opt.getName(), opt);
            }
            return optMap;
        }

        // try language model
        LanguageModel langModel = catalog.languageModel(nodeName);
        if (langModel != null) {
            for (LanguageModel.LanguageOptionModel opt : langModel.getOptions()) {
                optMap.put(opt.getName(), opt);
            }
            return optMap;
        }

        // try data format model
        DataFormatModel dfModel = catalog.dataFormatModel(nodeName);
        if (dfModel != null) {
            for (DataFormatModel.DataFormatOptionModel opt : dfModel.getOptions()) {
                optMap.put(opt.getName(), opt);
            }
            return optMap;
        }

        return optMap;
    }

    private String resolveRefFq(JsonNode prop) {
        if (prop.has("$ref")) {
            return prop.get("$ref").asText().replace("#/items/definitions/", "");
        }
        if (prop.has("items") && prop.get("items").has("$ref")) {
            return prop.get("items").get("$ref").asText().replace("#/items/definitions/", "");
        }
        return null;
    }

    private String resolveRef(JsonNode prop) {
        // direct $ref
        if (prop.has("$ref")) {
            String refPath = prop.get("$ref").asText();
            String fqRef = refPath.replace("#/items/definitions/", "");
            return defToNodeName.get(fqRef);
        }
        // array items $ref
        if (prop.has("items") && prop.get("items").has("$ref")) {
            String refPath = prop.get("items").get("$ref").asText();
            String fqRef = refPath.replace("#/items/definitions/", "");
            return defToNodeName.get(fqRef);
        }
        return null;
    }

    private void enrichChildFromCatalog(ObjectNode child, String name) {
        EipModel eipModel = catalog.eipModel(name);
        if (eipModel != null) {
            child.put("title", eipModel.getTitle());
            child.put("description", eipModel.getDescription());
            if (eipModel.getLabel() != null) {
                child.put("label", eipModel.getLabel());
            }
            if (eipModel.isDeprecated()) {
                child.put("deprecated", true);
            }
        }
    }

    private void putIfPresent(ObjectNode target, String targetField, JsonNode source, String sourceField) {
        if (source.has(sourceField)) {
            target.put(targetField, source.get(sourceField).asText());
        }
    }

    private static String decapitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
