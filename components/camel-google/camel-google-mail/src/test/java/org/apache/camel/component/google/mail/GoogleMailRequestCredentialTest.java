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
package org.apache.camel.component.google.mail;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.common.GoogleCredentialsHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The client sends the OAuth access token in the Authorization header; the request URL does not carry it.
 */
class GoogleMailRequestCredentialTest {

    private static final String OAUTH = "clientId=id&clientSecret=secret&accessToken=atoken";

    private final RecordingTransport transport = new RecordingTransport();

    @Test
    void shouldSendTheAccessTokenOfTheProducerOnlyInTheAuthorizationHeader() throws Exception {
        try (CamelContext context = createContext()) {
            Object body
                    = context.createProducerTemplate().requestBody("google-mail://users/getProfile?userId=me&" + OAUTH,
                            (Object) null);

            assertNotNull(body);
            assertSentWithTheTokenInTheHeaderOnly(transport.requests.get(0));
        }
    }

    @Test
    void shouldSendTheAccessTokenOfTheConsumerOnlyInTheAuthorizationHeader() throws Exception {
        try (CamelContext context = createContext()) {
            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMinimumMessageCount(1);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("google-mail://users/getProfile?userId=me&delay=100&" + OAUTH).to("mock:result");
                }
            });

            mock.assertIsSatisfied();
            assertSentWithTheTokenInTheHeaderOnly(transport.requests.get(0));
        }
    }

    private static void assertSentWithTheTokenInTheHeaderOnly(MockLowLevelHttpRequest request) {
        assertFalse(request.getUrl().contains("access_token"), request.getUrl());
        assertFalse(request.getUrl().contains("atoken"), request.getUrl());
        assertEquals("Bearer atoken", request.getFirstHeaderValue("Authorization"));
    }

    private CamelContext createContext() {
        CamelContext context = new DefaultCamelContext();
        context.getComponent("google-mail", GoogleMailComponent.class).setClientFactory(new OAuthClientFactory(transport));
        context.start();
        return context;
    }

    /**
     * Creates the client with the OAuth credential as the client factory of the component does, on a transport that
     * records the requests.
     */
    private static final class OAuthClientFactory implements GoogleMailClientFactory {

        private final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        private final MockHttpTransport transport;

        private OAuthClientFactory(MockHttpTransport transport) {
            this.transport = transport;
        }

        @Override
        public Gmail makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName, String refreshToken,
                String accessToken) {
            GoogleMailConfiguration configuration = new GoogleMailConfiguration();
            configuration.setClientId(clientId);
            configuration.setClientSecret(clientSecret);
            configuration.setRefreshToken(refreshToken);
            configuration.setAccessToken(accessToken);
            try {
                Credential credential
                        = GoogleCredentialsHelper.getOAuthCredential(null, configuration, scopes, transport, jsonFactory);
                return new Gmail.Builder(transport, jsonFactory, credential).setApplicationName("test").build();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Gmail makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            throw new UnsupportedOperationException("OAuth only");
        }
    }

    private static final class RecordingTransport extends MockHttpTransport {

        private final List<MockLowLevelHttpRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public MockLowLevelHttpRequest buildRequest(String method, String url) {
            MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url);
            request.setResponse(new MockLowLevelHttpResponse().setContentType(Json.MEDIA_TYPE)
                    .setContent("{\"emailAddress\":\"me@example.com\"}"));
            requests.add(request);
            return request;
        }
    }
}
