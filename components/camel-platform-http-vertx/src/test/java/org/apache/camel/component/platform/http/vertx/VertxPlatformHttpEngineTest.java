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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class VertxPlatformHttpEngineTest {

    @Test
    public void testEngine() throws Exception {
        final int port = AvailablePortFinder.getNextAvailable();
        final CamelContext context = new DefaultCamelContext();

        try {
            VertxPlatformHttpServerConfiguration conf = new VertxPlatformHttpServerConfiguration();
            conf.setBindPort(port);

            context.disableJMX();
            context.addService(new VertxPlatformHttpServer(context, conf), true, true);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("platform-http:/get")
                        .routeId("get")
                        .setBody().constant("get");
                    from("platform-http:/post")
                        .routeId("post")
                        .transform().body(String.class, b -> b.toUpperCase());
                }
            });

            context.start();

            assertThat(VertxPlatformHttpRouter.lookup(context)).isNotNull();
            assertThat(context.getComponent("platform-http")).isInstanceOfSatisfying(PlatformHttpComponent.class, component -> {
                assertThat(component.getEngine()).isInstanceOfSatisfying(VertxPlatformHttpEngine.class, e -> {
                    assertThat(e.getRouter().router()).isNotNull();
                    assertThat(e.getRouter().handlers()).isNotEmpty();
                });
            });

            given()
                .port(conf.getBindPort())
            .when()
                .get("/get")
            .then()
                .statusCode(200)
                .body(equalTo("get"));

            given()
                .port(conf.getBindPort())
                .body("post")
            .when()
                .post("/post")
            .then()
                .statusCode(200)
                .body(equalTo("POST"));

        } finally {
            context.stop();
        }
    }
}
