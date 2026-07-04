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
package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmClientVertexTest {

    // ---- resolveVertexModel tests ----

    @Test
    void mapsPreFourSixModels() {
        assertEquals("claude-sonnet-4-5@20250929", LlmClient.resolveVertexModel("claude-sonnet-4-5"));
        assertEquals("claude-haiku-4-5@20251001", LlmClient.resolveVertexModel("claude-haiku-4-5"));
        assertEquals("claude-opus-4-5@20251101", LlmClient.resolveVertexModel("claude-opus-4-5"));
    }

    @Test
    void passesThroughDatelessModels() {
        assertEquals("claude-sonnet-4-6", LlmClient.resolveVertexModel("claude-sonnet-4-6"));
        assertEquals("claude-opus-4-6", LlmClient.resolveVertexModel("claude-opus-4-6"));
        assertEquals("claude-opus-4-7", LlmClient.resolveVertexModel("claude-opus-4-7"));
    }

    @Test
    void passesThroughAlreadyVersionedModels() {
        assertEquals("claude-haiku-4-5@20251001", LlmClient.resolveVertexModel("claude-haiku-4-5@20251001"));
        assertEquals("claude-sonnet-4-5@20250929", LlmClient.resolveVertexModel("claude-sonnet-4-5@20250929"));
    }

    @Test
    void handlesNullModel() {
        assertNull(LlmClient.resolveVertexModel(null));
    }

    // ---- extractErrorMessage tests ----

    @Test
    void extractsJsonErrorMessage() {
        String json = "{\"error\":{\"message\":\"Model not found\",\"type\":\"not_found_error\"}}";
        assertEquals("Model not found", LlmClient.extractErrorMessage(json));
    }

    @Test
    void extractsJsonStringError() {
        String json = "{\"error\":\"Something went wrong\"}";
        assertEquals("Something went wrong", LlmClient.extractErrorMessage(json));
    }

    @Test
    void returnsNullForHtmlResponse() {
        String html = "<!DOCTYPE html><html><body><p>404 Not Found</p></body></html>";
        assertNull(LlmClient.extractErrorMessage(html));
    }

    @Test
    void returnsNullForHtmlWithWhitespace() {
        String html = "\n<!DOCTYPE html>\n<html lang=en>\n<p>404.</p>\n</html>\n";
        assertNull(LlmClient.extractErrorMessage(html));
    }

    @Test
    void returnsJsonBodyWhenNoErrorField() {
        String json = "{\"status\":\"error\",\"code\":404}";
        assertEquals(json, LlmClient.extractErrorMessage(json));
    }
}
