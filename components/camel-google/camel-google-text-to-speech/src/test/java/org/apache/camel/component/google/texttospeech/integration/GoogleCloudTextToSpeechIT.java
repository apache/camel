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
package org.apache.camel.component.google.texttospeech.integration;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.texttospeech.GoogleCloudTextToSpeechConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "GOOGLE_APPLICATION_CREDENTIALS", matches = ".*",
                              disabledReason = "Application credentials were not provided")
public class GoogleCloudTextToSpeechIT extends CamelTestSupport {

    final String serviceAccountKeyFile = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:synthesize")
    private MockEndpoint mockSynthesize;

    @EndpointInject("mock:listVoices")
    private MockEndpoint mockListVoices;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:synthesize")
                        .to("google-text-to-speech://synthesize?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&languageCode=en-US&audioEncoding=MP3")
                        .log("body:${body}")
                        .to("mock:synthesize");

                from("direct:listVoices")
                        .to("google-text-to-speech://listVoices?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&languageCode=en-US")
                        .log("body:${body}")
                        .to("mock:listVoices");
            }
        };
    }

    @Test
    public void testSynthesize() throws Exception {
        mockSynthesize.expectedMessageCount(1);

        Exchange exchange = template.request("direct:synthesize", e -> e.getIn().setBody("Hello from Apache Camel"));

        assertNotNull(exchange);
        byte[] audioContent = exchange.getMessage().getBody(byte[].class);
        assertNotNull(audioContent);
        assertTrue(audioContent.length > 0, "Audio content should not be empty");
        assertNotNull(exchange.getMessage().getHeader(GoogleCloudTextToSpeechConstants.RESPONSE_OBJECT));

        mockSynthesize.assertIsSatisfied(5000);
    }

    @Test
    public void testListVoices() throws Exception {
        mockListVoices.expectedMessageCount(1);

        Exchange exchange = template.request("direct:listVoices", e -> e.getIn().setBody(null));

        assertNotNull(exchange);
        List<?> voices = exchange.getMessage().getBody(List.class);
        assertNotNull(voices);
        assertTrue(voices.size() > 0, "Should return at least one voice for en-US");

        mockListVoices.assertIsSatisfied(5000);
    }
}
