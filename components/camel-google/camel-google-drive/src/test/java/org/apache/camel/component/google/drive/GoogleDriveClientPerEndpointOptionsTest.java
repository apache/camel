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
package org.apache.camel.component.google.drive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleDriveClientPerEndpointOptionsTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=";

    @Test
    void shouldCreateOneClientPerEndpoint() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-drive", GoogleDriveComponent.class).setClientFactory(factory);
            context.start();

            GoogleDriveEndpoint first
                    = context.getEndpoint("google-drive://drive-files/list" + OAUTH + "token-a", GoogleDriveEndpoint.class);
            GoogleDriveEndpoint second
                    = context.getEndpoint("google-drive://drive-files/list" + OAUTH + "token-b", GoogleDriveEndpoint.class);
            GoogleDriveEndpoint sameAsFirst
                    = context.getEndpoint("google-drive://drive-files/get" + OAUTH + "token-a", GoogleDriveEndpoint.class);

            assertEquals(Arrays.asList("token-a", "token-b", "token-a"), factory.created);
            assertNotSame(first.getClient(), second.getClient());
            assertNotSame(first.getClient(), sameAsFirst.getClient());
            assertSame(first.getClient(), first.getClient());
            assertEquals(3, factory.created.size());
        }
    }

    @Test
    void shouldUseTheClientFactoryOfTheEndpoint() throws Exception {
        RecordingClientFactory componentFactory = new RecordingClientFactory();
        RecordingClientFactory endpointFactory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("endpointFactory", endpointFactory);
            context.getComponent("google-drive", GoogleDriveComponent.class).setClientFactory(componentFactory);
            context.start();

            GoogleDriveEndpoint withOwnFactory = context.getEndpoint(
                    "google-drive://drive-files/list" + OAUTH + "token-a&clientFactory=#endpointFactory",
                    GoogleDriveEndpoint.class);
            GoogleDriveEndpoint withComponentFactory
                    = context.getEndpoint("google-drive://drive-files/get" + OAUTH + "token-a", GoogleDriveEndpoint.class);

            assertEquals(List.of("token-a"), endpointFactory.created);
            assertEquals(List.of("token-a"), componentFactory.created);
            assertNotSame(withOwnFactory.getClient(), withComponentFactory.getClient());
        }
    }

    private static final class RecordingClientFactory implements GoogleDriveClientFactory {

        private final List<String> created = new ArrayList<>();

        @Override
        public Drive makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName,
                String refreshToken, String accessToken) {
            created.add(refreshToken);
            return newClient();
        }

        @Override
        public Drive makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            created.add(delegate);
            return newClient();
        }

        private static Drive newClient() {
            return new Drive.Builder(new NetHttpTransport(), new JacksonFactory(), null).setApplicationName("test")
                    .build();
        }
    }
}
