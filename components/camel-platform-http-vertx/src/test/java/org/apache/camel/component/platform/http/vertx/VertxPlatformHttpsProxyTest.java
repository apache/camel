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
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class VertxPlatformHttpsProxyTest {

    private final int port = AvailablePortFinder.getNextAvailable();
    private final WireMockServer wireMockServer = new WireMockServer(
            options().httpsPort(port)
                    .httpDisabled(true)
                    .keystorePath("proxy/keystore.p12")
                    .keystorePassword("changeit")
                    .keyManagerPassword("changeit"));

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

    @Test
    void testProxy() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        try {

            context.getRegistry().bind("sslContextParameters", sslContextParameters());
            context.getRegistry().bind("x509HostnameVerifier", x509HostnameVerifier());

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:proxy")
                            .toD("https://${headers." + Exchange.HTTP_HOST
                                 + "}?bridgeEndpoint=true&sslContextParameters=#sslContextParameters&x509HostnameVerifier=#x509HostnameVerifier");
                }
            });

            context.start();

            // URI of proxy created with platform HTTP component
            final var proxyURI = "http://localhost:" + RestAssured.port;

            // In order to make sure that RestAssured don't perform a CONNECT instead of a GET, we do trick with http
            // if we want to do test manually from a terminal we use the real HTTPS address
            final var originURI = "http://localhost:" + port;

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

    public SSLContextParameters sslContextParameters() {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        KeyManagersParameters keyManagersParameters = new KeyManagersParameters();
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setPassword("changeit");
        keyStore.setResource("proxy/keystore.p12");
        keyManagersParameters.setKeyPassword("changeit");
        keyManagersParameters.setKeyStore(keyStore);
        sslContextParameters.setKeyManagers(keyManagersParameters);

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("proxy/keystore.p12");
        truststoreParameters.setPassword("changeit");

        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setKeyStore(truststoreParameters);
        sslContextParameters.setTrustManagers(trustManagersParameters);

        return sslContextParameters;
    }

    public NoopHostnameVerifier x509HostnameVerifier() {
        return NoopHostnameVerifier.INSTANCE;
    }

}
