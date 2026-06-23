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
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAIAudioTranscriptionMockTest extends CamelTestSupport {

    private static final String TRANSCRIPTION_TEXT = "Hello, this is a test transcription from Apache Camel.";

    @TempDir
    Path tempDir;

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenTranscription()
            .replyWithTranscription(TRANSCRIPTION_TEXT)
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:transcribe")
                        .to("openai:audio-transcription?audioModel=whisper-1&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:transcribe-no-model")
                        .to("openai:audio-transcription?apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("file:" + tempDir.toString() + "?noop=true&initialDelay=0&delay=100")
                        .to("openai:audio-transcription?audioModel=whisper-1&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1")
                        .to("mock:transcribed");
            }
        };
    }

    @Test
    void testTranscriptionWithFile() throws Exception {
        File audioFile = tempDir.resolve("test-audio.wav").toFile();
        Files.write(audioFile.toPath(), new byte[] { 0x00, 0x01, 0x02 });

        Exchange result = template.request("direct:transcribe", e -> e.getIn().setBody(audioFile));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals(TRANSCRIPTION_TEXT, result.getMessage().getBody(String.class));
    }

    @Test
    void testTranscriptionWithByteArray() {
        byte[] audioBytes = new byte[] { 0x00, 0x01, 0x02 };

        Exchange result = template.request("direct:transcribe", e -> e.getIn().setBody(audioBytes));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals(TRANSCRIPTION_TEXT, result.getMessage().getBody(String.class));
    }

    @Test
    void testTranscriptionWithInputStream() {
        InputStream audioStream = new ByteArrayInputStream(new byte[] { 0x00, 0x01, 0x02 });

        Exchange result = template.request("direct:transcribe", e -> e.getIn().setBody(audioStream));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals(TRANSCRIPTION_TEXT, result.getMessage().getBody(String.class));
    }

    @Test
    void testTranscriptionWithPath() throws Exception {
        Path audioPath = tempDir.resolve("test-audio.mp3");
        Files.write(audioPath, new byte[] { 0x00, 0x01, 0x02 });

        Exchange result = template.request("direct:transcribe", e -> e.getIn().setBody(audioPath));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals(TRANSCRIPTION_TEXT, result.getMessage().getBody(String.class));
    }

    @Test
    void testTranscriptionWithHeaderOverrides() {
        byte[] audioBytes = new byte[] { 0x00, 0x01, 0x02 };

        Exchange result = template.request("direct:transcribe", e -> {
            e.getIn().setBody(audioBytes);
            e.getIn().setHeader(OpenAIConstants.AUDIO_MODEL, "gpt-4o-transcribe");
            e.getIn().setHeader(OpenAIConstants.AUDIO_LANGUAGE, "en");
        });

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals(TRANSCRIPTION_TEXT, result.getMessage().getBody(String.class));
    }

    @Test
    void testTranscriptionFromFileComponent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:transcribed");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(TRANSCRIPTION_TEXT);

        Files.write(tempDir.resolve("recording.wav"), new byte[] { 0x00, 0x01, 0x02 });

        mock.await(10, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testTranscriptionMissingModel() {
        byte[] audioBytes = new byte[] { 0x00, 0x01, 0x02 };

        Exchange result = template.request("direct:transcribe-no-model", e -> e.getIn().setBody(audioBytes));

        assertNotNull(result);
        assertNotNull(result.getException());
        assertTrue(result.getException() instanceof IllegalArgumentException);
        assertTrue(result.getException().getMessage().contains("Audio model must be specified"));
    }
}
