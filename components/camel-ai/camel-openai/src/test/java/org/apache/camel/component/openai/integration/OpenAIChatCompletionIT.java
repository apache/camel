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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
public class OpenAIChatCompletionIT extends OpenAITestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route for simple message test
                from("direct:send-simple-message")
                        .toF("openai:chat-completion?apiKey=%s&baseUrl=%s&model=%s", apiKey, baseUrl, model)
                        .to("mock:response");
            }
        };
    }

    @Test
    public void testSendSimpleStringMessage() throws Exception {
        // Setup mock endpoint expectations
        MockEndpoint mockResponse = getMockEndpoint("mock:response");
        mockResponse.expectedMessageCount(1);

        // Send a test message to the OpenAI endpoint
        String response = template.requestBody("direct:send-simple-message",
                "What is Apache Camel?",
                String.class);

        // Verify the mock endpoint received the message
        mockResponse.assertIsSatisfied();

        // Verify response is not null and contains meaningful content
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response.length()).isGreaterThan(10);

        assertThat(response).contains("Camel");
        assertThat(response).contains("Apache");
        assertThat(response).contains("integration");
    }

    @Test
    public void testEmptyMessageThrowsException() {
        // Verify that empty messages result in an IllegalArgumentException
        Exception exception = assertThrows(Exception.class, () -> {
            template.requestBody("direct:send-simple-message", "", String.class);
        });

        // Verify the exception is an IllegalArgumentException about empty input
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        assertThat(exception.getCause().getMessage()).contains("No input provided to LLM");
    }
}
