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
package org.apache.camel.component.typesafeai;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiInputTest extends TypeSafeAiTestSupport {
    static Stream<Object> supportedInputs() {
        return Stream.of("data:image/png;base64,AA==", Map.of("type", "document", "url", "https://cms/42", "title", "Policy"),
                Map.of("type", "document", "source", "legal", "title", "Policy"),
                Map.of("type", "file", "url", "https://cms/42", "data", "Summary"),
                List.of(Map.of("type", "document", "data", Map.of("title", "Policy"), "source", "legal")),
                Map.of("mimeType", "application/javascript", "data", "const image = 'text';"),
                Map.of("media_type", "application/vnd.example+json", "data", Map.of("text", "hello")),
                Map.of("file", "report.pdf", "bytes", List.of(1, 2)),
                "Describe image/png and data:image/png;base64, as ordinary text",
                Map.of("image", "photo", "audio", "music", "video", "movie", "type", "image"),
                List.of(Map.of("type", "text", "text", "hello"), Map.of("amount", 42, "approved", true)),
                Map.of("items", List.of("text", 42, false), "mimeType", "image/png"));
    }

    @ParameterizedTest
    @MethodSource("supportedInputs")
    void preservesSupportedState(Object state) throws Exception {
        JsonObject result = template.requestBody("typesafe-ai:input", request(state), JsonObject.class);
        assertThat(result.getJsonObject("answers")).containsKey("predicate");
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo(Jsoner.deserialize(Jsoner.serialize(state)));
    }

    static Stream<Object> unsupportedInputs() {
        return Stream.of(new byte[] { 1 }, ByteBuffer.wrap(new byte[] { 1 }), new Object(),
                Map.of("$semanticInput", List.of(Map.of("type", "text", "text", "private"))),
                Map.of("nested", Map.of("$semanticInput", List.of(Map.of("type", "text", "text", "private")))),
                Map.of("$semanticInput", List.of(Map.of("type", "image", "data", "data:image/png;base64,private"))),
                Map.of("type", "file", "file", Map.of("file_data", "private")),
                Map.of("type", "document", "source", Map.of("type", "url", "url", "https://example.test/private")),
                Map.of("type", "document", "source", Map.of("type", "base64", "data", "private")),
                Map.of("type", "binary", "bytes", List.of(1, 2)),
                Map.of("mimeType", "application/pdf", "data", "private"),
                Map.of("media_type", "Application/Pdf; version=1.7", "data", "private"),
                Map.of("mime_type", "application/zip", "bytes", List.of(1, 2)),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.test/private")),
                Map.of("type", "input_image", "image_url", "https://example.test/private"),
                Map.of("type", "input_audio", "input_audio", Map.of("data", "private", "format", "wav")),
                Map.of("type", "video", "source", Map.of("url", "https://example.test/private")),
                Map.of("mimeType", "image/png", "data", "private"),
                Map.of("ticket", List.of(Map.of("type", "image", "source",
                        Map.of("type", "base64", "media_type", "image/png", "data", "private")))),
                List.of("valid text", Map.of("nested", new byte[] { 1 })));
    }

    @ParameterizedTest
    @MethodSource("unsupportedInputs")
    void rejectsUnsupportedStateBeforeHttp(Object state) {
        Exchange exchange = template.request("typesafe-ai:input", e -> e.getMessage().setBody(request(state)));
        assertThat(exchange.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("private");
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectsMediaInOneBatchQuestionBeforeHttp() {
        Object media = Map.of("type", "image_url", "image_url", Map.of("url", "https://example.test/private"));
        Exchange exchange = template.request("typesafe-ai:batch", e -> e.getMessage().setBody(Map.of(
                "state", "text", "questions", Map.of("valid", noulQuestion("text"), "invalid", noulQuestion(media)))));
        assertThat(exchange.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void componentRunsWithoutSemanticOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("org.apache.camel.semantic.SemanticAdapter"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.apache.camel.component.typesafeai.TypeSafeAiSemanticAdapter"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(template.requestBody("typesafe-ai:standalone", request("text"), JsonObject.class)
                .getJsonObject("answers")).containsKey("predicate");
    }
}
