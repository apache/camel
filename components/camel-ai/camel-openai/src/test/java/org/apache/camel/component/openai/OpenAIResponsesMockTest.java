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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAIResponsesMockTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("hello-responses")
            .replyWith("Hi from responses mock")
            .end()
            .when("turn-two")
            .replyWith("Second turn answer")
            .end()
            .when("json-responses")
            .replyWith("{\"ok\":true}")
            .end()
            .when("assert-previous-id")
            .assertRequest(request -> {
                try {
                    JsonNode root = OBJECT_MAPPER.readTree(request);
                    assertThat(root.get("previous_response_id").asText()).isEqualTo("resp_prev_123");
                    assertThat(root.get("instructions").asText()).isEqualTo("You are helpful");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .replyWith("Acknowledged")
            .end()
            .when("tools-request")
            .assertRequest(request -> {
                try {
                    assertThat(OpenAIResponsesSupport.collectBuiltinToolTypesInRequest(request))
                            .contains("web_search", "file_search");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .replyWith("Research done")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String base = openAIMock.getBaseUrl() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:responses-basic")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&baseUrl=" + base);

                from("direct:responses-system")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&systemMessage=You are helpful&baseUrl=" + base);

                from("direct:responses-previous")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&systemMessage=You are helpful&baseUrl=" + base);

                from("direct:responses-json")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&baseUrl=" + base);

                from("direct:responses-tools")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&builtinTools=web_search,file_search"
                            + "&fileSearchVectorStoreIds=vs_mock&baseUrl=" + base);

                from("direct:responses-store")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&storeFullResponse=true&baseUrl=" + base);

                from("direct:responses-streaming")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&streaming=true&baseUrl=" + base);
            }
        };
    }

    @Test
    void basicResponsesReturnsMockedTextAndHeaders() {
        Exchange result = template.request("direct:responses-basic", e -> e.getIn().setBody("hello-responses"));
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Hi from responses mock");
        assertThat(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID, String.class)).startsWith("resp_");
        assertThat(result.getMessage().getHeader(OpenAIConstants.PROMPT_TOKENS, Long.class)).isEqualTo(10L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.COMPLETION_TOKENS, Long.class)).isEqualTo(5L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.TOTAL_TOKENS, Long.class)).isEqualTo(15L);
    }

    @Test
    void instructionsFromSystemMessageAreSent() {
        Exchange result = template.request("direct:responses-previous", e -> {
            e.getIn().setBody("assert-previous-id");
            e.getIn().setHeader(OpenAIConstants.PREVIOUS_RESPONSE_ID, "resp_prev_123");
        });
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Acknowledged");
    }

    @Test
    void jsonSchemaStructuredOutputReturnsJsonBody() {
        Exchange result = template.request("direct:responses-json", e -> {
            e.getIn().setBody("json-responses");
            e.getIn().setHeader(OpenAIConstants.JSON_SCHEMA,
                    "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"boolean\"}}}");
        });
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void builtinToolsAreIncludedInRequest() {
        Exchange result = template.request("direct:responses-tools", e -> e.getIn().setBody("tools-request"));
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Research done");
    }

    @Test
    void storeFullResponseStoresResponsesObject() {
        Exchange result = template.request("direct:responses-store", e -> e.getIn().setBody("hello-responses"));
        assertThat(result.getException()).isNull();
        assertThat(result.getProperty(OpenAIConstants.RESPONSES_RESPONSE, Response.class)).isNotNull();
    }

    @Test
    void streamingIsRejectedForResponsesOperation() {
        Exchange result = template.request("direct:responses-streaming", e -> e.getIn().setBody("hello-responses"));
        assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Streaming is not supported");
    }

    @Test
    void hostedMcpToolsInvalidJsonFailsFast() {
        assertThatThrownBy(() -> OpenAIResponsesSupport.applyHostedMcpTools(
                ResponseCreateParams.builder(), "not-json"))
                .isInstanceOf(Exception.class);
    }
}
