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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenAIMockRecordedRequestsTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello")
            .replyWith("hi")
            .end()
            .build();

    @Test
    public void testRequestIsRecordedAndStillHandled() throws Exception {
        try (CloseableHttpClient hc = new CloseableHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openAIMock.getBaseUrl() + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers
                            .ofString("{\"model\": \"gpt-5\", \"messages\": [{\"role\": \"user\", \"content\": \"hello\"}]}"))
                    .build();

            HttpResponse<String> response = hc.send(request, HttpResponse.BodyHandlers.ofString());

            String content = new ObjectMapper().readTree(response.body())
                    .path("choices").get(0).path("message").path("content").asText();
            assertEquals("hi", content);

            assertEquals(1, openAIMock.getReceivedRequests().size());
            RecordedRequest recorded = openAIMock.getLastRequest();
            assertEquals("POST", recorded.method());
            assertEquals("/v1/chat/completions", recorded.path());
            assertEquals("gpt-5", recorded.bodyAsJson().path("model").asText());
        }
    }

    @Test
    public void testLastRequestFailsWhenNothingWasReceived() {
        assertThrows(IllegalStateException.class, () -> openAIMock.getLastRequest());
    }
}
