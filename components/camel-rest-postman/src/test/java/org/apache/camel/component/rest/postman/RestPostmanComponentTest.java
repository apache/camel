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

import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end producer tests against a stub HTTP backend.
 */
class RestPostmanComponentTest {

    private static final String COLLECTION = "classpath:petstore-collection.json";

    private WireMockServer server;
    private DefaultCamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());

        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        server.stop();
    }

    /**
     * Points the collection's {{baseUrl}} at the stub server, keeping its /v3 path prefix.
     */
    private String uri(String fragment, String extra) {
        return "rest-postman:" + COLLECTION + (fragment != null ? "#" + fragment : "")
               + "?variable.baseUrl=http://localhost:" + server.port() + "/v3"
               + (extra != null ? "&" + extra : "");
    }

    @Test
    void shouldInvokeASingleRequest() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/7"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":7}")));

        String body = template.requestBodyAndHeader(uri("getPetById", null), null, "petId", 7, String.class);

        assertThat(body).isEqualTo("{\"id\":7}");
        WireMock.verify(getRequestedFor(urlPathEqualTo("/v3/pet/7"))
                // the header declared in the collection is applied, with its variable resolved
                .withHeader("X-Tenant", equalTo("acme")));
    }

    @Test
    void shouldFallBackToThePathValueDeclaredInTheCollection() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/42"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":42}")));

        String body = template.requestBody(uri("getPetById", null), null, String.class);

        assertThat(body).isEqualTo("{\"id\":42}");
    }

    @Test
    void shouldBindQueryParametersToMessageHeaders() {
        server.stubFor(get(urlEqualTo("/v3/pet/7?verbose=false"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String body = template.requestBodyAndHeaders(uri("getPetById", null), null,
                Map.of("petId", 7, "verbose", false), String.class);

        assertThat(body).isEqualTo("ok");
    }

    @Test
    void shouldLetTheMessageHeaderOverrideTheCollectionHeader() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/7")).willReturn(aResponse().withStatus(200).withBody("ok")));

        template.requestBodyAndHeaders(uri("getPetById", null), null,
                Map.of("petId", 7, "X-Tenant", "override"), String.class);

        WireMock.verify(getRequestedFor(urlPathEqualTo("/v3/pet/7")).withHeader("X-Tenant", equalTo("override")));
    }

    @Test
    void shouldSendTheExchangeBodyForASingleRequest() {
        server.stubFor(post(urlPathEqualTo("/v3/pet")).willReturn(aResponse().withStatus(201).withBody("created")));

        String body = template.requestBody(uri("addPet", null), "{\"name\":\"Bella\"}", String.class);

        assertThat(body).isEqualTo("created");
        WireMock.verify(postRequestedFor(urlPathEqualTo("/v3/pet"))
                .withRequestBody(equalTo("{\"name\":\"Bella\"}"))
                .withHeader("Content-Type", WireMock.containing("application/json")));
    }

    @Test
    void shouldApplyCollectionBearerAuthWhenEnabled() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/42")).willReturn(aResponse().withStatus(200).withBody("ok")));

        template.requestBody(uri("getPetById", "collectionAuth=header"), null, String.class);

        WireMock.verify(getRequestedFor(urlPathEqualTo("/v3/pet/42"))
                .withHeader("Authorization", equalTo("Bearer s3cr3t")));
    }

    @Test
    void shouldRunEveryRequestOfAFolder() {
        server.stubFor(post(urlPathEqualTo("/v3/pet")).willReturn(aResponse().withStatus(201).withBody("created")));
        server.stubFor(get(urlPathEqualTo("/v3/pet/findByStatus"))
                .willReturn(aResponse().withStatus(200).withBody("[]")));

        @SuppressWarnings("unchecked")
        List<PostmanRunResult> results = template.requestBody(uri("pets", null), null, List.class);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(PostmanRunResult::requestId).containsExactly("addPet", "listPets");
        assertThat(results).allMatch(PostmanRunResult::isSuccess);
        assertThat(results).extracting(PostmanRunResult::httpStatus).containsExactly(201, 200);

        // the runner sends the body written in the collection, since one exchange body cannot serve both requests
        WireMock.verify(postRequestedFor(urlPathEqualTo("/v3/pet"))
                .withRequestBody(equalTo("{\"name\":\"Rex\",\"tenant\":\"acme\"}")));
    }

    @Test
    void shouldRunTheWholeCollectionWhenNoRequestIsNamed() {
        server.stubFor(get(urlPathEqualTo("/v3/pet/42")).willReturn(aResponse().withStatus(200).withBody("{}")));
        server.stubFor(post(urlPathEqualTo("/v3/pet")).willReturn(aResponse().withStatus(201).withBody("created")));
        server.stubFor(get(urlPathEqualTo("/v3/pet/findByStatus"))
                .willReturn(aResponse().withStatus(200).withBody("[]")));

        @SuppressWarnings("unchecked")
        List<PostmanRunResult> results = template.requestBody(uri(null, null), null, List.class);

        assertThat(results).extracting(PostmanRunResult::requestId)
                .containsExactly("getPetById", "addPet", "listPets");
    }

    @Test
    void shouldRecordFailuresPerRequestWhenNotFailingFast() {
        server.stubFor(post(urlPathEqualTo("/v3/pet")).willReturn(aResponse().withStatus(500).withBody("boom")));
        server.stubFor(get(urlPathEqualTo("/v3/pet/findByStatus"))
                .willReturn(aResponse().withStatus(200).withBody("[]")));

        @SuppressWarnings("unchecked")
        List<PostmanRunResult> results
                = template.requestBody(uri("pets", "runFailFast=false"), null, List.class);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).failure()).isNotNull();
        // the run continued past the failure
        assertThat(results.get(1).isSuccess()).isTrue();
    }
}
