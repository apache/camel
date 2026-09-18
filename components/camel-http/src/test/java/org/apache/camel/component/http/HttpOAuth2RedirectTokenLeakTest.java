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
package org.apache.camel.component.http;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.component.http.handler.OAuth2TokenRequestHandler;
import org.apache.camel.util.IOHelper;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpClient runs protocol-level request interceptors inside {@code ProtocolExec}, which sits below
 * {@code RedirectExec} in the exec chain, so the OAuth2 interceptor runs once per redirect hop. Without a check it
 * re-attaches the bearer token to whichever host the {@code Location} header named - a host chosen by the remote
 * server, not by the route.
 * <p>
 * The tests cover both a different host and a different port because credentials are scoped to an authority, not just a
 * host name.
 */
public class HttpOAuth2RedirectTokenLeakTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "xxx.yyy.zzz";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    private final AtomicReference<String> authorizationSeenAfterRedirect = new AtomicReference<>();

    private HttpServer localServer;
    private HttpServer differentHostRedirectTarget;
    private HttpServer differentPortRedirectTarget;

    @Override
    public void setupResources() throws Exception {
        differentHostRedirectTarget = createRedirectTarget("127.0.0.1");
        differentHostRedirectTarget.start();
        differentPortRedirectTarget = createRedirectTarget("localhost");
        differentPortRedirectTarget.start();

        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/token", new OAuth2TokenRequestHandler(FAKE_TOKEN, CLIENT_ID, CLIENT_SECRET))
                .register("/redirect-to-different-host", (request, response, context) -> {
                    response.setHeader("Location",
                            "http://127.0.0.1:" + differentHostRedirectTarget.getLocalPort() + "/elsewhere");
                    response.setCode(302);
                })
                .register("/redirect-to-different-port", (request, response, context) -> {
                    response.setHeader("Location",
                            "http://localhost:" + differentPortRedirectTarget.getLocalPort() + "/elsewhere");
                    response.setCode(302);
                })
                .register("/challenge-on-different-host", (request, response, context) -> {
                    response.setHeader("Location",
                            "http://127.0.0.1:" + differentHostRedirectTarget.getLocalPort() + "/challenge");
                    response.setCode(302);
                })
                .register("/challenge-on-different-port", (request, response, context) -> {
                    response.setHeader("Location",
                            "http://localhost:" + differentPortRedirectTarget.getLocalPort() + "/challenge");
                    response.setCode(302);
                })
                .create();

        localServer.start();
    }

    private HttpServer createRedirectTarget(String canonicalHostName) {
        return ServerBootstrap.bootstrap()
                .setCanonicalHostName(canonicalHostName).setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .register("/elsewhere", (request, response, context) -> {
                    authorizationSeenAfterRedirect.set(
                            request.containsHeader("Authorization")
                                    ? request.getFirstHeader("Authorization").getValue() : null);
                    response.setCode(200);
                    response.setEntity(new StringEntity("Bye World"));
                })
                .register("/challenge", (request, response, context) -> {
                    authorizationSeenAfterRedirect.set(
                            request.containsHeader("Authorization")
                                    ? request.getFirstHeader("Authorization").getValue() : null);
                    response.setHeader("WWW-Authenticate", "Basic realm=\"elsewhere\"");
                    response.setCode(401);
                })
                .create();
    }

    @Override
    public void cleanupResources() throws Exception {
        IOHelper.close(localServer, differentHostRedirectTarget, differentPortRedirectTarget);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void theBearerTokenIsNotSentToARedirectTarget(boolean differentHost) {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setFollowRedirects(true);

        String tokenEndpoint = "http://localhost:" + localServer.getLocalPort() + "/token";
        String redirectPath = differentHost ? "/redirect-to-different-host" : "/redirect-to-different-port";
        String uri = "http://localhost:" + localServer.getLocalPort() + redirectPath + "?oauth2ClientId=" + CLIENT_ID
                     + "&oauth2ClientSecret=" + CLIENT_SECRET + "&oauth2TokenEndpoint=" + tokenEndpoint;

        String body = fluentTemplate.to(uri).request(String.class);

        assertThat(body).as("the redirect should still have been followed").isEqualTo("Bye World");
        assertThat(authorizationSeenAfterRedirect.get())
                .as("the bearer token must not be re-attached to the authority the Location header named").isNull();
    }

    /**
     * The basic-auth half of the same problem: authHost is optional and unset in the common configuration, which made
     * the credentials scope {@code new AuthScope(null, -1)} - any host, any port, any scheme. HttpClient then offers
     * the credentials to whichever host issues a 401 challenge, including one reached by following a redirect the
     * remote server chose.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void basicCredentialsAreNotOfferedToARedirectTarget(boolean differentHost) {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setFollowRedirects(true);

        String challengePath = differentHost ? "/challenge-on-different-host" : "/challenge-on-different-port";
        String uri = "http://localhost:" + localServer.getLocalPort() + challengePath
                     + "?throwExceptionOnFailure=false"
                     + "&authUsername=scott&authPassword=tiger";

        fluentTemplate.to(uri).request(String.class);

        assertThat(authorizationSeenAfterRedirect.get())
                .as("basic credentials must not be offered to the authority the Location header named").isNull();
    }
}
