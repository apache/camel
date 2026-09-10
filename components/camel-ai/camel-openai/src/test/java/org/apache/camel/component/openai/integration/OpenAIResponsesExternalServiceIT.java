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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.responses.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiErrorProperties;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.component.openai.OpenAIConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = OpenAIExternalServiceTestSupport.ENABLE_LIVE_TESTS, matches = "true",
                         disabledReason = "Set -Dopenai.live.tests=true and configure an OpenAI-compatible Responses API service")
public class OpenAIResponsesExternalServiceIT extends OpenAIExternalServiceTestSupport {

    private static final String RESPONSES_MODEL = "openai.live.responses.model";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record CityInfo(String city, String country) {
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getComponent("openai", OpenAIComponent.class).setModel(requiredProperty(RESPONSES_MODEL));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:basic")
                        .to("openai:responses?temperature=0");

                from("direct:instructions")
                        .to("openai:responses?temperature=0"
                            + "&systemMessage=Reply only with the single word BANANA no matter what the user says");

                from("direct:store")
                        .to("openai:responses?temperature=0&storeFullResponse=true");

                from("direct:max-tokens")
                        .to("openai:responses?temperature=0&maxTokens=5");

                from("direct:developer")
                        .to("openai:responses?temperature=0"
                            + "&developerMessage=Reply only with the single word BANANA no matter what the user says");

                from("direct:memory")
                        .to("openai:responses?temperature=0&conversationMemory=true")
                        .setBody(constant("What is my name? Answer with one word."))
                        .to("openai:responses?temperature=0&conversationMemory=true");

                from("ai-tool:get_weather?tags=responses-it"
                     + "&description=Get the current weather for a city"
                     + "&parameter.city=string&parameter.city.required=true")
                        .setBody(simple("It is 31 degrees and sunny in ${header.city}"));

                from("direct:route-tools")
                        .to("openai:responses?temperature=0&tags=responses-it");
            }
        };
    }

    @Test
    void routeToolsAreCalledByTheModel() {
        Exchange result = template.request("direct:route-tools",
                e -> e.getIn().setBody("What is the weather in Rome right now? Use the available tool."));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.TOOL_ITERATIONS, Integer.class)).isPositive();
        assertThat(result.getMessage().getBody(String.class)).contains("31");
    }

    @Test
    void conversationMemoryChainsResponsesInTheExchange() {
        Exchange result = template.request("direct:memory",
                e -> e.getIn().setBody("My name is Federico. Reply with just: ok"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).contains("Federico");
    }

    @Test
    void developerMessageIsSent() {
        Exchange result = template.request("direct:developer", e -> e.getIn().setBody("Tell me about Apache Camel."));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).contains("BANANA");
    }

    @Test
    void textInputReturnsAssistantTextAndHeaders() {
        Exchange result = template.request("direct:basic",
                e -> e.getIn().setBody("What is the capital of Italy? Answer with one word."));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).containsIgnoringCase("Rome");
        assertThat(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID, String.class)).isNotBlank();
        assertThat(result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL, String.class))
                .isEqualTo(System.getProperty(RESPONSES_MODEL));
        assertThat(result.getMessage().getHeader(OpenAIConstants.FINISH_REASON, String.class)).isEqualTo("stop");

        long prompt = result.getMessage().getHeader(OpenAIConstants.PROMPT_TOKENS, Long.class);
        long completion = result.getMessage().getHeader(OpenAIConstants.COMPLETION_TOKENS, Long.class);
        assertThat(prompt).isPositive();
        assertThat(completion).isPositive();
        assertThat(result.getMessage().getHeader(OpenAIConstants.TOTAL_TOKENS, Long.class)).isEqualTo(prompt + completion);
    }

    @Test
    void systemMessageIsSentAsInstructions() {
        Exchange result = template.request("direct:instructions", e -> e.getIn().setBody("Tell me about Apache Camel."));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).contains("BANANA");
    }

    @Test
    void previousResponseIdContinuesServerSideConversation() {
        Exchange first = template.request("direct:basic",
                e -> e.getIn().setBody("My name is Federico. Reply with just: ok"));
        assertThat(first.getException()).isNull();
        String firstId = first.getMessage().getHeader(OpenAIConstants.RESPONSE_ID, String.class);

        Exchange second = template.request("direct:basic", e -> {
            e.getIn().setBody("What is my name? Answer with one word.");
            e.getIn().setHeader(OpenAIConstants.PREVIOUS_RESPONSE_ID, firstId);
        });

        assertThat(second.getException()).isNull();
        assertThat(second.getMessage().getBody(String.class)).contains("Federico");
    }

    @Test
    void jsonSchemaProducesStructuredJson() throws Exception {
        Exchange result = template.request("direct:basic", e -> {
            e.getIn().setBody("What is the capital of Italy?");
            e.getIn().setHeader(OpenAIConstants.JSON_SCHEMA,
                    "{\"type\":\"object\",\"properties\":{\"capital\":{\"type\":\"string\"}},"
                                                             + "\"required\":[\"capital\"],\"additionalProperties\":false}");
        });

        assertThat(result.getException()).isNull();
        JsonNode json = MAPPER.readTree(result.getMessage().getBody(String.class));
        assertThat(json.path("capital").asText()).containsIgnoringCase("Rome");
    }

    @Test
    void outputClassProducesJsonMatchingTheClass() throws Exception {
        Exchange result = template.request("direct:basic", e -> {
            e.getIn().setBody("In which city is the Colosseum, and in which country is that city?");
            e.getIn().setHeader(OpenAIConstants.OUTPUT_CLASS, CityInfo.class.getName());
        });

        assertThat(result.getException()).isNull();
        CityInfo info = MAPPER.readValue(result.getMessage().getBody(String.class), CityInfo.class);
        assertThat(info.city()).containsIgnoringCase("Rome");
        assertThat(info.country()).containsIgnoringCase("Italy");
    }

    @Test
    void storeFullResponseKeepsTheSdkResponse() {
        Exchange result = template.request("direct:store", e -> e.getIn().setBody("Say hello."));

        assertThat(result.getException()).isNull();
        Response response = result.getProperty(OpenAIConstants.RESPONSES_RESPONSE, Response.class);
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID, String.class));
    }

    @Test
    void maxTokensLimitsOutputTokens() {
        Exchange result = template.request("direct:max-tokens",
                e -> e.getIn().setBody("Write a long essay about enterprise integration patterns."));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getHeader(OpenAIConstants.COMPLETION_TOKENS, Long.class)).isLessThanOrEqualTo(5L);
    }

    @Test
    void apiErrorSetsStructuredErrorMetadata() {
        Exchange result = template.request("direct:basic", e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(OpenAIConstants.MODEL, "no-such-model");
        });

        assertThat(result.getException()).isNotNull();
        assertThat(result.getProperty(GenAiErrorProperties.ERROR_CATEGORY, String.class)).isNotNull();
    }
}
