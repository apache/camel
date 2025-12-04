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
 * Integration test for system message functionality.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatSystemMessageIT extends OllamaTestSupport {

    @Test
    public void testSystemMessageInfluencesResponse() {
        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("What should I do today?");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are a fitness coach. Always recommend physical activities.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("exercise", "workout", "run", "walk", "activity", "physical");
    }

    @Test
    public void testSystemMessageWithSpecificFormat() {
        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("What is 5 + 3?");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are a math teacher. Always explain your answer step by step.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response).contains("8");
    }

    @Test
    public void testSystemMessageAsExpert() {
        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody("Tell me about integration patterns in one sentence.");
            e.getIn()
                    .setHeader(
                            SpringAiChatConstants.SYSTEM_MESSAGE,
                            "You are an expert in Enterprise Integration Patterns and Apache Camel.");
        });

        String response = exchange.getMessage().getBody(String.class);
        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("integration", "pattern", "system", "message");
    }

    @Test
    public void testWithoutSystemMessage() {
        String response = template().requestBody("direct:chat", "Say hello in one word.", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("hello", "hi");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                from("direct:chat").to("spring-ai-chat:test?chatModel=#chatModel");
            }
        };
    }
}
