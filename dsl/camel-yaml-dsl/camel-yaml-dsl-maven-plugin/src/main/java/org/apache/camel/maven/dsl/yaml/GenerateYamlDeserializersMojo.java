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
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import javax.lang.model.element.Modifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.Kind;
import org.apache.camel.maven.dsl.yaml.support.Schema;
import org.apache.camel.maven.dsl.yaml.support.TypeSpecHolder;
import org.apache.camel.maven.dsl.yaml.support.YamlProperties;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;

@Mojo(
      name = "generate-yaml-deserializers",
      inheritByDefault = false,
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class GenerateYamlDeserializersMojo extends GenerateYamlSupportMojo {
    @Parameter(defaultValue = "org.apache.camel.dsl.yaml.deserializers")
    protected String packageName;
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Schema> schemes = new HashMap<>();

    @Override
    protected void generate() throws MojoFailureException {
        try {
            for (String name : CATALOG.findDataFormatNames()) {
                loadScheme(schemes, CATALOG.dataFormatJSonSchema(name));
            }
            for (String name : CATALOG.findLanguageNames()) {
                loadScheme(schemes, CATALOG.languageJSonSchema(name));
            }
            for (String name : CATALOG.findOtherNames()) {
                loadScheme(schemes, CATALOG.otherJSonSchema(name));
            }
            for (String name : CATALOG.findModelNames()) {
                loadScheme(schemes, CATALOG.modelJSonSchema(name));
            }

            write(generateExpressionDeserializers());
            write(generateDeserializers());
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void loadScheme(Map<String, Schema> schemes, String s) throws Exception {
        if (s == null) {
            return;
        }

        Schema descriptor = new Schema(MAPPER.createObjectNode(), MAPPER.createObjectNode());
        Schema schema = MAPPER.readerForUpdating(descriptor).readValue(s);
        JsonNode type = schema.meta.at("/javaType");
        if (!type.isMissingNode() && type.isTextual()) {
            schemes.put(type.asText(), schema);
        }
    }

    private void write(TypeSpec... specs) throws Exception {
        write(Arrays.asList(specs));
    }

    private void write(Collection<TypeSpec> specs) throws Exception {
        for (TypeSpec typeSpec : specs) {
            StringWriter sw = new StringWriter();
            JavaFile.builder(packageName, typeSpec)
                    .addFileComment("Generated by camel-yaml-dsl-maven-plugin - do NOT edit this file!")
                    .indent("    ")
                    .build()
                    .writeTo(sw);
            Path outputDirectory = sourcesOutputDir.toPath();
            if (!packageName.isEmpty()) {
                for (String packageComponent : packageName.split("\\.")) {
                    outputDirectory = outputDirectory.resolve(packageComponent);
                }
                Files.createDirectories(outputDirectory);
            }
            FileUtil.updateFile(outputDirectory.resolve(typeSpec.name + ".java"), sw.toString());
        }
    }

    private TypeSpec generateExpressionDeserializers() {
        TypeSpec.Builder type = TypeSpec.classBuilder("ExpressionDeserializers");
        type.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        type.superclass(CN_DESERIALIZER_SUPPORT);

        // PMD suppression
        AnnotationSpec.Builder suppress = AnnotationSpec.builder(SuppressWarnings.class);
        suppress.addMember("value", "$L", "\"PMD.UnnecessaryFullyQualifiedName\"");
        type.addAnnotation(suppress.build());

        // add private constructor
        type.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        // parser
        type.addMethod(MethodSpec.methodBuilder("constructExpressionType")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Node.class, "node")
                .returns(CN_EXPRESSION_DEFINITION)
                .addCode(
                        CodeBlock.builder()
                                .addStatement("$T mn = asMappingNode(node)", CN_MAPPING_NODE)
                                .beginControlFlow("if (mn.getValue().size() != 1)")
                                .addStatement("return null")
                                .endControlFlow()
                                .addStatement("$T nt = mn.getValue().get(0)", CN_NODE_TUPLE)
                                .addStatement("$T dc = getDeserializationContext(node)", CN_DESERIALIZATION_CONTEXT)
                                .addStatement("String key = asText(nt.getKeyNode())")
                                .addStatement("$T val = setDeserializationContext(nt.getValueNode(), dc)", CN_NODE)
                                .addStatement("ExpressionDefinition answer = constructExpressionType(key, val)")
                                .beginControlFlow("if (answer == null)")
                                .addStatement(
                                        "throw new org.apache.camel.dsl.yaml.common.exception.InvalidExpressionException(node, \"Unknown expression with id: \" + key)")
                                .endControlFlow()
                                .addStatement("return answer")
                                .build())
                .build());

        CodeBlock.Builder cb = CodeBlock.builder();
        cb.beginControlFlow("switch(id)");

        elementsOf(EXPRESSION_DEFINITION_CLASS).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        e -> {
                            cb.beginControlFlow("case $S:", e.getKey());
                            cb.addStatement("return asType(node, $L.class)", e.getValue().name().toString());
                            cb.endControlFlow();

                            if (!e.getKey().equals(StringHelper.camelCaseToDash(e.getKey()))) {
                                cb.beginControlFlow("case $S:", StringHelper.camelCaseToDash(e.getKey()));
                                cb.addStatement("return asType(node, $L.class)", e.getValue().name().toString());
                                cb.endControlFlow();
                            }
                        });

        cb.beginControlFlow("case \"expression\":");
        cb.addStatement("return constructExpressionType(node)");
        cb.endControlFlow();

        cb.beginControlFlow("case \"expression-type\":");
        cb.addStatement("return constructExpressionType(node)");
        cb.endControlFlow();

        cb.beginControlFlow("case \"expressionType\":");
        cb.addStatement("return constructExpressionType(node)");
        cb.endControlFlow();

        cb.endControlFlow();

        cb.addStatement("return null");

        type.addMethod(MethodSpec.methodBuilder("constructExpressionType")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(String.class, "id")
                .addParameter(Node.class, "node")
                .returns(CN_EXPRESSION_DEFINITION)
                .addCode(cb.build())
                .build());

        //
        // ExpressionDefinitionDeserializers
        //
        AnnotationSpec.Builder edAnnotation = AnnotationSpec.builder(CN_YAML_TYPE);
        edAnnotation.addMember("types", "org.apache.camel.model.language.ExpressionDefinition.class");
        edAnnotation.addMember("order", "org.apache.camel.dsl.yaml.common.YamlDeserializerResolver.ORDER_LOWEST - 1");

        String oneOfGroup = "expression";
        elementsOf(EXPRESSION_DEFINITION_CLASS).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        e -> {
                            edAnnotation.addMember(
                                    "properties",
                                    "$L",
                                    yamlPropertyWithSubtype(
                                            e.getKey(),
                                            "object",
                                            e.getValue().name().toString(),
                                            oneOfGroup));

                            if (!e.getKey().equals(StringHelper.camelCaseToDash(e.getKey()))) {
                                edAnnotation.addMember(
                                        "properties",
                                        "$L",
                                        yamlPropertyWithSubtype(
                                                StringHelper.camelCaseToDash(e.getKey()),
                                                "object",
                                                e.getValue().name().toString(),
                                                oneOfGroup));
                            }
                        });

        type.addType(
                TypeSpec.classBuilder("ExpressionDefinitionDeserializers")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addSuperinterface(ConstructNode.class)
                        .addAnnotation(edAnnotation.build())
                        .addMethod(
                                MethodSpec.methodBuilder("construct")
                                        .addModifiers(Modifier.PUBLIC)
                                        .addAnnotation(Override.class)
                                        .addParameter(Node.class, "node")
                                        .returns(Object.class)
                                        .addStatement("return constructExpressionType(node)")
                                        .build())
                        .build());

        //
        // ExpressionSubElementDefinitionDeserializers
        //
        AnnotationSpec.Builder esdAnnotation = AnnotationSpec.builder(CN_YAML_TYPE);
        esdAnnotation.addMember("types", "org.apache.camel.model.ExpressionSubElementDefinition.class");
        esdAnnotation.addMember("order", "org.apache.camel.dsl.yaml.common.YamlDeserializerResolver.ORDER_LOWEST - 1");

        elementsOf(EXPRESSION_DEFINITION_CLASS).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        e -> {
                            esdAnnotation.addMember(
                                    "properties",
                                    "$L",
                                    yamlPropertyWithSubtype(
                                            e.getKey(),
                                            "object",
                                            e.getValue().name().toString(),
                                            oneOfGroup));

                            if (!e.getKey().equals(StringHelper.camelCaseToDash(e.getKey()))) {
                                esdAnnotation.addMember(
                                        "properties",
                                        "$L",
                                        yamlPropertyWithSubtype(
                                                StringHelper.camelCaseToDash(e.getKey()),
                                                "object",
                                                e.getValue().name().toString(),
                                                oneOfGroup));
                            }
                        });

        type.addType(
                TypeSpec.classBuilder("ExpressionSubElementDefinitionDeserializers")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addSuperinterface(ConstructNode.class)
                        .addAnnotation(esdAnnotation.build())
                        .addMethod(
                                MethodSpec.methodBuilder("construct")
                                        .addModifiers(Modifier.PUBLIC)
                                        .addAnnotation(Override.class)
                                        .addParameter(Node.class, "node")
                                        .returns(Object.class)
                                        .addStatement("$T val = constructExpressionType(node)", CN_EXPRESSION_DEFINITION)
                                        .addStatement("return new org.apache.camel.model.ExpressionSubElementDefinition(val)")
                                        .build())
                        .build());

        return type.build();
    }

    private Collection<TypeSpec> generateDeserializers() {
        TypeSpec.Builder deserializers = TypeSpec.classBuilder("ModelDeserializers");
        deserializers.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        deserializers.superclass(CN_DESERIALIZER_SUPPORT);

        // PMD suppression
        AnnotationSpec.Builder suppress = AnnotationSpec.builder(SuppressWarnings.class);
        suppress.addMember("value", "$L", "\"PMD.UnnecessaryFullyQualifiedName\"");
        deserializers.addAnnotation(suppress.build());

        // add private constructor
        deserializers.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        CodeBlock.Builder constructors = CodeBlock.builder();
        constructors.beginControlFlow("switch(id)");

        all()
                .filter(ci -> {
                    return !ci.name().equals(EXPRESSION_DEFINITION_CLASS)
                            && !ci.name().equals(EXPRESSION_SUBELEMENT_DEFINITION_CLASS)
                            && !implementType(ci, ERROR_HANDLER_BUILDER_CLASS);
                })
                .map(this::generateParser)
                .sorted(Comparator.comparing(o -> o.type.name))
                .forEach(holder -> {
                    // add inner classes
                    deserializers.addType(holder.type);

                    if (holder.attributes.containsKey("node")) {
                        holder.attributes.get("node").forEach(node -> constructors.addStatement(
                                "case $S: return new ModelDeserializers.$L()", node, holder.type.name));
                    }
                    if (holder.attributes.containsKey("type")) {
                        holder.attributes.get("type").forEach(type -> constructors.addStatement(
                                "case $S: return new ModelDeserializers.$L()", type, holder.type.name));
                    }
                });

        constructors.endControlFlow();
        constructors.addStatement("return null");

        // resolve
        TypeSpec.Builder resolver = TypeSpec.classBuilder("ModelDeserializersResolver");
        resolver.addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        resolver.addSuperinterface(CN_DESERIALIZER_RESOLVER);

        resolver.addMethod(
                MethodSpec.methodBuilder("getOrder")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(int.class)
                        .addStatement("return YamlDeserializerResolver.ORDER_LOWEST - 1")
                        .build());
        resolver.addMethod(
                MethodSpec.methodBuilder("resolve")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addParameter(String.class, "id")
                        .returns(ConstructNode.class)
                        .addCode(constructors.build())
                        .build());

        return Arrays.asList(
                deserializers.build(),
                resolver.build());
    }

    private TypeSpecHolder generateParser(ClassInfo info) {
        final ClassName targetType = ClassName.get(info.name().prefix().toString(), info.name().withoutPackagePrefix());
        final TypeSpec.Builder builder = TypeSpec.classBuilder(info.simpleName() + "Deserializer");
        final Map<String, Set<String>> attributes = new HashMap<>();
        final List<AnnotationSpec> properties = new ArrayList<>();
        final AnnotationSpec.Builder yamlTypeAnnotation = AnnotationSpec.builder(CN_YAML_TYPE);

        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        if (extendsType(info, SEND_DEFINITION_CLASS) || extendsType(info, TO_DYNAMIC_DEFINITION_CLASS)) {
            builder.superclass(ParameterizedTypeName.get(CN_ENDPOINT_AWARE_DESERIALIZER_BASE, targetType));
        } else {
            builder.superclass(ParameterizedTypeName.get(CN_DESERIALIZER_BASE, targetType));
        }

        TypeSpecHolder.put(attributes, "type", info.name().toString());

        //TODO: add an option on Camel's definitions to distinguish between IN/OUT types
        if (info.name().toString().equals("org.apache.camel.model.OnExceptionDefinition")) {
            builder.addAnnotation(CN_YAML_IN);
        }
        if (info.name().toString().equals("org.apache.camel.model.rest.RestDefinition")) {
            builder.addAnnotation(CN_YAML_IN);
        }
        if (info.name().toString().equals("org.apache.camel.model.rest.RestConfigurationDefinition")) {
            builder.addAnnotation(CN_YAML_IN);
        }

        final AtomicReference<String> modelName = new AtomicReference<>();
        annotationValue(info, XML_ROOT_ELEMENT_ANNOTATION_CLASS, "name")
                .map(AnnotationValue::asString)
                .filter(value -> !"##default".equals(value))
                .ifPresent(value -> {
                    // generate the kebab case variant for backward compatibility
                    // https://issues.apache.org/jira/browse/CAMEL-17097
                    if (!Objects.equals(value, StringHelper.camelCaseToDash(value))) {
                        yamlTypeAnnotation.addMember("nodes", "$S", StringHelper.camelCaseToDash(value));
                        TypeSpecHolder.put(attributes, "node", StringHelper.camelCaseToDash(value));
                    }

                    yamlTypeAnnotation.addMember("nodes", "$S", value);
                    modelName.set(value);
                    TypeSpecHolder.put(attributes, "node", value);
                });

        //
        // Constructors
        //
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($L.class)", info.simpleName())
                .build());

        //
        // T handledTypeInstance();
        //
        builder.addMethod(MethodSpec.methodBuilder("newInstance")
                .addAnnotation(AnnotationSpec.builder(Override.class).build())
                .addModifiers(Modifier.PROTECTED)
                .returns(targetType)
                .addCode(
                        CodeBlock.builder()
                                .addStatement("return new $L()", info.simpleName())
                                .build())
                .build());

        //
        // T handledTypeInstance(String value);
        //
        for (MethodInfo ctor : info.constructors()) {

            // do not generate inline for error handlers (only ref error handler is allowed)
            boolean eh = implementType(info, ERROR_HANDLER_DEFINITION_CLASS);
            boolean ref = eh && implementType(info, REF_ERROR_HANDLER_DEFINITION_CLASS);
            if (eh && !ref) {
                break;
            }

            if (ctor.parameterTypes().size() == 1 && ctor.parameterTypes().get(0).name().equals(STRING_CLASS)) {
                if ((ctor.flags() & java.lang.reflect.Modifier.PUBLIC) == 0) {
                    break;
                }

                yamlTypeAnnotation.addMember("inline", "true");

                builder
                        .addMethod(MethodSpec.methodBuilder("newInstance")
                                .addAnnotation(AnnotationSpec.builder(Override.class).build())
                                .addModifiers(Modifier.PROTECTED)
                                .addParameter(String.class, "value")
                                .returns(targetType)
                                .addCode(
                                        CodeBlock.builder()
                                                .addStatement("return new $L(value)", info.simpleName())
                                                .build())
                                .build());
                break;
            }
        }

        //
        // Generate setProperty body
        //
        boolean caseAdded = false;

        CodeBlock.Builder setProperty = CodeBlock.builder();
        setProperty.beginControlFlow("switch(propertyKey)");

        final Schema descriptor = schemes.computeIfAbsent(
                info.name().toString(),
                k -> new Schema(MAPPER.createObjectNode(), MAPPER.createObjectNode()));

        for (FieldInfo field : fields(info)) {
            if (generateSetValue(descriptor, modelName.get(), setProperty, field, properties)) {
                caseAdded = true;
            }
        }

        if (implementType(info, ID_AWARE_CLASS)) {
            setProperty.beginControlFlow("case $S:", "id");
            setProperty.addStatement("String val = asText(node)");
            setProperty.addStatement("target.setId(val)");
            setProperty.addStatement("break");
            setProperty.endControlFlow();
            setProperty.beginControlFlow("case $S:", "description");
            setProperty.addStatement("String val = asText(node)");
            setProperty.addStatement("target.setDescription(val)");
            setProperty.addStatement("break");
            setProperty.endControlFlow();

            properties.add(
                    YamlProperties.annotation("id", "string")
                            .withDescription(descriptor.description("id"))
                            .withDisplayName(descriptor.displayName("id"))
                            .build());

            properties.add(
                    YamlProperties.annotation("description", "string")
                            .withDescription(descriptor.description("id"))
                            .withDisplayName(descriptor.displayName("id"))
                            .build());
        }

        if (implementType(info, OUTPUT_NODE_CLASS)) {
            caseAdded = true;

            setProperty.beginControlFlow("case \"steps\":");
            setProperty.addStatement("setSteps(target, node)");
            setProperty.addStatement("break");
            setProperty.endControlFlow();

            properties.add(
                    yamlProperty(
                            "steps",
                            "array:org.apache.camel.model.ProcessorDefinition"));
        }

        if (extendsType(info, SEND_DEFINITION_CLASS) || extendsType(info, TO_DYNAMIC_DEFINITION_CLASS)) {
            setProperty.beginControlFlow("default:");
            setProperty.addStatement("return false");
            setProperty.endControlFlow();

            properties.add(
                    yamlProperty(
                            "parameters",
                            "object"));

            builder.addMethod(MethodSpec.methodBuilder("setEndpointUri")
                    .addAnnotation(AnnotationSpec.builder(Override.class).build())
                    .addModifiers(Modifier.PROTECTED)
                    .addParameter(CamelContext.class, "camelContext")
                    .addParameter(Node.class, "node")
                    .addParameter(targetType, "target")
                    .addParameter(
                            ParameterizedTypeName.get(
                                    ClassName.get(Map.class),
                                    ClassName.get(String.class),
                                    ClassName.get(Object.class)),
                            "parameters")
                    .addCode(
                            CodeBlock.builder()
                                    .addStatement(
                                            "target.setUri(org.apache.camel.dsl.yaml.common.YamlSupport.createEndpointUri(camelContext, node, target.getUri(), parameters))")
                                    .build())
                    .build());
        } else if (implementType(info, HAS_EXPRESSION_TYPE_CLASS)) {
            setProperty.beginControlFlow("default:");
            setProperty.addStatement("$T ed = target.getExpressionType()", CN_EXPRESSION_DEFINITION);
            setProperty.beginControlFlow("if (ed != null)");
            setProperty.addStatement(
                    "throw new org.apache.camel.dsl.yaml.common.exception.DuplicateFieldException(node, propertyName, \"as an expression\")");
            setProperty.endControlFlow();
            setProperty.addStatement("ed = ExpressionDeserializers.constructExpressionType(propertyKey, node)");
            setProperty.beginControlFlow("if (ed != null)");
            setProperty.addStatement("target.setExpressionType(ed)");
            setProperty.nextControlFlow("else");
            setProperty.addStatement("return false");
            setProperty.endControlFlow();
            setProperty.endControlFlow();

            if (!extendsType(info, EXPRESSION_DEFINITION_CLASS)) {
                properties.add(
                        yamlProperty(
                                "__extends",
                                "object:org.apache.camel.model.language.ExpressionDefinition",
                                "expression"));
            }
        } else {
            setProperty.beginControlFlow("default:");
            setProperty.addStatement("return false");
            setProperty.endControlFlow();
        }

        setProperty.endControlFlow();

        if (caseAdded) {
            setProperty.addStatement("return true");
        }

        //
        // setProperty(T target, String propertyKey, String propertyName, Node value) throws Exception
        //
        builder
                .addMethod(MethodSpec.methodBuilder("setProperty")
                        .addAnnotation(AnnotationSpec.builder(Override.class).build())
                        .addModifiers(Modifier.PROTECTED)
                        .addParameter(targetType, "target")
                        .addParameter(String.class, "propertyKey")
                        .addParameter(String.class, "propertyName")
                        .addParameter(Node.class, "node")
                        .returns(boolean.class)
                        .addCode(setProperty.build())
                        .build());

        //
        // YamlType
        //
        yamlTypeAnnotation.addMember(
                "types",
                "$L.class",
                info.name().toString());
        yamlTypeAnnotation.addMember(
                "order",
                "org.apache.camel.dsl.yaml.common.YamlDeserializerResolver.ORDER_LOWEST - 1",
                info.name().toString());

        JsonNode yamlTypeDisplayName = descriptor.meta.at("/title");
        if (!yamlTypeDisplayName.isMissingNode() && yamlTypeDisplayName.isTextual()) {
            yamlTypeAnnotation.addMember(
                    "displayName",
                    "$S",
                    yamlTypeDisplayName.textValue());
        }

        JsonNode yamlTypeDescription = descriptor.meta.at("/description");
        if (!yamlTypeDescription.isMissingNode() && yamlTypeDescription.isTextual()) {
            yamlTypeAnnotation.addMember(
                    "description",
                    "$S",
                    yamlTypeDescription.textValue());

        }

        JsonNode yamlTypeDeprecated = descriptor.meta.at("/deprecated");
        if (!yamlTypeDeprecated.isMissingNode() && yamlTypeDeprecated.isBoolean()) {
            yamlTypeAnnotation.addMember(
                    "deprecated",
                    "$L",
                    yamlTypeDeprecated.booleanValue());
        }

        properties.stream().sorted(Comparator.comparing(a -> a.members.get("name").toString())).forEach(spec -> {
            yamlTypeAnnotation.addMember("properties", "$L", spec);
        });

        builder.addAnnotation(yamlTypeAnnotation.build());

        return new TypeSpecHolder(builder.build(), attributes);
    }

    private boolean expressionRequired(String modelName) {
        if ("method".equals(modelName) || "tokenize".equals(modelName)) {
            // skip expression attribute on these three languages as they are
            // solely configured using attributes
            return false;
        }
        return true;
    }

    @SuppressWarnings("MethodLength")
    private boolean generateSetValue(
            Schema descriptor,
            String modelName,
            CodeBlock.Builder cb,
            FieldInfo field,
            Collection<AnnotationSpec> annotations) {

        if (hasAnnotation(field, XML_TRANSIENT_CLASS) && !hasAnnotation(field, DSL_PROPERTY_ANNOTATION)) {
            return false;
        }

        //
        // XmlElements
        //
        if (hasAnnotation(field, XML_ELEMENTS_ANNOTATION_CLASS)) {
            AnnotationInstance[] elements = field.annotation(XML_ELEMENTS_ANNOTATION_CLASS).value().asNestedArray();

            if (elements.length > 1) {
                //TODO: org.apache.camel.model.cloud.ServiceCallExpressionConfiguration#expressionConfiguration is
                //      wrongly defined and need to be fixed
                cb.beginControlFlow("case $S:", StringHelper.camelCaseToDash(field.name()).toLowerCase(Locale.US));
                cb.addStatement("$T val = asMappingNode(node)", CN_MAPPING_NODE);
                cb.addStatement("setProperties(target, val)");
                cb.addStatement("break");
                cb.endControlFlow();
            }

            if (field.type().name().equals(LIST_CLASS)) {
                Type parameterized = field.type().asParameterizedType().arguments().get(0);

                for (AnnotationInstance element : elements) {
                    AnnotationValue name = element.value("name");
                    AnnotationValue type = element.value("type");

                    if (name != null && type != null) {
                        String fieldName = StringHelper.camelCaseToDash(name.asString()).toLowerCase(Locale.US);
                        String paramType = parameterized.name().toString();

                        cb.beginControlFlow("case $S:", fieldName);
                        cb.addStatement("$L val = asType(node, $L.class)", type.asString(), type.asString());
                        cb.addStatement("java.util.List<$L> existing = target.get$L()", paramType,
                                StringHelper.capitalize(field.name()));
                        cb.beginControlFlow("if (existing == null)");
                        cb.addStatement("existing = new java.util.ArrayList<>()");
                        cb.endControlFlow();
                        cb.addStatement("existing.add(val)");
                        cb.addStatement("target.set$L(existing)", StringHelper.capitalize(field.name()));
                        cb.addStatement("break");
                        cb.endControlFlow();

                        annotations.add(
                                YamlProperties.annotation(fieldName, "object")
                                        .withSubType(type.asString())
                                        .withRequired(isRequired(field))
                                        .withDescription(descriptor.description(fieldName))
                                        .withDisplayName(descriptor.displayName(fieldName))
                                        .withDefaultValue(descriptor.defaultValue(fieldName))
                                        .withIsSecret(descriptor.isSecret(fieldName))
                                        .build());
                    }
                }
            } else {
                for (AnnotationInstance element : elements) {
                    AnnotationValue name = element.value("name");
                    AnnotationValue type = element.value("type");

                    if (name != null && type != null) {
                        String fieldName = StringHelper.camelCaseToDash(name.asString()).toLowerCase(Locale.US);

                        cb.beginControlFlow("case $S:", fieldName);
                        cb.addStatement("$L val = asType(node, $L.class)", type.asString(), type.asString());
                        cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                        cb.addStatement("break");
                        cb.endControlFlow();

                        annotations.add(
                                YamlProperties.annotation(fieldName, "object")
                                        .withSubType(type.asString())
                                        .withRequired(isRequired(field))
                                        .withDescription(descriptor.description(fieldName))
                                        .withDisplayName(descriptor.displayName(fieldName))
                                        .withDefaultValue(descriptor.defaultValue(fieldName))
                                        .withIsSecret(descriptor.isSecret(fieldName))
                                        .withOneOf(field.name())
                                        .build());
                    }
                }
            }

            return true;
        }

        //
        // XmlElementRef
        //
        if (hasAnnotation(field, XML_ELEMENT_REF_ANNOTATION_CLASS)) {
            if (field.type().name().equals(LIST_CLASS)) {
                Type parameterized = field.type().asParameterizedType().arguments().get(0);
                ClassInfo refType = view.getClassByName(parameterized.name());

                // special handling for Rest + Verb definition
                if (extendsType(refType, VERB_DEFINITION_CLASS)) {
                    implementsOrExtends(parameterized).forEach(ci -> {
                        Optional<String> name = annotationValue(ci, XML_ROOT_ELEMENT_ANNOTATION_CLASS,
                                "name")
                                .map(AnnotationValue::asString)
                                .filter(value -> !"##default".equals(value));

                        if (!name.isPresent()) {
                            return;
                        }

                        String fieldName = name.get();
                        String fieldType = ci.name().toString();

                        cb.beginControlFlow("case $S:", fieldName);
                        cb.addStatement("java.util.List<$L> existing = target.get$L()", refType.name().toString(),
                                StringHelper.capitalize(field.name()));
                        cb.beginControlFlow("if (existing == null)");
                        cb.addStatement("existing = new java.util.ArrayList<>()");
                        cb.endControlFlow();
                        cb.addStatement("java.util.List val = asFlatList(node, $L.class)", fieldType);
                        cb.addStatement("existing.addAll(val)");
                        cb.addStatement("target.set$L(existing)", StringHelper.capitalize(field.name()));
                        cb.addStatement("break");
                        cb.endControlFlow();

                        annotations.add(
                                YamlProperties.annotation(fieldName, "array")
                                        .withSubType(fieldType)
                                        .withRequired(isRequired(field))
                                        .withDescription(descriptor.description(fieldName))
                                        .withDisplayName(descriptor.displayName(fieldName))
                                        .withDefaultValue(descriptor.defaultValue(fieldName))
                                        .withIsSecret(descriptor.isSecret(fieldName))
                                        .build());
                    });
                    return true;
                }
            }
        }

        //
        // Skip elements with unsupported annotations.
        //
        if (!hasAnnotation(field, XML_ATTRIBUTE_ANNOTATION_CLASS) &&
                !hasAnnotation(field, XML_VALUE_ANNOTATION_CLASS) &&
                !hasAnnotation(field, XML_ELEMENT_ANNOTATION_CLASS) &&
                !hasAnnotation(field, XML_ELEMENT_REF_ANNOTATION_CLASS) &&
                !hasAnnotation(field, XML_TRANSIENT_CLASS)) {
            return false;
        }

        final String fieldName = StringHelper.camelCaseToDash(fieldName(field)).toLowerCase(Locale.US);

        //
        // Parametrized
        //
        if (field.type().kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType parameterized = field.type().asParameterizedType();

            if (!parameterized.name().equals(CLASS_CLASS) && parameterized.arguments().size() == 1) {
                final Type parametrizedType = parameterized.arguments().get(0);
                if (parametrizedType.name().equals(PROCESSOR_DEFINITION_CLASS)) {
                    return false;
                }

                switch (parameterized.name().toString()) {
                    case "java.util.List":
                        if (parametrizedType.name().equals(STRING_CLASS)) {
                            cb.beginControlFlow("case $S:", fieldName);
                            cb.addStatement("java.util.List<String> val = asStringList(node)");
                            cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                            cb.addStatement("break");
                            cb.endControlFlow();

                            annotations.add(
                                    YamlProperties.annotation(fieldName, "array")
                                            .withSubType("string")
                                            .withRequired(isRequired(field))
                                            .withDescription(descriptor.description(fieldName))
                                            .withDisplayName(descriptor.displayName(fieldName))
                                            .withDefaultValue(descriptor.defaultValue(fieldName))
                                            .withIsSecret(descriptor.isSecret(fieldName))
                                            .build());
                        } else {
                            ClassInfo ci = view.getClassByName(parametrizedType.name());
                            String name = fieldName(ci, field);

                            cb.beginControlFlow("case $S:", StringHelper.camelCaseToDash(name).toLowerCase(Locale.US));
                            cb.addStatement("java.util.List<$L> val = asFlatList(node, $L.class)",
                                    parametrizedType.name().toString(), parametrizedType.name().toString());
                            cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                            cb.addStatement("break");
                            cb.endControlFlow();

                            annotations.add(
                                    YamlProperties
                                            .annotation(StringHelper.camelCaseToDash(name).toLowerCase(Locale.US), "array")
                                            .withSubType(parametrizedType.name().toString())
                                            .withRequired(isRequired(field))
                                            .withDescription(descriptor.description(name))
                                            .withDisplayName(descriptor.displayName(name))
                                            .withDefaultValue(descriptor.defaultValue(name))
                                            .withIsSecret(descriptor.defaultValue(name))
                                            .build());
                        }
                        return true;
                    case "java.util.Set":
                        if (parametrizedType.name().equals(STRING_CLASS)) {
                            cb.beginControlFlow("case $S:", fieldName);
                            cb.addStatement("java.util.Set<String> val = asStringSet(node)");
                            cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                            cb.addStatement("break");
                            cb.endControlFlow();

                            annotations.add(
                                    YamlProperties.annotation(fieldName, "array")
                                            .withSubType("string")
                                            .withRequired(isRequired(field))
                                            .withDescription(descriptor.description(fieldName))
                                            .withDisplayName(descriptor.displayName(fieldName))
                                            .withDefaultValue(descriptor.defaultValue(fieldName))
                                            .withIsSecret(descriptor.isSecret(fieldName))
                                            .build());
                        } else {
                            ClassInfo ci = view.getClassByName(parametrizedType.name());
                            String name = fieldName(ci, field);

                            cb.beginControlFlow("case $S:", StringHelper.camelCaseToDash(name).toLowerCase(Locale.US));
                            cb.addStatement("var val = asFlatSet(node, $L.class)", parametrizedType.name().toString());
                            cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                            cb.addStatement("break");
                            cb.endControlFlow();

                            annotations.add(
                                    YamlProperties
                                            .annotation(StringHelper.camelCaseToDash(name).toLowerCase(Locale.US), "array")
                                            .withSubType(parametrizedType.name().toString())
                                            .withRequired(isRequired(field))
                                            .withDescription(descriptor.description(name))
                                            .withDisplayName(descriptor.displayName(name))
                                            .withDefaultValue(descriptor.defaultValue(name))
                                            .withIsSecret(descriptor.defaultValue(name))
                                            .build());
                        }
                        return true;
                    default:
                        throw new UnsupportedOperationException(
                                "Unable to handle field: " + field.name() + " with type: " + field.type().name());
                }
            }
        }

        if ("expression".equals(fieldName) && !expressionRequired(modelName)) {
            // special for some language models which does not have required expression
            // which should be skipped
            return true;
        }

        //
        // Others
        //
        cb.beginControlFlow("case $S:", fieldName);

        ClassInfo c = view.getClassByName(field.type().name());
        if (hasAnnotation(field, XML_JAVA_TYPE_ADAPTER_CLASS)) {
            // conversion using JAXB Adapter of known class
            Optional<AnnotationValue> adapter = annotationValue(field, XML_JAVA_TYPE_ADAPTER_CLASS, "value");
            if (adapter.isEmpty()) {
                return false;
            }
            String adapterClass = adapter.get().asClass().name().toString();
            ClassInfo adapterClassInfo = view.getClassByName(adapter.get().asClass().name());
            if (adapterClassInfo.superClassType().kind() == Type.Kind.PARAMETERIZED_TYPE) {
                List<Type> arguments = adapterClassInfo.superClassType().asParameterizedType().arguments();
                if (arguments.size() == 2) {
                    // this is for extends XmlAdapter<BeanPropertiesDefinition, Map<String, Object>>
                    // we can't use JaxbUnmarshaller here (as in XML DSL) and we have to convert to map directly
                    Type type = arguments.get(1);
                    if (type.name().toString().equals("java.util.Map")) {
                        cb.addStatement("$L val = asMap(node)", field.type().name().toString());
                        cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                        cb.addStatement("break");

                        annotations.add(
                                YamlProperties.annotation(fieldName, "object")
                                        .withRequired(isRequired(field))
                                        .withDeprecated(isDeprecated(field))
                                        .withDescription(descriptor.description(fieldName))
                                        .withDisplayName(descriptor.displayName(fieldName))
                                        .withDefaultValue(descriptor.defaultValue(fieldName))
                                        .withIsSecret(descriptor.isSecret(fieldName))
                                        .build());
                    }
                }
            }
        } else if (c != null && c.isEnum()) {
            cb.addStatement("target.set$L(asEnum(node, $L.class))", StringHelper.capitalize(field.name()),
                    field.type().name().toString());
            cb.addStatement("break");

            Set<String> values = new TreeSet<>();

            // gather enum values
            List<FieldInfo> fields = c.fields();
            for (int i = 1; i < fields.size(); i++) {
                FieldInfo f = fields.get(i);
                if (f.isEnumConstant()) {
                    values.add(f.name());
                }
            }

            annotations.add(
                    YamlProperties.annotation(fieldName, "enum:" + String.join(",", values))
                            .withRequired(isRequired(field))
                            .withRequired(isDeprecated(field))
                            .withDescription(descriptor.description(fieldName))
                            .withDisplayName(descriptor.displayName(fieldName))
                            .withDefaultValue(descriptor.defaultValue(fieldName))
                            .withIsSecret(descriptor.isSecret(fieldName))
                            .build());
        } else if (isEnum(field)) {
            // this is a fake enum where the model is text based by have enum values to represent the user to choose between
            cb.addStatement("String val = asText(node)");
            cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
            cb.addStatement("break");

            annotations.add(
                    YamlProperties.annotation(fieldName, "enum:" + getEnums(field))
                            .withRequired(isRequired(field))
                            .withRequired(isDeprecated(field))
                            .withDescription(descriptor.description(fieldName))
                            .withDisplayName(descriptor.displayName(fieldName))
                            .withDefaultValue(descriptor.defaultValue(fieldName))
                            .withIsSecret(descriptor.isSecret(fieldName))
                            .build());

        } else {
            switch (field.type().name().toString()) {
                case "[B":
                    cb.addStatement("byte[] val = asByteArray(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "string")
                                    .withFormat("binary")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                case "Z":
                case "boolean":
                    cb.addStatement("boolean val = asBoolean(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "boolean")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                case "I":
                case "int":
                    cb.addStatement("int val = asInt(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "number")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                case "J":
                case "long":
                    cb.addStatement("long val = asLong(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "number")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                case "D":
                case "double":
                    cb.addStatement("double val = asDouble(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "number")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                case "java.lang.String":
                    cb.addStatement("String val = asText(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    String javaType = annotationValue(field, METADATA_ANNOTATION_CLASS, "javaType")
                            .map(AnnotationValue::asString)
                            .orElse("string");

                    switch (javaType) {
                        case "java.lang.Boolean":
                            annotations.add(
                                    YamlProperties.annotation(fieldName, "boolean")
                                            .withRequired(isRequired(field))
                                            .withDeprecated(isDeprecated(field))
                                            .withDescription(descriptor.description(fieldName))
                                            .withDisplayName(descriptor.displayName(fieldName))
                                            .withDefaultValue(descriptor.defaultValue(fieldName))
                                            .withIsSecret(descriptor.isSecret(fieldName))
                                            .build());
                            break;
                        case "java.lang.Integer":
                        case "java.lang.Short":
                        case "java.lang.Long":
                        case "java.lang.Float":
                        case "java.lang.Double":
                            annotations.add(
                                    YamlProperties.annotation(fieldName, "number")
                                            .withRequired(isRequired(field))
                                            .withDeprecated(isDeprecated(field))
                                            .withDescription(descriptor.description(fieldName))
                                            .withDisplayName(descriptor.displayName(fieldName))
                                            .withDefaultValue(descriptor.defaultValue(fieldName))
                                            .withIsSecret(descriptor.isSecret(fieldName))
                                            .build());
                            break;
                        default:
                            annotations.add(
                                    YamlProperties.annotation(fieldName, "string")
                                            .withRequired(isRequired(field))
                                            .withDeprecated(isDeprecated(field))
                                            .withDescription(descriptor.description(fieldName))
                                            .withDisplayName(descriptor.displayName(fieldName))
                                            .withDefaultValue(descriptor.defaultValue(fieldName))
                                            .withIsSecret(descriptor.isSecret(fieldName))
                                            .build());
                    }

                    break;
                case "java.lang.Class":
                    cb.addStatement("java.lang.Class<?> val = asClass(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "string")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());

                    break;
                case "[Ljava.lang.Class;":
                    cb.addStatement("java.lang.Class<?>[] val = asClassArray(node)");
                    cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                    cb.addStatement("break");
                    break;
                case "java.lang.Integer":
                case "java.lang.Short":
                case "java.lang.Long":
                case "java.lang.Float":
                case "java.lang.Double":
                    cb.addStatement("String val = asText(node)");
                    cb.addStatement("target.set$L($L.valueOf(val))", StringHelper.capitalize(field.name()),
                            field.type().name().toString());
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "number")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                case "java.lang.Boolean":
                    cb.addStatement("String val = asText(node)");
                    cb.addStatement("target.set$L($L.valueOf(val))", StringHelper.capitalize(field.name()),
                            field.type().name().toString());
                    cb.addStatement("break");

                    annotations.add(
                            YamlProperties.annotation(fieldName, "boolean")
                                    .withRequired(isRequired(field))
                                    .withRequired(isDeprecated(field))
                                    .withDescription(descriptor.description(fieldName))
                                    .withDisplayName(descriptor.displayName(fieldName))
                                    .withDefaultValue(descriptor.defaultValue(fieldName))
                                    .withIsSecret(descriptor.isSecret(fieldName))
                                    .build());
                    break;
                default:
                    if (field.type().kind() == Type.Kind.CLASS) {
                        cb.addStatement("$L val = asType(node, $L.class)", field.type().name().toString(),
                                field.type().name().toString());
                        cb.addStatement("target.set$L(val)", StringHelper.capitalize(field.name()));
                        cb.addStatement("break");

                        annotations.add(
                                YamlProperties.annotation(fieldName, "object")
                                        .withSubType(field.type().name().toString())
                                        .withRequired(isRequired(field))
                                        .withRequired(isDeprecated(field))
                                        .withDescription(descriptor.description(fieldName))
                                        .withDisplayName(descriptor.displayName(fieldName))
                                        .withDefaultValue(descriptor.defaultValue(fieldName))
                                        .withIsSecret(descriptor.isSecret(fieldName))
                                        .withOneOf("expression".equals(fieldName) ? "expression" : "")
                                        .build());
                    } else {
                        throw new UnsupportedOperationException(
                                "Unable to handle field: " + field.name() + " with type: " + field.type().name());
                    }
            }
        }

        cb.endControlFlow();

        return true;
    }
}
