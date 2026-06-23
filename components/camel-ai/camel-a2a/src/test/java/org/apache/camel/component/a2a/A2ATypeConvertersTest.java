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
package org.apache.camel.component.a2a;

import java.util.List;
import java.util.Map;

import org.apache.camel.component.a2a.model.DataPart;
import org.apache.camel.component.a2a.model.FilePart;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TextPart;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2ATypeConvertersTest {

    // ---- taskToString ----

    @Test
    void taskToStringExtractsFromLatestMessage() {
        Message first = Message.builder()
                .parts(List.of(new TextPart("old response")))
                .build();
        Message last = Message.builder()
                .parts(List.of(new TextPart("latest response")))
                .build();
        Task task = Task.builder()
                .history(List.of(first, last))
                .build();

        assertThat(A2ATypeConverters.taskToString(task)).isEqualTo("latest response");
    }

    @Test
    void taskToStringReturnsEmptyForNoHistory() {
        Task task = Task.builder().build();

        assertThat(A2ATypeConverters.taskToString(task)).isEmpty();
    }

    @Test
    void taskToStringReturnsEmptyForNullHistory() {
        Task task = Task.builder().history(null).build();

        assertThat(A2ATypeConverters.taskToString(task)).isEmpty();
    }

    @Test
    void taskToStringReturnsEmptyForEmptyHistory() {
        Task task = Task.builder().history(List.of()).build();

        assertThat(A2ATypeConverters.taskToString(task)).isEmpty();
    }

    // ---- messageToString with TextPart ----

    @Test
    void messageToStringExtractsTextPart() {
        Message message = Message.builder()
                .parts(List.of(new TextPart("hello world")))
                .build();

        assertThat(A2ATypeConverters.messageToString(message)).isEqualTo("hello world");
    }

    // ---- messageToString with DataPart ----

    @Test
    void messageToStringSerializesDataPartAsJson() {
        DataPart dataPart = new DataPart(Map.of("key", "value"));
        Message message = Message.builder()
                .parts(List.of(dataPart))
                .build();

        String result = A2ATypeConverters.messageToString(message);
        assertThat(result).contains("key").contains("value");
    }

    // ---- messageToString with FilePart (URL) ----

    @Test
    void messageToStringExtractsFileWithUrl() {
        FilePart filePart = FilePart.ofUrl("https://example.com/doc.pdf", "application/pdf", "doc.pdf");
        Message message = Message.builder()
                .parts(List.of(filePart))
                .build();

        assertThat(A2ATypeConverters.messageToString(message)).isEqualTo("https://example.com/doc.pdf");
    }

    // ---- messageToString with FilePart (raw bytes) ----

    @Test
    void messageToStringDescribesFileWithRawBytes() {
        FilePart filePart = FilePart.ofBytes("aGVsbG8=", "image/png", "photo.png");
        Message message = Message.builder()
                .parts(List.of(filePart))
                .build();

        assertThat(A2ATypeConverters.messageToString(message)).isEqualTo("[binary 8 chars base64]");
    }

    // ---- messageToString with multiple parts ----

    @Test
    void messageToStringConcatenatesMultipleParts() {
        Message message = Message.builder()
                .parts(List.of(
                        new TextPart("Hello"),
                        new DataPart(Map.of("status", "ok"))))
                .build();

        String result = A2ATypeConverters.messageToString(message);
        assertThat(result).startsWith("Hello\n");
        assertThat(result).contains("status").contains("ok");
    }

    @Test
    void messageToStringConcatenatesTextAndFileParts() {
        Message message = Message.builder()
                .parts(List.of(
                        new TextPart("See attachment:"),
                        FilePart.ofUrl("https://example.com/readme.txt", "text/plain", "readme.txt")))
                .build();

        String result = A2ATypeConverters.messageToString(message);
        assertThat(result).isEqualTo("See attachment:\nhttps://example.com/readme.txt");
    }

    // ---- messageToString edge cases ----

    @Test
    void messageToStringReturnsEmptyForNoParts() {
        Message message = Message.builder()
                .parts(List.of())
                .build();

        assertThat(A2ATypeConverters.messageToString(message)).isEmpty();
    }

    @Test
    void messageToStringReturnsEmptyForNullParts() {
        Message message = Message.builder()
                .parts(null)
                .build();

        assertThat(A2ATypeConverters.messageToString(message)).isEmpty();
    }

    @Test
    void messageToStringReturnsEmptyForNullMessage() {
        assertThat(A2ATypeConverters.messageToString(null)).isEmpty();
    }
}
