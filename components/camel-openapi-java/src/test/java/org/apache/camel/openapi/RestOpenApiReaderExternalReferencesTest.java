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
package org.apache.camel.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.support.DefaultComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The externalReferences option of a contract-first openApi element, applied to the contract served as api-doc. The
 * contracts that must be rejected reference a host that does not exist; the check runs before the parser, so nothing is
 * contacted.
 */
class RestOpenApiReaderExternalReferencesTest {

    private final CamelContext context = new DefaultCamelContext();

    @BeforeEach
    void start() {
        context.start();
    }

    @AfterEach
    void stop() {
        context.stop();
    }

    @Test
    void resolvesTheReferencedDocumentByDefault() throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi("external-references.yaml");

        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
        // the parser imported the Pet schema from external-references-pet.yaml
        assertNotNull(openApi.getComponents().getSchemas().get("Pet"));
    }

    @Test
    void failsWhenTheContractAllowsNoExternalReference() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references.yaml").externalReferences("none");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("external-references-pet.yaml#/components/schemas/Pet"), e.getMessage());
        assertTrue(e.getMessage().contains("externalReferences=none"), e.getMessage());
    }

    @Test
    void checksTheReferencesOfADocumentInASubdirectory() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references-subdir-remote.yaml").externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the parser reads the Owner reference of schemas/external-references-pet-remote.yaml next to that document
        assertTrue(e.getMessage().contains("https://schemas.example.invalid/address.yaml"), e.getMessage());
        assertTrue(e.getMessage().contains("in schemas/external-references-owner-remote.yaml"), e.getMessage());
    }

    @Test
    void checksAnOpenApi31ReferenceAgainstTheIdOfItsSchema() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references-31-id.yaml").externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("owner.yaml resolves to https://schemas.example.invalid/schemas/owner.yaml"),
                e.getMessage());
    }

    @Test
    void keepsTheInheritedIdAcrossAnExternalReference() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references-31-inherited-id.yaml").externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the parser carries the $id of the Pet schema into the bridge schema it references on the classpath
        assertTrue(e.getMessage().contains("in classpath:///external-references-31-bridge.yaml"), e.getMessage());
        assertTrue(e.getMessage().contains("owner.yaml resolves to https://schemas.example.invalid/schemas/owner.yaml"),
                e.getMessage());
    }

    @Test
    void finishesForASchemaThatReferencesItself() throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references-31-recursive.yaml").externalReferences("local");

        // the schema is reached again under its own $id, with the same base, so the check visits it once
        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
    }

    @Test
    void checksADocumentReachedAgainUnderAnotherId() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references-31-revisit.yaml").externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the shared schema passes without an $id and is checked again below the $id of Pet
        assertTrue(e.getMessage().contains("in classpath:///external-references-31-shared.yaml"), e.getMessage());
        assertTrue(e.getMessage().contains(
                "external-references-31-owner.yaml resolves to https://schemas.example.invalid/schemas/external-references-31-owner.yaml"),
                e.getMessage());
    }

    @Test
    void resolvesASchemaThatReferencesItselfBelowARelativeIdByDefault(@TempDir Path dir) throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi("file:" + contract31(dir, null, relativeIdNode(dir).toUri().toString()));

        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
    }

    @Test
    void finishesForASchemaThatReferencesItselfBelowARelativeId(@TempDir Path dir) throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, relativeIdNode(dir).toUri().toString()))
                .externalReferences("local");

        // the relative $id moves the base one level further on every visit; the check stops where the parser stops, at
        // the reference it is already resolving
        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
    }

    @Test
    void finishesForACycleOfTwoDocumentsWithARelativeId(@TempDir Path dir) throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, twoDocumentCycle(dir).toUri().toString()))
                .externalReferences("local");

        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
    }

    @Test
    void checksTheSecondVisitOfACycleBelowARelativeId(@TempDir Path dir) throws Exception {
        Path node = dir.resolve("node.yaml");
        Path kind = Files.writeString(dir.resolve("kind.yaml"), "type: string\n");
        // kind is resolved before the cycle and changes the schema the parser is visiting, so the parser walks node.yaml
        // a second time, one level further, and stops at the next reference there
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
        Files.writeString(dir.resolve("sub/owner.yaml"), "type: string\n");
        Files.writeString(sub.resolve("owner.yaml"), """
                type: object
                properties:
                  name:
                    $ref: 'https://schemas.example.invalid/schema.yaml'
                """);
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, node.toUri().toString())).externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(
                e.getMessage()
                        .contains("references https://schemas.example.invalid/schema.yaml at properties.name.$ref in file:"
                                  + sub.resolve("owner.yaml")),
                e.getMessage());
    }

    @Test
    void checksAFragmentAgainstTheInheritedIdOnly(@TempDir Path dir) throws Exception {
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
        Files.writeString(dir.resolve("owner.yaml"), "type: string\n");
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, "https://schemas.example.invalid/schemas/",
                other.toUri() + "#/$defs/X")).externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the parser deserializes only X, so owner.yaml resolves against the $id of Pet, not the $id of other.yaml
        assertTrue(e.getMessage().contains("in " + other.toUri() + "#/$defs/X"), e.getMessage());
        assertTrue(e.getMessage().contains("owner.yaml resolves to https://schemas.example.invalid/schemas/owner.yaml"),
                e.getMessage());
    }

    @Test
    void checksAFragmentWithoutTheIdsAboveIt(@TempDir Path dir) throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, documentBelowAnId(dir).toUri() + "#/$defs/Owner"))
                .externalReferences("local");

        // name.yaml is the file next to other.yaml: the $id at the root of other.yaml is not above the node the parser
        // deserializes
        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
    }

    @Test
    void checksTheSchemaAnAnchorSelects(@TempDir Path dir) throws Exception {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, documentBelowAnId(dir).toUri() + "#Owner"))
                .externalReferences("local");

        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets/{petId}"));
    }

    @Test
    void followsAReferenceInsideAReferencedDocument(@TempDir Path dir) throws Exception {
        Path hop = Files.writeString(dir.resolve("hop.yaml"), """
                $defs:
                  X:
                    $id: https://schemas.example.invalid/schemas/
                    type: object
                    properties:
                      y:
                        $ref: '#/$defs/Y'
                  Y:
                    type: object
                    properties:
                      owner:
                        $ref: owner.yaml
                """);
        Files.writeString(dir.resolve("owner.yaml"), "type: string\n");
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, hop.toUri() + "#/$defs/X")).externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the parser resolves #/$defs/Y in hop.yaml, and owner.yaml in Y against the $id of X, where the reference is
        assertTrue(e.getMessage().contains("in " + hop.toUri() + "#/$defs/Y"), e.getMessage());
        assertTrue(e.getMessage().contains("owner.yaml resolves to https://schemas.example.invalid/schemas/owner.yaml"),
                e.getMessage());
    }

    @Test
    void rejectsAReferenceToADocumentThatAnIdElsewhereClaims(@TempDir Path dir) throws Exception {
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
                """.formatted(claimed.toUri()));
        Path root = Files.writeString(dir.resolve("root.yaml"), """
                openapi: 3.1.0
                info:
                  title: Pets
                  version: "1.0"
                servers:
                  - url: http://localhost:8080/api
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
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + root).externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the $id of Alias resolves to the location of claimed.yaml, so the parser can take Owner from other.yaml
        assertTrue(e.getMessage().contains(claimed.toUri() + " is also the $id of a schema in " + other.toUri()
                                           + "#/$defs/Alias"),
                e.getMessage());
    }

    @Test
    void checksADocumentReachedAgainWithIdsThatGiveTheSameBaseThere(@TempDir Path dir) throws Exception {
        Path other = Files.createDirectories(dir.resolve("other/sub"));
        Files.writeString(other.resolve("owner.yaml"), "type: string\n");
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
                    $ref: 'https://schemas.example.invalid/schema.yaml'
                """);
        Files.writeString(dir.resolve("s.yaml"), """
                type: object
                properties:
                  t:
                    $id: sub/
                    $ref: %s
                """.formatted(t.toUri()));
        // entry.yaml reaches s.yaml without an $id and below ../<dir>/s.yaml, which gives the base of s.yaml at s.yaml
        // itself, but not at other/t.yaml, where owner.yaml is then a document that references a server
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
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, entry.toUri().toString())).externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(
                e.getMessage()
                        .contains("references https://schemas.example.invalid/schema.yaml at properties.name.$ref in file:"
                                  + far.resolve("owner.yaml")),
                e.getMessage());
    }

    @Test
    void checksARootFragmentAgainstTheInheritedId(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), """
                openapi: 3.1.0
                info:
                  title: Pets
                  version: "1.0"
                servers:
                  - url: http://localhost:8080/api
                paths:
                  /pets:
                    get:
                      responses:
                        "200":
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/A'
                components:
                  schemas:
                    A:
                      $id: 'https://schemas.example.invalid/schemas/'
                      $ref: '#/components/schemas/X'
                    X:
                      type: object
                      properties:
                        owner:
                          $ref: owner.yaml
                """);
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + root).externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("in local#/components/schemas/X"), e.getMessage());
        assertTrue(e.getMessage().contains("owner.yaml resolves to https://schemas.example.invalid/schemas/owner.yaml"),
                e.getMessage());
    }

    @Test
    void resolvesARootFragmentAgainstTheInheritedIdByDefault(@TempDir Path dir) throws Exception {
        Path root = Files.writeString(dir.resolve("root.yaml"), """
                openapi: 3.1.0
                info:
                  title: Pets
                  version: "1.0"
                servers:
                  - url: http://localhost:8080/api
                paths:
                  /pets:
                    get:
                      responses:
                        "200":
                          content:
                            application/json:
                              schema:
                                $ref: '#/components/schemas/A'
                components:
                  schemas:
                    A:
                      $id: 'sub/'
                      $ref: '#/components/schemas/X'
                    X:
                      type: object
                      properties:
                        name:
                          type: string
                """);
        RestDefinition rest = new RestDefinition();
        rest.openApi("file:" + root);

        OpenAPI openApi = read(rest);

        assertNotNull(openApi.getPaths().get("/pets"));
    }

    @Test
    void checksARootFragmentWithoutTheIdsAboveIt(@TempDir Path dir) throws Exception {
        Path classes = Path.of("target/test-classes");
        Path sub = Files.createDirectories(classes.resolve("sub"));
        Path subOwner = Files.writeString(sub.resolve("root-fragment-reader-owner.yaml"), "type: string\n");
        Path rootOwner = Files.writeString(classes.resolve("root-fragment-reader-owner.yaml"), """
                type: object
                properties:
                  name:
                    $ref: 'https://schemas.example.invalid/schema.yaml'
                """);
        try {
            Path root = Files.writeString(dir.resolve("root.yaml"), """
                    openapi: 3.1.0
                    info:
                      title: Pets
                      version: "1.0"
                    servers:
                      - url: http://localhost:8080/api
                    paths:
                      /pets:
                        get:
                          responses:
                            "200":
                              content:
                                application/json:
                                  schema:
                                    $ref: '#/components/schemas/P/$defs/X'
                    components:
                      schemas:
                        P:
                          $id: sub/
                          $defs:
                            X:
                              type: object
                              properties:
                                owner:
                                  $ref: root-fragment-reader-owner.yaml
                    """);
            RestDefinition rest = new RestDefinition();
            rest.openApi().specification("file:" + root).externalReferences("local");

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

            assertTrue(e.getMessage().contains("references https://schemas.example.invalid/schema.yaml"), e.getMessage());
            assertTrue(e.getMessage().contains("externalReferences=local allows no http(s) reference"), e.getMessage());
        } finally {
            Files.deleteIfExists(rootOwner);
            Files.deleteIfExists(subOwner);
        }
    }

    @Test
    void resolvesARootFragmentWithoutTheIdsAboveItByDefault(@TempDir Path dir) throws Exception {
        Path classes = Path.of("target/test-classes");
        Path sub = Files.createDirectories(classes.resolve("sub"));
        Path subOwner = Files.writeString(sub.resolve("root-fragment-reader-owner.yaml"), "type: integer\n");
        Path rootOwner = Files.writeString(classes.resolve("root-fragment-reader-owner.yaml"), "type: string\n");
        try {
            Path root = Files.writeString(dir.resolve("root.yaml"), """
                    openapi: 3.1.0
                    info:
                      title: Pets
                      version: "1.0"
                    servers:
                      - url: http://localhost:8080/api
                    paths:
                      /pets:
                        get:
                          responses:
                            "200":
                              content:
                                application/json:
                                  schema:
                                    $ref: '#/components/schemas/P/$defs/X'
                    components:
                      schemas:
                        P:
                          $id: sub/
                          $defs:
                            X:
                              type: object
                              properties:
                                owner:
                                  $ref: root-fragment-reader-owner.yaml
                    """);
            RestDefinition rest = new RestDefinition();
            rest.openApi("file:" + root);

            OpenAPI openApi = read(rest);

            assertNotNull(openApi.getPaths().get("/pets"));
        } finally {
            Files.deleteIfExists(rootOwner);
            Files.deleteIfExists(subOwner);
        }
    }

    @Test
    void failsWhenAFragmentCannotBeFound(@TempDir Path dir) throws Exception {
        Path schema = Files.writeString(dir.resolve("schema.yaml"), """
                $defs:
                  Owner:
                    type: object
                """);
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("file:" + contract31(dir, null, schema.toUri() + "#/$defs/Missing"))
                .externalReferences("local");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("#/$defs/Missing cannot be found in " + schema.toUri()), e.getMessage());
    }

    @Test
    void checksAReferenceGivenAsAYamlAlias() {
        RestDefinition rest = new RestDefinition();
        rest.openApi().specification("external-references-alias.yaml").externalReferences("none");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        // the alias is expanded before the check, as the parser expands it
        assertTrue(e.getMessage().contains("references https://schemas.example.invalid/schema.yaml"), e.getMessage());
    }

    @Test
    void usesTheSettingOfTheRestOpenApiComponent() {
        context.addComponent("rest-openapi", new RestrictedComponent());
        RestDefinition rest = new RestDefinition();
        rest.openApi("external-references.yaml");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(rest));

        assertTrue(e.getMessage().contains("externalReferences=none"), e.getMessage());
    }

    private OpenAPI read(RestDefinition rest) throws Exception {
        return new RestOpenApiReader().read(context, List.of(rest), new BeanConfig(), context.getName(),
                context.getClassResolver());
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
     * A document with an http $id, on a host that does not exist, at its root, and an Owner schema (also the anchor
     * Owner) whose name is the file name.yaml next to it.
     */
    private static Path documentBelowAnId(Path dir) throws IOException {
        Files.writeString(dir.resolve("name.yaml"), "type: string\n");
        return Files.writeString(dir.resolve("other.yaml"), """
                $id: https://schemas.example.invalid/other/
                $defs:
                  Owner:
                    $anchor: Owner
                    type: object
                    properties:
                      name:
                        $ref: name.yaml
                """);
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
     * Writes an OpenAPI 3.1 contract whose Pet schema has the given $id (none when null) and whose owner property is
     * the given reference.
     */
    private static Path contract31(Path dir, String petId, String ownerRef) throws IOException {
        return Files.writeString(dir.resolve("root.yaml"), """
                openapi: 3.1.0
                info:
                  title: Pets
                  version: "1.0"
                servers:
                  - url: http://localhost:8080/api
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
                """.formatted(petId != null ? "      $id: " + petId + "\n" : "", ownerRef));
    }

    /**
     * Stands in for the rest-openapi component, which is not on the classpath of this module. Public like the real
     * component, since the reader finds the setting through bean introspection.
     */
    public static class RestrictedComponent extends DefaultComponent {

        @SuppressWarnings("unused") // read through bean introspection
        public String getExternalReferences() {
            return "none";
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return null;
        }
    }
}
