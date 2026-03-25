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
package org.apache.camel.component.google.speechtotext.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.speechtotext.GoogleCloudSpeechToTextConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".*",
                              disabledReason = "Application credentials were not provided")
public class GoogleCloudSpeechToTextIT extends CamelTestSupport {

    final String serviceAccountKeyFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:recognize")
    private MockEndpoint mockRecognize;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:recognize")
                        .to("google-speech-to-text://recognize?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&encoding=LINEAR16&sampleRateHertz=16000&languageCode=en-US")
                        .log("body:${body}")
                        .to("mock:recognize");
            }
        };
    }

    @Test
    public void testRecognize() throws Exception {
        mockRecognize.expectedMessageCount(1);

        // Generate a minimal valid WAV audio with silence (LINEAR16, 16000 Hz, mono)
        byte[] audioData = createSilentWavAudio();

        Exchange exchange = template.request("direct:recognize", e -> e.getIn().setBody(audioData));

        assertNotNull(exchange);
        assertNotNull(exchange.getMessage().getHeader(GoogleCloudSpeechToTextConstants.RESPONSE_OBJECT));
        // Silence produces empty transcript
        assertTrue(exchange.getMessage().getBody(String.class) != null);

        mockRecognize.assertIsSatisfied(5000);
    }

    /**
     * Creates a minimal LINEAR16 audio buffer (1 second of silence at 16000 Hz, mono).
     */
    private byte[] createSilentWavAudio() {
        int sampleRate = 16000;
        int durationSeconds = 1;
        int numSamples = sampleRate * durationSeconds;
        // LINEAR16 = 2 bytes per sample
        byte[] audioData = new byte[numSamples * 2];
        // All zeros = silence
        return audioData;
    }
}
