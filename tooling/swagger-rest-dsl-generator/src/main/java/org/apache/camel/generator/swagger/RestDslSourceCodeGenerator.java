/**
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
package org.apache.camel.generator.swagger;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collector;

import javax.annotation.Generated;
import javax.lang.model.element.Modifier;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import io.swagger.models.Info;
import io.swagger.models.Swagger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.StringHelper.notEmpty;

public abstract class RestDslSourceCodeGenerator<T> extends RestDslGenerator<RestDslSourceCodeGenerator<T>> {
    static final String DEFAULT_CLASS_NAME = "RestDslRoute";

    static final String DEFAULT_PACKAGE_NAME = "rest.dsl.generated";

    private static final String DEFAULT_INDENT = "    ";

    private Function<Swagger, String> classNameGenerator = RestDslSourceCodeGenerator::generateClassName;

    private Instant generated = Instant.now();

    private String indent = DEFAULT_INDENT;

    private Function<Swagger, String> packageNameGenerator = RestDslSourceCodeGenerator::generatePackageName;

    private boolean sourceCodeTimestamps;

    RestDslSourceCodeGenerator(final Swagger swagger) {
        super(swagger);
    }

    public abstract void generate(T destination) throws IOException;

    public RestDslSourceCodeGenerator<T> withClassName(final String className) {
        notEmpty(className, "className");
        this.classNameGenerator = (s) -> className;

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
        this.packageNameGenerator = (s) -> packageName;

        return this;
    }

    public RestDslSourceCodeGenerator<T> withSourceCodeTimestamps() {
        sourceCodeTimestamps = true;

        return this;
    }

    MethodSpec generateConfigureMethod(final Swagger swagger) {
        final MethodSpec.Builder configure = MethodSpec.methodBuilder("configure").addModifiers(Modifier.PUBLIC)
            .returns(void.class).addJavadoc("Defines Apache Camel routes using REST DSL fluent API.\n");

        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(configure);

        final PathVisitor<MethodSpec> restDslStatement = new PathVisitor<>(emitter, destinationGenerator());
        swagger.getPaths().forEach(restDslStatement::visit);

        return emitter.result();
    }

    Instant generated() {
        return generated;
    }

    JavaFile generateSourceCode() {
        final MethodSpec methodSpec = generateConfigureMethod(swagger);

        final String classNameToUse = classNameGenerator.apply(swagger);

        final AnnotationSpec.Builder generatedAnnotation = AnnotationSpec.builder(Generated.class).addMember("value",
            "$S", getClass().getName());

        if (sourceCodeTimestamps) {
            generatedAnnotation.addMember("date", "$S", generated());
        }

        final TypeSpec generatedRouteBulder = TypeSpec.classBuilder(classNameToUse).superclass(RouteBuilder.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(methodSpec)
            .addAnnotation(generatedAnnotation.build())
            .addJavadoc("Generated from Swagger specification by Camel REST DSL generator.\n").build();

        final String packageNameToUse = packageNameGenerator.apply(swagger);

        return JavaFile.builder(packageNameToUse, generatedRouteBulder).indent(indent).build();
    }

    RestDslSourceCodeGenerator<T> withGeneratedTime(final Instant generated) {
        this.generated = generated;

        return this;
    }

    static String generateClassName(final Swagger swagger) {
        final Info info = swagger.getInfo();
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

    static String generatePackageName(final Swagger swagger) {
        final String host = swagger.getHost();

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
}
