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

class TitanStreamParserTest {

    private final TitanStreamParser parser = new TitanStreamParser();

    @Test
    void testExtractText() throws Exception {
        String chunk =
                "{\"outputText\":\"Hello world\",\"index\":0,\"totalOutputTextTokenCount\":null,\"completionReason\":null}";
        assertEquals("Hello world", parser.extractText(chunk));
    }

    @Test
    void testExtractTextEmpty() throws Exception {
        String chunk =
                "{\"outputText\":\"\",\"index\":2,\"totalOutputTextTokenCount\":150,\"completionReason\":\"FINISH\"}";
        assertEquals("", parser.extractText(chunk));
    }

    @Test
    void testExtractCompletionReason() throws Exception {
        String chunk =
                "{\"outputText\":\"\",\"index\":2,\"totalOutputTextTokenCount\":150,\"completionReason\":\"FINISH\"}";
        assertEquals("FINISH", parser.extractCompletionReason(chunk));
    }

    @Test
    void testExtractTokenCount() throws Exception {
        String chunk =
                "{\"outputText\":\"\",\"index\":2,\"totalOutputTextTokenCount\":150,\"completionReason\":\"FINISH\"}";
        assertEquals(150, parser.extractTokenCount(chunk));
    }

    @Test
    void testIsFinalChunk() throws Exception {
        String finalChunk =
                "{\"outputText\":\"\",\"index\":2,\"totalOutputTextTokenCount\":150,\"completionReason\":\"FINISH\"}";
        assertTrue(parser.isFinalChunk(finalChunk));

        String normalChunk =
                "{\"outputText\":\"text\",\"index\":0,\"totalOutputTextTokenCount\":null,\"completionReason\":null}";
        assertFalse(parser.isFinalChunk(normalChunk));
    }
}
