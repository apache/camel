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

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class VertxPlatformHttpNoBodyHandlerTest {
    private final int port = AvailablePortFinder.getNextAvailable();
    private final WireMockServer wireMockServer = new WireMockServer(options().port(port));

    @BeforeEach
    void before() {
        wireMockServer.stubFor(post(urlPathEqualTo("/test"))
                .withRequestBody(containing("Hello World"))
                .willReturn(aResponse()
                        .withBody("This is a test")));

        wireMockServer.start();
    }

    @AfterEach
    void after() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testNoBodyHandler() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();
        final var mockUrl = "http://localhost:" + wireMockServer.port();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/camel?matchOnUriPrefix=true&useBodyHandler=false")
                            .removeHeader("CamelHttpUri")
                            .setHeader("OrgCamelHttpUri", simple(mockUrl + "${header.CamelHttpPath}"))
                            .setHeader("CamelHttpPath", simple(""))
                            .toD("${bean:" + PathCreator.class.getName()
                                 + "?method=createNewUri(${header.OrgCamelHttpUri})}?bridgeEndpoint=true");
                }
            });

            context.start();

            given()
                    .body("Hello World")
                    .post("/camel/test")
                    .then()
                    .statusCode(200)
                    .body(is("This is a test"));
        } finally {
            context.stop();
        }
    }
}
