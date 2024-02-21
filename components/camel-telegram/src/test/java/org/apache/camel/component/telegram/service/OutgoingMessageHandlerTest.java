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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Multipart message serialization tests")
class OutgoingMessageHandlerTest {

    @DisplayName("Test single body message")
    @Test
    void testSingleBody() {
        TelegramBodyPublisher bodyPublisher = new TelegramBodyPublisher(100);

        bodyPublisher.addBodyPart(new TelegramBodyPublisher.SingleBodyPart("message=\"value\""));

        ByteBuffer buffer = ByteBuffer.allocate(100);
        Set<TelegramBodyPublisher.TelegramBodyPart> bodyParts = bodyPublisher.getBodyParts();

        TelegramBodyPublisher.serialize(bodyParts, buffer, "");

        int written = buffer.capacity() - buffer.remaining();
        String serialized = new String(buffer.array(), 0, written);

        assertEquals("message=\"value\"", serialized);
    }

    @DisplayName("Test multi body message with 1 body")
    @Test
    void testMultiBody() {
        TelegramBodyPublisher bodyPublisher = new TelegramBodyPublisher(1024);

        bodyPublisher
                .addBodyPart(new TelegramBodyPublisher.MultilineBodyPart<>("message", "value1", StandardCharsets.UTF_8.name()));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        Set<TelegramBodyPublisher.TelegramBodyPart> bodyParts = bodyPublisher.getBodyParts();

        TelegramBodyPublisher.serialize(bodyParts, buffer, "aaa");

        int written = buffer.capacity() - buffer.remaining();
        String serialized = new String(buffer.array(), 0, written);

        assertEquals("--aaa\r\n" +
                     "Content-Disposition: form-data; name=\"message\"\r\n" +
                     "Content-Type: UTF-8; charset=UTF-8\r\n\r\n" +
                     "value1\r\n" +
                     "--aaa--\r\n",
                serialized);
    }

    @DisplayName("Test multi body message with 1 body and multiple headers")
    @Test
    void testMultiBodyWithMoreHeaders() {
        TelegramBodyPublisher bodyPublisher = new TelegramBodyPublisher(1024);

        final TelegramBodyPublisher.MultilineBodyPart<String> stringMultilineBodyPart
                = new TelegramBodyPublisher.MultilineBodyPart<>("message", "value1", StandardCharsets.UTF_8.name());

        // Headers must be serialized in the insertion order
        stringMultilineBodyPart.addHeader("key1", "value1");
        stringMultilineBodyPart.addHeader("key2", "value2");
        bodyPublisher.addBodyPart(stringMultilineBodyPart);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        Set<TelegramBodyPublisher.TelegramBodyPart> bodyParts = bodyPublisher.getBodyParts();

        TelegramBodyPublisher.serialize(bodyParts, buffer, "aaa");

        int written = buffer.capacity() - buffer.remaining();
        String serialized = new String(buffer.array(), 0, written);

        assertEquals("--aaa\r\n" +
                     "Content-Disposition: form-data; name=\"message\"; key1=\"value1\"; key2=\"value2\"\r\n"
                     +
                     "Content-Type: UTF-8; charset=UTF-8\r\n\r\n" +
                     "value1\r\n" +
                     "--aaa--\r\n",
                serialized);
    }

    @DisplayName("Test multi body message with 2 bodies and multiple headers")
    @Test
    void testMultiBodyWith2BodiesWithMoreHeaders() {
        TelegramBodyPublisher bodyPublisher = new TelegramBodyPublisher(1024);

        final TelegramBodyPublisher.MultilineBodyPart<String> stringMultilineBodyPart1
                = new TelegramBodyPublisher.MultilineBodyPart<>("message1", "value1", StandardCharsets.UTF_8.name());

        // Headers must be serialized in the insertion order
        stringMultilineBodyPart1.addHeader("key1", "value1");
        stringMultilineBodyPart1.addHeader("key2", "value2");
        bodyPublisher.addBodyPart(stringMultilineBodyPart1);

        final TelegramBodyPublisher.MultilineBodyPart<String> stringMultilineBodyPart2
                = new TelegramBodyPublisher.MultilineBodyPart<>("message2", "value2", StandardCharsets.UTF_8.name());

        // Headers must be serialized in the insertion order
        stringMultilineBodyPart2.addHeader("key1", "value1");
        stringMultilineBodyPart2.addHeader("key2", "value2");
        bodyPublisher.addBodyPart(stringMultilineBodyPart2);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        Set<TelegramBodyPublisher.TelegramBodyPart> bodyParts = bodyPublisher.getBodyParts();

        TelegramBodyPublisher.serialize(bodyParts, buffer, "aaa");

        int written = buffer.capacity() - buffer.remaining();
        String serialized = new String(buffer.array(), 0, written);

        assertEquals("--aaa\r\n" +
                     "Content-Disposition: form-data; name=\"message1\"; key1=\"value1\"; key2=\"value2\"\r\n"
                     +
                     "Content-Type: UTF-8; charset=UTF-8\r\n\r\n" +
                     "value1\r\n" +
                     "--aaa\r\n" +
                     "Content-Disposition: form-data; name=\"message2\"; key1=\"value1\"; key2=\"value2\"\r\n"
                     +
                     "Content-Type: UTF-8; charset=UTF-8\r\n\r\n" +
                     "value2\r\n" +
                     "--aaa--\r\n",
                serialized);
    }
}
