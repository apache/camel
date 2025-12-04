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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test for Spring AI Chat component SafeGuard advisor.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatSafeguardIT extends OllamaTestSupport {

    @Test
    public void testSafeguardBlocksSensitiveWords() {
        // Request that would normally return something containing "password"
        String response = template.requestBody(
                "direct:safeguard", "Tell me a story about a user who forgot their password", String.class);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();

        // The SafeGuard advisor should have blocked the response and returned the failure message
        assertThat(response).contains("I cannot provide information containing sensitive words");
    }

    @Test
    public void testSafeguardAllowsNonSensitiveContent() {
        // Request that doesn't contain sensitive words
        String response = template.requestBody(
                "direct:safeguard", "What is the capital of France? Answer in one word.", String.class);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();

        // Should get a normal response without being blocked
        assertThat(response.toLowerCase()).contains("paris");
        assertThat(response).doesNotContain("I cannot provide information containing sensitive words");
    }

    @Test
    public void testSafeguardWithHeaderOverride() {
        // Override safeguard settings via headers
        var exchange = template.request("direct:safeguardWithoutConfig", e -> {
            e.getIn().setBody("Tell me about API keys and secrets");
            e.getIn().setHeader(SpringAiChatConstants.SAFEGUARD_SENSITIVE_WORDS, "secret,api,key");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SAFEGUARD_FAILURE_RESPONSE,
                            "Custom blocked message: This topic is restricted");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).contains("Custom blocked message: This topic is restricted");
    }

    @Test
    public void testSafeguardWithCustomFailureResponse() {
        String response = template.requestBody("direct:safeguardCustom", "How do I reset my password?", String.class);

        assertThat(response).isNotNull();
        assertThat(response).contains("Security policy violation detected");
    }

    @Test
    public void testMultipleSensitiveWords() {
        // Test with one of multiple configured sensitive words
        String response1 = template.requestBody("direct:safeguard", "Tell me about confidential data", String.class);

        assertThat(response1).isNotNull();
        assertThat(response1).contains("I cannot provide information containing sensitive words");

        // Test with another sensitive word
        String response2 = template.requestBody("direct:safeguard", "What are the secret codes?", String.class);

        assertThat(response2).isNotNull();
        assertThat(response2).contains("I cannot provide information containing sensitive words");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                SpringAiChatComponent component = new SpringAiChatComponent();
                component.setChatModel(chatModel);
                context.addComponent("spring-ai-chat", component);

                // Route with safeguard configured on endpoint
                from("direct:safeguard")
                        .to("spring-ai-chat:test?safeguardSensitiveWords=password,secret,confidential"
                                + "&safeguardFailureResponse=I cannot provide information containing sensitive words");

                // Route without safeguard config - will use headers
                from("direct:safeguardWithoutConfig").to("spring-ai-chat:test");

                // Route with custom failure response
                from("direct:safeguardCustom")
                        .to("spring-ai-chat:test?safeguardSensitiveWords=password,secret"
                                + "&safeguardFailureResponse=Security policy violation detected");
            }
        };
    }
}
