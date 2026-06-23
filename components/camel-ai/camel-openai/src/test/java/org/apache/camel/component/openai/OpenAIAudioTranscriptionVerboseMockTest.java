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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OpenAIAudioTranscriptionVerboseMockTest extends CamelTestSupport {

    private static final String TRANSCRIPTION_TEXT = "The stale smell of old beer lingers.";

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenTranscription()
            .replyWithTranscription(TRANSCRIPTION_TEXT)
            .withDuration(5.2)
            .withLanguage("en")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:transcribe-verbose")
                        .to("openai:audio-transcription?audioModel=whisper-1&audioResponseFormat=verbose_json"
                            + "&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
            }
        };
    }

    @Test
    void testVerboseResponseSetsHeaders() {
        Exchange result = template.request("direct:transcribe-verbose",
                e -> e.getIn().setBody(new byte[] { 0x00, 0x01, 0x02 }));

        assertNotNull(result);
        assertNull(result.getException());
        assertEquals(TRANSCRIPTION_TEXT, result.getMessage().getBody(String.class));
        assertEquals(5.2, result.getMessage().getHeader(OpenAIConstants.AUDIO_DURATION, Double.class), 0.001);
        assertEquals("en", result.getMessage().getHeader(OpenAIConstants.AUDIO_DETECTED_LANGUAGE, String.class));
    }
}
