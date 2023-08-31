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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.maven.dsl.yaml.support.ToolingSupport;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.StringUtils;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

@Mojo(
      name = "generate-yaml-schema",
      inheritByDefault = false,
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true,
      requiresProject = false)
public class GenerateYamlSchemaMojo extends GenerateYamlSupportMojo {
    @Parameter(required = true)
    private File outputFile;
    @Parameter(defaultValue = "true")
    private boolean kebabCase = true;
    @Parameter(defaultValue = "true")
    private boolean additionalProperties = true;

    private ObjectNode items;
    private ObjectNode definitions;
    private ObjectNode step;

    @Override
    protected void generate() throws MojoFailureException {
        final ObjectMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();

        final ObjectNode root = mapper.createObjectNode();

        root.put("$schema", "http://json-schema.org/draft-04/schema#");
        root.put("type", "array");

        items = root.putObject("items");
        items.put("maxProperties", 1);

        definitions = items.putObject("definitions");
        step = definitions.withObject("/org.apache.camel.model.ProcessorDefinition")
                .put("type", "object")
                .put("maxProperties", 1);
        if (!additionalProperties) {
            step.put("additionalProperties", false);
        }

        Map<String, ClassInfo> types = new TreeMap<>();

        annotated(YAML_TYPE_ANNOTATION)
                .sorted(Comparator.comparingInt(GenerateYamlSupportMojo::getYamlTypeOrder))
                .forEach(ci -> {
                    annotationValue(ci, YAML_TYPE_ANNOTATION, "types")
                            .map(AnnotationValue::asStringArray)
                            .ifPresent(values -> Stream.of(values).forEach(item -> types.putIfAbsent(item, ci)));

                    if (!hasAnnotationValue(ci, YAML_TYPE_ANNOTATION, "types")) {
                        types.putIfAbsent(ci.name().toString(), ci);
                    }
                });

        Set<String> inheritedDefinitions = new HashSet<>();
        Set<String> inlineDefinitions = new HashSet<>();
        for (Map.Entry<String, ClassInfo> entry : types.entrySet()) {
            Set<String> nodes = annotationValue(entry.getValue(), YAML_TYPE_ANNOTATION, "nodes")
                    .map(AnnotationValue::asStringArray)
                    .stream()
                    .flatMap(Stream::of)
                    .collect(Collectors.toCollection(TreeSet::new));

            final DotName name = DotName.createSimple(entry.getKey());
            final ClassInfo info = view.getClassByName(name);
            if (isBanned(info)) {
                continue;
            }
            if (hasAnnotation(entry.getValue(), YAML_IN_ANNOTATION)) {
                nodes.forEach(node -> {
                    items.withObject("/properties")
                            .putObject(node)
                            .put("$ref", "#/items/definitions/" + entry.getKey());
                });
            } else {
                if (extendsType(info, PROCESSOR_DEFINITION_CLASS)) {
                    nodes.forEach(node -> {
                        step.withObject("/properties")
                                .putObject(node)
                                .put("$ref", "#/items/definitions/" + entry.getKey());
                    });
                }
            }

            generate(entry.getKey(), entry.getValue(), inheritedDefinitions, inlineDefinitions);
        }

        // filter out unwanted cases when in camelCase mode
        if (!kebabCase) {
            for (JsonNode definition : definitions) {
                kebabToCamelCase(definition);
            }
            kebabToCamelCase(step);
            kebabToCamelCase(root.withObject("/items"));
        }

        if (!inheritedDefinitions.isEmpty()) {
            postProcessInheritance(inheritedDefinitions, inlineDefinitions);
        }

        try {
            ToolingSupport.mkparents(outputFile);

            StringWriter sw = new StringWriter();
            mapper.writerWithDefaultPrettyPrinter().writeValue(sw, root);
            FileUtil.updateFile(outputFile.toPath(), sw.toString());
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void generate(String type, ClassInfo info, Set<String> inheritedDefinitions, Set<String> inlineDefinitions) {
        final ObjectNode definition = definitions.withObject("/" + type);
        final List<AnnotationInstance> properties = new ArrayList<>();

        annotationValue(info, YAML_TYPE_ANNOTATION, "displayName").map(AnnotationValue::asString).ifPresent(v -> {
            definition.put("title", v);
        });
        annotationValue(info, YAML_TYPE_ANNOTATION, "description").map(AnnotationValue::asString).ifPresent(v -> {
            definition.put("description", v);
        });
        annotationValue(info, YAML_TYPE_ANNOTATION, "deprecated").map(AnnotationValue::asBoolean).ifPresent(v -> {
            if (v) {
                definition.put("deprecated", true);
            }
        });

        ObjectNode objectDefinition = definition;

        if (annotationValue(info, YAML_TYPE_ANNOTATION, "inline").map(AnnotationValue::asBoolean).orElse(false)) {
            ArrayNode oneOf = definition.withArray("oneOf");
            oneOf.addObject().put("type", "string");

            objectDefinition = oneOf.addObject();
            inlineDefinitions.add(type);
        }

        objectDefinition.put("type", "object");
        if (!additionalProperties) {
            objectDefinition.put("additionalProperties", false);
        }

        collectYamlProperties(properties, info);

        properties.sort(
                Comparator.comparing(property -> annotationValue(property, "name").map(AnnotationValue::asString).orElse("")));

        Map<String, ObjectNode> oneOfGroups = new HashMap<>();
        for (AnnotationInstance property : properties) {
            final String propertyName = annotationValue(property, "name")
                    .map(AnnotationValue::asString)
                    .orElse("");
            final String propertyType = annotationValue(property, "type")
                    .map(AnnotationValue::asString)
                    .orElse("");
            final String propertyDescription = annotationValue(property, "description")
                    .map(AnnotationValue::asString)
                    .orElse("");
            final String propertyDisplayName = annotationValue(property, "displayName")
                    .map(AnnotationValue::asString)
                    .orElse("");
            boolean propertyRequired = annotationValue(property, "required")
                    .map(AnnotationValue::asBoolean)
                    .orElse(false);
            final boolean propertyDeprecated = annotationValue(property, "deprecated")
                    .map(AnnotationValue::asBoolean)
                    .orElse(false);
            final String propertyDefaultValue = annotationValue(property, "defaultValue")
                    .map(AnnotationValue::asString)
                    .orElse("");
            final String propertyFormat = annotationValue(property, "format")
                    .map(AnnotationValue::asString)
                    .orElse("");
            final String propertyOneOf = annotationValue(property, "oneOf")
                    .map(AnnotationValue::asString)
                    .orElse("");

            boolean isInOneOf = !StringUtils.isEmpty(propertyOneOf);
            if (isInOneOf) {
                if (!oneOfGroups.containsKey(propertyOneOf)) {
                    var oneOfGroup = objectDefinition.withArray("anyOf").addObject();
                    oneOfGroups.put(propertyOneOf, oneOfGroup);
                }
            }

            //
            // Internal properties
            //
            if (propertyName.equals("__extends") && propertyType.startsWith("object:")) {
                String objectRef = StringHelper.after(propertyType, ":");
                if (isInOneOf) {
                    var oneOf = oneOfGroups.get(propertyOneOf).withArray("oneOf");
                    var entry = oneOf.addObject();
                    entry.put("$ref", "#/items/definitions/" + objectRef);
                    if (!propertyRequired) {
                        makeOptional(oneOf, entry);
                    }
                } else {
                    objectDefinition.put("$ref", "#/items/definitions/" + objectRef);
                }
                inheritedDefinitions.add(objectRef);
                continue;
            }
            if (propertyName.equals("__extends") && propertyType.startsWith("array:")) {
                String objectRef = StringHelper.after(propertyType, ":");
                objectDefinition
                        .put("type", "array")
                        .withObject("/items")
                        .put("$ref", "#/items/definitions/" + objectRef);

                continue;
            }
            if (propertyName.startsWith("__")) {
                // this is an internal name
                continue;
            }

            var finalObjectDefinition = objectDefinition;
            if (isInOneOf) {
                var oneOf = oneOfGroups.get(propertyOneOf).withArray("oneOf");
                var entry = oneOf.addObject();
                entry.put("type", "object");
                entry.withArray("required").add(propertyName);
                if (!propertyRequired) {
                    makeOptional(oneOf, entry);
                }
                finalObjectDefinition = entry;
                propertyRequired = false;
            }

            setProperty(
                    finalObjectDefinition,
                    propertyName,
                    propertyType,
                    propertyDescription,
                    propertyDisplayName,
                    propertyDefaultValue,
                    propertyFormat,
                    propertyDeprecated);

            if (propertyRequired) {
                String name = kebabCase ? propertyName : StringHelper.dashToCamelCase(propertyName);
                definition.withArray("required").add(name);
            }
        }
    }

    private void kebabToCamelCase(JsonNode node) {
        if (node.has("not")) {
            node = node.withObject("/not");
        }
        var composition = extractComposition(node);
        if (composition != null) {
            composition.forEach(this::kebabToCamelCase);
        }
        if (node.has("properties")) {
            ObjectNode props = node.withObject("/properties");
            ArrayNode required = null;
            if (node.has("required")) {
                required = node.withArray("required");
            }
            Map<String, JsonNode> rebuild = new LinkedHashMap<>();
            // the properties are in mixed kebab-case and camelCase
            for (Iterator<String> it = props.fieldNames(); it.hasNext();) {
                String n = it.next();
                String t = StringHelper.dashToCamelCase(n);
                JsonNode prop = props.get(n);
                rebuild.put(t, prop);
                if (required != null) {
                    for (int i = 0; i < required.size(); i++) {
                        String r = required.get(i).asText();
                        if (r.equals(n)) {
                            required.set(i, t);
                        }
                    }
                }
            }
            if (!rebuild.isEmpty()) {
                props.removeAll();
                rebuild.forEach(props::set);
            }
        }
    }

    private void setProperty(
            ObjectNode objectDefinition,
            String propertyName,
            String propertyType,
            String propertyDescription,
            String propertyDisplayName,
            String propertyDefaultValue,
            String propertyFormat,
            boolean deprecated) {

        final ObjectNode current = objectDefinition.withObject("/properties/" + propertyName);
        current.put("type", propertyType);

        if (!Strings.isNullOrEmpty(propertyDisplayName)) {
            current.put("title", propertyDisplayName);
        }
        if (!Strings.isNullOrEmpty(propertyDescription)) {
            current.put("description", propertyDescription);
        }
        if (deprecated) {
            current.put("deprecated", true);
        }

        if (!Strings.isNullOrEmpty(propertyDefaultValue)) {
            current.put("default", propertyDefaultValue);
        }
        if (!Strings.isNullOrEmpty(propertyFormat)) {
            current.put("format", propertyFormat);
        }

        if (propertyType.startsWith("object:")) {
            current.remove("type");

            String objectType = StringHelper.after(propertyType, ":");
            current.put("$ref", "#/items/definitions/" + objectType);

        } else if (propertyType.startsWith("array:")) {

            current.put("type", "array");

            String arrayType = StringHelper.after(propertyType, ":");
            if (arrayType.contains(".")) {
                current.withObject("/items").put("$ref", "#/items/definitions/" + arrayType);
            } else {
                current.withObject("/items").put("type", arrayType);
            }
        } else if (propertyType.startsWith("enum:")) {

            current.put("type", "string");

            String enumValues = StringHelper.after(propertyType, ":");
            for (String enumValue : enumValues.split(",")) {
                current.withArray("enum").add(enumValue);
            }
        }
    }

    private void collectYamlProperties(List<AnnotationInstance> annotations, ClassInfo info) {
        if (info == null) {
            return;
        }

        annotationValue(info, YAML_TYPE_ANNOTATION, "properties")
                .map(AnnotationValue::asNestedArray)
                .stream()
                .flatMap(Stream::of)
                .forEach(property -> {
                    final String propertyName = annotationValue(property, "name").map(AnnotationValue::asString).orElse("");
                    final String propertyType = annotationValue(property, "type").map(AnnotationValue::asString).orElse("");

                    if (ObjectHelper.isEmpty(propertyName) || ObjectHelper.isEmpty(propertyType)) {
                        getLog().warn(
                                "Missing name or type for property + " + property + " on type " + info.name().toString());
                        return;
                    }

                    if (propertyType.startsWith("object:")) {
                        final DotName dn = DotName.createSimple(propertyType.substring(7));
                        if (isBanned(view.getClassByName(dn))) {
                            return;
                        }
                    }

                    if (propertyName.startsWith("__")) {
                        // reserved property, add it
                        annotations.add(property);
                    } else {
                        boolean matches = annotations.stream()
                                .map(p -> annotationValue(p, "name").map(AnnotationValue::asString).orElse(""))
                                .anyMatch(propertyName::equals);

                        if (matches) {
                            // duplicate
                            return;
                        }

                        annotations.add(property);
                    }

                    DotName superName = info.superName();
                    if (superName != null) {
                        collectYamlProperties(
                                annotations,
                                view.getClassByName(superName));
                    }
                });
    }

    private void makeOptional(ArrayNode parent, ObjectNode target) {
        ObjectNode negations = StreamSupport.stream(parent.spliterator(), false)
                .map(ObjectNode.class::cast)
                .filter(entry -> entry.has("not"))
                .findAny()
                .orElseGet(parent::addObject);
        negations.withObject("/not");
        if (target.has("required")) {
            extractRequiredFromComposition(negations, target);
            var required = target.withArray("required");
            negations.withObject("/not")
                    .withArray("anyOf")
                    .addObject()
                    .withArray("required")
                    .addAll(required);
        } else if (target.has("$ref")) {
            // At this point, referred object might not yet be processed.
            // Just copy the $ref and let postProcessInheritance() handle it.
            negations.withObject("/not")
                    .withArray("anyOf")
                    .addObject()
                    .set("$ref", target.get("$ref"));
        }
    }

    private void extractRequiredFromComposition(ObjectNode negations, ObjectNode object) {
        ArrayNode composition = extractComposition(object);
        if (composition == null) {
            return;
        }

        composition.forEach(compositionEntry -> {
            if (!compositionEntry.has("$ref")) {
                String parentName = StringHelper.after(compositionEntry.get("$ref").asText(), "/definitions/");
                ObjectNode referredObject = definitions.withObject("/" + parentName);
                extractRequiredFromComposition(negations, referredObject);
                if (referredObject.has("required")) {
                    negations.withObject("/not")
                            .withArray("anyOf")
                            .addObject()
                            .withArray("required")
                            .addAll((ArrayNode) referredObject.withArray("required"));
                }
                return;
            }
            if (compositionEntry.has("required")) {
                negations.withObject("/not")
                        .withArray("anyOf")
                        .addObject()
                        .withArray("required")
                        .addAll((ArrayNode) compositionEntry.withArray("required"));
            }
        });
    }

    /**
     * Post-process the definitions to handle inheritance with schema composition. When a definition has "$ref" to refer
     * other definition, it's possible that the referred definition is not yet generated in the {@link #generate()} main
     * process, therefore some jobs are left unprocessed. This method handles the rest of the jobs for such cases.
     *
     * @param inheritedDefinitions The name of the definitions that are inherited by other definitions
     * @param inlineDefinitions    The name of the definitions that has inline option enabled
     */
    private void postProcessInheritance(Set<String> inheritedDefinitions, Set<String> inlineDefinitions) {
        // additionalProperties=false prevents inheritance with allOf/anyOf/oneOf
        // Remove it to be true by default
        for (String inherited : inheritedDefinitions) {
            ObjectNode node = definitions.withObject("/" + inherited);
            node.remove("additionalProperties");
        }

        // Redeclare the inherited properties
        // https://json-schema.org/understanding-json-schema/reference/object.html#extending-closed-schemas
        // TODO Consider using unevaluatedProperties instead once we update to draft-2019-09 or later
        // https://json-schema.org/understanding-json-schema/reference/object.html#unevaluated-properties
        definitions.properties().forEach(entry -> {
            String name = entry.getKey();
            JsonNode temp = entry.getValue();
            if (inlineDefinitions.contains(name)) {
                temp = temp.withArray("oneOf").get(1);
            }
            if (temp.has("anyOf")) {
                final var definition = temp;
                StreamSupport.stream(temp.withArray("anyOf").spliterator(), false)
                        .forEach(group -> postProcessComposition(name, definition, group, inheritedDefinitions));
            } else {
                postProcessComposition(name, temp, temp, inheritedDefinitions);
            }
        });
    }

    private ArrayNode extractComposition(JsonNode target) {
        if (target.has("allOf")) {
            return target.withArray("allOf");
        } else if (target.has("oneOf")) {
            return target.withArray("oneOf");
        } else if (target.has("anyOf")) {
            return target.withArray("anyOf");
        } else {
            return null;
        }
    }

    private void postProcessComposition(
            String name, JsonNode definition, JsonNode inherited, Set<String> inheritedDefinitions) {
        ArrayNode composition = extractComposition(inherited);
        if (composition == null) {
            return;
        }

        int indexToRemove = -1;
        for (int i = 0; i < composition.size(); i++) {
            var compositionEntry = composition.get(i);
            if (compositionEntry.has("not")) {
                if (inheritedDefinitions.contains(name)) {
                    indexToRemove = i;
                    continue;
                }
                postProcessNot(compositionEntry.withObject("/not"));
                continue;
            }
            if (compositionEntry.has("properties")) {
                compositionEntry.withObject("/properties")
                        .properties()
                        .stream()
                        .filter(prop -> !definition.withObject("/properties").has(prop.getKey()))
                        .forEach(prop -> definition.withObject("/properties").putObject(prop.getKey()));
            }
            postProcessComposition(name, definition, compositionEntry, inheritedDefinitions);

            if (!compositionEntry.has("$ref")) {
                continue;
            }
            String parentName = StringHelper.after(compositionEntry.get("$ref").asText(), "/definitions/");
            if (!inheritedDefinitions.contains(parentName)) {
                continue;
            }
            JsonNode parent = definitions.withObject("/" + parentName);
            postProcessComposition(parentName, definition, parent, inheritedDefinitions);
            parent
                    .withObject("/properties")
                    .properties()
                    .stream()
                    .filter(prop -> !definition.withObject("/properties").has(prop.getKey()))
                    .forEach(prop -> definition.withObject("/properties").putObject(prop.getKey()));
        }
        if (indexToRemove >= 0) {
            composition.remove(indexToRemove);
        }
    }

    private void postProcessNot(JsonNode notEntry) {
        ArrayNode notAnyOf = notEntry.withArray("anyOf");
        List<ArrayNode> extractedRequired = new ArrayList<>();
        StreamSupport.stream(notAnyOf.spliterator(), false)
                .filter(n -> n.has("$ref"))
                .forEach(n -> postProcessNotRef(extractedRequired, n));
        var processed = ((ObjectNode) notEntry).putArray("anyOf");
        StreamSupport.stream(notAnyOf.spliterator(), false)
                .filter(n -> !n.has("$ref"))
                .forEach(processed::add);
        extractedRequired.forEach(required -> processed
                .addObject()
                .withArray("required")
                .addAll(required));
    }

    private void postProcessNotRef(List<ArrayNode> extracted, JsonNode objectWithRef) {
        String parentName = StringHelper.after(objectWithRef.get("$ref").asText(), "/definitions/");
        ObjectNode referredObject = definitions.withObject("/" + parentName);
        postProcessNotRefComposition(extracted, referredObject);
    }

    private void postProcessNotRefComposition(List<ArrayNode> extracted, ObjectNode node) {
        if (node.has("$ref")) {
            postProcessNotRef(extracted, node);
            return;
        }
        if (node.has("required")) {
            extracted.add(node.withArray("required"));
            return;
        }
        var composition = extractComposition(node);
        if (composition != null) {
            StreamSupport.stream(composition.spliterator(), false)
                    .map(ObjectNode.class::cast)
                    .forEach(entry -> {
                        if (entry.has("$ref")) {
                            postProcessNotRef(extracted, entry);
                        } else if (entry.has("required")) {
                            extracted.add(entry.withArray("required"));
                        } else {
                            postProcessNotRefComposition(extracted, entry);
                        }
                    });
        }
    }
}
