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

import java.util.ArrayList;
import java.util.List;

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
                    assertThat(collectBuiltinToolTypesInRequest(request))
                            .contains("web_search", "file_search");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .replyWith("Research done")
            .end()
            .when("hosted-mcp")
            .replyWith("Docs found")
            .end()
            .when("approval-needed")
            .replyWithResponsesOutput("""
                    [{"type":"mcp_approval_request","id":"mcpr_1","server_label":"deepwiki",
                      "name":"ask_question","arguments":"{}"}]""")
            .end()
            .when("citations")
            .replyWithResponsesOutput("""
                    [{"type":"message","id":"msg_1","role":"assistant","status":"completed",
                      "content":[{"type":"output_text","text":"Apache Camel is an integration framework.",
                        "annotations":[{"type":"url_citation","url":"https://camel.apache.org",
                          "title":"Apache Camel","start_index":0,"end_index":12}]}]}]""")
            .end()
            .when("Summarize this document")
            .replyWith("A short report")
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
    void hostedMcpToolsSendEveryToolField() {
        OpenAIEndpoint endpoint = context.getEndpoint(responsesUri(), OpenAIEndpoint.class);
        endpoint.getConfiguration().setHostedMcpTools("""
                [{"server_label":"deepwiki","server_url":"https://mcp.deepwiki.com/mcp",
                  "require_approval":"never","allowed_tools":["ask_question"],
                  "headers":{"X-Api-Key":"secret"}}]""");

        Exchange result = template.request(endpoint, e -> e.getIn().setBody("hosted-mcp"));

        assertThat(result.getException()).isNull();
        JsonNode tool = openAIMock.getLastRequest().bodyAsJson().path("tools").path(0);
        assertThat(tool.path("type").asText()).isEqualTo("mcp");
        assertThat(tool.path("server_label").asText()).isEqualTo("deepwiki");
        assertThat(tool.path("server_url").asText()).isEqualTo("https://mcp.deepwiki.com/mcp");
        assertThat(tool.path("require_approval").asText()).isEqualTo("never");
        assertThat(tool.path("allowed_tools").toString()).isEqualTo("[\"ask_question\"]");
        assertThat(tool.path("headers").path("X-Api-Key").asText()).isEqualTo("secret");
    }

    @Test
    void hostedMcpToolsAcceptCamelCaseServerFields() {
        OpenAIEndpoint endpoint = context.getEndpoint(responsesUri(), OpenAIEndpoint.class);
        endpoint.getConfiguration().setHostedMcpTools("""
                [{"serverLabel":"deepwiki","serverUrl":"https://mcp.deepwiki.com/mcp","serverDescription":"Docs"}]""");

        Exchange result = template.request(endpoint, e -> e.getIn().setBody("hosted-mcp"));

        assertThat(result.getException()).isNull();
        JsonNode tool = openAIMock.getLastRequest().bodyAsJson().path("tools").path(0);
        assertThat(tool.path("server_label").asText()).isEqualTo("deepwiki");
        assertThat(tool.path("server_url").asText()).isEqualTo("https://mcp.deepwiki.com/mcp");
        assertThat(tool.path("server_description").asText()).isEqualTo("Docs");
    }

    @Test
    void pendingHostedMcpApprovalFailsTheExchange() {
        Exchange result = template.request("direct:responses-basic", e -> e.getIn().setBody("approval-needed"));

        assertThat(result.getException())
                .hasMessageContaining("deepwiki/ask_question")
                .hasMessageContaining("require_approval");
    }

    @Test
    void developerMessageIsSentBeforeTheUserInput() {
        Exchange result = template.request("direct:responses-basic", e -> {
            e.getIn().setBody("hello-responses");
            e.getIn().setHeader(OpenAIConstants.DEVELOPER_MESSAGE, "Answer in French");
        });

        assertThat(result.getException()).isNull();
        JsonNode input = openAIMock.getLastRequest().bodyAsJson().path("input");
        assertThat(input.path(0).path("role").asText()).isEqualTo("developer");
        assertThat(input.path(0).path("content").asText()).isEqualTo("Answer in French");
        assertThat(input.path(1).path("role").asText()).isEqualTo("user");
        assertThat(input.path(1).path("content").asText()).isEqualTo("hello-responses");
    }

    @Test
    void outputTextAnnotationsAreExposed() {
        Exchange result = template.request("direct:responses-basic", e -> e.getIn().setBody("citations"));

        assertThat(result.getException()).isNull();
        List<?> annotations = result.getMessage().getHeader(OpenAIConstants.RESPONSE_ANNOTATIONS, List.class);
        assertThat(annotations).singleElement().asString()
                .contains("type=url_citation")
                .contains("url=https://camel.apache.org");
    }

    @Test
    void pdfBodyIsSentAsInputFile() {
        byte[] pdf = { '%', 'P', 'D', 'F', '-', '1', '.', '4' };

        Exchange result = template.request("direct:responses-basic", e -> {
            e.getIn().setBody(pdf);
            e.getIn().setHeader(OpenAIConstants.MEDIA_TYPE, "application/pdf");
            e.getIn().setHeader(Exchange.FILE_NAME, "report.pdf");
            e.getIn().setHeader(OpenAIConstants.USER_MESSAGE, "Summarize this document");
        });

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("A short report");
        JsonNode file = openAIMock.getLastRequest().bodyAsJson().path("input").path(0).path("content").path(1);
        assertThat(file.path("type").asText()).isEqualTo("input_file");
        assertThat(file.path("filename").asText()).isEqualTo("report.pdf");
        assertThat(file.path("file_data").asText()).startsWith("data:application/pdf;base64,");
    }

    private String responsesUri() {
        return "openai:responses?model=gpt-5&apiKey=dummy&baseUrl=" + openAIMock.getBaseUrl() + "/v1";
    }

    @Test
    void basicResponsesReturnsMockedTextAndHeaders() {
        Exchange result = template.request("direct:responses-basic", e -> e.getIn().setBody("hello-responses"));
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Hi from responses mock");
        assertThat(result.getMessage().getHeader(OpenAIConstants.RESPONSE_ID, String.class)).startsWith("resp_");
        assertThat(result.getMessage().getHeader(OpenAIConstants.RESPONSE_MODEL, String.class)).isEqualTo("openai-mock");
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

    private static List<String> collectBuiltinToolTypesInRequest(String requestBody) throws Exception {
        List<String> types = new ArrayList<>();
        JsonNode root = OBJECT_MAPPER.readTree(requestBody);
        JsonNode tools = root.get("tools");
        if (tools != null && tools.isArray()) {
            for (JsonNode tool : tools) {
                if (tool.has("type")) {
                    types.add(tool.get("type").asText());
                }
            }
        }
        return types;
    }
}
