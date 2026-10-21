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
package org.apache.camel.component.google.mail.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.mail.GoogleMailClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleMailStreamClientPerEndpointOptionsTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=";

    @Test
    void shouldKeepOneClientPerDistinctOptions() throws Exception {
        RecordingClientFactory factory = new RecordingClientFactory();
        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-mail-stream", GoogleMailStreamComponent.class).setClientFactory(factory);
            context.start();

            GoogleMailStreamEndpoint first = context.getEndpoint("google-mail-stream://box-a" + OAUTH + "token-a",
                    GoogleMailStreamEndpoint.class);
            GoogleMailStreamEndpoint second = context.getEndpoint("google-mail-stream://box-b" + OAUTH + "token-b",
                    GoogleMailStreamEndpoint.class);
            GoogleMailStreamEndpoint sameAsFirst = context.getEndpoint("google-mail-stream://box-c" + OAUTH + "token-a",
                    GoogleMailStreamEndpoint.class);

            Gmail firstClient = first.getClient();
            Gmail secondClient = second.getClient();

            assertSame(firstClient, sameAsFirst.getClient());
            assertNotSame(firstClient, secondClient);
            assertEquals(Arrays.asList("token-a", "token-b"), factory.created);
        }
    }

    private static final class RecordingClientFactory implements GoogleMailClientFactory {

        private final List<String> created = new ArrayList<>();

        @Override
        public Gmail makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName,
                String refreshToken, String accessToken) {
            created.add(refreshToken);
            return newClient();
        }

        @Override
        public Gmail makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            created.add(delegate);
            return newClient();
        }

        private static Gmail newClient() {
            return new Gmail.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), null)
                    .setApplicationName("test").build();
        }
    }
}
