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

import java.io.IOException;
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
    void localFinishesForASchemaThatReferencesItself(@TempDir Path dir) throws Exception {
        Path node = dir.resolve("node.yaml");
        Files.writeString(node, """
                $id: %s
                type: object
                properties:
                  next:
                    $ref: %s
                """.formatted(node.toUri(), node.toUri()));
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(null, node.toUri().toString()));

        // the schema is reached again under its own $id, with the same base, so the check visits it once
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local");

        assertThat(openApi.getPaths()).containsKey("/pets/{petId}");
    }

    @Test
    void localChecksADocumentReachedAgainUnderAnotherId(@TempDir Path dir) throws Exception {
        Path shared = Files.writeString(dir.resolve("shared.yaml"), """
                type: object
                properties:
                  owner:
                    $ref: owner.yaml
                """);
        Files.writeString(dir.resolve("owner.yaml"), OWNER_SCHEMA);
        Path root = Files.writeString(dir.resolve("root.yaml"), """
                openapi: 3.1.0
                info:
                  title: Pets
                  version: "1.0"
                paths:
                  /pets/{petId}:
                    get:
                      operationId: getPetById
                      responses:
                        "200":
                          description: The pet
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/Pet'
                components:
                  schemas:
                    Safe:
                      type: object
                      properties:
                        shared:
                          $ref: '%s'
                    Pet:
                      $id: %s/schemas/
                      type: object
                      properties:
                        shared:
                          $ref: '%s'
                """.formatted(shared.toUri(), SERVER.baseUrl(), shared.toUri()));

        // shared.yaml is first reached without an $id, where owner.yaml is the file next to it, then below the $id of
        // Pet, where the parser resolves owner.yaml against the server: the second visit must not be skipped
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references owner.yaml at properties.owner.$ref in " + shared.toUri())
                .hasMessageContaining("(owner.yaml resolves to " + SERVER.baseUrl() + "/schemas/owner.yaml)");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allLoadsASchemaThatReferencesItselfBelowARelativeId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(null, relativeIdNode(dir).toUri().toString()));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        assertThat(openApi.getPaths()).containsKey("/pets/{petId}");
    }

    @Test
    void localFinishesForASchemaThatReferencesItselfBelowARelativeId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(null, relativeIdNode(dir).toUri().toString()));

        // the relative $id moves the base one level further on every visit; the check stops where the parser stops, at
        // the reference it is already resolving
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local");

        assertThat(openApi.getPaths()).containsKey("/pets/{petId}");
    }

    @Test
    void allLoadsACycleOfTwoDocumentsWithARelativeId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(null, twoDocumentCycle(dir).toUri().toString()));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        assertThat(openApi.getPaths()).containsKey("/pets/{petId}");
    }

    @Test
    void localFinishesForACycleOfTwoDocumentsWithARelativeId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), contract31(null, twoDocumentCycle(dir).toUri().toString()));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local");

        assertThat(openApi.getPaths()).containsKey("/pets/{petId}");
    }

    @Test
    void localChecksTheSecondVisitOfACycleBelowARelativeId(@TempDir Path dir) throws Exception {
        Path root = secondVisitContract(dir);

        // on the second visit owner.yaml resolves against sub/sub/, where the owner references the server
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + SERVER.baseUrl() + "/schema.yaml at properties.name.$ref in file:"
                                      + dir.resolve("sub/sub/owner.yaml"))
                .hasMessageContaining("externalReferences=local allows no http(s) reference");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesTheSecondVisitOfACycleBelowARelativeId(@TempDir Path dir) throws Exception {
        Path root = secondVisitContract(dir);

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        // the parser walks node.yaml a second time, below the $id it added on the first visit
        SERVER.verify(1, getRequestedFor(urlEqualTo("/schema.yaml")));
    }

    @Test
    void localChecksAFragmentAgainstTheInheritedIdOnly(@TempDir Path dir) throws Exception {
        Path root = fragmentBelowAnHttpId(dir);

        // the parser deserializes only X, so owner.yaml resolves against the $id of Pet, not the $id of other.yaml
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references owner.yaml at properties.owner.$ref in "
                                      + dir.resolve("other.yaml").toUri() + "#/$defs/X")
                .hasMessageContaining("(owner.yaml resolves to " + SERVER.baseUrl() + "/schemas/owner.yaml)");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAFragmentAgainstTheInheritedId(@TempDir Path dir) throws Exception {
        Path root = fragmentBelowAnHttpId(dir);

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        SERVER.verify(1, getRequestedFor(urlEqualTo("/schemas/owner.yaml")));
    }

    @Test
    void localChecksAFragmentWithoutTheIdsAboveIt(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(null, documentBelowAnHttpId(dir).toUri() + "#/$defs/Owner"));

        // name.yaml is the file next to other.yaml: the $id at the root of other.yaml is not above the node the parser
        // deserializes
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local");

        Schema<?> owner = (Schema<?>) petSchema(openApi).getProperties().get("owner");
        assertThat(owner.getProperties()).containsKey("name");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void localChecksTheSchemaAnAnchorSelects(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(null, documentBelowAnHttpId(dir).toUri() + "#Owner"));

        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local");

        Schema<?> owner = (Schema<?>) petSchema(openApi).getProperties().get("owner");
        assertThat(owner.getProperties()).containsKey("name");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void localFailsWhenAFragmentCannotBeFound(@TempDir Path dir) throws Exception {
        Path schema = Files.writeString(dir.resolve("schema.yaml"), PET_SCHEMAS);
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(null, schema.toUri() + "#/components/schemas/Missing"));

        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + schema.toUri() + "#/components/schemas/Missing")
                .hasMessageContaining("#/components/schemas/Missing cannot be found in " + schema.toUri());
    }

    @Test
    void localFollowsAReferenceInsideAReferencedDocument(@TempDir Path dir) throws Exception {
        Path root = referenceInsideAReferencedDocument(dir);

        // the parser resolves #/$defs/Y in hop.yaml, and owner.yaml in Y against the $id of X, where the reference is
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references owner.yaml at properties.owner.$ref in "
                                      + dir.resolve("hop.yaml").toUri() + "#/$defs/Y")
                .hasMessageContaining("(owner.yaml resolves to " + SERVER.baseUrl() + "/schemas/owner.yaml)");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAReferenceInsideAReferencedDocument(@TempDir Path dir) throws Exception {
        Path root = referenceInsideAReferencedDocument(dir);

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        SERVER.verify(1, getRequestedFor(urlEqualTo("/schemas/owner.yaml")));
    }

    @Test
    void localRejectsAReferenceToADocumentThatAnIdElsewhereClaims(@TempDir Path dir) throws Exception {
        Path root = claimedDocumentContract(dir);

        // the $id of Alias in other.yaml resolves to the location of claimed.yaml, so the parser can take Owner from
        // other.yaml instead of claimed.yaml
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + dir.resolve("claimed.yaml").toUri()
                                      + "#/$defs/Owner at components.schemas.Pet.properties.owner.$ref")
                .hasMessageContaining(dir.resolve("claimed.yaml").toUri() + " is also the $id of a schema in "
                                      + dir.resolve("other.yaml").toUri() + "#/$defs/Alias");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAReferenceInTheDocumentThatClaimsItsLocation(@TempDir Path dir) throws Exception {
        Path root = claimedDocumentContract(dir);

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        // the parser met the $id first, so it took Owner from other.yaml, which references the server
        SERVER.verify(1, getRequestedFor(urlEqualTo("/schema.yaml")));
    }

    @Test
    void localChecksADocumentReachedAgainWithIdsThatGiveTheSameBaseThere(@TempDir Path dir) throws Exception {
        Path root = sameBaseContract(dir);

        // entry.yaml reaches s.yaml without an $id and below ../<dir>/s.yaml, which gives the same base at s.yaml but
        // another one at other/t.yaml, where owner.yaml is then a document that references the server
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references " + SERVER.baseUrl() + "/schema.yaml at properties.name.$ref in file:"
                                      + dir.resolve(dir.getFileName().toString()).resolve("sub/owner.yaml"))
                .hasMessageContaining("externalReferences=local allows no http(s) reference");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesADocumentReachedAgainWithIdsThatGiveTheSameBaseThere(@TempDir Path dir) throws Exception {
        Path root = sameBaseContract(dir);

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        // the parser walks the properties of entry.yaml before its allOf, so it reaches s.yaml below the $id first
        SERVER.verify(1, getRequestedFor(urlEqualTo("/schema.yaml")));
    }

    /**
     * A schema that references itself below a relative $id.
     */
    private static Path relativeIdNode(Path dir) throws IOException {
        Path node = dir.resolve("node.yaml");
        return Files.writeString(node, """
                $id: sub/
                type: object
                properties:
                  next:
                    $ref: %s
                """.formatted(node.toUri()));
    }

    /**
     * Two schemas that reference each other, the first below a relative $id; returns the first.
     */
    private static Path twoDocumentCycle(Path dir) throws IOException {
        Path first = dir.resolve("first.yaml");
        Path second = dir.resolve("second.yaml");
        Files.writeString(first, """
                $id: sub/
                type: object
                properties:
                  second:
                    $ref: %s
                """.formatted(second.toUri()));
        Files.writeString(second, """
                type: object
                properties:
                  first:
                    $ref: %s
                """.formatted(first.toUri()));
        return first;
    }

    /**
     * A contract whose Pet owner is a schema that references itself below a relative $id. The kind property is resolved
     * before the cycle and changes the schema the parser is visiting, so the parser walks it a second time, one level
     * further, and stops at the next reference there. The owner.yaml of the first visit is harmless, the one of the
     * second references the server.
     */
    private static Path secondVisitContract(Path dir) throws IOException {
        Path node = dir.resolve("node.yaml");
        Path kind = Files.writeString(dir.resolve("kind.yaml"), "type: string\n");
        Files.writeString(node, """
                $id: sub/
                type: object
                properties:
                  kind:
                    $ref: %s
                  next:
                    description: The next node
                    $ref: %s
                  owner:
                    $ref: owner.yaml
                """.formatted(kind.toUri(), node.toUri()));
        Path sub = Files.createDirectories(dir.resolve("sub/sub"));
        Files.writeString(dir.resolve("sub/owner.yaml"), OWNER_SCHEMA);
        Files.writeString(sub.resolve("owner.yaml"), """
                type: object
                properties:
                  name:
                    $ref: '%s/schema.yaml'
                """.formatted(SERVER.baseUrl()));
        return Files.writeString(dir.resolve("root.yaml"), contract31(null, node.toUri().toString()));
    }

    /**
     * A contract whose Pet, below an http $id, references the schema X of other.yaml, whose own $id is its location.
     */
    private static Path fragmentBelowAnHttpId(Path dir) throws IOException {
        Path other = dir.resolve("other.yaml");
        Files.writeString(other, """
                $id: %s
                $defs:
                  X:
                    type: object
                    properties:
                      owner:
                        $ref: owner.yaml
                """.formatted(other.toUri()));
        Files.writeString(dir.resolve("owner.yaml"), OWNER_SCHEMA);
        return Files.writeString(dir.resolve("root.yaml"),
                contract31(SERVER.baseUrl() + "/schemas/", other.toUri() + "#/$defs/X"));
    }

    /**
     * A document with an http $id at its root and an Owner schema (also the anchor Owner) whose name is the file
     * name.yaml next to it.
     */
    private static Path documentBelowAnHttpId(Path dir) throws IOException {
        Files.writeString(dir.resolve("name.yaml"), "type: string\n");
        return Files.writeString(dir.resolve("other.yaml"), """
                $id: %s/other/
                $defs:
                  Owner:
                    $anchor: Owner
                    type: object
                    properties:
                      name:
                        $ref: name.yaml
                """.formatted(SERVER.baseUrl()));
    }

    /**
     * A contract whose Pet, below an http $id, references the schema Owner of the same specification, which references
     * owner.yaml.
     */
    private static Path referenceInsideTheSpecificationBelowAnHttpId(Path dir) throws IOException {
        return Files.writeString(dir.resolve("root.yaml"),
                contract31(SERVER.baseUrl() + "/schemas/", "#/components/schemas/Owner") + """
                            Owner:
                              type: object
                              properties:
                                owner:
                                  $ref: owner.yaml
                        """);
    }

    /**
     * A contract whose Pet references the schema X of hop.yaml; X has an http $id and references Y in the same
     * document, whose owner.yaml the parser resolves against that $id.
     */
    private static Path referenceInsideAReferencedDocument(Path dir) throws IOException {
        Path hop = Files.writeString(dir.resolve("hop.yaml"), """
                $defs:
                  X:
                    $id: %s/schemas/
                    type: object
                    properties:
                      y:
                        $ref: '#/$defs/Y'
                  Y:
                    type: object
                    properties:
                      owner:
                        $ref: owner.yaml
                """.formatted(SERVER.baseUrl()));
        Files.writeString(dir.resolve("owner.yaml"), OWNER_SCHEMA);
        return Files.writeString(dir.resolve("root.yaml"), contract31(null, hop.toUri() + "#/$defs/X"));
    }

    /**
     * A contract that references the Alias schema of other.yaml, whose $id is the location of claimed.yaml, and then
     * Owner in claimed.yaml. other.yaml has an Owner too, which references the server.
     */
    private static Path claimedDocumentContract(Path dir) throws IOException {
        Path claimed = Files.writeString(dir.resolve("claimed.yaml"), """
                $defs:
                  Owner:
                    type: object
                """);
        Path other = Files.writeString(dir.resolve("other.yaml"), """
                $defs:
                  Alias:
                    $id: %s
                    type: object
                  Owner:
                    type: object
                    properties:
                      name:
                        $ref: '%s/schema.yaml'
                """.formatted(claimed.toUri(), SERVER.baseUrl()));
        return Files.writeString(dir.resolve("root.yaml"), """
                openapi: 3.1.0
                info:
                  title: Pets
                  version: "1.0"
                paths: {}
                components:
                  schemas:
                    Alias:
                      $ref: '%s#/$defs/Alias'
                    Pet:
                      type: object
                      properties:
                        owner:
                          $ref: '%s#/$defs/Owner'
                """.formatted(other.toUri(), claimed.toUri()));
    }

    /**
     * A contract whose Pet owner is a/entry.yaml, which reaches s.yaml twice by a relative reference: through its allOf
     * without an $id, and through a property below the relative $id ../&lt;dir&gt;/s.yaml. That $id gives the base of
     * s.yaml at s.yaml itself, but not at other/t.yaml, which s.yaml references below the $id sub/, nor at entry.yaml,
     * so it is not the $id of another document.
     */
    private static Path sameBaseContract(Path dir) throws IOException {
        Path other = Files.createDirectories(dir.resolve("other/sub"));
        Files.writeString(other.resolve("owner.yaml"), OWNER_SCHEMA);
        Path t = Files.writeString(dir.resolve("other/t.yaml"), """
                type: object
                properties:
                  owner:
                    $ref: owner.yaml
                """);
        Path far = Files.createDirectories(dir.resolve(dir.getFileName().toString()).resolve("sub"));
        Files.writeString(far.resolve("owner.yaml"), """
                type: object
                properties:
                  name:
                    $ref: '%s/schema.yaml'
                """.formatted(SERVER.baseUrl()));
        Files.writeString(dir.resolve("s.yaml"), """
                type: object
                properties:
                  t:
                    $id: sub/
                    $ref: %s
                """.formatted(t.toUri()));
        Path entry = Files.createDirectory(dir.resolve("a")).resolve("entry.yaml");
        Files.writeString(entry, """
                type: object
                allOf:
                  - $ref: ../s.yaml
                properties:
                  pet:
                    $id: ../%s/s.yaml
                    $ref: ../s.yaml
                """.formatted(dir.getFileName()));
        return Files.writeString(dir.resolve("root.yaml"), contract31(null, entry.toUri().toString()));
    }

    @Test
    void localFollowsAReferenceInsideTheSpecificationBelowAnHttpId(@TempDir Path dir) throws Exception {
        Path root = referenceInsideTheSpecificationBelowAnHttpId(dir);

        // the parser deserializes only Owner, so owner.yaml resolves against the $id of Pet, not against the specification
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("(owner.yaml resolves to " + SERVER.baseUrl() + "/schemas/owner.yaml)");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void allResolvesAReferenceInsideTheSpecificationBelowAnHttpId(@TempDir Path dir) throws Exception {
        Path root = referenceInsideTheSpecificationBelowAnHttpId(dir);

        RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root);

        SERVER.verify(1, getRequestedFor(urlEqualTo("/schemas/owner.yaml")));
    }

    @Test
    void localFollowsAReferenceInsideTheSpecificationIntoAFragmentBelowAnId(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectories(dir.resolve("sub"));
        Files.writeString(sub.resolve("external-references-owner-remote.yaml"), OWNER_SCHEMA);
        Path root = Files.writeString(dir.resolve("root.yaml"),
                contract31(null, "#/components/schemas/Holder/$defs/X") + """
                            Holder:
                              $id: %s
                              type: object
                              $defs:
                                X:
                                  type: object
                                  properties:
                                    owner:
                                      $ref: external-references-owner-remote.yaml
                        """.formatted(sub.toUri()));

        // the parser deserializes only X, so the reference resolves against the specification, not against the $id of Holder
        assertThatThrownBy(() -> RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "file:" + root, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references https://schemas.example.invalid/name.yaml");
        SERVER.verify(0, anyRequestedFor(anyUrl()));
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
