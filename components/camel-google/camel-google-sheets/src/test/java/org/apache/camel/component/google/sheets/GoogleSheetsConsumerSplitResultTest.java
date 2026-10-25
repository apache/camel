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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleSheetsConsumerSplitResultTest {

    private static final String URI
            = "google-sheets://spreadsheets/get?spreadsheetId=abc&clientId=id&clientSecret=secret&refreshToken=token";

    @Test
    void shouldSplitResultByDefault() throws Exception {
        try (CamelContext context = createContext()) {
            GoogleSheetsConsumer consumer = createConsumer(context, URI);
            assertTrue(consumer.isSplitResult());
        }
    }

    @Test
    void shouldNotSplitResultWhenTurnedOff() throws Exception {
        try (CamelContext context = createContext()) {
            GoogleSheetsConsumer consumer = createConsumer(context, URI + "&splitResult=false");
            assertFalse(consumer.isSplitResult());
        }
    }

    private static CamelContext createContext() {
        CamelContext context = new DefaultCamelContext();
        context.getComponent("google-sheets", GoogleSheetsComponent.class).setClientFactory(new TestClientFactory());
        context.start();
        return context;
    }

    private static GoogleSheetsConsumer createConsumer(CamelContext context, String uri) throws Exception {
        GoogleSheetsEndpoint endpoint = context.getEndpoint(uri, GoogleSheetsEndpoint.class);
        return (GoogleSheetsConsumer) endpoint.createConsumer(exchange -> {
        });
    }

    private static final class TestClientFactory implements GoogleSheetsClientFactory {

        @Override
        public Sheets makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName,
                String refreshToken, String accessToken) {
            return newClient();
        }

        @Override
        public Sheets makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            return newClient();
        }

        private static Sheets newClient() {
            return new Sheets.Builder(new NetHttpTransport(), new JacksonFactory(), null).setApplicationName("test")
                    .build();
        }
    }
}
