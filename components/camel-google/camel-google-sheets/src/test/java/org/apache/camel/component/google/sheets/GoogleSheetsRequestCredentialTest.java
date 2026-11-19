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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The client sends the OAuth access token in the Authorization header; the request URL does not carry it.
 */
class GoogleSheetsRequestCredentialTest {

    private static final String OAUTH = "clientId=id&clientSecret=secret&accessToken=atoken";

    private final RecordingTransport transport = new RecordingTransport();

    @Test
    void shouldSendTheAccessTokenOfTheProducerOnlyInTheAuthorizationHeader() throws Exception {
        try (CamelContext context = createContext()) {
            ValueRange body = context.createProducerTemplate().requestBody(
                    "google-sheets://data/get?spreadsheetId=abc&range=A1:B1&" + OAUTH, null, ValueRange.class);

            assertEquals(List.of(List.of("a", "b")), body.getValues());
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
                    from("google-sheets://data/get?spreadsheetId=abc&range=A1:B1&delay=100&" + OAUTH)
                            .to("mock:result");
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

    private CamelContext createContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getComponent("google-sheets", GoogleSheetsComponent.class)
                .setClientFactory(new BatchGoogleSheetsClientFactory(transport));
        context.start();
        return context;
    }

    private static final class RecordingTransport extends MockHttpTransport {

        private final List<MockLowLevelHttpRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public MockLowLevelHttpRequest buildRequest(String method, String url) {
            MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url);
            request.setResponse(new MockLowLevelHttpResponse().setContentType(Json.MEDIA_TYPE)
                    .setContent("{\"range\":\"Sheet1!A1:B1\",\"majorDimension\":\"ROWS\",\"values\":[[\"a\",\"b\"]]}"));
            requests.add(request);
            return request;
        }
    }
}
