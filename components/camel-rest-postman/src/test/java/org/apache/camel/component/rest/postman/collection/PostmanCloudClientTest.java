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
package org.apache.camel.component.rest.postman.collection;

import java.io.IOException;
import java.time.Duration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostmanCloudClientTest {

    private static final String UID = "12ece9e1-2abf-4edc-8e34-de66e74114d2";
    private static final String API_KEY = "PMAK-do-not-leak-me";
    private static final String PATH = "/collections/" + UID;

    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private PostmanCloudClient client() {
        return new PostmanCloudClient(
                "http://localhost:" + server.port(), API_KEY, "X-Api-Key",
                Duration.ofSeconds(5), Duration.ofSeconds(5), null);
    }

    @Test
    void shouldSendTheApiKeyHeader() throws Exception {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{\"collection\":{}}")));

        String body = client().fetchCollection(UID);

        assertThat(body).isEqualTo("{\"collection\":{}}");
        WireMock.verify(getRequestedFor(urlEqualTo(PATH)).withHeader("X-Api-Key", equalTo(API_KEY)));
    }

    @Test
    void shouldHonourACustomApiKeyHeaderName() throws Exception {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("{}")));

        new PostmanCloudClient(
                "http://localhost:" + server.port(), API_KEY, "X-Custom-Key",
                Duration.ofSeconds(5), Duration.ofSeconds(5), null).fetchCollection(UID);

        WireMock.verify(getRequestedFor(urlEqualTo(PATH)).withHeader("X-Custom-Key", equalTo(API_KEY)));
    }

    /**
     * Following a redirect would replay the API key to whatever host the Location names.
     */
    @Test
    void shouldRejectRedirectsRatherThanFollowThem() {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(
                aResponse().withStatus(302).withHeader("Location", "https://evil.example.com/steal")));

        assertThatThrownBy(() -> client().fetchCollection(UID))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("redirects are blocked")
                .hasMessageContaining("https://evil.example.com/steal");

        // and nothing was sent to the redirect target
        WireMock.verify(1, getRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void shouldNotLeakTheApiKeyInAFailureMessage() {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(
                aResponse().withStatus(401).withBody("{\"error\":\"key " + API_KEY + " is invalid\"}")));

        assertThatThrownBy(() -> client().fetchCollection(UID))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 401")
                // neither the key nor the response body, which may echo it, appears in the message
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void shouldRejectAnOversizedCollection() {
        server.stubFor(get(urlEqualTo(PATH)).willReturn(
                aResponse().withStatus(200).withBody("x".repeat((int) PostmanCloudClient.MAX_COLLECTION_BYTES + 1))));

        assertThatThrownBy(() -> client().fetchCollection(UID))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @Test
    void shouldRejectAUidThatCouldEscapeThePath() {
        assertThatThrownBy(() -> client().fetchCollection("../../admin"))
                .hasMessageContaining("illegal characters");
    }

    @Test
    void shouldAcceptHttpsApiUrls() {
        assertThatCode(() -> PostmanCloudClient.validateApiUrl("https://api.getpostman.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptPlainHttpForLoopbackOnly() {
        assertThatCode(() -> PostmanCloudClient.validateApiUrl("http://localhost:8080"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PostmanCloudClient.validateApiUrl("http://127.0.0.1:8080"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectPlainHttpToARemoteHost() {
        assertThatThrownBy(() -> PostmanCloudClient.validateApiUrl("http://api.getpostman.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would send the Postman API key in clear text");
    }

    @Test
    void shouldRejectANonHttpApiUrl() {
        assertThatThrownBy(() -> PostmanCloudClient.validateApiUrl("ftp://api.getpostman.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use http or https");
    }

    @Test
    void shouldRejectARelativeApiUrl() {
        assertThatThrownBy(() -> PostmanCloudClient.validateApiUrl("api.getpostman.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an absolute URL");
    }
}
