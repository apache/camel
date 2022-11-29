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
package org.apache.camel.component.google.sheets;

import java.util.Collection;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;

public class MockGoogleSheetsClientFactory implements GoogleSheetsClientFactory {

    public static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private final HttpTransport transport;

    public MockGoogleSheetsClientFactory(MockLowLevelHttpResponse lowLevelHttpResponse) {
        transport = new MockHttpTransport.Builder().setLowLevelHttpResponse(lowLevelHttpResponse).build();
    }

    @Override
    public Sheets makeClient(
            CamelContext camelContext, String serviceAccountKey, Collection<String> scopes,
            String applicationName, String delegate) {
        return makeClient();
    }

    @Override
    public Sheets makeClient(
            String clientId, String clientSecret, Collection<String> scopes, String applicationName,
            String refreshToken, String accessToken) {
        return makeClient();
    }

    private Sheets makeClient() {
        Credential credential = new MockGoogleCredential.Builder().build();
        return new Sheets.Builder(transport, JSON_FACTORY, credential).setApplicationName("mock").build();
    }

}
