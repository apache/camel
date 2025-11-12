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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating Spring AI's Structured Output Converter with Camel.
 *
 * This test shows how to convert LLM responses into typed Java objects, Maps, or Lists using Spring AI's
 * StructuredOutputConverter API.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatStructuredOutputIT extends OllamaTestSupport {

    @Test
    public void testBeanStructuredOutputWithHeaders() {
        String userMessage = "Generate filmography for Tom Hanks";

        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody(userMessage);
            e.getIn().setHeader(SpringAiChatConstants.OUTPUT_FORMAT, "BEAN");
            e.getIn().setHeader(SpringAiChatConstants.OUTPUT_CLASS, ActorFilms.class);
        });

        assertThat(exchange).isNotNull();
        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(ActorFilms.class);

        ActorFilms actorFilms = (ActorFilms) body;
        assertThat(actorFilms.actor()).isNotNull();
        assertThat(actorFilms.movies()).isNotNull().isNotEmpty();
    }

    @Test
    public void testBeanStructuredOutputWithConverter() {
        String userMessage = "Generate filmography for Steven Spielberg";

        ActorFilms result = template().requestBody("direct:bean-output", userMessage, ActorFilms.class);

        assertThat(result).isNotNull();
        assertThat(result.actor()).isNotNull();
        assertThat(result.movies()).isNotNull().isNotEmpty();
    }

    @Test
    public void testMapStructuredOutputWithHeaders() {
        String userMessage = "Tell me about the weather in San Francisco";

        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody(userMessage);
            e.getIn().setHeader(SpringAiChatConstants.OUTPUT_FORMAT, "MAP");
        });

        assertThat(exchange).isNotNull();
        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) body;
        assertThat(resultMap).isNotNull().isNotEmpty();
    }

    @Test
    public void testMapStructuredOutputWithConverter() {
        String userMessage = "Provide information about Paris";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = template().requestBody("direct:map-output", userMessage, Map.class);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    public void testListStructuredOutputWithHeaders() {
        String userMessage = "List 5 popular programming languages";

        var exchange = template().request("direct:chat", e -> {
            e.getIn().setBody(userMessage);
            e.getIn().setHeader(SpringAiChatConstants.OUTPUT_FORMAT, "LIST");
        });

        assertThat(exchange).isNotNull();
        Object body = exchange.getMessage().getBody();
        assertThat(body).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>) body;
        assertThat(resultList).isNotNull().isNotEmpty();
    }

    @Test
    public void testListStructuredOutputWithConverter() {
        String userMessage = "List 3 European capital cities";

        @SuppressWarnings("unchecked")
        List<String> result = template().requestBody("direct:list-output", userMessage, List.class);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    public void testStructuredOutputAccessRawResponse() {
        String userMessage = "Generate filmography for Meryl Streep";

        var exchange = template().request("direct:bean-output", e -> {
            e.getIn().setBody(userMessage);
        });

        assertThat(exchange).isNotNull();

        // Get structured output from body
        ActorFilms structured = exchange.getMessage().getBody(ActorFilms.class);
        assertThat(structured).isNotNull();
        assertThat(structured.actor()).isNotNull();
        assertThat(structured.movies()).isNotNull().isNotEmpty();

        // Get raw response from header
        String rawResponse = exchange.getMessage().getHeader(SpringAiChatConstants.CHAT_RESPONSE, String.class);
        assertThat(rawResponse).isNotNull().isNotEmpty();

        // Get structured output from header as well
        ActorFilms alsoStructured
                = exchange.getMessage().getHeader(SpringAiChatConstants.STRUCTURED_OUTPUT, ActorFilms.class);
        assertThat(alsoStructured).isNotNull().isEqualTo(structured);
    }

    @Test
    public void testBeanOutputWithEndpointConfiguration() {
        String userMessage = "Generate filmography for Christopher Nolan";

        ActorFilms result = template().requestBody("direct:bean-output-via-config", userMessage, ActorFilms.class);

        assertThat(result).isNotNull();
        assertThat(result.actor()).isNotNull();
        assertThat(result.movies()).isNotNull().isNotEmpty();
    }

    @Test
    public void testMapOutputWithEndpointConfiguration() {
        String userMessage = "Tell me about Rome";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = template().requestBody("direct:map-output-via-config", userMessage, Map.class);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    public void testListOutputWithEndpointConfiguration() {
        String userMessage = "List 3 colors";

        @SuppressWarnings("unchecked")
        List<String> result = template().requestBody("direct:list-output-via-config", userMessage, List.class);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(this.getCamelContext());

                // Create and register converters
                BeanOutputConverter<ActorFilms> beanConverter = new BeanOutputConverter<>(ActorFilms.class);
                MapOutputConverter mapConverter = new MapOutputConverter();
                ListOutputConverter listConverter = new ListOutputConverter(new DefaultConversionService());

                this.getCamelContext().getRegistry().bind("beanConverter", beanConverter);
                this.getCamelContext().getRegistry().bind("mapConverter", mapConverter);
                this.getCamelContext().getRegistry().bind("listConverter", listConverter);

                // Simple route for header-based conversion
                from("direct:chat")
                        .to("spring-ai-chat:test?chatModel=#chatModel");

                // Route with pre-configured bean converter
                from("direct:bean-output")
                        .to("spring-ai-chat:test?chatModel=#chatModel&structuredOutputConverter=#beanConverter");

                // Route with pre-configured map converter
                from("direct:map-output")
                        .to("spring-ai-chat:test?chatModel=#chatModel&structuredOutputConverter=#mapConverter");

                // Route with pre-configured list converter
                from("direct:list-output")
                        .to("spring-ai-chat:test?chatModel=#chatModel&structuredOutputConverter=#listConverter");

                // Routes using endpoint configuration for outputFormat and outputClass
                from("direct:bean-output-via-config")
                        .to("spring-ai-chat:test?chatModel=#chatModel&outputFormat=BEAN&outputClass="
                            + ActorFilms.class.getName());

                from("direct:map-output-via-config")
                        .to("spring-ai-chat:test?chatModel=#chatModel&outputFormat=MAP");

                from("direct:list-output-via-config")
                        .to("spring-ai-chat:test?chatModel=#chatModel&outputFormat=LIST");
            }
        };
    }

    @JsonPropertyOrder({ "actor", "movies" })
    public record ActorFilms(String actor, List<String> movies) {
    }
}
