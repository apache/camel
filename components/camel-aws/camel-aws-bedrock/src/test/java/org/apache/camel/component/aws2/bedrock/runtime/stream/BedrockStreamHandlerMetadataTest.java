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
package org.apache.camel.component.aws2.bedrock.runtime.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Claude and Nova report the completion reason and token usage on the {@code message_delta} chunk, which is not the
 * final chunk ({@code message_stop}). Metadata extraction must therefore not be gated on the final chunk.
 */
class BedrockStreamHandlerMetadataTest {

    private static final String MESSAGE_DELTA
            = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":150}}";
    private static final String MESSAGE_STOP = "{\"type\":\"message_stop\"}";
    private static final String CONTENT_DELTA
            = "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}";

    @Test
    void claudeMetadataIsCapturedFromTheNonFinalMessageDeltaChunk() throws Exception {
        ClaudeStreamParser parser = new ClaudeStreamParser();
        BedrockStreamHandler.StreamMetadata metadata = new BedrockStreamHandler.StreamMetadata();

        // the chunk carrying the metadata is explicitly not the final chunk
        assertFalse(parser.isFinalChunk(MESSAGE_DELTA));

        BedrockStreamHandler.applyChunkMetadata(parser, MESSAGE_DELTA, metadata);

        assertEquals("end_turn", metadata.getCompletionReason());
        assertEquals(150, metadata.getTokenCount());
    }

    @Test
    void chunksWithoutMetadataLeaveEarlierValuesIntact() throws Exception {
        ClaudeStreamParser parser = new ClaudeStreamParser();
        BedrockStreamHandler.StreamMetadata metadata = new BedrockStreamHandler.StreamMetadata();

        // a realistic ordering: text chunk, then the metadata-bearing delta, then the final stop chunk
        BedrockStreamHandler.applyChunkMetadata(parser, CONTENT_DELTA, metadata);
        BedrockStreamHandler.applyChunkMetadata(parser, MESSAGE_DELTA, metadata);
        BedrockStreamHandler.applyChunkMetadata(parser, MESSAGE_STOP, metadata);

        assertEquals("end_turn", metadata.getCompletionReason());
        assertEquals(150, metadata.getTokenCount());
    }

    @Test
    void titanMetadataOnTheFinalChunkStillWorks() throws Exception {
        TitanStreamParser parser = new TitanStreamParser();
        BedrockStreamHandler.StreamMetadata metadata = new BedrockStreamHandler.StreamMetadata();
        String finalChunk = "{\"outputText\":\"done\",\"completionReason\":\"FINISH\",\"totalOutputTextTokenCount\":42}";

        BedrockStreamHandler.applyChunkMetadata(parser, finalChunk, metadata);

        assertEquals("FINISH", metadata.getCompletionReason());
        assertEquals(42, metadata.getTokenCount());
    }
}
