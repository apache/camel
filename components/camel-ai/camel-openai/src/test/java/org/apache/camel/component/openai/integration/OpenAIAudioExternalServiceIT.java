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
package org.apache.camel.component.openai.integration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "openai.live.tests", matches = "true",
                         disabledReason = "Set -Dopenai.live.tests=true and configure an OpenAI-compatible audio service")
public class OpenAIAudioExternalServiceIT extends OpenAIExternalServiceTestSupport {

    private static final String AUDIO_MODEL = "openai.live.audio.model";
    private static final String SPEECH_MODEL = "openai.live.speech.model";
    private static final String SPEECH_VOICE = "openai.live.speech.voice";

    @Override
    protected RouteBuilder createRouteBuilder() {
        String audioModel = requiredProperty(AUDIO_MODEL);
        String speechModel = requiredProperty(SPEECH_MODEL);
        String speechVoice = requiredProperty(SPEECH_VOICE);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:speech").toF("openai:audio-speech?speechModel=%s&speechVoice=%s&speechResponseFormat=wav",
                        speechModel, speechVoice);
                from("direct:transcription").toF("openai:audio-transcription?audioModel=%s", audioModel)
                        .log("${body}");
                from("direct:translation").toF("openai:audio-translation?audioModel=%s", audioModel)
                        .log("${body}");
            }
        };
    }

    @Test
    void speechCanBeTranscribedAndTranslated() throws Exception {
        Exchange speech = template.request("direct:speech", exchange -> exchange.getIn().setBody("Apache Camel integration"));
        assertThat(speech.getException()).isNull();
        byte[] audio = speech.getMessage().getBody(byte[].class);
        assertThat(audio).isNotEmpty();
        writeArtifact("speech.wav", audio);

        Exchange transcription = template.request("direct:transcription", exchange -> exchange.getIn().setBody(audio));
        assertThat(transcription.getException()).isNull();
        assertThat(transcription.getMessage().getBody(String.class)).isNotBlank();

        Exchange translation = template.request("direct:translation", exchange -> exchange.getIn().setBody(audio));
        assertThat(translation.getException()).isNull();
        assertThat(translation.getMessage().getBody(String.class)).isNotBlank();
    }
}
