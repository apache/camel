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

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenAIAudioSpeechMockTest extends CamelTestSupport {

    private static final byte[] AUDIO_DATA = "FAKE-MP3-AUDIO-BYTES".getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenSpeech()
            .replyWithSpeech(AUDIO_DATA)
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:speak")
                        .to("openai:audio-speech?speechModel=tts-1&speechVoice=alloy&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:speak-no-model")
                        .to("openai:audio-speech?speechVoice=alloy&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");

                from("direct:speak-wav")
                        .to("openai:audio-speech?speechModel=tts-1&speechVoice=nova&speechResponseFormat=wav&apiKey=dummy&baseUrl="
                            + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void testSpeechReturnsAudioBytes() {
        Exchange result = template.request("direct:speak", e -> e.getIn().setBody("Hello from Apache Camel"));

        assertNotNull(result);
        assertNull(result.getException());
        assertArrayEquals(AUDIO_DATA, result.getMessage().getBody(byte[].class));
    }

    @Test
    void testSpeechSetsContentType() {
        Exchange result = template.request("direct:speak", e -> e.getIn().setBody("Hello"));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals("audio/mpeg", result.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    void testSpeechWavContentType() {
        Exchange result = template.request("direct:speak-wav", e -> e.getIn().setBody("Hello"));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals("audio/wav", result.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertArrayEquals(AUDIO_DATA, result.getMessage().getBody(byte[].class));
    }

    @Test
    void testSpeechWithHeaderOverrides() {
        Exchange result = template.request("direct:speak", e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(OpenAIConstants.SPEECH_MODEL, "gpt-4o-mini-tts");
            e.getIn().setHeader(OpenAIConstants.SPEECH_VOICE, "shimmer");
            e.getIn().setHeader(OpenAIConstants.SPEECH_RESPONSE_FORMAT, "flac");
            e.getIn().setHeader(OpenAIConstants.SPEECH_SPEED, 1.25);
            e.getIn().setHeader(OpenAIConstants.SPEECH_INSTRUCTIONS, "Speak cheerfully");
        });

        assertNotNull(result);
        assertNull(result.getException());
        assertArrayEquals(AUDIO_DATA, result.getMessage().getBody(byte[].class));
        assertEquals("audio/flac", result.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    void testSpeechMissingModel() {
        Exchange result = template.request("direct:speak-no-model", e -> e.getIn().setBody("Hello"));

        assertNotNull(result);
        assertNotNull(result.getException());
        assertTrue(result.getException() instanceof IllegalArgumentException);
        assertTrue(result.getException().getMessage().contains("Speech model must be specified"));
    }

    @Test
    void testSpeechMissingInput() {
        Exchange result = template.request("direct:speak", e -> e.getIn().setBody(null));

        assertNotNull(result);
        assertNotNull(result.getException());
        assertTrue(result.getException() instanceof IllegalArgumentException);
        assertTrue(result.getException().getMessage().contains("input text"));
    }
}
