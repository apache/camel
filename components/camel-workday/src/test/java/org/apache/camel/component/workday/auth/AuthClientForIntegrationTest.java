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
package org.apache.camel.component.workday.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthClientForIntegrationTest {

    @Test
    void tokenRequestHasTheBasicAuthorizationHeader() throws Exception {
        TokenEndpoint tokenEndpoint = new TokenEndpoint("{\"access_token\":\"the-token\"}");

        assertEquals("the-token", new AuthClientForIntegration(configuration()).getBearerToken(tokenEndpoint));

        ClassicHttpRequest request = tokenEndpoint.request;
        assertEquals("https://impl.workday.com/ccx/oauth2/camel/token", request.getUri().toString());
        // Base64 of client-id:client-secret
        assertEquals("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=", request.getFirstHeader("Authorization").getValue());
        assertEquals("grant_type=refresh_token&refresh_token=the-refresh-token",
                EntityUtils.toString(request.getEntity(), StandardCharsets.UTF_8));
    }

    @Test
    void accessTokenIsReadFromAnyJsonLayout() throws Exception {
        TokenEndpoint tokenEndpoint = new TokenEndpoint(
                "{\n  \"access_token\" : \"the-token\",\n  \"token_type\" : \"Bearer\",\n  \"expires_in\" : 3600\n}");

        assertEquals("the-token", new AuthClientForIntegration(configuration()).getBearerToken(tokenEndpoint));
    }

    @Test
    void responseWithoutAccessTokenIsRejected() {
        AuthClientForIntegration client = new AuthClientForIntegration(configuration());

        for (String response : new String[] {
                "{\"error\":\"invalid_client\",\"error_description\":\"no access_token for you\"}", "access_token", "" }) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> client.getBearerToken(new TokenEndpoint(response)), response);
            assertEquals("No valid access token response.", e.getMessage());
        }
    }

    private static WorkdayConfiguration configuration() {
        WorkdayConfiguration configuration = new WorkdayConfiguration();
        configuration.setHost("impl.workday.com");
        configuration.setTenant("camel");
        configuration.setClientId("client-id");
        configuration.setClientSecret("client-secret");
        configuration.setTokenRefresh("the-refresh-token");
        return configuration;
    }

    /**
     * Records the token request and answers it with the given JSON, in place of the Workday token endpoint.
     */
    private static final class TokenEndpoint extends CloseableHttpClient {

        private final String response;
        private ClassicHttpRequest request;

        private TokenEndpoint(String response) {
            this.response = response;
        }

        @Override
        public <T> T execute(ClassicHttpRequest request, HttpClientResponseHandler<? extends T> responseHandler)
                throws IOException {
            this.request = request;
            try (BasicClassicHttpResponse httpResponse = new BasicClassicHttpResponse(HttpStatus.SC_OK)) {
                httpResponse.setEntity(new StringEntity(response, ContentType.APPLICATION_JSON));
                return responseHandler.handleResponse(httpResponse);
            } catch (HttpException e) {
                throw new IOException(e);
            }
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, ClassicHttpRequest request, HttpContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }

        @Override
        public void close(CloseMode closeMode) {
        }
    }
}
