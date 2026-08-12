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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the dispatch and validation logic with plain {@code direct} routes and no HTTP server.
 */
class DefaultRestPostmanProcessorStrategyTest {

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

    private List<PostmanRequestBinding> bindings(String uri) {
        return context.getEndpoint(uri, RestPostmanEndpoint.class).resolveBindings();
    }

    private DefaultRestPostmanProcessorStrategy strategy(String missingRequest) {
        DefaultRestPostmanProcessorStrategy strategy = new DefaultRestPostmanProcessorStrategy();
        strategy.setCamelContext(context);
        strategy.setMissingRequest(missingRequest);
        return strategy;
    }

    /**
     * The folder qualified id contains a slash, and it is used verbatim as a {@code direct} endpoint name. This pins
     * that a slash survives endpoint URI normalisation, which the whole dispatch contract depends on.
     */
    @Test
    void shouldMatchADirectRouteWhoseNameContainsASlash() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:users/getUserById").to("mock:out");
            }
        });
        context.start();

        assertThat(context.getRoutes().get(0).getEndpoint().getEndpointBaseUri())
                .isEqualTo("direct://users/getUserById");
    }

    @Test
    void shouldResolveTheDispatchIdToTheRouteThatExists() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getPetById").to("mock:out");
            }
        });
        context.start();

        PostmanRequestBinding binding = bindings("rest-postman:" + COLLECTION + "#getPetById").get(0);

        assertThat(strategy("fail").resolveDispatchId(binding)).isEqualTo("getPetById");
    }

    /**
     * A collection fetched from the cloud carries request ids, so a route may legitimately be named after one.
     */
    @Test
    void shouldAlsoAcceptTheRequestIdAsADispatchId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:3f2504e0-4f89-11d3-9a0c-0305e82c3301").to("mock:out");
            }
        });
        context.start();

        PostmanRequestBinding binding = bindings("rest-postman:" + COLLECTION + "#getPetById").get(0);

        assertThat(strategy("fail").resolveDispatchId(binding))
                .isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    }

    @Test
    void shouldFailWhenARequestHasNoRoute() throws Exception {
        context.start();
        List<PostmanRequestBinding> bindings = bindings("rest-postman:" + COLLECTION);

        assertThatThrownBy(() -> strategy("fail").validateCollection(bindings, "/v3", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not mapped to a corresponding route")
                .hasMessageContaining("direct:getPetById");
    }

    @Test
    void shouldNotFailWhenMissingRequestIsIgnore() throws Exception {
        context.start();
        List<PostmanRequestBinding> bindings = bindings("rest-postman:" + COLLECTION);

        assertThatCode(() -> strategy("ignore").validateCollection(bindings, "/v3", null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotFailWhenMissingRequestIsMock() throws Exception {
        context.start();
        List<PostmanRequestBinding> bindings = bindings("rest-postman:" + COLLECTION);

        assertThatCode(() -> strategy("mock").validateCollection(bindings, "/v3", null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldPassValidationWhenEveryRequestHasARoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getPetById").to("mock:out");
                from("direct:addPet").to("mock:out");
                from("direct:listPets").to("mock:out");
            }
        });
        context.start();
        List<PostmanRequestBinding> bindings = bindings("rest-postman:" + COLLECTION);

        assertThatCode(() -> strategy("fail").validateCollection(bindings, "/v3", null))
                .doesNotThrowAnyException();
    }

    /**
     * Two requests on the same method and path would shadow each other in the matcher, which is worse than a loud
     * startup failure.
     */
    @Test
    void shouldFailWhenTwoRequestsShareAMethodAndPath() throws Exception {
        context.start();
        List<PostmanRequestBinding> bindings = bindings("rest-postman:classpath:shadowed-collection.json");

        assertThatThrownBy(() -> strategy("ignore").validateCollection(bindings, "/", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shadow each other")
                .hasMessageContaining("requestFilter");
    }

    @Test
    void shouldAllowShadowedRequestsToBeFilteredOut() throws Exception {
        context.start();
        List<PostmanRequestBinding> bindings
                = bindings("rest-postman:classpath:shadowed-collection.json?requestFilter=!getUserError");

        assertThat(bindings).hasSize(1);
        assertThatCode(() -> strategy("ignore").validateCollection(bindings, "/", null))
                .doesNotThrowAnyException();
    }
}
