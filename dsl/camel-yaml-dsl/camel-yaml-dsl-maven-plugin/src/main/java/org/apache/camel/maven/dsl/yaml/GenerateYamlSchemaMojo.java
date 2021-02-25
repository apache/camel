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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.maven.dsl.yaml.support.ToolingSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
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
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/camel-yaml-dsl.json")
    private File outputFile;

    private ObjectNode items;
    private ObjectNode definitions;
    private ObjectNode step;

    @Override
    protected void generate() throws MojoFailureException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode root = mapper.createObjectNode();

        root.put("$schema", "http://json-schema.org/draft-04/schema#");
        root.put("type", "array");

        items = root.putObject("items");
        items.put("maxProperties", 1);

        definitions = items.putObject("definitions");
        step = definitions.with("org.apache.camel.model.ProcessorDefinition")
            .put("type", "object")
            .put("maxProperties", 1);

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

        for (Map.Entry<String, ClassInfo> entry : types.entrySet()) {
            Set<String> nodes = annotationValue(entry.getValue(), YAML_TYPE_ANNOTATION, "nodes")
                    .map(AnnotationValue::asStringArray)
                    .map(values -> Stream.of(values).collect(Collectors.toCollection(TreeSet::new)))
                    .orElseGet(TreeSet::new);

            if (hasAnnotation(entry.getValue(), YAML_IN_ANNOTATION)) {
                nodes.forEach(node -> {
                    items.with("properties")
                        .putObject(node)
                        .put("$ref", "#/items/definitions/" + entry.getKey());
                });
            } else {
                final DotName name = DotName.createSimple(entry.getKey());
                final ClassInfo info = view.getClassByName(name);

                if (extendsType(info, PROCESSOR_DEFINITION_CLASS)) {
                    nodes.forEach(node -> {
                        step.with("properties")
                            .putObject(node)
                            .put("$ref", "#/items/definitions/" + entry.getKey());
                    });
                }
            }

            generate(entry.getKey(), entry.getValue());
        }

        try {
            ToolingSupport.mkparents(outputFile);

            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, root);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void generate(String type, ClassInfo info) {
        final ObjectNode definition = definitions.with(type);
        final List<AnnotationInstance> properties = new ArrayList<>();

        ObjectNode objectDefinition = definition;

        if (annotationValue(info, YAML_TYPE_ANNOTATION, "inline").map(AnnotationValue::asBoolean).orElse(false)) {
            ArrayNode anyOf = definition.withArray("oneOf");
            anyOf.addObject().put("type", "string");

            objectDefinition = anyOf.addObject();
        }

        objectDefinition.put("type", "object");

        collectYamlProperties(properties, info);

        properties.sort(
                Comparator.comparing(property -> annotationValue(property, "name").map(AnnotationValue::asString).orElse("")));

        for (AnnotationInstance property : properties) {
            final String propertyName = annotationValue(property, "name").map(AnnotationValue::asString).orElse("");
            final String propertyType = annotationValue(property, "type").map(AnnotationValue::asString).orElse("");
            final boolean propertyRequired
                    = annotationValue(property, "required").map(AnnotationValue::asBoolean).orElse(false);

            //
            // Internal properties
            //
            if (propertyName.equals("__extends") && propertyType.startsWith("object:")) {
                String objectRef = StringHelper.after(propertyType, ":");
                definition
                        .withArray("anyOf")
                        .addObject()
                        .put("$ref", "#/items/definitions/" + objectRef);

                continue;
            }
            if (propertyName.startsWith("__")) {
                // this is an internal name
                continue;
            }

            //
            // Type properties
            //
            if (propertyType.startsWith("object:")) {
                String objectType = StringHelper.after(propertyType, ":");
                objectDefinition
                        .with("properties")
                        .with(propertyName)
                        .put("$ref", "#/items/definitions/" + objectType);
            } else if (propertyType.startsWith("array:")) {
                String arrayType = StringHelper.after(propertyType, ":");
                if (arrayType.contains(".")) {
                    objectDefinition
                            .with("properties")
                            .with(propertyName)
                            .put("type", "array")
                            .with("items").put("$ref", "#/items/definitions/" + arrayType);
                } else {
                    objectDefinition
                        .with("properties")
                        .with(propertyName)
                        .put("type", "array")
                        .with("items").put("type", arrayType);
                }
            } else if (propertyType.startsWith("enum:")) {
                objectDefinition
                        .with("properties")
                        .with(propertyName)
                        .put("type", "string");

                String enumValues = StringHelper.after(propertyType, ":");
                for (String enumValue : enumValues.split(",")) {
                    objectDefinition
                            .with("properties")
                            .with(propertyName)
                            .withArray("enum")
                            .add(enumValue);
                }
            } else {
                objectDefinition
                        .with("properties")
                        .with(propertyName)
                        .put("type", propertyType);
            }

            if (propertyRequired) {
                definition.withArray("required").add(propertyName);
            }
        }
    }

    private void collectYamlProperties(List<AnnotationInstance> annotations, ClassInfo info) {
        if (info == null) {
            return;
        }

        annotationValue(info, YAML_TYPE_ANNOTATION, "properties")
                .map(AnnotationValue::asNestedArray)
                .ifPresent(properties -> {
                    for (AnnotationInstance property : properties) {
                        final String propertyName = annotationValue(property, "name").map(AnnotationValue::asString).orElse("");
                        final String propertyType = annotationValue(property, "type").map(AnnotationValue::asString).orElse("");

                        if (ObjectHelper.isEmpty(propertyName) || ObjectHelper.isEmpty(propertyType)) {
                            getLog().warn(
                                    "Missing name or type for property + " + property + " on type " + info.name().toString());
                            return;
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
                            } else {
                                annotations.add(property);
                            }
                        }

                        DotName superName = info.superName();
                        if (superName != null) {
                            collectYamlProperties(
                                    annotations,
                                    view.getClassByName(superName));
                        }
                    }
                });
    }
}
