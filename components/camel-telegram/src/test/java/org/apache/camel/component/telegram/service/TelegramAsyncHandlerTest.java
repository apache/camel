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

package org.apache.camel.component.telegram.service;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests charset information parsing")
class TelegramAsyncHandlerTest {

    @DisplayName("default charset information")
    @Test
    void testExtractCharset() {
        assertEquals("UTF-8", TelegramAsyncHandler.extractCharset("Content-Type: text/plain; charset=UTF-8",
                StandardCharsets.US_ASCII.name()));
    }

    @DisplayName("null charset information")
    @Test
    void testExtractCharsetNull() {
        assertEquals("UTF-8", TelegramAsyncHandler.extractCharset(null, StandardCharsets.UTF_8.name()));
    }

    @DisplayName("with more items than expected")
    @Test
    void testExtractCharsetWithMoreItems() {
        assertEquals("UTF-8", TelegramAsyncHandler.extractCharset("Content-Type: text/plain; charset=UTF-8; name=\"some-name\"",
                StandardCharsets.US_ASCII.name()));
    }

    @DisplayName("with the charset information in the middle")
    @Test
    void testExtractCharsetInTheMiddle() {
        assertEquals("UTF-8", TelegramAsyncHandler.extractCharset("Content-Type: text/plain; name=\"some-name\"; charset=UTF-8",
                StandardCharsets.US_ASCII.name()));
    }

    @DisplayName("default charset information")
    @Test
    void testMissingCharset() {
        assertEquals("ISO-8859-1", TelegramAsyncHandler.extractCharset("Content-Type: text/plain",
                StandardCharsets.ISO_8859_1.name()));
    }
}
