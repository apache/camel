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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InlineQueryResultCachedVoice} builder and JSON serialization.
 */
class InlineQueryResultCachedVoiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testBuilderAssignsVoiceFileId() {
        InlineQueryResultCachedVoice result = InlineQueryResultCachedVoice.builder()
                .id("1")
                .voiceFileId("voice-file-123")
                .title("Test Voice")
                .build();

        assertThat(result.getId()).isEqualTo("1");
        assertThat(result.getVoiceFileId()).isEqualTo("voice-file-123");
        assertThat(result.getTitle()).isEqualTo("Test Voice");
    }

    @Test
    void testBuilderVoiceFileIdDoesNotOverwriteId() {
        InlineQueryResultCachedVoice result = InlineQueryResultCachedVoice.builder()
                .id("query-result-1")
                .voiceFileId("voice-file-456")
                .build();

        assertThat(result.getId()).isEqualTo("query-result-1");
        assertThat(result.getVoiceFileId()).isEqualTo("voice-file-456");
    }

    @Test
    void testSerializesToJsonWithVoiceFileId() throws JsonProcessingException {
        InlineQueryResultCachedVoice result = InlineQueryResultCachedVoice.builder()
                .id("1")
                .voiceFileId("voice-file-789")
                .title("My Voice")
                .caption("A voice message")
                .build();

        String json = objectMapper.writeValueAsString(result);

        assertThat(json)
                .contains("\"voice_file_id\":\"voice-file-789\"")
                .contains("\"id\":\"1\"")
                .contains("\"title\":\"My Voice\"")
                .contains("\"caption\":\"A voice message\"")
                .contains("\"type\":\"voice\"");
    }

    @Test
    void testDeserializesFromJson() throws JsonProcessingException {
        String json = "{\"type\":\"voice\",\"id\":\"1\",\"voice_file_id\":\"voice-file-abc\",\"title\":\"Voice\"}";

        InlineQueryResultCachedVoice result = objectMapper.readValue(json, InlineQueryResultCachedVoice.class);

        assertThat(result.getVoiceFileId()).isEqualTo("voice-file-abc");
        assertThat(result.getId()).isEqualTo("1");
        assertThat(result.getTitle()).isEqualTo("Voice");
    }
}
