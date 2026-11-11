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
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * contracts/openapi-server: the REST DSL serves the contract stock-api.json, and each operation is the route
 * direct:operationId. The routes name no operation; the overview reads them from the contract.
 */
class RestContractsTest {

    private static final DefaultCamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final String CONTRACT = """
            {
              "openapi": "3.0.3",
              "servers": [ { "url": "http://localhost:8080/api" } ],
              "paths": {
                "/stock": { "get": { "operationId": "listStock", "summary": "All stock levels" } },
                "/stock/{sku}": { "get": { "operationId": "getStock", "summary": "Stock level of one SKU" } },
                "/stock/{sku}/reserve": { "post": { "operationId": "reserveStock" } }
              }
            }
            """;

    private static final String OPERATIONS = """
            - route:
                id: listStock
                from:
                  uri: direct:listStock
                  steps:
                    - log: listed
            - route:
                id: getStock
                from:
                  uri: direct:getStock
                  steps:
                    - log: one
            - route:
                id: reserveStock
                from:
                  uri: direct:reserveStock
                  steps:
                    - log: reserved
            """;

    @TempDir
    Path dir;

    @Test
    void theOperationsOfTheContractAreTheEntryPointsInEveryDsl() throws Exception {
        Files.writeString(dir.resolve("stock-api.json"), CONTRACT);
        Map<String, String> rests = Map.of(
                "rest.camel.yaml", """
                        - rest:
                            openApi:
                              specification: stock-api.json
                        """,
                "rest.xml", """
                        <routes xmlns="http://camel.apache.org/schema/xml-io">
                          <rest><openApi specification="stock-api.json"/></rest>
                        </routes>
                        """,
                "Rest.java", """
                        public class Rest extends RouteBuilder {
                            public void configure() {
                                rest().openApi().specification("stock-api.json");
                            }
                        }
                        """);
        for (Map.Entry<String, String> rest : rests.entrySet()) {
            ProjectOverview.Overview o = ProjectOverview.analyze(dir,
                    Map.of(rest.getKey(), rest.getValue(), "operations.camel.yaml", OPERATIONS), CATALOG);

            assertThat(o.entryPoints()).as(rest.getKey())
                    .extracting(ProjectOverview.EntryPoint::label, ProjectOverview.EntryPoint::route)
                    .contains(tuple("GET /api/stock", "listStock"), tuple("GET /api/stock/{sku}", "getStock"),
                            tuple("POST /api/stock/{sku}/reserve", "reserveStock"));
            // About has them as what comes in from outside
            assertThat(About.of(dir, o).in()).as(rest.getKey()).extracting(About.In::route)
                    .containsExactlyInAnyOrder("listStock", "getStock", "reserveStock");
        }
    }

    @Test
    void aContractThatIsNotThereHasNoOperations() {
        ProjectOverview.Overview o = ProjectOverview.analyze(dir, Map.of("rest.camel.yaml", """
                - rest:
                    openApi:
                      specification: missing.json
                """, "operations.camel.yaml", OPERATIONS), CATALOG);

        assertThat(o.routes()).noneMatch(r -> RestContracts.KIND.equals(r.kind()));
        assertThat(o.entryPoints()).isEmpty();
    }

    @Test
    void theRestComponentsOfAContract() {
        assertThat(RouteComponents.of("""
                - rest:
                    openApi:
                      specification: stock-api.json
                """, "yaml", CATALOG)).contains("rest", "rest-openapi");
    }
}
