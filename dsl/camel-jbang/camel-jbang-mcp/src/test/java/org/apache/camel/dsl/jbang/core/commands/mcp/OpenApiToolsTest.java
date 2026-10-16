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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiToolsTest {

    private final OpenApiTools tools = new OpenApiTools();

    private static final String MINIMAL_SPEC = """
            {
              "openapi": "3.0.3",
              "info": {
                "title": "Pet Store",
                "version": "1.0.0"
              },
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "listPets",
                    "summary": "List all pets",
                    "responses": {
                      "200": {
                        "description": "A list of pets",
                        "content": {
                          "application/json": {
                            "schema": { "type": "array", "items": { "type": "string" } },
                            "example": ["dog", "cat"]
                          }
                        }
                      }
                    }
                  },
                  "post": {
                    "operationId": "createPet",
                    "summary": "Create a pet",
                    "requestBody": {
                      "content": {
                        "application/json": {
                          "schema": { "type": "object" }
                        }
                      }
                    },
                    "responses": {
                      "201": { "description": "Pet created" }
                    }
                  }
                }
              }
            }
            """;

    private static final String SPEC_NO_OPERATION_IDS = """
            {
              "openapi": "3.0.3",
              "info": { "title": "Test", "version": "1.0.0" },
              "paths": {
                "/items": {
                  "get": {
                    "responses": { "200": { "description": "OK" } }
                  }
                }
              }
            }
            """;

    private static final String SPEC_WITH_SECURITY = """
            {
              "openapi": "3.0.3",
              "info": { "title": "Secure API", "version": "1.0.0" },
              "paths": {
                "/data": {
                  "get": {
                    "operationId": "getData",
                    "responses": { "200": { "description": "OK" } }
                  }
                }
              },
              "components": {
                "securitySchemes": {
                  "apiKeyQuery": {
                    "type": "apiKey",
                    "in": "query",
                    "name": "api_key"
                  },
                  "apiKeyHeader": {
                    "type": "apiKey",
                    "in": "header",
                    "name": "X-API-Key"
                  },
                  "bearerAuth": {
                    "type": "http",
                    "scheme": "bearer"
                  },
                  "oauth": {
                    "type": "oauth2",
                    "flows": {}
                  }
                }
              }
            }
            """;

    private static final String SPEC_NO_PATHS = """
            {
              "openapi": "3.0.3",
              "info": { "title": "Empty", "version": "1.0.0" },
              "paths": {}
            }
            """;

    private static final String SPEC_31_WITH_WEBHOOKS = """
            {
              "openapi": "3.1.0",
              "info": { "title": "Webhook API", "version": "1.0.0" },
              "paths": {
                "/hook": {
                  "post": {
                    "operationId": "receiveHook",
                    "responses": { "200": { "description": "OK" } }
                  }
                }
              },
              "webhooks": {
                "petEvent": {
                  "post": {
                    "operationId": "petWebhook",
                    "responses": { "200": { "description": "OK" } }
                  }
                }
              }
            }
            """;

    private static final String SPEC_MULTI_RESPONSE = """
            {
              "openapi": "3.0.3",
              "info": { "title": "Multi", "version": "1.0.0" },
              "paths": {
                "/items": {
                  "get": {
                    "operationId": "getItems",
                    "responses": {
                      "200": {
                        "description": "Items list",
                        "content": {
                          "application/xml": {
                            "schema": { "type": "string" }
                          }
                        }
                      }
                    }
                  },
                  "delete": {
                    "operationId": "deleteItem",
                    "responses": {
                      "204": { "description": "Deleted" }
                    }
                  }
                }
              }
            }
            """;

    // ---- Validate tests ----

    @Test
    void validateValidSpec() {
        OpenApiTools.ValidateResult result = tools.camel_openapi_validate(MINIMAL_SPEC);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.operationCount()).isEqualTo(2);
    }

    @Test
    void validateNullSpecThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_validate(null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("spec");
    }

    @Test
    void validateBlankSpecThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_validate("   "))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("spec");
    }

    @Test
    void validateInvalidSpecThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_validate("{ invalid json !!!"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Failed to parse OpenAPI spec");
    }

    @Test
    void validateMissingOperationIdWarns() {
        OpenApiTools.ValidateResult result = tools.camel_openapi_validate(SPEC_NO_OPERATION_IDS);

        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).anySatisfy(w -> {
            assertThat(w.code()).isEqualTo("MISSING_OPERATION_ID");
            assertThat(w.message()).contains("direct:GENOPID_GET.items");
        });
    }

    @Test
    void validateSecuritySchemeWarnings() {
        OpenApiTools.ValidateResult result = tools.camel_openapi_validate(SPEC_WITH_SECURITY);

        // camel-rest-openapi enforces none of the schemes, an apiKey in query included
        assertThat(result.info()).isEmpty();
        assertThat(result.warnings())
                .extracting(OpenApiTools.DiagnosticMessage::code)
                .containsExactlyInAnyOrder("SECURITY_APIKEY", "SECURITY_APIKEY", "SECURITY_HTTP", "SECURITY_OAUTH2");
        assertThat(result.warnings()).allSatisfy(w -> assertThat(w.message()).contains("does not enforce"));
    }

    @Test
    void validateNoPathsError() {
        OpenApiTools.ValidateResult result = tools.camel_openapi_validate(SPEC_NO_PATHS);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.code()).isEqualTo("NO_PATHS"));
    }

    @Test
    void validateWebhooksWarning() {
        OpenApiTools.ValidateResult result = tools.camel_openapi_validate(SPEC_31_WITH_WEBHOOKS);

        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("WEBHOOKS_PRESENT"));
        // 3.1 is parsed and served like 3.0: information, not a warning
        assertThat(result.info()).anySatisfy(i -> assertThat(i.code()).isEqualTo("OPENAPI_31"));
    }

    // ---- Scaffold tests ----

    @Test
    void scaffoldGeneratesCorrectYaml() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, null);

        assertThat(result.yaml()).contains("rest:");
        assertThat(result.yaml()).contains("openApi:");
        assertThat(result.yaml()).contains("specification: openapi.json");
        assertThat(result.operationCount()).isEqualTo(2);
        assertThat(result.apiTitle()).isEqualTo("Pet Store");
    }

    @Test
    void scaffoldNullSpecThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_scaffold(null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("spec");
    }

    @Test
    void scaffoldContainsDirectRoutes() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, null);

        assertThat(result.yaml()).contains("direct:listPets");
        assertThat(result.yaml()).contains("direct:createPet");
    }

    @Test
    void scaffoldResponseCodesFromSpec() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, null);

        assertThat(result.yaml()).contains("""
                        - setHeader:
                            name: CamelHttpResponseCode
                            expression:
                              constant:
                                expression: 200
                """);
        assertThat(result.yaml()).contains("""
                        - setHeader:
                            name: CamelHttpResponseCode
                            expression:
                              constant:
                                expression: 201
                """);
    }

    @Test
    void scaffoldContentTypeHeaders() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, null);

        assertThat(result.yaml()).contains("""
                        - setHeader:
                            name: Content-Type
                            expression:
                              constant:
                                expression: application/json
                """);
    }

    @Test
    void scaffoldIsCanonicalYaml() throws Exception {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, null);

        // the scaffold is the starting point of a user or an AI agent: it must not be in the deprecated compact notation
        YamlValidator canonicalValidator = new YamlValidator(true);
        canonicalValidator.init();
        assertThat(canonicalValidator.validate(result.yaml()))
                .as("canonical validation of the scaffold:%n%s", result.yaml())
                .isEmpty();
    }

    @Test
    void scaffoldCustomFilename() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, "petstore.yaml", null);

        assertThat(result.yaml()).contains("specification: petstore.yaml");
        assertThat(result.specFilename()).isEqualTo("petstore.yaml");
    }

    @Test
    void scaffoldMissingOperationModeApplied() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, "mock");

        assertThat(result.yaml()).contains("missingOperation: mock");
        assertThat(result.missingOperation()).isEqualTo("mock");
    }

    @Test
    void scaffoldInvalidModeThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_scaffold(MINIMAL_SPEC, null, "invalid"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("missingOperation");
    }

    @Test
    void scaffoldMockModeListsOneMockFilePerPath() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, "mock");

        assertThat(result.modeExplanation()).contains("ends with the request path");
        assertThat(result.mockFiles()).singleElement().satisfies(f -> {
            assertThat(f.filePath()).isEqualTo("camel-mock/pets.json");
            assertThat(f.operationIds()).containsExactly("listPets", "createPet");
            assertThat(f.exampleContent()).contains("dog");
            assertThat(f.note()).contains("GET answers the example", "POST echoes the request body");
        });
    }

    @Test
    void scaffoldNamesTheRootPathMockFileVisibly() {
        OpenApiTools.ScaffoldResult result
                = tools.camel_openapi_scaffold(MINIMAL_SPEC.replace("\"/pets\":", "\"/\":"), null, "mock");

        assertThat(result.mockFiles()).singleElement().satisfies(f -> {
            assertThat(f.filePath()).isEqualTo("camel-mock/index.json");
            assertThat(f.requestPath()).isEqualTo("/");
            assertThat(f.note()).contains("any mock file");
        });
    }

    @Test
    void scaffoldNamesThePathParameterMockFileWithASampleValue() {
        String spec = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Pets", "version": "1.0" },
                  "paths": {
                    "/pets/{petId}": {
                      "get": {
                        "operationId": "showPetById",
                        "responses": { "201": { "description": "Created" } }
                      }
                    }
                  }
                }
                """;
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(spec, null, "mock");

        assertThat(result.mockFiles()).singleElement().satisfies(f -> {
            assertThat(f.filePath()).isEqualTo("camel-mock/pets/1.json");
            assertThat(f.requestPath()).isEqualTo("/pets/{petId}");
            assertThat(f.note()).contains("camel-mock/pets/1.json answers /pets/1",
                    "GET answers HTTP 204 with an empty body");
        });
    }

    @Test
    void scaffoldListsNoMockFileOutsideMockMode() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, "ignore");

        assertThat(result.modeExplanation()).contains("ignore");
        assertThat(result.mockFiles()).isNull();
    }
}
