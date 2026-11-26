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

import java.util.Objects;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * The camel-http login options given on the rest-openapi endpoint reach the HTTP client, also when other rest-openapi
 * endpoints of the same operation have other login options, or none (CAMEL-27234).
 */
public class RestOpenApiHttpLoginTest extends ManagedCamelTestSupport {

    public static WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    public static void startWireMockServer() {
        server.start();
        server.stubFor(get(urlEqualTo("/api/items"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
        server.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"oauth-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @AfterAll
    public static void stopWireMockServer() {
        server.stop();
    }

    @BeforeEach
    public void resetWireMock() throws Exception {
        server.resetRequests();
        initializeContextForComponent("http");
    }

    @Test
    public void shouldNotSendLoginOfOtherEndpoints() {
        template.sendBody("direct:anonymous", null);

        server.verify(getRequestedFor(urlEqualTo("/api/items")).withoutHeader("Authorization"));
    }

    @Test
    public void shouldSendPreemptiveBasicAuthentication() {
        template.sendBody("direct:basic", null);

        server.verify(getRequestedFor(urlEqualTo("/api/items"))
                .withHeader("Authorization", equalTo("Basic YW5uOnNlY3JldA==")));
    }

    @Test
    public void shouldSendBearerToken() {
        template.sendBody("direct:bearer", null);

        server.verify(getRequestedFor(urlEqualTo("/api/items"))
                .withHeader("Authorization", equalTo("Bearer TOKEN")));
    }

    @Test
    public void shouldSendOAuth2Token() {
        template.sendBody("direct:oauth2", null);

        server.verify(postRequestedFor(urlEqualTo("/oauth/token")));
        server.verify(getRequestedFor(urlEqualTo("/api/items"))
                .withHeader("Authorization", equalTo("Bearer oauth-token")));
    }

    @Override
    protected CamelContext createCamelContext(String componentName) {
        final CamelContext camelContext = new DefaultCamelContext();

        final RestOpenApiComponent component = new RestOpenApiComponent();
        component.setComponentName(componentName);
        component.setHost("http://localhost:" + server.port());
        component.setBasePath("/api");
        component.setSpecificationUri(Objects
                .requireNonNull(RestOpenApiHttpLoginTest.class.getResource("/http-login.yaml"))
                .toString());

        camelContext.addComponent("items", component);

        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:anonymous")
                        .to("items:listItems");

                from("direct:basic")
                        .to("items:listItems?authMethod=Basic&authUsername=ann&authPassword=secret"
                            + "&authenticationPreemptive=true");

                from("direct:bearer")
                        .to("items:listItems?authMethod=Bearer&authBearerToken=TOKEN&authenticationPreemptive=true");

                from("direct:oauth2")
                        .to("items:listItems?oauth2ClientId=cid&oauth2ClientSecret=csecret"
                            + "&oauth2TokenEndpoint=http://localhost:" + server.port() + "/oauth/token");
            }
        };
    }
}
