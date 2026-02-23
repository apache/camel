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
            assertThat(w.message()).contains("GENOPID_");
        });
    }

    @Test
    void validateSecuritySchemeWarnings() {
        OpenApiTools.ValidateResult result = tools.camel_openapi_validate(SPEC_WITH_SECURITY);

        // apiKey in query should be info
        assertThat(result.info()).anySatisfy(i -> assertThat(i.code()).isEqualTo("SECURITY_APIKEY_QUERY"));
        // apiKey in header should be warning
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("SECURITY_APIKEY_NOT_QUERY"));
        // HTTP bearer should be warning
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("SECURITY_HTTP"));
        // OAuth2 should be warning
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("SECURITY_OAUTH2"));
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
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("OPENAPI_31"));
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

        assertThat(result.yaml()).contains("constant: 200");
        assertThat(result.yaml()).contains("constant: 201");
    }

    @Test
    void scaffoldContentTypeHeaders() {
        OpenApiTools.ScaffoldResult result = tools.camel_openapi_scaffold(MINIMAL_SPEC, null, null);

        assertThat(result.yaml()).contains("constant: application/json");
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

    // ---- Mock guidance tests ----

    @Test
    void mockGuidanceDefaultModeIsMock() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, null);

        assertThat(result.mode()).isEqualTo("mock");
        assertThat(result.modeExplanation()).contains("mock");
    }

    @Test
    void mockGuidanceGeneratesMockFilePaths() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "mock");

        assertThat(result.mockFiles()).isNotNull();
        assertThat(result.mockFiles()).anySatisfy(f -> assertThat(f.filePath()).contains("camel-mock/"));
    }

    @Test
    void mockGuidanceFailModeExplanation() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "fail");

        assertThat(result.mode()).isEqualTo("fail");
        assertThat(result.modeExplanation()).contains("fail");
        assertThat(result.modeExplanation()).contains("exception");
        // No mock files for fail mode
        assertThat(result.mockFiles()).isNull();
        assertThat(result.directoryStructure()).isNull();
    }

    @Test
    void mockGuidanceIgnoreModeExplanation() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "ignore");

        assertThat(result.mode()).isEqualTo("ignore");
        assertThat(result.modeExplanation()).contains("ignore");
        assertThat(result.modeExplanation()).contains("404");
    }

    @Test
    void mockGuidanceConfigYamlCorrect() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "mock");

        assertThat(result.configurationYaml()).contains("missingOperation: mock");
        assertThat(result.configurationYaml()).contains("specification: openapi.json");
    }

    @Test
    void mockGuidanceNullSpecThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_mock_guidance(null, "mock"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("spec");
    }

    @Test
    void mockGuidanceExampleContentPopulated() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "mock");

        // The MINIMAL_SPEC has example data on the GET /pets response
        assertThat(result.mockFiles()).isNotNull();
        assertThat(result.mockFiles()).anySatisfy(f -> {
            assertThat(f.operation()).isEqualTo("listPets");
            assertThat(f.exampleContent()).isNotNull();
        });
    }

    @Test
    void mockGuidanceInvalidModeThrows() {
        assertThatThrownBy(() -> tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "invalid"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("mode");
    }

    @Test
    void mockGuidanceDirectoryStructurePresent() {
        OpenApiTools.MockGuidanceResult result = tools.camel_openapi_mock_guidance(MINIMAL_SPEC, "mock");

        assertThat(result.directoryStructure()).isNotNull();
        assertThat(result.directoryStructure()).contains("camel-mock");
    }
}
