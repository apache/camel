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
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HttpClient runs protocol-level request interceptors inside {@code ProtocolExec}, which sits below
 * {@code RedirectExec} in the exec chain, so the OAuth2 interceptor runs once per redirect hop. Without a check it
 * re-attaches the bearer token to whichever host the {@code Location} header named - a host chosen by the remote
 * server, not by the route.
 * <p>
 * The redirect here points at {@code 127.0.0.1} while the endpoint addresses {@code localhost}: the same machine, so
 * the test needs no second server, but a different host as far as the check is concerned.
 */
public class HttpOAuth2RedirectTokenLeakTest extends BaseHttpTest {

    private static final String FAKE_TOKEN = "xxx.yyy.zzz";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    private final AtomicReference<String> authorizationSeenAfterRedirect = new AtomicReference<>();

    private HttpServer localServer;
    private HttpServer redirectTarget;

    @Override
    public void setupResources() throws Exception {
        // A second server, addressed as 127.0.0.1, so the redirect genuinely names a different host than the
        // endpoint's localhost. Both servers reject a Host header that is not their canonical name, so the
        // redirect has to point at a server that answers to 127.0.0.1.
        redirectTarget = ServerBootstrap.bootstrap()
                .setCanonicalHostName("127.0.0.1").setHttpProcessor(getBasicHttpProcessor())
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
                    // demand credentials, which is what makes HttpClient consult the credentials provider
                    response.setHeader("WWW-Authenticate", "Basic realm=\"elsewhere\"");
                    response.setCode(401);
                })
                .create();
        redirectTarget.start();

        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/token", new OAuth2TokenRequestHandler(FAKE_TOKEN, CLIENT_ID, CLIENT_SECRET))
                .register("/redirect", (request, response, context) -> {
                    response.setHeader("Location",
                            "http://127.0.0.1:" + redirectTarget.getLocalPort() + "/elsewhere");
                    response.setCode(302);
                })
                .register("/redirect-to-challenge", (request, response, context) -> {
                    response.setHeader("Location",
                            "http://127.0.0.1:" + redirectTarget.getLocalPort() + "/challenge");
                    response.setCode(302);
                })
                .create();

        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (redirectTarget != null) {
            redirectTarget.close();
        }
    }

    @Test
    public void theBearerTokenIsNotSentToARedirectTarget() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setFollowRedirects(true);

        String tokenEndpoint = "http://localhost:" + localServer.getLocalPort() + "/token";
        String uri = "http://localhost:" + localServer.getLocalPort() + "/redirect?oauth2ClientId=" + CLIENT_ID
                     + "&oauth2ClientSecret=" + CLIENT_SECRET + "&oauth2TokenEndpoint=" + tokenEndpoint;

        String body = fluentTemplate.to(uri).request(String.class);

        assertEquals("Bye World", body, "the redirect should still have been followed");
        assertNull(authorizationSeenAfterRedirect.get(),
                "the bearer token must not be re-attached to the host the Location header named");
    }

    /**
     * The basic-auth half of the same problem: authHost is optional and unset in the common configuration, which made
     * the credentials scope {@code new AuthScope(null, -1)} - any host, any port, any scheme. HttpClient then offers
     * the credentials to whichever host issues a 401 challenge, including one reached by following a redirect the
     * remote server chose.
     */
    @Test
    public void basicCredentialsAreNotOfferedToARedirectTarget() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setFollowRedirects(true);

        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/redirect-to-challenge?throwExceptionOnFailure=false"
                     + "&authUsername=scott&authPassword=tiger";

        fluentTemplate.to(uri).request(String.class);

        assertNull(authorizationSeenAfterRedirect.get(),
                "basic credentials must not be offered to the host the Location header named");
    }
}
