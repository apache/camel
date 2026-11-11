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
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.Json;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void shouldSendTheResultAsOneMessageAlthoughSplitResultIsOn() throws Exception {
        // no method returns a list: the ValueRange of data/get is one message with all the rows
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse().setContentType(Json.MEDIA_TYPE)
                .setContent("{\"range\":\"Sheet1!A1:A3\",\"values\":[[\"a\"],[\"b\"],[\"c\"]]}");
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-sheets", GoogleSheetsComponent.class)
                    .setClientFactory(new MockGoogleSheetsClientFactory(response));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("google-sheets://data/get?spreadsheetId=abc&range=Sheet1!A1:A3&repeatCount=1"
                         + "&clientId=id&clientSecret=secret&refreshToken=token")
                            .to("mock:result");
                }
            });
            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            // a split result would send the other rows right after the first one
            mock.setAssertPeriod(500);

            context.start();

            mock.assertIsSatisfied();

            ValueRange body = mock.getReceivedExchanges().get(0).getIn().getBody(ValueRange.class);
            assertEquals(List.of(List.of("a"), List.of("b"), List.of("c")), body.getValues());
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
