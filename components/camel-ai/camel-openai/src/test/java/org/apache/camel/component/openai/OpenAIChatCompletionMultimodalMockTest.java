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
package org.apache.camel.component.openai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIChatCompletionMultimodalMockTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final byte[] PDF_BYTES = { '%', 'P', 'D', 'F', '-', '1', '.', '4' };
    private static final byte[] WAV_BYTES = { 'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E' };
    private static final byte[] MP3_BYTES = { (byte) 0xFF, (byte) 0xFB, 0x10, 0x00 };

    @TempDir
    Path tempDir;

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("Summarize this document")
            .assertRequest(request -> assertFilePart(request, "application/pdf", PDF_BYTES))
            .replyWith("PDF summary")
            .end()
            .when("Transcribe this clip")
            .assertRequest(request -> assertInputAudioPart(request, "wav", WAV_BYTES))
            .replyWith("Audio answer")
            .end()
            .when("Listen to this mp3")
            .assertRequest(request -> assertInputAudioPart(request, "mp3", MP3_BYTES))
            .replyWith("MP3 answer")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("openai:chat-completion?model=gpt-4o&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void pdfByteArrayIsSentAsFileContentPart() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(PDF_BYTES);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "application/pdf");
            e.getIn().setHeader(Exchange.FILE_NAME, "report.pdf");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "Summarize this document");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("PDF summary");
    }

    @Test
    void pdfFileBodyIsSentAsFileContentPart() throws Exception {
        Path pdfFile = tempDir.resolve("notes.pdf");
        Files.write(pdfFile, PDF_BYTES);

        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(pdfFile.toFile());
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "Summarize this document");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("PDF summary");
    }

    @Test
    void wavByteArrayIsSentAsInputAudioPart() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(WAV_BYTES);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "audio/wav");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "Transcribe this clip");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Audio answer");
    }

    @Test
    void mp3InputStreamIsSentAsInputAudioPart() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(new ByteArrayInputStream(MP3_BYTES));
            e.getIn().setHeader(Exchange.CONTENT_TYPE, "audio/mpeg");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "Listen to this mp3");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("MP3 answer");
    }

    @Test
    void audioBodyWithoutUserMessageFails() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(WAV_BYTES);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "audio/wav");
        });

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User message");
    }

    @Test
    void unsupportedAudioMimeTypeFails() {
        Exchange result = template.request("direct:chat", e -> {
            e.getIn().setBody(WAV_BYTES);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "audio/ogg");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "Transcribe this clip");
        });

        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported audio MIME type");
    }

    private static void assertFilePart(String request, String expectedMime, byte[] expectedBytes) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(request);
            JsonNode content = root.path("messages").path(0).path("content");
            JsonNode filePart = null;
            for (JsonNode part : content) {
                if ("file".equals(part.path("type").asText())) {
                    filePart = part;
                }
            }
            assertThat(filePart).isNotNull();
            assertThat(filePart.path("file").path("filename").asText()).isNotBlank();
            String fileData = filePart.path("file").path("file_data").asText();
            String prefix = "data:" + expectedMime + ";base64,";
            assertThat(fileData).startsWith(prefix);
            assertThat(Base64.getDecoder().decode(fileData.substring(prefix.length()))).isEqualTo(expectedBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertInputAudioPart(String request, String expectedFormat, byte[] expectedBytes) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(request);
            JsonNode content = root.path("messages").path(0).path("content");
            JsonNode audioPart = null;
            for (JsonNode part : content) {
                if ("input_audio".equals(part.path("type").asText())) {
                    audioPart = part;
                }
            }
            assertThat(audioPart).isNotNull();
            assertThat(audioPart.path("input_audio").path("format").asText()).isEqualTo(expectedFormat);
            byte[] decoded = Base64.getDecoder().decode(audioPart.path("input_audio").path("data").asText());
            assertThat(decoded).isEqualTo(expectedBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
