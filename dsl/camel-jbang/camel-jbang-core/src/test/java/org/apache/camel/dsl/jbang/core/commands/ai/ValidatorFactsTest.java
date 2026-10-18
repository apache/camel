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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25762: the facts the validator cannot read from a route file - the verbs of the OpenAPI operations it uses, and
 * the REST binding mode of the project - are read from the files beside it.
 */
class ValidatorFactsTest {

    private static final String STOCK_API = """
            {
              "openapi": "3.0.2",
              "paths": {
                "/stock/{sku}": { "get": { "operationId": "getStock", "responses": { "200": { "description": "ok" } } } },
                "/reservations": { "post": { "operationId": "reserveStock", "responses": { "201": { "description": "ok" } } } }
              }
            }
            """;

    private static final String ORDERS_API = """
            openapi: 3.0.2
            paths:
              /orders:
                post:
                  operationId: createOrder
                  responses:
                    '201':
                      description: created
            """;

    private static final String RESERVE = """
            - route:
                id: reserve
                from:
                  uri: file:orders
                  steps:
                    - unmarshal:
                        json: {}
                    - to:
                        uri: rest-openapi:stock-api.json#reserveStock
                        parameters:
                          host: http://localhost:8080
            """;

    @Test
    void theOperationsOfTheSpecificationsTheFileUses(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), STOCK_API);
        Files.createDirectories(dir.resolve("src/main/resources"));
        Files.writeString(dir.resolve("src/main/resources/orders-api.yaml"), ORDERS_API);
        String content = """
                - rest:
                    openApi:
                      specification: stock-api.json
                - route:
                    from:
                      uri: direct:getStock
                      steps:
                        - to: rest-openapi:orders-api.yaml#createOrder
                """;

        Map<String, Map<String, String>> operations = OpenApiVerbs.operations(content, dir);

        assertThat(operations).containsOnlyKeys("stock-api.json", "orders-api.yaml");
        assertThat(operations.get("stock-api.json")).containsEntry("getStock", "get").containsEntry("reserveStock", "post");
        assertThat(operations.get("orders-api.yaml")).containsEntry("createOrder", "post");
    }

    @Test
    void theOperationsWithoutABodyComeFromTheSameSpecification(@TempDir Path dir) throws Exception {
        // a Maven project keeps the specification in src/main/resources: both facts find it there
        Files.createDirectories(dir.resolve("src/main/resources"));
        Files.writeString(dir.resolve("src/main/resources/stock-api.json"), STOCK_API);
        String content = "- rest:\n    openApi:\n      specification: stock-api.json\n";

        assertThat(OpenApiVerbs.bodylessEndpoints(content, dir)).containsExactly("direct:getStock");
        assertThat(OpenApiVerbs.operations(content, dir)).containsOnlyKeys("stock-api.json");
    }

    @Test
    void aCallThatNamesNoSpecificationUsesTheDefault(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("openapi.json"), STOCK_API);

        assertThat(OpenApiVerbs.operations("- to: rest-openapi:reserveStock\n", dir)).containsOnlyKeys("openapi.json");
        assertThat(OpenApiVerbs.operations("- to: direct:x\n", dir)).isEmpty();
    }

    @Test
    void theRestBindingModeOfTheProperties(@TempDir Path dir) throws Exception {
        assertThat(ValidatorFacts.restBindingMode(dir)).isEqualTo("off");

        Files.writeString(dir.resolve("application.properties"), "# camel.rest.bindingMode = json\ncamel.server.port=8080\n");
        assertThat(ValidatorFacts.restBindingMode(dir)).as("a comment sets nothing").isEqualTo("off");

        Files.writeString(dir.resolve("application.properties"), "camel.rest.bindingMode = off\n");
        assertThat(ValidatorFacts.restBindingMode(dir)).isEqualTo("off");

        // a profile that binds wins: a route only some profiles bind is not reported
        Files.writeString(dir.resolve("application-dev.properties"), "camel.rest.binding-mode=auto\n");
        assertThat(ValidatorFacts.restBindingMode(dir)).isEqualTo("auto");
    }

    @Test
    void theRestBindingModeOfYamlConfiguration(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src/main/resources"));
        Files.writeString(dir.resolve("src/main/resources/application.yaml"), """
                camel:
                  rest:
                    binding-mode: json
                """);
        assertThat(ValidatorFacts.restBindingMode(dir)).isEqualTo("json");
    }

    @Test
    void parsedDataSentByRestOpenApi(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), STOCK_API);

        List<String> msgs = SourceValidator.validate("orders.camel.yaml", RESERVE, new DefaultCamelCatalog(), null, dir);
        assertThat(msgs).anyMatch(m -> m.contains("to: rest-openapi sends the body as the request of the operation"
                                                  + " reserveStock (POST in stock-api.json)"));

        // the binding of the properties writes the Map as JSON
        Files.writeString(dir.resolve("application.properties"), "camel.rest.bindingMode=json\n");
        assertThat(SourceValidator.validate("orders.camel.yaml", RESERVE, new DefaultCamelCatalog(), null, dir))
                .noneMatch(m -> m.contains("rest-openapi sends the body"));
    }

    @Test
    void parsedDataAsTheReplyOfAContractFirstOperation(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), STOCK_API);
        Files.writeString(dir.resolve("stock.json"), "[{\"sku\": \"A1\", \"qty\": 3}]");
        String content = """
                - rest:
                    openApi:
                      specification: stock-api.json
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setBody:
                            groovy: body.find { it.sku == headers.sku }
                """;

        List<String> msgs = SourceValidator.validate("stock.camel.yaml", content, new DefaultCamelCatalog(), null, dir);
        assertThat(msgs).anyMatch(m -> m.contains("the body at the end of the route is the reply to the REST call"));
        // without a directory nothing is known about the operations or the binding: nothing is said
        assertThat(SourceValidator.validate("stock.camel.yaml", content, new DefaultCamelCatalog(), null, null))
                .noneMatch(m -> m.contains("reply to the REST call"));
    }
}
