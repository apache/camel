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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIAudioTranscriptionDiarizedMockTest extends CamelTestSupport {

    private static final String DIARIZED_TEXT = "Speaker A: Hello. Speaker B: Hi there.";

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenTranscription()
            .replyWithDiarizedTranscription(DIARIZED_TEXT)
            .withDuration(12.4)
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:transcribe-diarized")
                        .to("openai:audio-transcription?audioModel=gpt-4o-transcribe-diarize"
                            + "&audioResponseFormat=diarized_json&audioChunkingStrategy=auto"
                            + "&audioKnownSpeakerNames=Alice,Bob&audioKnownSpeakerReferences=ref-a,ref-b"
                            + "&audioKeywords=Apache,Camel&audioLanguages=en,fr&audioInclude=logprobs"
                            + "&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void diarizedResponseSetsBodyAndDurationHeader() {
        Exchange result = template.request("direct:transcribe-diarized",
                e -> e.getIn().setBody(new byte[] { 0x00, 0x01, 0x02 }));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo(DIARIZED_TEXT);
        assertThat(result.getMessage().getHeader(OpenAIConstants.AUDIO_DURATION, Double.class)).isEqualTo(12.4);
        assertThat(result.getMessage().getHeader(OpenAIConstants.AUDIO_DETECTED_LANGUAGE)).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.AUDIO_DIARIZED_SEGMENTS, List.class)).hasSize(1);
    }
}
