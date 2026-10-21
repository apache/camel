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
package org.apache.camel.component.google.sheets.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.sheets.GoogleSheetsClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleSheetsStreamClientPerEndpointOptionsTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=";
    private static final String SERVICE_ACCOUNT = "?serviceAccountKey=classpath:service-account.json&delegate=";

    @Test
    void shouldKeepOneClientPerDistinctOptions() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-sheets-stream", GoogleSheetsStreamComponent.class).setClientFactory(factory);
            context.start();

            GoogleSheetsStreamEndpoint first = context.getEndpoint("google-sheets-stream://sheet-a" + OAUTH + "token-a",
                    GoogleSheetsStreamEndpoint.class);
            GoogleSheetsStreamEndpoint second = context.getEndpoint("google-sheets-stream://sheet-b" + OAUTH + "token-b",
                    GoogleSheetsStreamEndpoint.class);
            GoogleSheetsStreamEndpoint sameAsFirst = context.getEndpoint(
                    "google-sheets-stream://sheet-c" + OAUTH + "token-a", GoogleSheetsStreamEndpoint.class);

            Sheets firstClient = first.getClient();
            Sheets secondClient = second.getClient();

            assertSame(firstClient, sameAsFirst.getClient());
            assertNotSame(firstClient, secondClient);
            assertEquals(Arrays.asList("token-a", "token-b"), factory.created);
        }
    }

    @Test
    void shouldKeepOneClientPerDelegate() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-sheets-stream", GoogleSheetsStreamComponent.class).setClientFactory(factory);
            context.start();

            GoogleSheetsStreamEndpoint first
                    = context.getEndpoint("google-sheets-stream://sheet-a" + SERVICE_ACCOUNT + "user-a",
                            GoogleSheetsStreamEndpoint.class);
            GoogleSheetsStreamEndpoint second
                    = context.getEndpoint("google-sheets-stream://sheet-b" + SERVICE_ACCOUNT + "user-b",
                            GoogleSheetsStreamEndpoint.class);

            assertNotSame(first.getClient(), second.getClient());
            assertEquals(Arrays.asList("user-a", "user-b"), factory.created);
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
