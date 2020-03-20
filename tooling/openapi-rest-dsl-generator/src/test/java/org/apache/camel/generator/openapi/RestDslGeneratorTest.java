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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Server;
import io.apicurio.datamodels.openapi.v3.models.Oas30ServerVariable;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestsDefinition;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslGeneratorTest {

    static OasDocument document;

    final Instant generated = Instant.parse("2017-10-17T00:00:00.000Z");

    @Test
    public void shouldCreateDefinitions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            final RestsDefinition definition = RestDslGenerator.toDefinition(document).generate(context);
            assertThat(definition).isNotNull();
            assertThat(definition.getRests()).hasSize(1);
            assertThat(definition.getRests().get(0).getPath()).isEqualTo("/v2");
        }
    }

    @Test
    public void shouldDetermineBasePathFromV2Document() {
        final Oas20Document oas20Document = new Oas20Document();
        oas20Document.basePath = "/api";
        assertThat(RestDslGenerator.determineBasePathFrom(oas20Document)).isEqualTo("/api");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsServerUrl() {
        final Oas30Document oas30Document = new Oas30Document();
        final Oas30Server server = new Oas30Server();
        server.url = "https://example.com/api";

        oas30Document.servers = Collections.singletonList(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsServerUrlWithTemplateVariables() {
        final Oas30Document oas30Document = new Oas30Document();
        final Oas30Server server = new Oas30Server();
        addVariableTo(server, "base", "api");
        addVariableTo(server, "path", "v3");
        server.url = "https://example.com/{base}/{path}";

        oas30Document.servers = Collections.singletonList(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api/v3");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsWhenServerUrlIsRelative() {
        final Oas30Document oas30Document = new Oas30Document();
        final Oas30Server server = new Oas30Server();
        server.url = "/api/v3";

        oas30Document.servers = Collections.singletonList(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api/v3");
    }

    @Test
    public void shouldDetermineBasePathFromV3DocumentsWhenServerUrlIsRelativeWithoutStartingSlash() {
        final Oas30Document oas30Document = new Oas30Document();
        final Oas30Server server = new Oas30Server();
        server.url = "api/v3";

        oas30Document.servers = Collections.singletonList(server);
        assertThat(RestDslGenerator.determineBasePathFrom(oas30Document)).isEqualTo("/api/v3");
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
            .withDestinationGenerator(o -> "direct:rest-" + o.operationId)
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
            .withDestinationGenerator(o -> "direct:rest-" + o.operationId).generate(code);

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
        assertThat(RestDslGenerator.resolveVariablesIn("", new Oas30Server())).isEmpty();
    }

    @Test
    public void shouldResolveMultipleOccurancesOfVariables() {
        final Oas30Server server = new Oas30Server();
        addVariableTo(server, "var1", "value1");
        addVariableTo(server, "var2", "value2");

        assertThat(RestDslGenerator.resolveVariablesIn("{var2} before {var1} after {var2}", server)).isEqualTo("value2 before value1 after value2");
    }

    @Test
    public void shouldResolveMultipleVariables() {
        final Oas30Server server = new Oas30Server();
        addVariableTo(server, "var1", "value1");
        addVariableTo(server, "var2", "value2");

        assertThat(RestDslGenerator.resolveVariablesIn("before {var1} after {var2}", server)).isEqualTo("before value1 after value2");
    }

    @Test
    public void shouldResolveSingleVariable() {
        final Oas30Server server = new Oas30Server();
        addVariableTo(server, "var", "value");
        assertThat(RestDslGenerator.resolveVariablesIn("before {var} after", server)).isEqualTo("before value after");
    }

    @BeforeClass
    public static void readOpenApiDoc() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = RestDslGeneratorTest.class.getResourceAsStream("openapi-v2.json")) {
            final JsonNode node = mapper.readTree(is);
            document = (OasDocument) Library.readDocument(node);
        }
    }

    private static void addVariableTo(final Oas30Server server, final String name, final String value) {
        final Oas30ServerVariable variable = new Oas30ServerVariable(name);
        variable.default_ = value;

        server.addServerVariable(name, variable);
    }
}
