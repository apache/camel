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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test for Spring AI Chat prompt template operation.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatPromptTemplateIT extends OllamaTestSupport {

    @Test
    public void testPromptTemplateWithVariables() {
        String template = "What is the capital of {country}? Answer in one word.";
        Map<String, Object> variables = new HashMap<>();
        variables.put("country", "France");

        var exchange = template().request("direct:prompt", e -> {
            e.getIn().setBody(variables);
            e.getIn().setHeader(SpringAiChatConstants.PROMPT_TEMPLATE, template);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        assertThat(response.toLowerCase()).contains("paris");
    }

    @Test
    public void testPromptTemplateWithMultipleVariables() {
        String template = "Write a {length} sentence about {subject} in {language}.";
        Map<String, Object> variables = new HashMap<>();
        variables.put("length", "short");
        variables.put("subject", "technology");
        variables.put("language", "English");

        var exchange = template().request("direct:prompt", e -> {
            e.getIn().setBody(variables);
            e.getIn().setHeader(SpringAiChatConstants.PROMPT_TEMPLATE, template);
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();

        // Verify token usage is tracked
        Integer totalTokens = exchange.getMessage().getHeader(SpringAiChatConstants.TOTAL_TOKEN_COUNT, Integer.class);
        assertThat(totalTokens).isNotNull().isGreaterThan(0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                from("direct:prompt")
                        .to("spring-ai-chat:prompt?chatModel=#chatModel&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT");
            }
        };
    }
}
