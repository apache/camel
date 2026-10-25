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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.Json;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.drive.Drive;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file of the file consumer as the content of drive-files/create is uploaded with its content, not only as metadata:
 * the type converter sets the media content header while the producer converts the body.
 */
class GoogleDriveFilesCreateFromFileTest {

    private static final String OAUTH = "?clientId=id&clientSecret=secret&refreshToken=token";

    @TempDir
    Path dir;

    private final List<String> requests = new CopyOnWriteArrayList<>();

    @Test
    void shouldUploadTheContentOfAFileBody() throws Exception {
        Path path = Files.writeString(dir.resolve("hello.txt"), "Hello");
        GenericFile<File> body = new GenericFile<>();
        body.setFile(path.toFile());
        body.setFileName("hello.txt");

        try (CamelContext context = new DefaultCamelContext()) {
            context.getComponent("google-drive", GoogleDriveComponent.class).setClientFactory(new MockClientFactory());
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            template.sendBody("google-drive://drive-files/create" + OAUTH + "&inBody=content", body);
        }

        assertTrue(requests.stream().anyMatch(r -> r.startsWith("POST https://www.googleapis.com/upload/drive/v3/files")),
                "The content should be uploaded: " + requests);
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
                    MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url);
                    // the location of the upload session for a resumable upload, and the created file
                    request.setResponse(new MockLowLevelHttpResponse()
                            .addHeader("Location", "https://www.googleapis.com/upload/drive/v3/files?upload_id=1")
                            .setContentType(Json.MEDIA_TYPE)
                            .setContent("{\"id\":\"1\",\"name\":\"hello.txt\"}"));
                    return request;
                }
            };
            return new Drive.Builder(transport, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build();
        }
    }
}
