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

package org.apache.camel.dsl.jbang.core.commands.k;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.Matchers.hasItems;

@Disabled("Deprecated and resource intensive")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentTest {

    @BeforeAll
    public void setupFixtures() {
        CommandLineHelper.useHomeDir("target");
        PluginHelper.enable(PluginType.CAMEL_K);
    }

    @Test
    public void testInspect() throws Exception {
        Agent agent = cmd();
        agent.port = 0;

        Vertx vertx = Vertx.vertx();
        HttpServer server = agent.serve(vertx).toCompletableFuture().get();

        try {
            int port = server.actualPort();
            String route = """
                    - route:
                        from:
                          uri: 'timer:tick'
                          steps:
                          - to: 'log:info'
                    """;

            RestAssured.given()
                    .baseUri("http://localhost")
                    .port(port)
                    .body(route)
                    .when()
                    .post("/inspect/routes.yaml")
                    .then()
                    .statusCode(200)
                    .body("resources.components", hasItems("timer", "log"));

        } finally {
            server.close();
            vertx.close();
        }
    }

    @ParameterizedTest
    @CsvSource({ "component/log,200", "component/baz,204" })
    public void testCatalog(String entityRef, int code) throws Exception {
        Agent agent = cmd();
        agent.port = 0;

        Vertx vertx = Vertx.vertx();
        HttpServer server = agent.serve(vertx).toCompletableFuture().get();

        try {
            int port = server.actualPort();
            RestAssured.given()
                    .baseUri("http://localhost")
                    .port(port)
                    .when()
                    .get("/catalog/model/" + entityRef)
                    .then()
                    .statusCode(code);

        } finally {
            server.close();
            vertx.close();
        }
    }

    @Disabled
    @Test
    public void testCall() throws Exception {
        cmd().doCall();
    }

    private Agent cmd() {
        return new Agent(new CamelJBangMain().withPrinter(new StringPrinter()));
    }
}
