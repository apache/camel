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
package org.apache.camel.component.springai.chat;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for parameter override via headers.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatParameterOverrideIT extends OllamaTestSupport {

    @Test
    public void testTemperatureOverrideViaHeader() {
        // Higher temperature should produce more varied responses
        var exchange = template().request("direct:chat-with-config", e -> {
            e.getIn().setBody("Say a random number between 1 and 100.");
            e.getIn().setHeader(SpringAiChatConstants.TEMPERATURE, 1.5);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testMaxTokensOverrideViaHeader() {
        var exchange = template().request("direct:chat-with-config", e -> {
            e.getIn().setBody("Write a story about a cat.");
            e.getIn().setHeader(SpringAiChatConstants.MAX_TOKENS, 50);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();

        // Verify the response is limited by tokens
        Integer outputTokens = exchange.getMessage().getHeader(SpringAiChatConstants.OUTPUT_TOKEN_COUNT, Integer.class);
        assertThat(outputTokens).isNotNull();
        // Output should be around or less than max tokens
        assertThat(outputTokens).isLessThanOrEqualTo(60); // Allow some buffer
    }

    @Test
    public void testTopPOverrideViaHeader() {
        var exchange = template().request("direct:chat-with-config", e -> {
            e.getIn().setBody("Complete this: The sky is");
            e.getIn().setHeader(SpringAiChatConstants.TOP_P, 0.1);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        // With low top_p, should get more deterministic responses
        assertThat(response.toLowerCase()).containsAnyOf("blue", "above", "high");
    }

    @Test
    public void testMultipleParameterOverrides() {
        var exchange = template().request("direct:chat-with-config", e -> {
            e.getIn().setBody("What is 2+2? Answer with just the number.");
            e.getIn().setHeader(SpringAiChatConstants.TEMPERATURE, 0.1);
            e.getIn().setHeader(SpringAiChatConstants.MAX_TOKENS, 10);
            e.getIn().setHeader(SpringAiChatConstants.TOP_P, 0.9);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).contains("4");
    }

    @Test
    public void testHeadersOverrideEndpointConfiguration() {
        // Endpoint has temperature=0.3, we override with header
        var exchange = template().request("direct:chat-with-config", e -> {
            e.getIn().setBody("Say hello in one word.");
            e.getIn().setHeader(SpringAiChatConstants.TEMPERATURE, 0.9);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();

        // Verify token tracking still works with parameter overrides
        Integer totalTokens = exchange.getMessage().getHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT, Integer.class);
        assertThat(totalTokens).isNotNull().isGreaterThan(0);
    }

    @Test
    public void testUsingEndpointConfigurationWithoutHeaders() {
        // This should use the endpoint's configured temperature=0.3
        String response = template().requestBody("direct:chat-with-config",
                "What is the capital of France? Answer in one word.", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).contains("paris");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                from("direct:chat-with-config")
                        .to("spring-ai-chat:configured?chatModel=#chatModel&temperature=0.3&maxTokens=500");
            }
        };
    }
}
