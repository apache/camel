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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Document;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20DocumentImpl;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30DocumentImpl;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Server;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30ServerImpl;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30ServerVariable;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30ServerVariableImpl;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslGeneratorTest {

    static OpenApiDocument document;

    final Instant generated = Instant.parse("2017-10-17T00:00:00.000Z");

    @Test
    public void shouldCreateDefinitions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            final RestsDefinition definition = RestDslGenerator.toDefinition(document).generate();
            assertThat(definition).isNotNull();
            assertThat(definition.getRests()).hasSize(1);
            assertThat(definition.getRests().get(0).getPath()).isEqualTo("/v2");
        }
    }

    @Test
    public void shouldDetermineBasePathFromV2Document() {
        final OpenApi20Document oas20Document = new OpenApi20DocumentImpl();
        oas20Document.setBasePath("/api");
        assertThat(RestDslGenerator.determineBasePathFrom(oas20Document)).isEqualTo("/api");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsServerUrl() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("https://example.com/api");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsServerUrlWithTemplateVariables() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        addVariableTo(server, "base", "api");
        addVariableTo(server, "path", "v3");
        server.setUrl("https://example.com/{base}/{path}");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api/v3");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsWhenServerUrlIsRelative() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("api/v3");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api/v3");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsWhenServerUrlIsRelativeWithoutStartingSlash() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("api/v3");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api/v3");
    }

    @Test
    public void shouldDetermineBasePathFromParameterOverDocument() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("api/v3");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom("/api/v4", oas30Document)).isEqualTo("/api/v4");
    }

    @Test
    public void shouldDetermineBasePathFromParameterOverDocumentWithoutStartingSlash() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("api/v3");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom("api/v4", oas30Document)).isEqualTo("/api/v4");
    }

    @Test
    public void shouldDetermineBasePathFromParameterOverDocumentWithEmptyParameter() {
        final OpenApi30Document oas30Document = new OpenApi30DocumentImpl();
        final OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("/api/v3");

        oas30Document.addServer(server);
        assertThat(RestDslGenerator.determineBasePathFrom(null, oas30Document)).isEqualTo("/api/v3");
        assertThat(RestDslGenerator.determineBasePathFrom("/", oas30Document)).isEqualTo("");
        assertThat(RestDslGenerator.determineBasePathFrom("", oas30Document)).isEqualTo("");
        assertThat(RestDslGenerator.determineBasePathFrom("   ", oas30Document)).isEqualTo("");
    }

    @Test
    public void shouldGenerateSourceCodeWithDefaults() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document).withGeneratedTime(generated).generate(code);

        final URI file = RestDslGeneratorTest.class.getResource("/OpenApiPetstore.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldGenerateSourceCodeWithFilter() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .withClassName("MyRestRoute")
                .withPackageName("com.example")
                .withIndent("\t")
                .withSourceCodeTimestamps()
                .withOperationFilter("find*,deletePet,updatePet")
                .withDestinationGenerator(o -> "direct:rest-" + o.getOperationId())
                .generate(code);

        final URI file = RestDslGeneratorTest.class.getResource("/MyRestRouteFilter.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldGenerateSourceCodeWithOptions() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .withClassName("MyRestRoute")
                .withPackageName("com.example")
                .withIndent("\t")
                .withSourceCodeTimestamps()
                .withDestinationGenerator(o -> "direct:rest-" + o.getOperationId()).generate(code);

        final URI file = RestDslGeneratorTest.class.getResource("/MyRestRoute.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldGenerateSourceCodeWithRestComponent() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .withRestComponent("servlet")
                .withRestContextPath("/")
                .generate(code);

        final URI file = RestDslGeneratorTest.class.getResource("/OpenApiPetstoreWithRestComponent.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldResolveEmptyVariables() {
        assertThat(RestDslGenerator.resolveVariablesIn("", new OpenApi30ServerImpl())).isEmpty();
    }

    @Test
    public void shouldResolveMultipleOccurancesOfVariables() {
        final OpenApi30Server server = new OpenApi30ServerImpl();
        addVariableTo(server, "var1", "value1");
        addVariableTo(server, "var2", "value2");

        assertThat(RestDslGenerator.resolveVariablesIn("{var2} before {var1} after {var2}", server))
                .isEqualTo("value2 before value1 after value2");
    }

    @Test
    public void shouldResolveMultipleVariables() {
        final OpenApi30Server server = new OpenApi30ServerImpl();
        addVariableTo(server, "var1", "value1");
        addVariableTo(server, "var2", "value2");

        assertThat(RestDslGenerator.resolveVariablesIn("before {var1} after {var2}", server))
                .isEqualTo("before value1 after value2");
    }

    @Test
    public void shouldResolveSingleVariable() {
        final OpenApi30Server server = new OpenApi30ServerImpl();
        addVariableTo(server, "var", "value");
        assertThat(RestDslGenerator.resolveVariablesIn("before {var} after", server)).isEqualTo("before value after");
    }

    @BeforeAll
    public static void readOpenApiDoc() throws Exception {
        try (InputStream is = RestDslGeneratorTest.class.getResourceAsStream("openapi-v2.json")) {
            String json = IOHelper.loadText(is);
            document = (OpenApiDocument) Library.readDocumentFromJSONString(json);
        }
    }

    private static void addVariableTo(final OpenApi30Server server, final String name, final String value) {
        final OpenApi30ServerVariable variable = new OpenApi30ServerVariableImpl();
        variable.setDefault(value);
        server.addVariable(name, variable);
    }
}
