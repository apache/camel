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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.Json;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * drive-files/export, and drive-files/get and drive-revisions/get with alt=media, give the content of the file as an
 * InputStream: their execute() gives null (export) or fails to read the content as JSON (get).
 */
class GoogleDriveMediaContentTest {

    private static final String OAUTH = "clientId=id&clientSecret=secret&refreshToken=token";
    private static final String EXPORT
            = "google-drive://drive-files/export?fileId=f1&mimeType=application/pdf&" + OAUTH;
    // not text: the content must come through byte for byte
    private static final byte[] CONTENT = { (byte) 0xff, (byte) 0xfe, 0x00, 'P', 'D', 'F' };

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicBoolean contentClosed = new AtomicBoolean();

    @Test
    void shouldGiveTheContentOfDriveFilesExport() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();

            Object body = context.createProducerTemplate().requestBody(EXPORT, (Object) null);

            assertArrayEquals(CONTENT, readAndClose(body));
        }
        assertTrue(requests.stream().anyMatch(r -> r.contains("/files/f1/export?") && r.contains("alt=media")),
                "The content should be downloaded: " + requests);
    }

    @Test
    void shouldGiveTheContentOfDriveFilesGetWithAltMedia() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();

            Object body = context.createProducerTemplate().requestBodyAndHeader(
                    "google-drive://drive-files/get?fileId=f1&" + OAUTH, null, "CamelGoogleDrive.alt", "media");

            assertArrayEquals(CONTENT, readAndClose(body));
        }
        assertTrue(requests.stream().anyMatch(r -> r.contains("/files/f1?") && r.contains("alt=media")),
                "The content should be downloaded: " + requests);
    }

    @Test
    void shouldGiveTheContentOfDriveRevisionsGetWithAltMedia() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();

            Object body = context.createProducerTemplate().requestBodyAndHeader(
                    "google-drive://drive-revisions/get?fileId=f1&revisionId=r1&" + OAUTH, null, "CamelGoogleDrive.alt",
                    "media");

            assertArrayEquals(CONTENT, readAndClose(body));
        }
        assertTrue(requests.stream().anyMatch(r -> r.contains("/files/f1/revisions/r1?") && r.contains("alt=media")),
                "The content should be downloaded: " + requests);
    }

    @Test
    void shouldGiveTheMetadataOfDriveFilesGetWithoutAltMedia() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();

            Object body = context.createProducerTemplate().requestBody("google-drive://drive-files/get?fileId=f1&" + OAUTH,
                    (Object) null);

            assertEquals("hello.txt", assertInstanceOf(File.class, body).getName());
        }
    }

    @Test
    void shouldLeaveTheContentOpenForTheCallerOfARoute() throws Exception {
        try (CamelContext context = createContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:export").to(EXPORT);
                }
            });
            context.start();

            // the caller reads the body after the exchange is done
            byte[] body = context.createProducerTemplate().requestBody("direct:export", null, byte[].class);

            assertArrayEquals(CONTENT, body);
        }
    }

    @Test
    void shouldConsumeTheContentOfDriveFilesExport() throws Exception {
        try (CamelContext context = createContext()) {
            context.addRoutes(consumeExport());
            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            context.start();

            mock.assertIsSatisfied();

            assertArrayEquals(CONTENT, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
        }
    }

    @Test
    void shouldCloseTheConsumedContentWhenTheExchangeIsDone() throws Exception {
        try (CamelContext context = createContext()) {
            // without stream caching nothing in the route reads the content
            context.setStreamCaching(false);
            context.addRoutes(consumeExport());
            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.expectedMessageCount(1);
            NotifyBuilder done = new NotifyBuilder(context).whenDone(1).create();
            context.start();

            mock.assertIsSatisfied();
            assertInstanceOf(InputStream.class, mock.getReceivedExchanges().get(0).getIn().getBody());
            assertTrue(done.matches(10, TimeUnit.SECONDS), "The exchange should be done");

            assertTrue(contentClosed.get(), "The content should be closed when the exchange is done");
        }
    }

    private static RouteBuilder consumeExport() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("google-drive://drive-files/export?fileId=f1&mimeType=application/pdf&repeatCount=1&" + OAUTH)
                        .to("mock:result");
            }
        };
    }

    private static byte[] readAndClose(Object body) throws Exception {
        try (InputStream content = assertInstanceOf(InputStream.class, body)) {
            return content.readAllBytes();
        }
    }

    private CamelContext createContext() {
        CamelContext context = new DefaultCamelContext();
        context.getComponent("google-drive", GoogleDriveComponent.class).setClientFactory(new MockClientFactory());
        return context;
    }

    private final class MockClientFactory implements GoogleDriveClientFactory {

        @Override
        public Drive makeClient(
                String clientId, String clientSecret, Collection<String> scopes, String applicationName,
                String refreshToken, String accessToken) {
            return newClient();
        }

        @Override
        public Drive makeClient(
                CamelContext camelContext, String serviceAccountKey, Collection<String> scopes, String applicationName,
                String delegate) {
            return newClient();
        }

        private Drive newClient() {
            MockHttpTransport transport = new MockHttpTransport() {
                @Override
                public LowLevelHttpRequest buildRequest(String method, String url) {
                    requests.add(method + " " + url);
                    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                    if (url.contains("alt=media")) {
                        // the content of the file
                        response.setContentType("application/pdf").setContent(new ByteArrayInputStream(CONTENT) {
                            @Override
                            public void close() {
                                contentClosed.set(true);
                            }
                        });
                    } else {
                        // the metadata of the file
                        response.setContentType(Json.MEDIA_TYPE).setContent("{\"id\":\"f1\",\"name\":\"hello.txt\"}");
                    }
                    return new MockLowLevelHttpRequest(url).setResponse(response);
                }
            };
            return new Drive.Builder(transport, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build();
        }
    }
}
