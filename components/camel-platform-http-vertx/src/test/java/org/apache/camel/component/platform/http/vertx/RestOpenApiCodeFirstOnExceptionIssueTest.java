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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class RestOpenApiCodeFirstOnExceptionIssueTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testOnExceptionHandledTrue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                        .log("Error processing request: ${exception.message}")
                        .process(e -> {
                            log.info("onException: {}", e);
                        })
                        .to("mock:error")
                        .handled(true);

                restConfiguration().contextPath("/api/v3");

                rest()
                    .get("pet/{id}")
                    .to("direct:getPetById");

                from("direct:getPetById").routeId("directRoute")
                        .process(e -> {
                            throw new RuntimeException("Simulated error get pet");
                        });
            }
        });

        getMockEndpoint("mock:error").expectedMessageCount(1);

        given()
                .when()
                .get("/api/v3/pet/1")
                .then()
                .statusCode(204);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOnExceptionHandledFalse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                        .log("Error processing request: ${exception.message}")
                        .process(e -> {
                            log.info("onException: {}", e);
                        })
                        .to("mock:error")
                        .handled(false);

                restConfiguration().contextPath("/api/v3");

                rest()
                    .get("pet/{id}")
                    .to("direct:getPetById");

                from("direct:getPetById").routeId("directRoute")
                        .process(e -> {
                            throw new RuntimeException("Simulated error get pet");
                        });
            }
        });

        getMockEndpoint("mock:error").expectedMessageCount(1);

        given()
                .when()
                .get("/api/v3/pet/1")
                .then()
                .statusCode(500);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    public CamelContext createCamelContext() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
        conf.setBindPort(port);

        RestAssured.port = port;

        CamelContext context = new DefaultCamelContext();
        context.addService(new VertxPlatformHttpServer(conf));
        return context;
    }

}
