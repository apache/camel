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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The externalReferences option: which $ref the specification may use, checked before the parser resolves them. The
 * server stands in for every URL a specification could reach, so that a test can assert that nothing was fetched.
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

    private static final String OWNER_SCHEMA = """
            type: object
            properties:
              name:
                type: string
            """;

    private final CamelContext camelContext = new DefaultCamelContext();

    @BeforeAll
    static void startServer() {
        SERVER.start();
        SERVER.stubFor(get(urlEqualTo("/petstore.yaml")).willReturn(yaml(contract("pet.yaml#/components/schemas/Pet"))));
        SERVER.stubFor(get(urlEqualTo("/pet.yaml")).willReturn(yaml(PET_SCHEMAS)));
        SERVER.stubFor(get(urlEqualTo("/self-contained.yaml"))
                .willReturn(yaml(contract("'#/components/schemas/Pet'") + PET_SCHEMAS)));
        SERVER.stubFor(get(urlEqualTo("/schema.yaml")).willReturn(yaml(OWNER_SCHEMA)));
        SERVER.stubFor(get(urlEqualTo("/schemas/owner.yaml")).willReturn(yaml(OWNER_SCHEMA)));
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
        Path file = Files.writeString(dir.resolve("contract.yaml"),
                contract(SERVER.baseUrl() + "/pet.yaml#/components/schemas/Pet"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + file, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SERVER.baseUrl() + "/pet.yaml#/components/schemas/Pet")
                .hasMessageContaining("externalReferences=local allows no http(s) reference");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
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
    void localResolvesTheReferencesOfADocumentInASubdirectory() {
        OpenAPI openApi
                = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "external-references-subdir.yaml", "local");

        // the parser read the Owner reference of schemas/external-references-pet.yaml next to that document
        Schema<?> owner = (Schema<?>) petSchema(openApi).getProperties().get("owner");
        assertThat(owner.getProperties()).containsKey("name");
    }

    @Test
    void localChecksTheReferencesOfADocumentInASubdirectory() {
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext,
                "external-references-subdir-remote.yaml", "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https://schemas.example.invalid/address.yaml#/components/schemas/Address")
                .hasMessageContaining(
                        "at components.schemas.Owner.properties.address.$ref in schemas/external-references-owner-remote.yaml")
                .hasMessageContaining("externalReferences=local");
    }

    @Test
    void localChecksADocumentReferencedByAbsolutePath(@TempDir Path dir) throws Exception {
        Path specs = Files.createDirectory(dir.resolve("specs"));
        Files.writeString(specs.resolve("first.yaml"), """
                components:
                  schemas:
                    Pet:
                      type: object
                      properties:
                        owner:
                          $ref: 'second.yaml#/components/schemas/Owner'
                """);
        Files.writeString(specs.resolve("second.yaml"), """
                components:
                  schemas:
                    Owner:
                      $ref: '%s/schema.yaml'
                """.formatted(SERVER.baseUrl()));
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract(specs.resolve("first.yaml") + "#/components/schemas/Pet"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SERVER.baseUrl() + "/schema.yaml")
                // the parser reads second.yaml next to first.yaml, not from the working directory
                .hasMessageContaining("in " + specs.resolve("second.yaml"))
                .hasMessageContaining("externalReferences=local");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void localFailsWhenAReferencedDocumentCannotBeRead(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract("missing.yaml#/components/schemas/Pet"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references missing.yaml#/components/schemas/Pet")
                .hasMessageContaining("cannot be read to check its references");
        // the default lets the parser report the missing document and keep the rest
        assertThat(RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root).getPaths())
                .containsKey("/pets/{petId}");
    }

    @Test
    void noneRejectsAReferenceGivenAsAYamlAlias(@TempDir Path dir) throws Exception {
        // an anchor whose name starts with # looks like a reference inside the document before the alias is expanded
        Path root = Files.writeString(dir.resolve("root.yaml"), "x-schema: &#remote '" + SERVER.baseUrl() + "/schema.yaml'\n"
                                                                + contract("*#remote"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "none"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + SERVER.baseUrl() + "/schema.yaml")
                .hasMessageContaining("externalReferences=none");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void localRejectsAReferenceGivenAsAYamlAlias(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), "x-schema: &remote '" + SERVER.baseUrl() + "/schema.yaml'\n"
                                                                + contract("*remote"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + SERVER.baseUrl() + "/schema.yaml")
                .hasMessageContaining("externalReferences=local allows no http(s) reference");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAReferenceGivenAsAYamlAlias(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), "x-schema: &remote '" + SERVER.baseUrl() + "/schema.yaml'\n"
                                                                + contract("*remote"));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        // the parser expands the alias and fetches the schema
        assertThat(petSchema(openApi).getProperties()).containsKey("name");
        SERVER.verify(1, getRequestedFor(urlEqualTo("/schema.yaml")));
    }

    @Test
    void localRejectsAnOpenApi31ReferenceResolvedAgainstAnHttpId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(SERVER.baseUrl() + "/schemas/", "owner.yaml"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references owner.yaml at components.schemas.Pet.properties.owner.$ref")
                .hasMessageContaining("(owner.yaml resolves to " + SERVER.baseUrl() + "/schemas/owner.yaml)")
                .hasMessageContaining("externalReferences=local");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAnOpenApi31ReferenceAgainstItsId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(SERVER.baseUrl() + "/schemas/", "owner.yaml"));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        // the parser resolves the reference against the $id of the schema and fetches it
        Schema<?> owner = (Schema<?>) petSchema(openApi).getProperties().get("owner");
        assertThat(owner.getProperties()).containsKey("name");
        SERVER.verify(1, getRequestedFor(urlEqualTo("/schemas/owner.yaml")));
    }

    @Test
    void localKeepsTheInheritedIdAcrossAnExternalReference(@TempDir Path dir) throws Exception {
        Path bridge = Files.writeString(dir.resolve("bridge.yaml"), """
                type: object
                properties:
                  owner:
                    $ref: owner.yaml
                """);
        Files.writeString(dir.resolve("owner.yaml"), OWNER_SCHEMA);
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(SERVER.baseUrl() + "/schemas/", bridge.toUri().toString()));

        // the parser carries the $id of the Pet schema into bridge.yaml, so owner.yaml is not the file next to it
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references owner.yaml at properties.owner.$ref in " + bridge.toUri())
                .hasMessageContaining("(owner.yaml resolves to " + SERVER.baseUrl() + "/schemas/owner.yaml)")
                .hasMessageContaining("externalReferences=local");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAnOpenApi31ReferenceAgainstTheInheritedId(@TempDir Path dir) throws Exception {
        Path bridge = Files.writeString(dir.resolve("bridge.yaml"), """
                type: object
                properties:
                  owner:
                    $ref: owner.yaml
                """);
        Files.writeString(dir.resolve("owner.yaml"), OWNER_SCHEMA);
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(SERVER.baseUrl() + "/schemas/", bridge.toUri().toString()));

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        // the parser fetches owner.yaml from the server of the inherited $id, not from the directory of bridge.yaml
        SERVER.verify(1, getRequestedFor(urlEqualTo("/schemas/owner.yaml")));
    }

    @Test
    void noneAllowsAReferenceInsideTheDocumentBelowAnHttpId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(SERVER.baseUrl() + "/schemas/", "#/components/schemas/Owner") + """
                            Owner:
                              type: object
                              properties:
                                name:
                                  type: string
                        """);

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "none");

        // the parser evaluates the fragment in the document, whatever the $id above it
        Schema<?> owner = (Schema<?>) petSchema(openApi).getProperties().get("owner");
        assertThat(owner.getProperties()).containsKey("name");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void localChecksAnOpenApi31FileReference(@TempDir Path dir) throws Exception {
        Path schema = Files.writeString(dir.resolve("schema.yaml"), """
                components:
                  schemas:
                    Pet:
                      type: object
                      properties:
                        owner:
                          $ref: '%s/schema.yaml'
                """.formatted(SERVER.baseUrl()));
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(null, schema.toUri() + "#/components/schemas/Pet"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + SERVER.baseUrl() + "/schema.yaml")
                .hasMessageContaining("in " + schema.toUri())
                .hasMessageContaining("externalReferences=local");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void localAllowsAnOpenApi31FileReference(@TempDir Path dir) throws Exception {
        Path schema = Files.writeString(dir.resolve("schema.yaml"), PET_SCHEMAS);
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(null, schema.toUri() + "#/components/schemas/Pet"));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local");

        Schema<?> owner = (Schema<?>) petSchema(openApi).getProperties().get("owner");
        assertThat(owner.getProperties()).containsKeys("id", "name");
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

    private static ResponseDefinitionBuilder yaml(String body) {
        return aResponse().withHeader("Content-Type", "application/yaml").withBody(body);
    }

    private static Schema<?> petSchema(OpenAPI openApi) {
        return openApi.getPaths().get("/pets/{petId}").getGet().getResponses().get("200").getContent()
                .get("application/json").getSchema();
    }

    /**
     * An OpenAPI 3.0 contract whose response schema is the given reference, written as given (so a YAML alias stays an
     * alias; a reference that starts with # must be quoted).
     */
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
                                $ref: %s
                """.formatted(petSchemaRef);
    }

    /**
     * An OpenAPI 3.1 contract whose Pet schema has the given $id (none when null) and whose owner property is the given
     * reference. Further schemas can be appended under components.schemas.
     */
    private static String contract31(String petId, String ownerRef) {
        return """
                openapi: 3.1.0
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
                                $ref: '#/components/schemas/Pet'
                components:
                  schemas:
                    Pet:
                %s      type: object
                      properties:
                        owner:
                          $ref: '%s'
                """.formatted(petId != null ? "      $id: " + petId + "\n" : "", ownerRef);
    }
}
