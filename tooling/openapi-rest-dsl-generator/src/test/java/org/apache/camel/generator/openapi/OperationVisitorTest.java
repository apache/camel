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

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import io.apicurio.datamodels.models.openapi.OpenApiOperation;
import io.apicurio.datamodels.models.openapi.OpenApiParameter;
import io.apicurio.datamodels.models.openapi.OpenApiPathItem;
import io.apicurio.datamodels.models.openapi.OpenApiPaths;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Document;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20DocumentImpl;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Parameter;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20ParameterImpl;
import org.apache.camel.generator.openapi.PathVisitor.HttpMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OperationVisitorTest {

    @Test
    public void shouldEmitCodeForOas2ParameterInQuery() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(emitter, null, null, null);

        final OpenApi20Parameter parameter = new OpenApi20ParameterImpl();
        parameter.setName("param");
        parameter.setIn("query");

        visitor.emit(parameter);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
                                                        + "      param()\n"
                                                        + "        .name(\"param\")\n"
                                                        + "        .type(org.apache.camel.model.rest.RestParamType.query)\n"
                                                        + "        .required(false)\n"
                                                        + "      .endParam()\n"
                                                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas32ParameterInPath() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor
                = new OperationVisitor<>(emitter, new OperationFilter(), "/path/{param}", new DefaultDestinationGenerator());

        final OpenApi20Document document = new OpenApi20DocumentImpl();
        final OpenApiPaths paths = document.createPaths();
        final OpenApiPathItem path = paths.createPathItem();
        paths.addItem("/path/{param}", path);
        final OpenApiOperation operation = path.createOperation();
        path.setGet(operation);
        final OpenApiParameter parameter = new OpenApi20ParameterImpl();
        parameter.setName("param");
        parameter.setIn("path");
        path.addParameter(parameter);

        visitor.visit(HttpMethod.GET, operation);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
                                                        + "    get(\"/path/{param}\")\n"
                                                        + "      .param()\n"
                                                        + "        .name(\"param\")\n"
                                                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                                                        + "        .required(false)\n"
                                                        + "      .endParam()\n"
                                                        + "      .to(\"direct:rest1\")\n"
                                                        + "    }\n");
    }
    /*
    @Test
    public void shouldEmitCodeForOas3ParameterInPath() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor
                = new OperationVisitor<>(emitter, new OperationFilter(), "/path/{param}", new DefaultDestinationGenerator());

        final OpenApi30Document document = new OpenApi30DocumentImpl();
        final OpenApiPaths paths = (OpenApi30Paths) document.createPaths();
        final OasPathItem path = paths.addItem("", paths.createPathItem("/path/{param}"));
        final OasOperation operation = path.createOperation("get");
        final Oas30Parameter parameter = new Oas30Parameter("param");
        parameter.in = "path";
        path.addParameter(parameter);

        visitor.visit(HttpMethod.GET, operation);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
                                                        + "    get(\"/path/{param}\")\n"
                                                        + "      .param()\n"
                                                        + "        .name(\"param\")\n"
                                                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                                                        + "        .required(false)\n"
                                                        + "      .endParam()\n"
                                                        + "      .to(\"direct:rest1\")\n"
                                                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3ParameterWithDefaultValue() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(emitter, null, null, null);

        final Oas30Parameter parameter = new Oas30Parameter("param");
        parameter.in = "path";
        parameter.schema = parameter.createSchema();
        ((Oas30Schema) parameter.schema).default_ = "default";

        visitor.emit(parameter);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
                                                        + "      param()\n"
                                                        + "        .name(\"param\")\n"
                                                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                                                        + "        .defaultValue(\"default\")\n"
                                                        + "        .required(false)\n"
                                                        + "      .endParam()\n"
                                                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3ParameterWithEnum() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(emitter, null, null, null);

        final Oas30Parameter parameter = new Oas30Parameter("param");
        parameter.in = "query";
        parameter.schema = parameter.createSchema();
        ((Oas30Schema) parameter.schema).enum_ = Arrays.asList("one", "two", "three");

        visitor.emit(parameter);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
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
        final OperationVisitor<?> visitor = new OperationVisitor<>(emitter, null, null, null);

        final Oas30Parameter parameter = new Oas30Parameter("param");
        parameter.in = "query";
        parameter.schema = parameter.createSchema();
        ((Oas30Schema) parameter.schema).type = "integer";

        visitor.emit(parameter);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
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
        final OperationVisitor<?> visitor = new OperationVisitor<>(emitter, null, null, null);

        final Oas30Parameter parameter = new Oas30Parameter("param");
        parameter.in = "path";

        visitor.emit(parameter);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
                                                        + "      param()\n"
                                                        + "        .name(\"param\")\n"
                                                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                                                        + "        .required(false)\n"
                                                        + "      .endParam()\n"
                                                        + "    }\n");
    }

    @Test
    public void shouldEmitCodeForOas3RefParameters() {
        final Builder method = MethodSpec.methodBuilder("configure");
        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);
        final OperationVisitor<?> visitor = new OperationVisitor<>(emitter, null, null, null);

        final Oas30Document document = new Oas30Document();
        document.components = document.createComponents();
        final Oas30ParameterDefinition parameterDefinition = new Oas30ParameterDefinition("param");
        parameterDefinition.in = "query";
        document.components.addParameterDefinition("param", parameterDefinition);

        final Oas30Parameter parameter = new Oas30Parameter();
        parameter._ownerDocument = document;
        parameter.$ref = "#/components/parameters/param";

        visitor.emit(parameter);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
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
        final OperationVisitor<?> visitor
                = new OperationVisitor<>(
                        emitter, new OperationFilter(), "/path/{param}",
                        new DefaultDestinationGenerator("seda:${operationId}"));

        final Oas30Document document = new Oas30Document();
        final OasPaths paths = document.createPaths();
        final OasPathItem path = paths.addPathItem("", paths.createPathItem("/path/{param}"));
        final OasOperation operation = path.createOperation("get");
        operation.operationId = "my-operation";
        final Oas30Parameter parameter = new Oas30Parameter("param");
        parameter.in = "path";
        path.addParameter(parameter);

        visitor.visit(HttpMethod.GET, operation);

        assertThat(method.build().toString()).isEqualTo("void configure() {\n"
                                                        + "    get(\"/path/{param}\")\n"
                                                        + "      .id(\"my-operation\")\n"
                                                        + "      .param()\n"
                                                        + "        .name(\"param\")\n"
                                                        + "        .type(org.apache.camel.model.rest.RestParamType.path)\n"
                                                        + "        .required(false)\n"
                                                        + "      .endParam()\n"
                                                        + "      .to(\"seda:my-operation\")\n"
                                                        + "    }\n");
    }
    */
}
