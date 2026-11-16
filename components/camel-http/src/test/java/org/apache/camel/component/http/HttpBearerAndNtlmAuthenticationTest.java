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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpConfiguration;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpoint options {@code authMethod=Bearer} with {@code authBearerToken}, and {@code authMethod=NTLM} without
 * {@code authDomain}, must authenticate the same way as the same settings on the component.
 */
public class HttpBearerAndNtlmAuthenticationTest extends BaseHttpTest {

    private static final String TOKEN = "abc";

    private final List<String> seenByServer = new CopyOnWriteArrayList<>();
    private final List<String> seenByOtherHost = new CopyOnWriteArrayList<>();

    private HttpServer localServer;
    private HttpServer otherHost;

    @Override
    public void setupResources() throws Exception {
        otherHost = ServerBootstrap.bootstrap()
                .setCanonicalHostName("127.0.0.1")
                .register("/secure", bearerProtected(seenByOtherHost))
                .create();
        otherHost.start();

        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/secure", bearerProtected(seenByServer))
                .register("/basic", (request, response, context) -> {
                    Header auth = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    seenByServer.add(auth == null ? "<none>" : auth.getValue());
                    if (auth != null) {
                        response.setCode(HttpStatus.SC_OK);
                        response.setEntity(new StringEntity(auth.getValue()));
                    } else {
                        response.setCode(HttpStatus.SC_UNAUTHORIZED);
                        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"camel\"");
                    }
                })
                .register("/ntlm", (request, response, context) -> {
                    Header auth = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    seenByServer.add(auth == null ? "<none>" : auth.getValue());
                    response.setCode(HttpStatus.SC_UNAUTHORIZED);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "NTLM");
                })
                .register("/redirect", (request, response, context) -> {
                    Header auth = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    seenByServer.add(auth == null ? "<none>" : auth.getValue());
                    response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.setHeader(HttpHeaders.LOCATION, "http://127.0.0.1:" + otherHost.getLocalPort() + "/secure");
                })
                .create();
        localServer.start();
    }

    private static HttpRequestHandler bearerProtected(List<String> seen) {
        return (request, response, context) -> {
            Header auth = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
            seen.add(auth == null ? "<none>" : auth.getValue());
            if (auth != null && ("Bearer " + TOKEN).equals(auth.getValue())) {
                response.setCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("ok"));
            } else {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"camel\"");
            }
        };
    }

    @Override
    public void cleanupResources() throws Exception {
        closeImmediately(localServer, otherHost).close();
    }

    private String url(String path) {
        return "http://localhost:" + localServer.getLocalPort() + path;
    }

    private Exchange get(String uri) {
        return template.request(uri, exchange -> {
        });
    }

    @Test
    public void bearerTokenOnEndpointAnswersTheChallenge() {
        Exchange out = get(url("/secure?authMethod=Bearer&authBearerToken=" + TOKEN));

        assertNull(out.getException());
        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("ok", out.getMessage().getBody(String.class));
        assertEquals(List.of("<none>", "Bearer " + TOKEN), seenByServer);
    }

    @Test
    public void bearerTokenOnEndpointIsSentPreemptively() {
        Exchange out = get(url("/secure?authMethod=Bearer&authBearerToken=" + TOKEN + "&authenticationPreemptive=true"));

        assertNull(out.getException());
        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(List.of("Bearer " + TOKEN), seenByServer);
    }

    @Test
    public void bearerTokenOnComponentAnswersTheChallenge() {
        context.addComponent("bearer", componentWithBearerToken());

        Exchange out = get("bearer://localhost:" + localServer.getLocalPort() + "/secure");

        assertNull(out.getException());
        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(List.of("<none>", "Bearer " + TOKEN), seenByServer);
    }

    @Test
    public void bearerTokenOnComponentIsSentPreemptively() {
        context.addComponent("bearer", componentWithBearerToken());

        Exchange out = get("bearer://localhost:" + localServer.getLocalPort() + "/secure?authenticationPreemptive=true");

        assertNull(out.getException());
        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(List.of("Bearer " + TOKEN), seenByServer);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void bearerTokenIsNotSentToARedirectTargetOnAnotherHost(boolean preemptive) {
        Exchange out = get(url("/redirect?authMethod=Bearer&authBearerToken=" + TOKEN
                               + "&followRedirects=true&throwExceptionOnFailure=false&authenticationPreemptive="
                               + preemptive));

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(seenByOtherHost.stream().noneMatch(h -> h.contains(TOKEN)), "other host received: " + seenByOtherHost);
    }

    @Test
    public void authorizationHeaderIsSentAsIs() {
        Exchange out = template.request(url("/secure"),
                exchange -> exchange.getMessage().setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(List.of("Bearer " + TOKEN), seenByServer);
    }

    @Test
    public void basicCredentialsOnEndpointAreSentPreemptively() {
        Exchange out = get(url("/basic?authMethod=Basic&authUsername=u&authPassword=p&authenticationPreemptive=true"));

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(List.of("Basic dTpw"), seenByServer);
    }

    @Test
    public void basicCredentialsOnEndpointAnswerTheChallenge() {
        Exchange out = get(url("/basic?authUsername=u&authPassword=p"));

        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(List.of("<none>", "Basic dTpw"), seenByServer);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void ntlmOnEndpointWithoutDomainAnswersTheChallenge(boolean preemptive) {
        Exchange out = get(url("/ntlm?authMethod=NTLM&authUsername=u&authPassword=p&throwExceptionOnFailure=false"
                               + "&authenticationPreemptive=" + preemptive));

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(seenByServer.stream().anyMatch(h -> h.startsWith("NTLM ")), "server received: " + seenByServer);
    }

    @Test
    public void ntlmOnComponentAnswersTheChallenge() {
        HttpConfiguration configuration = new HttpConfiguration();
        configuration.setAuthMethod("NTLM");
        configuration.setAuthUsername("u");
        configuration.setAuthPassword("p");
        HttpComponent component = new HttpComponent();
        component.setHttpConfiguration(configuration);
        context.addComponent("ntlm", component);

        Exchange out = get("ntlm://localhost:" + localServer.getLocalPort() + "/ntlm?throwExceptionOnFailure=false");

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(seenByServer.stream().anyMatch(h -> h.startsWith("NTLM ")), "server received: " + seenByServer);
    }

    @Test
    public void ntlmOnEndpointWithDomainAnswersTheChallenge() {
        Exchange out = get(url("/ntlm?authMethod=NTLM&authUsername=u&authPassword=p&authDomain=D"
                               + "&throwExceptionOnFailure=false"));

        assertEquals(401, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertTrue(seenByServer.stream().anyMatch(h -> h.startsWith("NTLM ")), "server received: " + seenByServer);
    }

    private static HttpComponent componentWithBearerToken() {
        HttpConfiguration configuration = new HttpConfiguration();
        configuration.setAuthMethod("Bearer");
        configuration.setAuthBearerToken(TOKEN);
        HttpComponent component = new HttpComponent();
        component.setHttpConfiguration(configuration);
        return component;
    }
}
