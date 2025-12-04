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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

public class OperationVisitorTest {

    @Test
    public void shouldEmitCodeForOas2ParameterInQuery() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);

        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("query");

        visitor.emit(parameter);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.query)\n"
                        + "        .required(false)\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void referenceApiParameter() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);
        final OpenAPI document = new OpenAPIV3Parser()
                .read("src/test/resources/org/apache/camel/generator/openapi/openapi-v3-ref-param.yaml");
        final PathItem pathItem = document.getPaths().get("/path");
        pathItem.getGet().getParameters().forEach(visitor::emit);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"limit\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.query)\n"
                        + "        .dataType(\"integer\")\n"
                        + "        .collectionFormat(org.apache.camel.model.rest.CollectionFormat.multi)\n"
                        + "        .required(false)\n"
                        + "        .description(\"Limits the number of returned results\")\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitReferenceType() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(
                null, emitter, new OperationFilter(), "/path", new DefaultDestinationGenerator(), "camel.sample");

        final OpenAPI document = new OpenAPIV3Parser()
                .read("src/test/resources/org/apache/camel/generator/openapi/openapi-v3-ref-schema.yaml", null, null);
        final PathItem pathItem = document.getPaths().get("/pet");

        visitor.visit(PathItem.HttpMethod.PUT, pathItem.getPut(), pathItem);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "    put(\"/path\")\n"
                        + "      .id(\"updatePet\")\n"
                        + "      .description(\"Update an existing pet by Id\")\n"
                        + "      .consumes(\"application/json\")\n"
                        + "      .produces(\"application/json\")\n"
                        + "      .param()\n"
                        + "        .name(\"body\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.body)\n"
                        + "        .required(true)\n"
                        + "        .description(\"Update an existent pet in the store\")\n"
                        + "      .endParam()\n"
                        + "      .type(\"camel.sample.Pet[]\")\n"
                        + "      .outType(\"camel.sample.Pet[]\")\n"
                        + "      .to(\"direct:updatePet\")\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas32ParameterInPath() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(
                null, emitter, new OperationFilter(), "/path/{param}", new DefaultDestinationGenerator(), null);

        final OpenAPI document = new OpenAPI();
        final Paths paths = new Paths();
        final PathItem path = new PathItem();
        paths.addPathItem("/path/{param}", path);
        final Operation operation = new Operation();
        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("path");
        path.addParametersItem(parameter);
        document.setPaths(paths);

        visitor.visit(PathItem.HttpMethod.GET, operation, path);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "    get(\"/path/{param}\")\n"
                        + "      .param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                        + "        .required(true)\n"
                        + "      .endParam()\n"
                        + "      .to(\"direct:rest1\")\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3ParameterInPath() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(
                null, emitter, new OperationFilter(), "/path/{param}", new DefaultDestinationGenerator(), null);

        final Paths paths = new Paths();
        final PathItem path = new PathItem();
        paths.addPathItem("/path/{param}", path);

        final Operation operation = new Operation();
        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("path");
        path.addParametersItem(parameter);

        visitor.visit(PathItem.HttpMethod.GET, operation, path);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "    get(\"/path/{param}\")\n"
                        + "      .param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                        + "        .required(true)\n"
                        + "      .endParam()\n"
                        + "      .to(\"direct:rest1\")\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3ParameterWithDefaultValue() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);

        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("path");
        Schema schema = new Schema();
        schema.setDefault("default");
        parameter.setSchema(schema);

        visitor.emit(parameter);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                        + "        .defaultValue(\"default\")\n"
                        + "        .required(true)\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3ParameterWithEnum() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);

        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("query");
        Schema schema = new Schema();
        schema.setEnum(Arrays.asList("one", "two", "three"));
        parameter.setSchema(schema);

        visitor.emit(parameter);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.query)\n"
                        + "        .allowableValues(\"one,two,three\")\n"
                        + "        .required(false)\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3ParameterWithType() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);

        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("query");
        Schema schema = new Schema();
        schema.setType("integer");
        parameter.setSchema(schema);

        visitor.emit(parameter);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.query)\n"
                        + "        .dataType(\"integer\")\n"
                        + "        .required(false)\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3PathParameter() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);

        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("path");

        visitor.emit(parameter);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                        + "        .required(true)\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3RefParameters() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(null, emitter, null, null, null, null);

        final Paths paths = new Paths();
        final PathItem path = new PathItem();
        paths.addPathItem("/path/{param}", path);
        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("query");
        parameter.set$ref("#/components/parameters/param");
        path.addParametersItem(parameter);

        visitor.emit(parameter);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "      param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.query)\n"
                        + "        .required(false)\n"
                        + "      .endParam()\n"
                        + "    }\n");
    }

    @Test
    public void testDestinationToSyntax() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(
                null,
                emitter,
                new OperationFilter(),
                "/path/{param}",
                new DefaultDestinationGenerator("seda:${operationId}"),
                null);

        final Paths paths = new Paths();
        final PathItem pathItem = new PathItem();
        paths.addPathItem("/path/{param}", pathItem);

        final Operation operation = new Operation();
        operation.setOperationId("my-operation");
        final Parameter parameter = new Parameter();
        parameter.setName("param");
        parameter.setIn("path");
        pathItem.addParametersItem(parameter);

        visitor.visit(PathItem.HttpMethod.GET, operation, pathItem);

        assertThat(method.build().toString())
                .isEqualTo("void configure() {\n"
                        + "    get(\"/path/{param}\")\n"
                        + "      .id(\"my-operation\")\n"
                        + "      .param()\n"
                        + "        .name(\"param\")\n"
                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                        + "        .required(true)\n"
                        + "      .endParam()\n"
                        + "      .to(\"seda:my-operation\")\n"
                        + "    }\n");
    }
}
