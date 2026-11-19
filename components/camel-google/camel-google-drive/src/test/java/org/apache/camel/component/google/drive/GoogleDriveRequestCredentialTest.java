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
package org.apache.camel.component.google.drive;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.drive.Drive;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The client sends the OAuth access token in the Authorization header; the request URL does not carry it.
 */
class GoogleDriveRequestCredentialTest {

    private static final String OAUTH = "clientId=id&clientSecret=secret&accessToken=atoken";

    private final RecordingTransport transport = new RecordingTransport();

    @Test
    void shouldSendTheAccessTokenOfTheProducerOnlyInTheAuthorizationHeader() throws Exception {
        try (CamelContext context = createContext()) {
            Object body
                    = context.createProducerTemplate().requestBody("google-drive://drive-files/list?" + OAUTH, (Object) null);

            assertNotNull(body);
            assertSentWithTheTokenInTheHeaderOnly(transport.requests.get(0));
        }
    }

    @Test
    void shouldNotSendAnAccessTokenHeaderOnTheRequestWhateverItsCase() throws Exception {
        for (String header : List.of("CamelGoogleDrive.accessToken", "CamelGoogleDrive.AccessToken")) {
            transport.requests.clear();
            try (CamelContext context = createContext()) {
                context.createProducerTemplate().requestBodyAndHeader(
                        "google-drive://drive-files/list?" + OAUTH, null, header, "htoken");

                String url = transport.requests.get(0).getUrl();
                assertFalse(url.contains("access_token"), header + " -> " + url);
                assertFalse(url.contains("htoken"), header + " -> " + url);
            }
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
                    from("google-drive://drive-files/list?delay=100&" + OAUTH).to("mock:result");
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
        context.getComponent("google-drive", GoogleDriveComponent.class).setClientFactory(new OAuthClientFactory(transport));
        context.start();
        return context;
    }

    /**
     * Creates the client with the access token as the client factory of the component does, on a transport that records
     * the requests.
     */
    private static final class OAuthClientFactory implements GoogleDriveClientFactory {

        private final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        private final MockHttpTransport transport;

        private OAuthClientFactory(MockHttpTransport transport) {
            this.transport = transport;
        }

        @Override
        public Drive makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName, String refreshToken,
                String accessToken) {
            // a bearer token credential, as the client factory of the component sets the accessToken on its credential
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod()).setAccessToken(accessToken);
            return new Drive.Builder(transport, jsonFactory, credential).setApplicationName("test").build();
        }

        @Override
        public Drive makeClient(
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
                    .setContent("{\"files\":[]}"));
            requests.add(request);
            return request;
        }
    }
}
