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
package org.apache.camel.component.rest.openapi;

import java.util.Arrays;
import java.util.Objects;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.RestEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * A space after the ; of a media type parameter must not reach the server as a + (CAMEL-27204).
 */
public class RestOpenApiMediaTypeParametersTest extends ManagedCamelTestSupport {

    // the HTTP clients may write the charset in upper case, or add their own space after the ;
    private static final String CONTENT_TYPE = "application/json; ?charset=(?i:utf-8)";

    public static WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    public static void startWireMockServer() {
        server.start();
        server.stubFor(post(urlEqualTo("/api/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
    }

    @AfterAll
    public static void stopWireMockServer() {
        server.stop();
    }

    @BeforeEach
    public void resetWireMock() {
        server.resetRequests();
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldSendMediaTypesFromSpecificationWithoutPlus(String componentName) throws Exception {
        initializeContextForComponent(componentName);

        template.sendBody("direct:fromSpec", "{}");

        server.verify(postRequestedFor(urlEqualTo("/api/orders"))
                .withHeader("Accept", equalTo("application/json;charset=utf-8"))
                .withHeader("Content-Type", matching(CONTENT_TYPE)));
    }

    @ParameterizedTest
    @MethodSource("knownProducers")
    public void shouldSendMediaTypesFromEndpointWithoutPlus(String componentName) throws Exception {
        initializeContextForComponent(componentName);

        template.sendBody("direct:fromEndpoint", "{}");

        server.verify(postRequestedFor(urlEqualTo("/api/orders"))
                .withHeader("Accept", equalTo("application/vnd.api+json,application/json;q=0.5"))
                .withHeader("Content-Type", matching(CONTENT_TYPE)));
    }

    @Override
    protected CamelContext createCamelContext(String componentName) {
        final CamelContext camelContext = new DefaultCamelContext();

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName(componentName);
        component.setHost("http://localhost:" + server.port());
        component.setBasePath("/api");
        component.setSpecificationUri(Objects
                .requireNonNull(RestOpenApiMediaTypeParametersTest.class.getResource("/media-type-parameters.yaml"))
                .toString());

        camelContext.addComponent("orders", component);

        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fromSpec").to("orders:addOrder");

                from("direct:fromEndpoint").to(
                        "orders:addOrder?consumes=RAW(application/vnd.api+json, application/json; q=0.5)"
                                               + "&produces=RAW(application/json; charset=utf-8)");
            }
        };
    }

    public static Iterable<String> knownProducers() {
        return Arrays.asList(RestEndpoint.DEFAULT_REST_PRODUCER_COMPONENTS);
    }
}
