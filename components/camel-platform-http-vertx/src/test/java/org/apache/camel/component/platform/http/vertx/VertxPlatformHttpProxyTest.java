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
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class VertxPlatformHttpProxyTest {
    private final int port = AvailablePortFinder.getNextAvailable();
    private final WireMockServer wireMockServer = new WireMockServer(options().port(port));

    @BeforeEach
    void before() {
        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withBody(
                                "{\"message\": \"Hello World\"}")));

        wireMockServer.start();
    }

    @AfterEach
    void after() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testProxy(boolean useStreaming) throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:proxy?useStreaming=" + useStreaming)
                            .toD("${headers." + Exchange.HTTP_URI + "}?bridgeEndpoint=true");
                }
            });

            context.start();

            // URI of proxy created with platform HTTP component
            final var proxyURI = "http://localhost:" + RestAssured.port;

            final var originURI = "http://localhost:" + wireMockServer.port();

            given()
                    .proxy(proxyURI)
                    .contentType(ContentType.JSON)
                    .when().get(originURI)
                    .then()
                    .statusCode(200)
                    .body(containsString("{\"message\": \"Hello World\"}"));

        } finally {
            context.stop();
        }
    }

}
