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
package org.apache.camel.generator.openapi;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collector;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.apicurio.datamodels.models.Info;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import io.apicurio.datamodels.models.openapi.OpenApiPathItem;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * Generates Java source code
 */
public abstract class RestDslSourceCodeGenerator<T> extends RestDslGenerator<RestDslSourceCodeGenerator<T>> {
    static final String DEFAULT_CLASS_NAME = "RestDslRoute";

    static final String DEFAULT_PACKAGE_NAME = "rest.dsl.generated";

    private static final String DEFAULT_INDENT = "    ";

    private Function<OpenApiDocument, String> classNameGenerator = RestDslSourceCodeGenerator::generateClassName;

    private Instant generated = Instant.now();

    private String indent = DEFAULT_INDENT;

    private Function<OpenApiDocument, String> packageNameGenerator = RestDslSourceCodeGenerator::generatePackageName;

    private boolean sourceCodeTimestamps;

    RestDslSourceCodeGenerator(final OpenApiDocument document) {
        super(document);
    }

    public abstract void generate(T destination) throws IOException;

    public RestDslSourceCodeGenerator<T> withClassName(final String className) {
        notEmpty(className, "className");
        this.classNameGenerator = s -> className;

        return this;
    }

    public RestDslSourceCodeGenerator<T> withIndent(final String indent) {
        this.indent = ObjectHelper.notNull(indent, "indent");

        return this;
    }

    public RestDslSourceCodeGenerator<T> withoutSourceCodeTimestamps() {
        sourceCodeTimestamps = false;

        return this;
    }

    public RestDslSourceCodeGenerator<T> withPackageName(final String packageName) {
        notEmpty(packageName, "packageName");
        this.packageNameGenerator = s -> packageName;

        return this;
    }

    public RestDslSourceCodeGenerator<T> withSourceCodeTimestamps() {
        sourceCodeTimestamps = true;

        return this;
    }

    MethodSpec generateConfigureMethod(final OpenApiDocument document) {
        final MethodSpec.Builder configure = MethodSpec.methodBuilder("configure").addModifiers(Modifier.PUBLIC)
                .returns(void.class).addJavadoc("Defines Apache Camel routes using REST DSL fluent API.\n");

        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(configure);

        boolean restConfig = restComponent != null || restContextPath != null || clientRequestValidation;
        if (restConfig) {
            configure.addCode("\n");
            configure.addCode("restConfiguration()");
            if (ObjectHelper.isNotEmpty(restComponent)) {
                configure.addCode(".component(\"" + restComponent + "\")");
            }
            if (ObjectHelper.isNotEmpty(restContextPath)) {
                configure.addCode(".contextPath(\"" + restContextPath + "\")");
            }
            if (ObjectHelper.isNotEmpty(apiContextPath)) {
                configure.addCode(".apiContextPath(\"" + apiContextPath + "\")");
            }
            if (clientRequestValidation) {
                configure.addCode(".clientRequestValidation(true)");
            }
            configure.addCode(";\n");
        }

        final String basePath = RestDslGenerator.determineBasePathFrom(this.basePath, document);

        for (String name : document.getPaths().getItemNames()) {
            OpenApiPathItem s = document.getPaths().getItem(name);
            // there must be at least one verb
            if (s.getGet() != null || s.getDelete() != null || s.getHead() != null || s.getOptions() != null
                    || s.getPut() != null || s.getPatch() != null
                    || s.getPost() != null) {
                // there must be at least one operation accepted by the filter (otherwise we generate empty rest methods)
                boolean anyAccepted = filter == null || ofNullable(s.getGet(), s.getDelete(), s.getHead(), s.getOptions(),
                        s.getPut(), s.getPatch(), s.getPost())
                        .stream().anyMatch(o -> filter.accept(o.getOperationId()));
                if (anyAccepted) {
                    // create new rest statement per path to avoid a giant chained single method
                    PathVisitor<MethodSpec> restDslStatement
                            = new PathVisitor<>(basePath, emitter, filter, destinationGenerator());
                    restDslStatement.visit(name, s);
                    emitter.endEmit();
                }
            }
        }
        return emitter.result();
    }

    Instant generated() {
        return generated;
    }

    JavaFile generateSourceCode() {
        final MethodSpec methodSpec = generateConfigureMethod(document);

        final String classNameToUse = classNameGenerator.apply(document);

        final AnnotationSpec.Builder generatedAnnotation = AnnotationSpec.builder(Generated.class).addMember("value",
                "$S", getClass().getName());
        if (sourceCodeTimestamps) {
            generatedAnnotation.addMember("date", "$S", generated());
        }

        final TypeSpec.Builder builder = TypeSpec.classBuilder(classNameToUse).superclass(RouteBuilder.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(methodSpec)
                .addAnnotation(generatedAnnotation.build())
                .addJavadoc("Generated from OpenApi specification by Camel REST DSL generator.\n");
        if (springComponent) {
            final AnnotationSpec.Builder springAnnotation
                    = AnnotationSpec.builder(ClassName.bestGuess("org.springframework.stereotype.Component"));
            builder.addAnnotation(springAnnotation.build());
        }
        final TypeSpec generatedRouteBuilder = builder.build();

        final String packageNameToUse = packageNameGenerator.apply(document);

        return JavaFile.builder(packageNameToUse, generatedRouteBuilder).indent(indent).build();
    }

    RestDslSourceCodeGenerator<T> withGeneratedTime(final Instant generated) {
        this.generated = generated;

        return this;
    }

    static String generateClassName(final OpenApiDocument document) {
        final Info info = document.getInfo();
        if (info == null) {
            return DEFAULT_CLASS_NAME;
        }

        final String title = info.getTitle();
        if (title == null) {
            return DEFAULT_CLASS_NAME;
        }

        final String className = title.chars().filter(Character::isJavaIdentifierPart).filter(c -> c < 'z').boxed()
                .collect(Collector.of(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append,
                        StringBuilder::toString));

        if (className.isEmpty() || !Character.isJavaIdentifierStart(className.charAt(0))) {
            return DEFAULT_CLASS_NAME;
        }

        return className;
    }

    static String generatePackageName(final OpenApiDocument document) {
        final String host = RestDslGenerator.determineHostFrom(document);

        if (ObjectHelper.isNotEmpty(host)) {
            final StringBuilder packageName = new StringBuilder();

            final String hostWithoutPort = host.replaceFirst(":.*", "");

            if ("localhost".equalsIgnoreCase(hostWithoutPort)) {
                return DEFAULT_PACKAGE_NAME;
            }

            final String[] parts = hostWithoutPort.split("\\.");

            for (int i = parts.length - 1; i >= 0; i--) {
                packageName.append(parts[i]);
                if (i != 0) {
                    packageName.append('.');
                }
            }

            return packageName.toString();
        }

        return DEFAULT_PACKAGE_NAME;
    }

    private static <T> List<T> ofNullable(T... t) {
        List<T> list = new ArrayList<>();
        for (T o : t) {
            if (o != null) {
                list.add(o);
            }
        }
        return list;
    }

}
