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
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24844 phase B: the verb of a rest-openapi operation is in the specification beside the route, so a GET that
 * carries no body can be read from it - and a route it reaches that needs a body can be reported.
 */
class OpenApiBodyFlowTest {

    private static final String SPEC = """
            {
              "openapi": "3.0.2",
              "paths": {
                "/stock/{sku}": {
                  "get": { "operationId": "getStock", "responses": { "200": { "description": "ok" } } }
                },
                "/orders": {
                  "post": { "operationId": "createOrder", "responses": { "201": { "description": "created" } } }
                }
              }
            }
            """;

    private static final String ROUTES = """
            - rest:
                openApi:
                  specification: stock-api.json
            - route:
                id: getStock
                from:
                  uri: direct:getStock
                  steps:
                    - to:
                        uri: direct:lookup
            - route:
                id: lookup
                from:
                  uri: direct:lookup
                  steps:
                    - setBody:
                        expression:
                          jsonpath:
                            expression: "$.sku"
            """;

    @Test
    void theVerbsOfTheSpecificationAreRead(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), SPEC);
        Set<String> bodyless = OpenApiVerbs.bodylessEndpoints(ROUTES, dir);
        assertThat(bodyless).containsExactly("direct:getStock");
    }

    @Test
    void aGetOperationMakesTheMissingReadVisible(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), SPEC);
        Files.writeString(dir.resolve("routes.camel.yaml"), ROUTES);
        CamelCatalog catalog = new DefaultCamelCatalog();

        List<String> withTheSpec
                = SourceValidator.validate("routes.camel.yaml", ROUTES, catalog, null, dir);
        assertThat(withTheSpec).as("the specification says getStock is a GET, so lookup can have no body")
                .anyMatch(m -> m.contains("reads the message body"));

        // without the directory the specification cannot be read, and nothing is claimed
        List<String> withoutIt = SourceValidator.validateCamelYaml(ROUTES, catalog);
        assertThat(withoutIt).noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    void aPostOperationSaysNothing(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), SPEC);
        String routes = ROUTES.replace("direct:getStock", "direct:createOrder")
                .replace("id: getStock", "id: createOrder");
        Files.writeString(dir.resolve("routes.camel.yaml"), routes);
        assertThat(SourceValidator.validate("routes.camel.yaml", routes, new DefaultCamelCatalog(), null, dir))
                .as("a POST carries a body, so nothing is certain")
                .noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    void aMissingSpecificationSaysNothing(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("routes.camel.yaml"), ROUTES);
        assertThat(SourceValidator.validate("routes.camel.yaml", ROUTES, new DefaultCamelCatalog(), null, dir))
                .noneMatch(m -> m.contains("reads the message body"));
    }
}
