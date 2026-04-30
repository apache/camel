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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;

public class MockGoogleMailClientFactory implements GoogleMailClientFactory {

    public static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final HttpTransport transport;
    private final List<String> requestUrls = new ArrayList<>();

    public MockGoogleMailClientFactory(String jsonContent) {
        transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                requestUrls.add(url);
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() {
                        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                        response.setContentType("application/json");
                        response.setContent(jsonContent);
                        return response;
                    }
                };
            }
        };
    }

    public List<String> getRequestUrls() {
        return requestUrls;
    }

    @Override
    public Gmail makeClient(
            String clientId, String clientSecret, Collection<String> scopes, String applicationName,
            String refreshToken, String accessToken) {
        return makeClient();
    }

    @Override
    public Gmail makeClient(
            CamelContext camelContext, String serviceAccountKey, Collection<String> scopes,
            String applicationName, String delegate) {
        return makeClient();
    }

    private Gmail makeClient() {
        Credential credential = new MockGoogleCredential.Builder().build();
        return new Gmail.Builder(transport, JSON_FACTORY, credential).setApplicationName("mock").build();
    }
}
