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
package org.apache.camel.test.infra.typesafeai.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeSafeAiServiceTest {
    @RegisterExtension
    TypeSafeAiService service = new TypeSafeAiService(null, null, null);

    @Test
    void returnsProtocolCompatibleAnswersForAllQuestionTypes() throws Exception {
        assertEquals("jev-latest", service.getModel());
        String request = Jsoner.serialize(Map.of("model", "fixture-model", "state", "Help with my invoice",
                "questions", Map.of(
                        "urgent", Map.of("type", "noul", "instructions", "Urgent?"),
                        "department", Map.of("type", "choice", "instructions", "Team?",
                                "criteria", Map.of("billing", "Invoices", "technical", "Bugs")),
                        "frustration", Map.of("type", "score", "instructions", "Frustration?",
                                "criteria", new String[] { "Calm", "Angry" }))));

        HttpResponse<String> response = post(request, service.getApiKey());
        assertEquals(200, response.statusCode());
        JsonObject body = (JsonObject) Jsoner.deserialize(response.body());
        assertEquals("fixture-model", body.get("model"));
        JsonObject answers = body.getJsonObject("answers");
        assertEquals(3, answers.size());
        assertEquals(0.9, answers.getJsonObject("urgent").getDouble("noul"));
        assertTrue(Map.of("billing", true, "technical", true)
                .containsKey(answers.getJsonObject("department").getString("choice")));
        assertEquals(0.0, answers.getJsonObject("frustration").getDouble("score"));
        assertEquals(1, service.getRequests().size());
    }

    @Test
    void rejectsWrongCredentialsAndDoesNotRecordRequest() throws Exception {
        assertEquals(401, post("{}", "wrong-key").statusCode());
        assertEquals(400, post("{", service.getApiKey()).statusCode());
        assertTrue(service.getRequests().isEmpty());
    }

    @Test
    void remoteConfigurationBypassesTheMock() throws Exception {
        TypeSafeAiService remote = new TypeSafeAiService("http://127.0.0.1:8000", "local-test", "laya-rl-agent");
        remote.beforeEach(null);
        assertTrue(remote.isRemote());
        assertEquals("http://127.0.0.1:8000", remote.getBaseUrl());
        assertEquals("local-test", remote.getApiKey());
        assertEquals("laya-rl-agent", remote.getModel());
        assertTrue(remote.getRequests().isEmpty());
        remote.afterEach(null);
        assertFalse(service.isRemote());
        assertThrows(IllegalArgumentException.class,
                () -> new TypeSafeAiService("http://127.0.0.1:8000", null, null));
    }

    private HttpResponse<String> post(String body, String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(service.getBaseUrl() + "/v1/systemone"))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
