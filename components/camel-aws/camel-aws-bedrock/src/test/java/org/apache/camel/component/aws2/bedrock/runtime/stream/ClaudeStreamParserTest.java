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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClaudeStreamParserTest {

    private final ClaudeStreamParser parser = new ClaudeStreamParser();

    @Test
    void testExtractText() throws Exception {
        String chunk =
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello world\"}}";
        assertEquals("Hello world", parser.extractText(chunk));
    }

    @Test
    void testExtractTextFromNonDeltaChunk() throws Exception {
        String chunk = "{\"type\":\"message_start\",\"message\":{}}";
        assertEquals("", parser.extractText(chunk));
    }

    @Test
    void testExtractCompletionReason() throws Exception {
        String chunk =
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":150}}";
        assertEquals("end_turn", parser.extractCompletionReason(chunk));
    }

    @Test
    void testExtractTokenCount() throws Exception {
        String chunk =
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":150}}";
        assertEquals(150, parser.extractTokenCount(chunk));
    }

    @Test
    void testIsFinalChunk() throws Exception {
        String finalChunk = "{\"type\":\"message_stop\"}";
        assertTrue(parser.isFinalChunk(finalChunk));

        String normalChunk =
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"test\"}}";
        assertFalse(parser.isFinalChunk(normalChunk));
    }
}
