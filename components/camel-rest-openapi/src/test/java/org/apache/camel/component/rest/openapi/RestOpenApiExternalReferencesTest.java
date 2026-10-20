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
package org.apache.camel.component.rest.openapi;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The externalReferences option: which $ref the specification may use, checked before the parser resolves them.
 */
class RestOpenApiExternalReferencesTest {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    private static final String PET_SCHEMAS = """
            components:
              schemas:
                Pet:
                  type: object
                  properties:
                    id:
                      type: integer
                    name:
                      type: string
            """;

    private final CamelContext camelContext = new DefaultCamelContext();

    @BeforeAll
    static void startServer() {
        SERVER.start();
        SERVER.stubFor(get(urlEqualTo("/petstore.yaml"))
                .willReturn(aResponse().withHeader("Content-Type", "application/yaml")
                        .withBody(contract("pet.yaml#/components/schemas/Pet"))));
        SERVER.stubFor(get(urlEqualTo("/pet.yaml"))
                .willReturn(aResponse().withHeader("Content-Type", "application/yaml").withBody(PET_SCHEMAS)));
        SERVER.stubFor(get(urlEqualTo("/self-contained.yaml"))
                .willReturn(aResponse().withHeader("Content-Type", "application/yaml")
                        .withBody(contract("#/components/schemas/Pet") + PET_SCHEMAS)));
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop();
    }

    @BeforeEach
    void resetRequests() {
        SERVER.resetRequests();
    }

    @Test
    void allowsEveryReferenceByDefault() {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "external-references-root.yaml");

        assertThat(petSchema(openApi).getProperties()).containsKeys("id", "name");
    }

    @Test
    void localAllowsAReferenceToTheClasspath() {
        OpenAPI openApi
                = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "external-references-root.yaml", "local");

        assertThat(petSchema(openApi).getProperties()).containsKeys("id", "name");
    }

    @Test
    void noneRejectsAReferenceToAnotherDocument() {
        assertThatThrownBy(
                () -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "external-references-root.yaml", "none"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("external-references-root.yaml")
                .hasMessageContaining("external-references-pet.yaml#/components/schemas/Pet")
                .hasMessageContaining("at paths./pets/{petId}.get.responses.200.content.application/json.schema.$ref")
                .hasMessageContaining("externalReferences=none");
    }

    @Test
    void noneAllowsASelfContainedSpecification() {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "openapi-v3.yaml", "none");

        assertThat(openApi.getPaths()).isNotEmpty();
    }

    @Test
    void noneRejectsTheExternalReferencesOfAnOpenApi31Document() {
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "petstore-3.1.yaml", "none"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/components/schemas/petdetails")
                .hasMessageContaining("externalReferences=none");
    }

    @Test
    void localRejectsAnHttpReferenceWithoutFetchingIt(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("contract.yaml");
        Files.writeString(file, contract(SERVER.baseUrl() + "/pet.yaml#/components/schemas/Pet"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + file, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SERVER.baseUrl() + "/pet.yaml#/components/schemas/Pet")
                .hasMessageContaining("externalReferences=local allows no http(s) reference");
        SERVER.verify(0, getRequestedFor(urlEqualTo("/pet.yaml")));
    }

    @Test
    void localChecksTheDocumentsTheSpecificationReferences() {
        assertThatThrownBy(
                () -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "external-references-nested.yaml", "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https://schemas.example.invalid/owner.yaml#/components/schemas/Owner")
                .hasMessageContaining("at components.schemas.Pet.properties.owner.$ref in external-references-pet-remote.yaml")
                .hasMessageContaining("externalReferences=local");
    }

    @Test
    void localRejectsEveryExternalReferenceOfARemoteSpecification() {
        String uri = SERVER.baseUrl() + "/petstore.yaml";

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, uri, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pet.yaml#/components/schemas/Pet")
                .hasMessageContaining("loaded from a URL");
        SERVER.verify(1, getRequestedFor(urlEqualTo("/petstore.yaml")));
        SERVER.verify(0, getRequestedFor(urlEqualTo("/pet.yaml")));
    }

    @Test
    void allResolvesTheReferencesOfARemoteSpecification() {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, SERVER.baseUrl() + "/petstore.yaml");

        assertThat(petSchema(openApi).getProperties()).containsKeys("id", "name");
        SERVER.verify(1, getRequestedFor(urlEqualTo("/petstore.yaml")));
        SERVER.verify(1, getRequestedFor(urlEqualTo("/pet.yaml")));
    }

    @Test
    void noneAllowsASelfContainedRemoteSpecification() {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext,
                SERVER.baseUrl() + "/self-contained.yaml", "none");

        assertThat(petSchema(openApi).getProperties()).containsKeys("id", "name");
        SERVER.verify(1, getRequestedFor(urlEqualTo("/self-contained.yaml")));
    }

    @Test
    void rejectsAnUnknownValue() {
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "openapi-v3.yaml", "remote"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown externalReferences value: remote, expected all, local or none");
    }

    @Test
    void endpointOptionAppliesToTheProducer() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint = context
                    .getEndpoint("rest-openapi:external-references-root.yaml#getPetById?externalReferences=none");

            assertThatThrownBy(endpoint::createProducer)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("externalReferences=none");
        }
    }

    @Test
    void componentOptionAppliesToEveryEndpoint() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            context.getComponent("rest-openapi", RestOpenApiComponent.class).setExternalReferences("none");
            Endpoint endpoint = context.getEndpoint("rest-openapi:external-references-root.yaml#getPetById");

            assertThatThrownBy(endpoint::createProducer)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("externalReferences=none");
        }
    }

    private static Schema<?> petSchema(OpenAPI openApi) {
        return openApi.getPaths().get("/pets/{petId}").getGet().getResponses().get("200").getContent()
                .get("application/json").getSchema();
    }

    private static String contract(String petSchemaRef) {
        return """
                openapi: 3.0.3
                info:
                  title: Pets
                  version: "1.0"
                paths:
                  /pets/{petId}:
                    get:
                      operationId: getPetById
                      parameters:
                        - name: petId
                          in: path
                          required: true
                          schema:
                            type: integer
                      responses:
                        "200":
                          description: The pet
                          content:
                            application/json:
                              schema:
                                $ref: '%s'
                """.formatted(petSchemaRef);
    }
}
