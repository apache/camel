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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

class ConverseStreamHandlerTest {

    @Test
    void testCreateCompleteHandler() {
        ConverseStreamHandler.StreamMetadata metadata = new ConverseStreamHandler.StreamMetadata();
        StringBuilder fullText = new StringBuilder();

        ConverseStreamResponseHandler handler = ConverseStreamHandler.createCompleteHandler(metadata, fullText);

        assertNotNull(handler, "Handler should not be null");
        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(fullText, "Full text builder should not be null");
    }

    @Test
    void testCreateChunksHandler() {
        ConverseStreamHandler.StreamMetadata metadata = new ConverseStreamHandler.StreamMetadata();
        List<String> chunks = new ArrayList<>();

        ConverseStreamResponseHandler handler = ConverseStreamHandler.createChunksHandler(metadata, chunks, null);

        assertNotNull(handler, "Handler should not be null");
        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(chunks, "Chunks list should not be null");
    }

    @Test
    void testStreamMetadata() {
        ConverseStreamHandler.StreamMetadata metadata = new ConverseStreamHandler.StreamMetadata();

        // Test setting and getting fullText
        metadata.setFullText("Test response");
        assertEquals("Test response", metadata.getFullText());

        // Test setting and getting chunks
        List<String> chunks = List.of("chunk1", "chunk2");
        metadata.setChunks(chunks);
        assertEquals(chunks, metadata.getChunks());

        // Test setting and getting stopReason
        metadata.setStopReason("end_turn");
        assertEquals("end_turn", metadata.getStopReason());

        // Test setting and getting chunkCount
        metadata.setChunkCount(5);
        assertEquals(5, metadata.getChunkCount());

        // Test usage is null initially
        assertEquals(null, metadata.getUsage());
    }
}
