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
package org.apache.camel.component.platform.http.vertx;

import io.restassured.RestAssured;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.restassured.RestAssured.given;

/**
 * Reproduces the contract-first root basePath bug: when the REST configuration's context-path resolves to the root
 * ("/"), {@code VertxPlatformHttpConsumer} used to register Vert.x routes with a doubled leading slash (e.g.
 * "//pet/:petId"), so real requests to "/pet/1" never matched and the API 404'd.
 */
public class RestOpenApiContractFirstRootBasePathTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void getPetByIdAtRootBasePath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().contextPath("/");

                rest().openApi().specification("openapi-v3.json").missingOperation("ignore").routeId("petStoreRoot");

                from("direct:getPetById")
                        .setBody(constant("{\"id\":1,\"name\":\"doggie\"}"));
            }
        });

        given()
                .when()
                .get("/pet/1")
                .then()
                .statusCode(200);
    }

    @Test
    public void getInventoryAtRootBasePath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().contextPath("/");

                rest().openApi().specification("openapi-v3.json").missingOperation("ignore").routeId("petStoreRoot");

                from("direct:getInventory")
                        .setBody(constant("{\"available\":1}"));
            }
        });

        given()
                .when()
                .get("/store/inventory")
                .then()
                .statusCode(200);
    }

    @Override
    public CamelContext createCamelContext() throws Exception {
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port.getPort());

        RestAssured.port = port.getPort();

        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }

}
