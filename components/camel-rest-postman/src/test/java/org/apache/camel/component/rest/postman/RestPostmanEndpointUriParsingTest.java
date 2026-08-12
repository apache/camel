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

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RestPostmanEndpointUriParsingTest {

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @ParameterizedTest
    @CsvSource({
            // with an explicit fragment the source and the request are both given
            "'rest-postman:my-api.json#getPetById', my-api.json, getPetById",
            "'rest-postman:classpath:my-api.json#pets/addPet', classpath:my-api.json, pets/addPet",
            "'rest-postman:https://example.com/api.json#getPetById', https://example.com/api.json, getPetById",
            // a bare word is a request against the default collection
            "'rest-postman:getPetById', postman-collection.json, getPetById",
            // a bare .json is the collection, with the whole collection selected
            "'rest-postman:my-api.json', my-api.json, ",
            // a bare uid is a cloud collection, with the whole collection selected
            "'rest-postman:12ece9e1-2abf-4edc-8e34-de66e74114d2', 12ece9e1-2abf-4edc-8e34-de66e74114d2, ",
            // an empty fragment means the whole collection too
            "'rest-postman:my-api.json#', my-api.json, "
    })
    void shouldParseTheUriRemainder(String uri, String expectedSource, String expectedRequestId) {
        RestPostmanEndpoint endpoint = context.getEndpoint(uri, RestPostmanEndpoint.class);

        assertThat(endpoint.getCollectionSource()).isEqualTo(expectedSource);
        assertThat(endpoint.getRequestId()).isEqualTo(expectedRequestId);
    }

    @Test
    void shouldFallBackToTheCollectionSourceOfTheComponent() {
        RestPostmanComponent component = new RestPostmanComponent(context);
        component.setCollectionSource("shared.json");
        context.addComponent("rest-postman", component);

        RestPostmanEndpoint endpoint = context.getEndpoint("rest-postman:getPetById", RestPostmanEndpoint.class);

        assertThat(endpoint.getCollectionSource()).isEqualTo("shared.json");
        assertThat(endpoint.getRequestId()).isEqualTo("getPetById");
    }

    @Test
    void shouldBeLenientSoThatUnknownParametersBecomeLiteralValues() {
        RestPostmanEndpoint endpoint
                = context.getEndpoint("rest-postman:my-api.json#getPetById?version=v3", RestPostmanEndpoint.class);

        assertThat(endpoint.isLenientProperties()).isTrue();
        assertThat(endpoint.parameters).containsEntry("version", "v3");
    }

    @Test
    void shouldNotLeakVariableOptionsIntoTheLenientParameters() {
        // variable.x is a real multiValue option, so it must be consumed rather than treated as a query value
        RestPostmanEndpoint endpoint = context.getEndpoint(
                "rest-postman:my-api.json#getPetById?variable.baseUrl=https://x.example.com", RestPostmanEndpoint.class);

        assertThat(endpoint.parameters).doesNotContainKey("variable.baseUrl");
        assertThat(endpoint.getConfiguration().variablesAsStrings())
                .containsEntry("baseUrl", "https://x.example.com");
    }
}
