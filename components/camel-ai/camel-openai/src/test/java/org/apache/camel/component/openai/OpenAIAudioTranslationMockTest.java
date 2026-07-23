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

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAIAudioTranslationMockTest extends CamelTestSupport {

    private static final String TRANSLATION_TEXT = "Hello, this is a translated transcript.";

    @TempDir
    Path tempDir;

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenTranslation()
            .replyWithTranslation(TRANSLATION_TEXT)
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:translate")
                        .to("openai:audio-translation?audioModel=whisper-1&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:translate-no-model")
                        .to("openai:audio-translation?apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("file:" + tempDir.toString() + "?noop=true&initialDelay=0&delay=100")
                        .to("openai:audio-translation?audioModel=whisper-1&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1")
                        .to("mock:translated");
            }
        };
    }

    @Test
    void testTranslationWithFile() throws Exception {
        File audioFile = tempDir.resolve("test-audio.wav").toFile();
        Files.write(audioFile.toPath(), new byte[] { 0x00, 0x01, 0x02 });

        Exchange result = template.request("direct:translate", e -> e.getIn().setBody(audioFile));

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(TRANSLATION_TEXT);
    }

    @Test
    void testTranslationWithByteArray() {
        byte[] audioBytes = new byte[] { 0x00, 0x01, 0x02 };

        Exchange result = template.request("direct:translate", e -> e.getIn().setBody(audioBytes));

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(TRANSLATION_TEXT);
    }

    @Test
    void testTranslationWithInputStream() {
        InputStream audioStream = new ByteArrayInputStream(new byte[] { 0x00, 0x01, 0x02 });

        Exchange result = template.request("direct:translate", e -> e.getIn().setBody(audioStream));

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(TRANSLATION_TEXT);
    }

    @Test
    void testTranslationWithPath() throws Exception {
        Path audioPath = tempDir.resolve("test-audio.mp3");
        Files.write(audioPath, new byte[] { 0x00, 0x01, 0x02 });

        Exchange result = template.request("direct:translate", e -> e.getIn().setBody(audioPath));

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(TRANSLATION_TEXT);
    }

    @Test
    void testTranslationWithHeaderOverrides() {
        byte[] audioBytes = new byte[] { 0x00, 0x01, 0x02 };

        Exchange result = template.request("direct:translate", e -> {
            e.getIn().setBody(audioBytes);
            e.getIn().setHeader(OpenAIConstants.AUDIO_MODEL, "whisper-1");
            e.getIn().setHeader(OpenAIConstants.AUDIO_PROMPT, "Technical vocabulary");
            e.getIn().setHeader(OpenAIConstants.AUDIO_TEMPERATURE, 0.2);
        });

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(TRANSLATION_TEXT);
    }

    @Test
    void testTranslationFromFileComponent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:translated");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(TRANSLATION_TEXT);

        Files.write(tempDir.resolve("recording.wav"), new byte[] { 0x00, 0x01, 0x02 });

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    void testTranslationMissingModel() {
        byte[] audioBytes = new byte[] { 0x00, 0x01, 0x02 };

        Exchange result = template.request("direct:translate-no-model", e -> e.getIn().setBody(audioBytes));

        assertThat(result).isNotNull();
        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Audio model must be specified");
    }
}
