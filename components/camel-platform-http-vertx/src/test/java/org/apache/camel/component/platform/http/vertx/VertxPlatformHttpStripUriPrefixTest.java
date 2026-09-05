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
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests the stripUriPrefix consumer option, which lets a platform-http route combined with the http producer's
 * bridgeEndpoint option act as a path-based reverse proxy: the registered consumer path is stripped from CamelHttpPath
 * before the request reaches the route, so the backend only sees the path relative to the consumer.
 */
public class VertxPlatformHttpStripUriPrefixTest {

    @RegisterExtension
    AvailablePortFinder.Port backendPort = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port camelPort = AvailablePortFinder.find();

    private WireMockServer wireMockServer;

    @BeforeEach
    void before() {
        wireMockServer = new WireMockServer(options().port(backendPort.getPort()));
        // stubbed as it should be received once the /reverse-proxy prefix has been stripped
        wireMockServer.stubFor(get(urlEqualTo("/get?arg1=val1"))
                .willReturn(aResponse().withStatus(200).withBody("stripped")));
        // stubbed as it should be received when stripUriPrefix is NOT enabled (control)
        wireMockServer.stubFor(get(urlEqualTo("/reverse-proxy/get?arg1=val1"))
                .willReturn(aResponse().withStatus(200).withBody("unstripped")));
        // stubbed for an exact (non-prefixed) match, which should strip down to "/"
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200).withBody("root")));
        wireMockServer.start();
    }

    @AfterEach
    void after() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void stripUriPrefixRemovesTheConsumerPathBeforeBridging() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext(camelPort.getPort());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/reverse-proxy?matchOnUriPrefix=true&stripUriPrefix=true")
                            .to("http://localhost:" + backendPort.getPort() + "?bridgeEndpoint=true");
                }
            });
            context.start();

            given()
                    .when().get("http://localhost:" + camelPort.getPort() + "/reverse-proxy/get?arg1=val1")
                    .then()
                    .statusCode(200)
                    .body(equalTo("stripped"));
        } finally {
            context.stop();
        }
    }

    @Test
    void withoutStripUriPrefixTheFullPathIsForwardedUnchanged() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext(camelPort.getPort());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/reverse-proxy?matchOnUriPrefix=true")
                            .to("http://localhost:" + backendPort.getPort() + "?bridgeEndpoint=true");
                }
            });
            context.start();

            given()
                    .when().get("http://localhost:" + camelPort.getPort() + "/reverse-proxy/get?arg1=val1")
                    .then()
                    .statusCode(200)
                    .body(equalTo("unstripped"));
        } finally {
            context.stop();
        }
    }

    @Test
    void stripUriPrefixOnAnExactMatchLeavesTheRootPath() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext(camelPort.getPort());
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/reverse-proxy?matchOnUriPrefix=true&stripUriPrefix=true")
                            .to("http://localhost:" + backendPort.getPort() + "?bridgeEndpoint=true");
                }
            });
            context.start();

            given()
                    .when().get("http://localhost:" + camelPort.getPort() + "/reverse-proxy")
                    .then()
                    .statusCode(200)
                    .body(equalTo("root"));
        } finally {
            context.stop();
        }
    }
}
