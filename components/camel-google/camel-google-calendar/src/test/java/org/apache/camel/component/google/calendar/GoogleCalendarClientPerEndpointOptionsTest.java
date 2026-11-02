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
package org.apache.camel.component.google.calendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleCalendarClientPerEndpointOptionsTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=";
    private static final String SERVICE_ACCOUNT = "?serviceAccountKey=classpath:service-account.json&delegate=";

    @Test
    void shouldCreateOneClientPerEndpoint() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-calendar", GoogleCalendarComponent.class).setClientFactory(factory);
            context.start();

            GoogleCalendarEndpoint first = context.getEndpoint("google-calendar://calendars/get" + OAUTH + "token-a",
                    GoogleCalendarEndpoint.class);
            GoogleCalendarEndpoint second = context.getEndpoint("google-calendar://calendars/get" + OAUTH + "token-b",
                    GoogleCalendarEndpoint.class);
            GoogleCalendarEndpoint sameAsFirst = context.getEndpoint(
                    "google-calendar://calendars/insert" + OAUTH + "token-a", GoogleCalendarEndpoint.class);

            assertEquals(Arrays.asList("token-a", "token-b", "token-a"), factory.created);
            assertNotSame(first.getClient(), second.getClient());
            assertNotSame(first.getClient(), sameAsFirst.getClient());
            assertSame(first.getClient(), first.getClient());
            assertEquals(3, factory.created.size());
        }
    }

    @Test
    void shouldKeepOneClientPerDelegate() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-calendar", GoogleCalendarComponent.class).setClientFactory(factory);
            context.start();

            GoogleCalendarEndpoint first = context.getEndpoint(
                    "google-calendar://calendars/get" + SERVICE_ACCOUNT + "user-a", GoogleCalendarEndpoint.class);
            GoogleCalendarEndpoint second = context.getEndpoint(
                    "google-calendar://calendars/get" + SERVICE_ACCOUNT + "user-b", GoogleCalendarEndpoint.class);

            assertEquals(Arrays.asList("user-a", "user-b"), factory.created);
            assertNotSame(first.getClient(), second.getClient());
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
