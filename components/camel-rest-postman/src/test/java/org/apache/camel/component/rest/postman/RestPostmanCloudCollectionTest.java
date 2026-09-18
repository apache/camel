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
package org.apache.camel.component.rest.postman;

import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fetches a collection from a stub standing in for the Postman cloud, then calls the API it describes.
 * <p>
 * The point of this test is the separation of the two credentials: the Postman API key must authenticate the collection
 * download and must never appear on the call to the API itself.
 */
class RestPostmanCloudCollectionTest {

    private static final String UID = "12ece9e1-2abf-4edc-8e34-de66e74114d2";
    private static final String API_KEY = "PMAK-must-not-reach-the-target-api";

    private WireMockServer server;
    private DefaultCamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());

        // the Postman cloud wraps the collection in a "collection" envelope, and records an id per request
        String collection = IOHelper.loadText(
                getClass().getClassLoader().getResourceAsStream("petstore-collection.json"));
        server.stubFor(get(urlEqualTo("/collections/" + UID))
                .willReturn(aResponse().withStatus(200)
                        .withBody(("{\"collection\":" + collection + "}").getBytes(StandardCharsets.UTF_8))));

        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        server.stop();
    }

    private String uri(String extra) {
        return "rest-postman:" + UID + "#getPetById"
               + "?postmanApiUrl=http://localhost:" + server.port()
               + "&postmanApiKey=" + API_KEY
               + "&variable.baseUrl=http://localhost:" + server.port() + "/v3"
               + (extra != null ? "&" + extra : "");
    }

    @Test
    void shouldFetchTheCollectionWithTheKeyAndCallTheApiWithout() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/42"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":42}")));

        String body = template.requestBody(uri(null), null, String.class);

        assertThat(body).isEqualTo("{\"id\":42}");

        // the collection download carried the Postman API key
        WireMock.verify(getRequestedFor(urlEqualTo("/collections/" + UID))
                .withHeader("X-Api-Key", equalTo(API_KEY)));

        // the call to the API the collection describes did not
        WireMock.verify(getRequestedFor(urlPathEqualTo("/v3/pet/42"))
                .withHeader("X-Api-Key", absent()));
    }

    @Test
    void shouldFetchTheCollectionOnlyOnceForRepeatedCalls() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/42")).willReturn(aResponse().withStatus(200).withBody("ok")));

        template.requestBody(uri(null), null, String.class);
        template.requestBody(uri(null), null, String.class);

        WireMock.verify(1, getRequestedFor(urlEqualTo("/collections/" + UID)));
    }

    @Test
    void shouldResolveRequestsByTheirCloudId() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/42")).willReturn(aResponse().withStatus(200).withBody("ok")));

        String byId = "rest-postman:" + UID + "#3f2504e0-4f89-11d3-9a0c-0305e82c3301"
                      + "?postmanApiUrl=http://localhost:" + server.port()
                      + "&postmanApiKey=" + API_KEY
                      + "&variable.baseUrl=http://localhost:" + server.port() + "/v3";

        assertThat(template.requestBody(byId, null, String.class)).isEqualTo("ok");
    }

    @Test
    void shouldFailClearlyWhenTheApiKeyIsMissing() {
        String noKey = "rest-postman:" + UID + "#getPetById?postmanApiUrl=http://localhost:" + server.port();

        assertThatThrownBy(() -> template.requestBody(noKey, null, String.class))
                .rootCause()
                .hasMessageContaining("postmanApiKey is required")
                .hasMessageContaining("collectionSourceType=resource");
    }

    @Test
    void shouldRejectAPlainHttpApiUrlToARemoteHost() {
        String remote = "rest-postman:" + UID + "#getPetById"
                        + "?postmanApiUrl=http://api.getpostman.com&postmanApiKey=" + API_KEY;

        assertThatThrownBy(() -> template.requestBody(remote, null, String.class))
                .rootCause()
                .hasMessageContaining("would send the Postman API key in clear text");
    }

    /**
     * A uid-looking source can be forced to be read as a local resource, which is the escape hatch for a file that
     * happens to be named after a UUID.
     */
    @Test
    void shouldNotFetchFromTheCloudWhenSourceTypeIsResource() {
        String asResource = "rest-postman:" + UID + "#getPetById?collectionSourceType=resource";

        assertThatThrownBy(() -> template.requestBody(asResource, null, String.class))
                .rootCause()
                .hasMessageContaining(UID);
        WireMock.verify(0, getRequestedFor(urlEqualTo("/collections/" + UID)));
    }
}
