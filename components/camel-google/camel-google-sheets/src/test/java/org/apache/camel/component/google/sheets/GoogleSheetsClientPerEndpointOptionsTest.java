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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleSheetsClientPerEndpointOptionsTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=";

    @Test
    void shouldKeepOneClientPerDistinctOptions() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-sheets", GoogleSheetsComponent.class).setClientFactory(factory);
            context.start();

            GoogleSheetsEndpoint first = context.getEndpoint("google-sheets://spreadsheets/get" + OAUTH + "token-a",
                    GoogleSheetsEndpoint.class);
            GoogleSheetsEndpoint second = context.getEndpoint("google-sheets://spreadsheets/get" + OAUTH + "token-b",
                    GoogleSheetsEndpoint.class);
            GoogleSheetsEndpoint sameAsFirst = context.getEndpoint(
                    "google-sheets://spreadsheets/create" + OAUTH + "token-a", GoogleSheetsEndpoint.class);

            assertEquals(Arrays.asList("token-a", "token-b"), factory.created);
            assertSame(first.getClient(), sameAsFirst.getClient());
            assertNotSame(first.getClient(), second.getClient());
        }
    }

    @Test
    void shouldNotShareTheDefaultCredentialsClient() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-sheets", GoogleSheetsComponent.class).setClientFactory(factory);
            context.start();

            GoogleSheetsEndpoint defaultCredentials
                    = context.getEndpoint("google-sheets://spreadsheets/get", GoogleSheetsEndpoint.class);
            GoogleSheetsEndpoint explicitCredentials = context.getEndpoint(
                    "google-sheets://spreadsheets/get" + OAUTH + "token-b", GoogleSheetsEndpoint.class);

            assertEquals(Arrays.asList(null, "token-b"), factory.created);
            assertNotSame(defaultCredentials.getClient(), explicitCredentials.getClient());
        }
    }

    private static final class RecordingClientFactory implements GoogleSheetsClientFactory {

        private final List<String> created = new ArrayList<>();

        @Override
        public Sheets makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName,
                String refreshToken, String accessToken) {
            created.add(refreshToken);
            return newClient();
        }

        @Override
        public Sheets makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            created.add(delegate);
            return newClient();
        }

        private static Sheets newClient() {
            return new Sheets.Builder(new NetHttpTransport(), new JacksonFactory(), null).setApplicationName("test")
                    .build();
        }
    }
}
