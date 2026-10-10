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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.mail.GoogleMailClientFactory;

/**
 * A Gmail client whose transport answers the calls of the stream consumer from an in-memory mailbox: listing the
 * labels, listing the messages with the UNREAD label (the default query {@code is:unread}), getting a message, and
 * removing or adding the UNREAD label. A get can be made to fail once. Any other request fails with status 500 and is
 * recorded.
 */
class FakeGmailClientFactory implements GoogleMailClientFactory {

    private static final String MESSAGES = "/gmail/v1/users/me/messages";

    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Set<String> unread = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> gets = new ConcurrentHashMap<>();
    private final List<String> unexpected = new CopyOnWriteArrayList<>();
    private final Set<String> failNextGet = ConcurrentHashMap.newKeySet();
    private final CountDownLatch markedAsRead = new CountDownLatch(1);

    void addUnread(String id, String body) {
        bodies.put(id, body);
        unread.add(id);
    }

    /**
     * Makes the next get of the message fail with status 500.
     */
    void failNextGet(String id) {
        failNextGet.add(id);
    }

    boolean isUnread(String id) {
        return unread.contains(id);
    }

    /**
     * Waits until a message has been marked as read (the UNREAD label removed).
     */
    boolean awaitMarkedAsRead(long timeout, TimeUnit unit) throws InterruptedException {
        return markedAsRead.await(timeout, unit);
    }

    int gets(String id) {
        AtomicInteger count = gets.get(id);
        return count == null ? 0 : count.get();
    }

    List<String> unexpectedRequests() {
        return unexpected;
    }

    @Override
    public Gmail makeClient(
            String clientId, String clientSecret, Collection<String> scopes, String applicationName,
            String refreshToken, String accessToken) {
        return makeClient();
    }

    @Override
    public Gmail makeClient(
            CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
            String delegate) {
        return makeClient();
    }

    private Gmail makeClient() {
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() throws IOException {
                        return answer(method, URI.create(url).getPath(), getContentAsString());
                    }
                };
            }
        };
        return new Gmail.Builder(transport, GsonFactory.getDefaultInstance(), new MockGoogleCredential.Builder().build())
                .setApplicationName("fake").build();
    }

    private LowLevelHttpResponse answer(String method, String path, String content) {
        if ("GET".equals(method) && path.endsWith("/users/me/labels")) {
            return json("{\"labels\": [{\"id\": \"UNREAD\", \"name\": \"UNREAD\"}]}");
        }
        if ("GET".equals(method) && path.endsWith("/users/me/labels/UNREAD")) {
            return json("{\"id\": \"UNREAD\", \"name\": \"UNREAD\"}");
        }
        if ("GET".equals(method) && path.endsWith(MESSAGES)) {
            StringBuilder messages = new StringBuilder();
            for (String id : unread) {
                messages.append(messages.isEmpty() ? "" : ", ").append("{\"id\": \"").append(id).append("\"}");
            }
            return json("{\"messages\": [" + messages + "]}");
        }
        int idx = path.indexOf(MESSAGES + "/");
        if (idx >= 0) {
            String rest = path.substring(idx + MESSAGES.length() + 1);
            if ("GET".equals(method) && failNextGet.remove(rest)) {
                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                response.setStatusCode(500);
                return response;
            }
            if ("GET".equals(method) && bodies.containsKey(rest)) {
                gets.computeIfAbsent(rest, k -> new AtomicInteger()).incrementAndGet();
                String data = Base64.encodeBase64URLSafeString(bodies.get(rest).getBytes(StandardCharsets.UTF_8));
                return json("{\"id\": \"" + rest + "\", \"payload\": {\"mimeType\": \"text/plain\", \"body\": {\"data\": \""
                            + data + "\"}}}");
            }
            if ("POST".equals(method) && rest.endsWith("/modify")) {
                String id = rest.substring(0, rest.length() - "/modify".length());
                if (content.contains("removeLabelIds")) {
                    unread.remove(id);
                    markedAsRead.countDown();
                } else if (content.contains("addLabelIds")) {
                    unread.add(id);
                }
                return json("{\"id\": \"" + id + "\"}");
            }
        }
        unexpected.add(method + " " + path);
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        response.setStatusCode(500);
        return response;
    }

    private static LowLevelHttpResponse json(String content) {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
        response.setContentType("application/json");
        response.setContent(content);
        return response;
    }
}
