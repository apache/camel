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
package org.apache.camel.component.google.calendar.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.calendar.GoogleCalendarClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleCalendarStreamClientPerEndpointOptionsTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=";
    private static final String SERVICE_ACCOUNT = "?serviceAccountKey=classpath:service-account.json&delegate=";

    @Test
    void shouldCreateOneClientPerEndpoint() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-calendar-stream", GoogleCalendarStreamComponent.class).setClientFactory(factory);
            context.start();

            GoogleCalendarStreamEndpoint first = context.getEndpoint(
                    "google-calendar-stream://cal-a" + OAUTH + "token-a", GoogleCalendarStreamEndpoint.class);
            GoogleCalendarStreamEndpoint second = context.getEndpoint(
                    "google-calendar-stream://cal-b" + OAUTH + "token-b", GoogleCalendarStreamEndpoint.class);
            GoogleCalendarStreamEndpoint sameAsFirst = context.getEndpoint(
                    "google-calendar-stream://cal-c" + OAUTH + "token-a", GoogleCalendarStreamEndpoint.class);

            Calendar firstClient = first.getClient();
            Calendar secondClient = second.getClient();

            assertNotSame(firstClient, secondClient);
            assertNotSame(firstClient, sameAsFirst.getClient());
            assertSame(firstClient, first.getClient());
            assertEquals(Arrays.asList("token-a", "token-b", "token-a"), factory.created);
        }
    }

    @Test
    void shouldKeepOneClientPerDelegate() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-calendar-stream", GoogleCalendarStreamComponent.class).setClientFactory(factory);
            context.start();

            GoogleCalendarStreamEndpoint first
                    = context.getEndpoint("google-calendar-stream://cal-a" + SERVICE_ACCOUNT + "user-a",
                            GoogleCalendarStreamEndpoint.class);
            GoogleCalendarStreamEndpoint second
                    = context.getEndpoint("google-calendar-stream://cal-b" + SERVICE_ACCOUNT + "user-b",
                            GoogleCalendarStreamEndpoint.class);

            assertNotSame(first.getClient(), second.getClient());
            assertEquals(Arrays.asList("user-a", "user-b"), factory.created);
        }
    }

    private static final class RecordingClientFactory implements GoogleCalendarClientFactory {

        private final List<String> created = new ArrayList<>();

        @Override
        public Calendar makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName,
                String refreshToken, String accessToken, String emailAddress, String p12FileName, String user) {
            created.add(refreshToken);
            return newClient();
        }

        @Override
        public Calendar makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            created.add(delegate);
            return newClient();
        }

        private static Calendar newClient() {
            return new Calendar.Builder(new NetHttpTransport(), new JacksonFactory(), null).setApplicationName("test")
                    .build();
        }
    }
}
