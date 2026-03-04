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
package org.apache.camel.test.infra.openai.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenAIMockFailuresTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock();

    @Test
    public void testBadRequest() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"assistant\", \"content\": \"any sentence\"}]}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(500, response.statusCode());
    }

    @Test
    public void testNotFound() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"messages\": [{\"role\": \"user\", \"content\": \"not found sentence\"}]}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(500, response.statusCode());
    }

    @Test
    public void testBuilderExceptions() {
        OpenAIMockBuilder builder = new OpenAIMock().builder();
        assertThrows(IllegalStateException.class, () -> builder.replyWith("test"));
        assertThrows(IllegalStateException.class, () -> builder.invokeTool("test"));
        assertThrows(IllegalStateException.class, () -> builder.withParam("key", "value"));
        assertThrows(IllegalStateException.class, () -> builder.thenRespondWith((req, in) -> null));
        assertThrows(IllegalStateException.class, () -> builder.end());

        builder.when("sentence");
        assertThrows(IllegalStateException.class, () -> builder.withParam("key", "value"));
    }
}
