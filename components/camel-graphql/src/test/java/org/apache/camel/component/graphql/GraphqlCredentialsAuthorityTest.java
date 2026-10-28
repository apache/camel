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
package org.apache.camel.component.graphql;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpComponent;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The access token and the basic credentials belong to the authority of httpUri: they are sent to its scheme, host and
 * port only. The tests cover both a different host and a different port, and the message headers that camel-http reads
 * to build the request URL.
 */
class GraphqlCredentialsAuthorityTest extends BaseGraphqlTest {

    private static final String ACCESS_TOKEN = "token";
    private static final String USERNAME = "camel";
    private static final String PASSWORD = "password";

    private final AtomicReference<String> authorizationSeenByHttpUri = new AtomicReference<>();
    private final AtomicReference<String> authorizationSeenElsewhere = new AtomicReference<>();
    private final AtomicInteger requestsElsewhere = new AtomicInteger();

    private HttpServer localServer;
    private HttpServer differentHost;
    private HttpServer differentPort;

    @Override
    public void setupResources() throws Exception {
        differentHost = createOtherAuthority("127.0.0.1");
        differentHost.start();
        differentPort = createOtherAuthority("localhost");
        differentPort.start();

        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .register("/graphql", (request, response, context) -> {
                    authorizationSeenByHttpUri.set(authorization(request));
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity(getExpectedContent()));
                })
                .register("/moved-to-different-host",
                        movedTo("http://127.0.0.1:" + differentHost.getLocalPort() + "/graphql"))
                .register("/moved-to-different-port",
                        movedTo("http://localhost:" + differentPort.getLocalPort() + "/graphql"))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        // the client of the endpoint keeps its connections open until the context stops
        for (HttpServer server : new HttpServer[] { localServer, differentHost, differentPort }) {
            if (server != null) {
                server.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void accessTokenIsSentToTheHttpUriAuthorityOnly(boolean toDifferentHost) {
        context.getComponent("http", HttpComponent.class).setFollowRedirects(true);

        template.request(graphqlUri(toDifferentHost) + "&accessToken=" + ACCESS_TOKEN, exchange -> {
        });

        assertEquals(1, requestsElsewhere.get(), "the other authority should have been called");
        assertNull(authorizationSeenElsewhere.get(), "the access token must not be sent to another authority");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void basicCredentialsAreOfferedToTheHttpUriAuthorityOnly(boolean toDifferentHost) {
        context.getComponent("http", HttpComponent.class).setFollowRedirects(true);

        template.request(graphqlUri(toDifferentHost) + "&username=" + USERNAME + "&password=" + PASSWORD, exchange -> {
        });

        assertEquals(1, requestsElsewhere.get(), "the other authority should have been called once, without credentials");
        assertNull(authorizationSeenElsewhere.get(), "the basic credentials must not be offered to another authority");
    }

    @Test
    void controlHeadersDoNotChangeTheTarget() {
        Exchange exchange = template.request(
                "graphql://http://localhost:" + localServer.getLocalPort() + "/graphql?query={books{id name}}"
                                             + "&accessToken=" + ACCESS_TOKEN,
                e -> {
                    e.getIn().setHeader(Exchange.HTTP_URI,
                            "http://127.0.0.1:" + differentHost.getLocalPort() + "/graphql");
                    e.getIn().setHeader(Exchange.HTTP_PATH, "/other");
                    e.getIn().setHeader(Exchange.HTTP_QUERY, "other=true");
                });

        assertNull(exchange.getException());
        assertEquals("Bearer " + ACCESS_TOKEN, authorizationSeenByHttpUri.get());
        assertEquals(0, requestsElsewhere.get(), "the request must go to httpUri");
    }

    private String graphqlUri(boolean toDifferentHost) {
        String path = toDifferentHost ? "/moved-to-different-host" : "/moved-to-different-port";
        return "graphql://http://localhost:" + localServer.getLocalPort() + path
               + "?query={books{id name}}&throwExceptionOnFailure=false";
    }

    private HttpServer createOtherAuthority(String canonicalHostName) {
        // answers 401 with a Basic challenge when no credentials are sent, as a server asking for them would
        return ServerBootstrap.bootstrap()
                .setCanonicalHostName(canonicalHostName).setHttpProcessor(getBasicHttpProcessor())
                .register("/graphql", (request, response, context) -> {
                    requestsElsewhere.incrementAndGet();
                    String authorization = authorization(request);
                    authorizationSeenElsewhere.set(authorization);
                    if (authorization == null) {
                        response.setCode(HttpStatus.SC_UNAUTHORIZED);
                    } else {
                        response.setCode(HttpStatus.SC_OK);
                        response.setEntity(new StringEntity(getExpectedContent()));
                    }
                })
                .create();
    }

    private static HttpRequestHandler movedTo(String location) {
        return (request, response, context) -> {
            response.setHeader(HttpHeaders.LOCATION, location);
            response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
        };
    }

    private static String authorization(ClassicHttpRequest request) {
        return request.containsHeader(HttpHeaders.AUTHORIZATION)
                ? request.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue() : null;
    }
}
