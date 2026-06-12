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
package org.apache.camel.component.undertow.rest;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.StubOAuthTokenValidationFactory;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestUndertowOAuthProfileTest extends BaseUndertowTest {

    private final AtomicInteger routeInvocations = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind(OAuthTokenValidationFactory.FACTORY, new StubOAuthTokenValidationFactory());
        return context;
    }

    @Test
    public void endpointPropertyConfiguresOAuthOnRestEndpoint() {
        UndertowEndpoint endpoint = restEndpoint();

        assertEquals("myprofile", endpoint.getOauthProfile());
        assertNotNull(endpoint.getOauthHttpSecurity());
    }

    @Test
    public void rejectsMissingBearerToken() {
        Exchange out = requestAuthorization(null);

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void rejectsInvalidBearerToken() {
        Exchange out = requestAuthorization("Bearer invalid-token");

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Bearer error=\"invalid_token\"",
                out.getMessage().getHeader("WWW-Authenticate"));
        assertEquals(0, routeInvocations.get());
    }

    @Test
    public void validBearerTokenReachesRoute() {
        Exchange out = requestAuthorization("Bearer valid-token");

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("rest-ok", out.getMessage().getBody(String.class));
        assertEquals(1, routeInvocations.get());
    }

    private UndertowEndpoint restEndpoint() {
        return context.getEndpoints().stream()
                .filter(UndertowEndpoint.class::isInstance)
                .map(UndertowEndpoint.class::cast)
                .filter(candidate -> candidate.getEndpointUri().contains("/secure/basic"))
                .filter(candidate -> "GET,OPTIONS".equals(candidate.getHttpMethodRestrict()))
                .findFirst()
                .orElseThrow();
    }

    private Exchange requestAuthorization(String authorization) {
        return template.request("undertow:http://localhost:{{port}}/secure/basic?throwExceptionOnFailure=false",
                exchange -> {
                    if (authorization != null) {
                        exchange.getMessage().setHeader("Authorization", authorization);
                    }
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("undertow").host("localhost").port(getPort())
                        .endpointProperty("oauthProfile", "myprofile");

                rest("/secure/")
                        .get("basic")
                        .to("direct:secure");

                from("direct:secure")
                        .process(exchange -> {
                            routeInvocations.incrementAndGet();
                            exchange.getMessage().setBody("rest-ok");
                        });
            }
        };
    }
}
