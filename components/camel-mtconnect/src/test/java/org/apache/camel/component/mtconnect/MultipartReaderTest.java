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
package org.apache.camel.component.mtconnect;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Framing of the agent's streaming response, on byte streams a real HTTP server cannot produce on purpose. */
class MultipartReaderTest {

    private static final String BOUNDARY = "1ff5027f-5763-4505-8dfd-594bca1b5de8";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "multipart/mixed;boundary=1ff5027f-5763-4505-8dfd-594bca1b5de8 | 1ff5027f-5763-4505-8dfd-594bca1b5de8",
            "multipart/x-mixed-replace;boundary=0c233222c57d11aa0fb87d9c2990e2ae | 0c233222c57d11aa0fb87d9c2990e2ae",
            "Multipart/Mixed; Boundary=\"quoted\" | quoted",
            "application/xml | ",
            "multipart/mixed | " })
    void bothMultipartTypesYieldTheirBoundary(String contentType, String boundary) {
        assertThat(MultipartReader.boundary(contentType)).isEqualTo(boundary);
    }

    @Test
    void aPartHeaderSplitAcrossReadsIsReadWhole() throws IOException {
        byte[] stream = part("Content-Type", "Content-Length", "<a/>").getBytes(StandardCharsets.US_ASCII);
        InputStream oneByteAtATime = new InputStream() {
            private int position;

            @Override
            public int read() {
                return position < stream.length ? stream[position++] & 0xff : -1;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (length == 0) {
                    return 0;
                }
                if (position >= stream.length) {
                    return -1;
                }
                buffer[offset] = stream[position++];
                return 1;
            }
        };

        MultipartReader reader = new MultipartReader(oneByteAtATime, BOUNDARY);
        assertThat(new String(reader.nextPart(), StandardCharsets.UTF_8)).isEqualTo("<a/>");
        assertThat(reader.nextPart()).isNull();
    }

    @ParameterizedTest
    @CsvSource({ "Content-Type, Content-Length", "Content-type, Content-length", "CONTENT-TYPE, CONTENT-LENGTH" })
    void partHeaderNamesAreMatchedWithoutCase(String type, String length) throws IOException {
        String stream = part(type, length, "<first/>") + part(type, length, "<second/>");

        assertThat(parts(stream)).containsExactly("<first/>", "<second/>");
    }

    @Test
    void aPartWithoutContentLengthEndsAtTheNextBoundary() throws IOException {
        String stream = "--" + BOUNDARY + "\r\nContent-Type: text/xml\r\n\r\n<first/>\r\n--" + BOUNDARY
                        + "\r\nContent-Type: text/xml\r\n\r\n<second/>\r\n--" + BOUNDARY + "--\r\n";

        assertThat(parts(stream)).containsExactly("<first/>", "<second/>");
    }

    @Test
    void theStreamEndsAtTheEndOfInputWithoutAClosingBoundary() throws IOException {
        String stream = part("Content-Type", "Content-Length", "<only/>");

        assertThat(parts(stream)).containsExactly("<only/>");
    }

    @Test
    void aClosingBoundaryEndsTheStream() throws IOException {
        String stream = part("Content-Type", "Content-Length", "<only/>") + "--" + BOUNDARY + "--\r\n"
                        + part("Content-Type", "Content-Length", "<ignored/>");

        assertThat(parts(stream)).containsExactly("<only/>");
    }

    @Test
    void theEndOfInputInsideAPartIsAnError() {
        String stream = "--" + BOUNDARY + "\r\nContent-Type: text/xml\r\nContent-Length: 100\r\n\r\n<cut";

        assertThatThrownBy(() -> parts(stream)).isInstanceOf(EOFException.class);
    }

    @Test
    void anEmptyHeartbeatPartOfACapturedTwoXStreamIsADocumentWithoutObservations() throws Exception {
        List<AgentDocument> documents = documents("streams/v25-heartbeat.multipart.txt", BOUNDARY);

        assertThat(documents).hasSize(6).allSatisfy(document -> {
            AgentDocument.Streams streams = (AgentDocument.Streams) document;
            assertThat(streams.observations()).isEmpty();
            assertThat(streams.header().nextSequence()).isEqualTo(432L);
            assertThat(streams.header().drained()).isTrue();
        });
    }

    @Test
    void aCapturedOneXStreamWithLowerCaseHeadersAndHeartbeatsIsRead() throws Exception {
        List<AgentDocument> heartbeats = documents("streams/v15-heartbeat.multipart.txt", "defb68f5f8b01d478e110c58560203f7");
        assertThat(heartbeats).hasSize(3)
                .allSatisfy(document -> assertThat(((AgentDocument.Streams) document).observations()).isEmpty());

        List<AgentDocument> data = documents("streams/v13-data.multipart.txt", "0c233222c57d11aa0fb87d9c2990e2ae");
        assertThat(data).hasSize(6);
        List<Observation> observations = new ArrayList<>();
        data.forEach(document -> observations.addAll(((AgentDocument.Streams) document).observations()));
        assertThat(observations).isNotEmpty().allSatisfy(observation -> {
            assertThat(observation.deviceName()).isEqualTo("MACHINE-01");
            assertThat(observation.timestamp()).isNotNull();
        });
        assertThat(observations).extracting(Observation::sequence).isSorted().doesNotHaveDuplicates();
        assertThat(((AgentDocument.Streams) data.get(data.size() - 1)).header().nextSequence()).isEqualTo(3731481L);
    }

    private static String part(String typeHeader, String lengthHeader, String content) {
        return "--" + BOUNDARY + "\r\n" + typeHeader + ": application/xml\r\n" + lengthHeader + ": "
               + content.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + content + "\r\n";
    }

    private static List<String> parts(String stream) throws IOException {
        MultipartReader reader = new MultipartReader(
                new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), BOUNDARY);
        List<String> parts = new ArrayList<>();
        for (byte[] part = reader.nextPart(); part != null; part = reader.nextPart()) {
            parts.add(new String(part, StandardCharsets.UTF_8));
        }
        return parts;
    }

    private static List<AgentDocument> documents(String resource, String boundary) throws Exception {
        DocumentParser parser = new DocumentParser();
        List<AgentDocument> documents = new ArrayList<>();
        try (InputStream in = MultipartReaderTest.class.getClassLoader().getResourceAsStream(resource)) {
            MultipartReader reader = new MultipartReader(in, boundary);
            for (byte[] part = reader.nextPart(); part != null; part = reader.nextPart()) {
                documents.add(parser.parse(part));
            }
        }
        return documents;
    }
}
