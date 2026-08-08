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

import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the exact shape of the delegated {@code rest} endpoint, with no network involved. This is the contract
 * between this component and {@code camel-rest}, so it is pinned precisely.
 */
class RestPostmanProducerUriTest {

    private static final String COLLECTION = "classpath:petstore-collection.json";

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private RestPostmanEndpoint endpoint(String uri) {
        return context.getEndpoint(uri, RestPostmanEndpoint.class);
    }

    @Test
    void shouldMapAPathAndQueryRequest() {
        RestPostmanEndpoint endpoint = endpoint("rest-postman:" + COLLECTION + "#getPetById");

        PostmanRequestBinding binding = endpoint.resolveBindings().get(0);

        assertThat(binding.method()).isEqualTo("GET");
        // {{baseUrl}} expands to https://api.example.com/v3, so the path it carries becomes the base path
        assertThat(binding.host()).isEqualTo("https://api.example.com");
        assertThat(binding.basePath()).isEqualTo("/v3");
        assertThat(binding.uriTemplate()).isEqualTo("/pet/{petId}");
        assertThat(binding.queryParameters()).isEqualTo("verbose={verbose?}");
        assertThat(binding.staticHeaders()).containsEntry("X-Tenant", "acme");
        assertThat(binding.staticHeaders()).doesNotContainKey("X-Disabled");
        assertThat(binding.defaultPathValues()).containsEntry("petId", "42");

        // only the braces are escaped: = and ? are safe characters in a Camel endpoint URI
        assertThat(endpoint.buildDelegateUri(binding))
                .isEqualTo("rest:GET:/v3:/pet/{petId}?host=https://api.example.com"
                           + "&queryParameters=verbose=%7Bverbose?%7D");
    }

    @Test
    void shouldInferContentTypeFromTheBodyMode() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#addPet").resolveBindings().get(0);

        assertThat(binding.method()).isEqualTo("POST");
        assertThat(binding.produces()).isEqualTo("application/json");
        assertThat(binding.collectionBody()).isEqualTo("{\"name\":\"Rex\",\"tenant\":\"acme\"}");
    }

    /**
     * A collection describes no responses, so there is nothing to infer an Accept header from. Inventing one would
     * change behaviour versus what Postman itself sends.
     */
    @Test
    void shouldLeaveConsumesUnsetWhenTheCollectionDoesNotDeclareAccept() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#getPetById").resolveBindings().get(0);

        assertThat(binding.consumes()).isNull();
    }

    @Test
    void shouldTreatCollectionQueryValuesAsSampleDataByDefault() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#listPets").resolveBindings().get(0);

        assertThat(binding.queryParameters()).isEqualTo("status={status?}");
    }

    @Test
    void shouldSendCollectionQueryValuesInLiteralMode() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#listPets?queryParameterMode=literal")
                        .resolveBindings().get(0);

        assertThat(binding.queryParameters()).isEqualTo("status=available");
    }

    @Test
    void shouldLetTheHostOptionOverrideTheCollection() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#getPetById?host=http://localhost:8080")
                        .resolveBindings().get(0);

        assertThat(binding.host()).isEqualTo("http://localhost:8080");
    }

    @Test
    void shouldLetTheBasePathOptionOverrideTheCollection() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#getPetById?basePath=/api")
                        .resolveBindings().get(0);

        assertThat(binding.basePath()).isEqualTo("/api");
    }

    @Test
    void shouldLetTheVariablesOptionOverrideCollectionVariables() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#getPetById?variable.baseUrl=https://other.example.com/v9")
                        .resolveBindings().get(0);

        assertThat(binding.host()).isEqualTo("https://other.example.com");
        assertThat(binding.basePath()).isEqualTo("/v9");
    }

    /**
     * CAMEL-24113: endpoints are cached by URI, so two requests that differ only in an option carried outside the URI
     * would silently share one delegate.
     */
    @Test
    void shouldProduceDistinctDelegateUrisForDistinctHosts() {
        RestPostmanEndpoint first = endpoint("rest-postman:" + COLLECTION + "#getPetById?host=http://one.example.com");
        RestPostmanEndpoint second = endpoint("rest-postman:" + COLLECTION + "#getPetById?host=http://two.example.com");

        assertThat(first.buildDelegateUri(first.resolveBindings().get(0)))
                .isNotEqualTo(second.buildDelegateUri(second.resolveBindings().get(0)));
    }

    @Test
    void shouldSelectEveryRequestOfAFolder() {
        List<PostmanRequestBinding> bindings
                = endpoint("rest-postman:" + COLLECTION + "#pets").resolveBindings();

        assertThat(bindings).extracting(PostmanRequestBinding::id).containsExactly("addPet", "listPets");
    }

    @Test
    void shouldSelectEveryRequestOfTheCollectionWhenNoRequestIsNamed() {
        List<PostmanRequestBinding> bindings = endpoint("rest-postman:" + COLLECTION).resolveBindings();

        assertThat(bindings).extracting(PostmanRequestBinding::id)
                .containsExactly("getPetById", "addPet", "listPets");
    }

    @Test
    void shouldApplyBearerAuthInHeaderMode() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#getPetById?collectionAuth=header")
                        .resolveBindings().get(0);

        assertThat(binding.staticHeaders()).containsEntry("Authorization", "Bearer s3cr3t");
    }

    @Test
    void shouldNotApplyCollectionAuthByDefault() {
        PostmanRequestBinding binding
                = endpoint("rest-postman:" + COLLECTION + "#getPetById").resolveBindings().get(0);

        assertThat(binding.staticHeaders()).doesNotContainKey("Authorization");
    }

    /**
     * The Postman API key authenticates against Postman in order to download the collection. It must never reach the
     * endpoint URI of the delegate, which shows up in logs, JMX and the developer console.
     */
    @Test
    void shouldNeverPutThePostmanApiKeyInTheDelegateUri() {
        RestPostmanEndpoint endpoint
                = endpoint("rest-postman:" + COLLECTION + "#getPetById?postmanApiKey=PMAK-do-not-leak");

        assertThat(endpoint.buildDelegateUri(endpoint.resolveBindings().get(0)))
                .doesNotContain("PMAK-do-not-leak");
    }
}
