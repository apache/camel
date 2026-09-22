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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the batch operations against the mock: uploading the input file, polling the batch, cancelling it, and reading
 * the output and error files back.
 */
public class OpenAIBatchMockTest extends CamelTestSupport {

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            .whenBatchRequest("ticket-1").replyWithBatchContent("billing").end()
            .whenBatchRequest("ticket-2").replyWithBatchContent("bug").end()
            .whenBatchRequest("ticket-3")
            .replyWithBatchError(429, "rate_limit_exceeded", "Rate limit reached").end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:create")
                        .to("openai:batch?batchEndpoint=/v1/chat/completions&model=gpt-4o-mini"
                            + "&systemMessage=Classify the ticket&batchMetadata.job=ticket-triage");
                from("direct:createInvalidEndpoint")
                        .setHeader(OpenAIConstants.BATCH_ENDPOINT, constant("/v1/unsupported"))
                        .to("openai:batch");
                from("direct:createChatWithOptions")
                        .to("openai:batch?batchEndpoint=/v1/chat/completions&model=o3-mini&maxTokens=50"
                            + "&temperature=0&developerMessage=Be terse&additionalBodyProperty.reasoning_effort=low");
                from("direct:createStructured")
                        .to("openai:batch?batchEndpoint=/v1/chat/completions&model=gpt-4o-mini"
                            + "&outputClass=org.apache.camel.component.openai.OpenAIBatchMockTest$Ticket");
                from("direct:createResponses")
                        .to("openai:batch?batchEndpoint=/v1/responses&model=gpt-4o-mini&maxTokens=50&temperature=0"
                            + "&systemMessage=Classify the ticket&developerMessage=Be terse");
                from("direct:createEmbeddingsAfterChat")
                        .to("openai:batch?batchEndpoint=/v1/chat/completions&model=gpt-4o-mini")
                        .setBody(constant(Map.of("doc-1", "Camel routes messages")))
                        .to("openai:batch?batchEndpoint=/v1/embeddings&embeddingModel=text-embedding-3-small");
                from("direct:retrieve")
                        .to("openai:batch-retrieve");
                from("direct:cancel")
                        .to("openai:batch-cancel");
                from("direct:results")
                        .to("openai:batch-results")
                        .split(body()).streaming()
                        .to("mock:results")
                        .end();
                from("direct:errors")
                        .to("openai:batch-results?batchResultsFile=error")
                        .split(body()).streaming()
                        .to("mock:errors")
                        .end();
                from("direct:aggregate")
                        .aggregate(constant(true), new OpenAIBatchAggregationStrategy()).completionSize(2)
                        .to("openai:batch?batchEndpoint=/v1/chat/completions&model=gpt-4o-mini")
                        .to("mock:aggregated");
                from("direct:aggregateSpooled")
                        .aggregate(constant(true), new OpenAIBatchAggregationStrategy(spoolDirectory))
                        .completionSize(2)
                        .to("openai:batch?batchEndpoint=/v1/chat/completions&model=gpt-4o-mini")
                        .to("mock:aggregated");
            }
        };
    }

    @TempDir
    static Path spoolDirectory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("openai", mockComponent());
        return context;
    }

    private static OpenAIComponent mockComponent() {
        OpenAIComponent component = new OpenAIComponent();
        component.setBaseUrl(openAIMock.getBaseUrl());
        component.setApiKey("dummy");
        return component;
    }

    public record Ticket(String category) {
    }

    @Test
    void shouldUploadPromptsAsJsonlAndCreateTheBatch() {
        Map<String, Object> prompts = new LinkedHashMap<>();
        prompts.put("ticket-1", "I was charged twice");
        prompts.put("ticket-2", "The app crashes on startup");

        Exchange exchange = template.request("direct:create", e -> e.getIn().setBody(prompts));

        assertThat(exchange.getMessage().getHeader(OpenAIConstants.BATCH_ID, String.class)).isNotBlank();
        assertThat(exchange.getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("validating");
        assertThat(exchange.getMessage().getHeader(OpenAIConstants.BATCH_INPUT_FILE_ID, String.class)).isNotBlank();

        // the component writes the envelope of every line, and builds the request from the endpoint options
        assertThat(openAIMock.getBatchStore().getUploadedFile().lines())
                .hasSize(2)
                .anySatisfy(line -> assertThat(line)
                        .contains("\"custom_id\":\"ticket-1\"")
                        .contains("\"method\":\"POST\"")
                        .contains("\"url\":\"/v1/chat/completions\"")
                        .contains("\"model\":\"gpt-4o-mini\"")
                        .contains("\"content\":\"Classify the ticket\"")
                        .contains("\"content\":\"I was charged twice\""));

        assertThat(openAIMock.getLastRequest().body())
                .contains("\"completion_window\":\"24h\"")
                .contains("\"endpoint\":\"/v1/chat/completions\"")
                .contains("\"job\":\"ticket-triage\"");
    }

    @Test
    void shouldUploadRawJsonlBodyUnchanged() {
        String jsonl = "{\"custom_id\":\"ticket-1\",\"method\":\"POST\",\"url\":\"/v1/chat/completions\","
                       + "\"body\":{\"model\":\"gpt-4o-mini\",\"messages\":[]}}";

        template.requestBody("direct:create", jsonl);

        assertThat(openAIMock.getBatchStore().getUploadedFile().trim()).isEqualTo(jsonl);
    }

    @Test
    void shouldBuildChatLinesLikeTheChatCompletionOperation() {
        Map<String, Object> prompts = Map.of("ticket-1", "I was charged twice");

        template.request("direct:createChatWithOptions", e -> {
            e.getIn().setBody(prompts);
            e.getIn().setHeader(OpenAIConstants.JSON_SCHEMA,
                    "{\"type\":\"object\",\"properties\":{\"category\":{\"type\":\"string\"}}}");
        });

        // the line carries what the synchronous operation sends: the current token field, the developer message,
        // the additional body properties and the schema as given, not a strict variant of it
        assertThat(openAIMock.getBatchStore().getUploadedFile().trim())
                .contains("\"max_completion_tokens\":50")
                .doesNotContain("max_tokens\"")
                .contains("\"temperature\":0.0")
                .contains("\"content\":\"Be terse\",\"role\":\"developer\"")
                .contains("\"reasoning_effort\":\"low\"")
                .contains("\"type\":\"json_schema\"")
                .contains("\"name\":\"camel_schema\"")
                .contains("\"properties\":{\"category\":{\"type\":\"string\"}}")
                .doesNotContain("\"strict\":true");
    }

    @Test
    void shouldBuildChatLinesFromTheOutputClass() {
        template.requestBody("direct:createStructured", Map.of("ticket-1", "I was charged twice"));

        assertThat(openAIMock.getBatchStore().getUploadedFile().trim())
                .contains("\"response_format\":{")
                .contains("\"type\":\"json_schema\"")
                .contains("\"category\":{\"type\":\"string\"");
    }

    @Test
    void shouldBuildResponsesLinesLikeTheResponsesOperation() {
        template.requestBody("direct:createResponses", Map.of("ticket-1", "I was charged twice"));

        assertThat(openAIMock.getBatchStore().getUploadedFile().trim())
                .contains("\"url\":\"/v1/responses\"")
                .contains("\"instructions\":\"Classify the ticket\"")
                .contains("\"content\":\"Be terse\",\"role\":\"developer\"")
                .contains("\"content\":\"I was charged twice\",\"role\":\"user\"")
                .contains("\"max_output_tokens\":50")
                .contains("\"temperature\":0.0");
    }

    @Test
    void shouldUploadAFileUnderAJsonlName(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("requests.txt"),
                "{\"custom_id\":\"ticket-1\",\"method\":\"POST\",\"url\":\"/v1/chat/completions\",\"body\":{}}");

        template.requestBody("direct:create", file.toFile());

        // the Files API only accepts .jsonl for a batch input, whatever the file is called on disk
        assertThat(openAIMock.getBatchStore().getUploadedFileName()).isEqualTo("requests.txt.jsonl");
    }

    @Test
    void shouldNotReportTheEndpointOfTheBatchAsAHeader() {
        Exchange exchange = template.request("direct:createEmbeddingsAfterChat",
                e -> e.getIn().setBody(Map.of("ticket-1", "I was charged twice")));

        // the endpoint header is the per-message override of batchEndpoint, so it must not be fed from one batch
        // into the next: the second batch of the route targets its own endpoint
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(OpenAIConstants.BATCH_ENDPOINT)).isNull();
        assertThat(openAIMock.getLastRequest().body()).contains("\"endpoint\":\"/v1/embeddings\"");
    }

    @Test
    void shouldRejectAnUnsupportedEndpointBeforeUploading() {
        assertThatThrownBy(() -> template.requestBody("direct:createInvalidEndpoint", Map.of("a", "b")))
                .isInstanceOf(CamelExecutionException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported batch endpoint: /v1/unsupported");
    }

    @Test
    void shouldRejectUnsupportedOptionsWhenTheEndpointStarts() throws Exception {
        try (CamelContext other = new DefaultCamelContext()) {
            other.addComponent("openai", mockComponent());
            other.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:memory")
                            .to("openai:batch?batchEndpoint=/v1/chat/completions&conversationMemory=true");
                }
            });

            assertThatThrownBy(other::start)
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conversationMemory is not supported");
        }
    }

    @Test
    void shouldRejectAnUnsupportedBody() {
        assertThatThrownBy(() -> template.requestBody("direct:create", new Object()))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported body type");
    }

    @Test
    void shouldRejectANullValueBeforeUploading() {
        Map<String, Object> prompts = new LinkedHashMap<>();
        prompts.put("ticket-1", null);

        assertThatThrownBy(() -> template.requestBody("direct:create", prompts))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null custom_ids or values");
        assertThat(openAIMock.getReceivedRequests()).isEmpty();
    }

    @Test
    void shouldNumberTheLinesOfAListBody() {
        template.requestBody("direct:create", List.of("I was charged twice", "The app crashes on startup"));

        assertThat(openAIMock.getBatchStore().getUploadedFile().lines())
                .hasSize(2)
                .anySatisfy(line -> assertThat(line).contains("\"custom_id\":\"0\""))
                .anySatisfy(line -> assertThat(line).contains("\"custom_id\":\"1\""));
    }

    @Test
    void shouldAggregateMessagesIntoABatch() throws Exception {
        MockEndpoint aggregated = getMockEndpoint("mock:aggregated");
        aggregated.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:aggregate", "I was charged twice", OpenAIConstants.BATCH_CUSTOM_ID,
                "ticket-1");
        template.sendBody("direct:aggregate", "The app crashes on startup");

        aggregated.assertIsSatisfied();
        Exchange batch = aggregated.getExchanges().get(0);
        assertThat(batch.getMessage().getHeader(OpenAIConstants.BATCH_ID, String.class)).isNotBlank();
        // the custom_id is the header when set, and the message id otherwise
        assertThat(openAIMock.getBatchStore().getUploadedFile().lines())
                .hasSize(2)
                .anySatisfy(line -> assertThat(line)
                        .contains("\"custom_id\":\"ticket-1\"")
                        .contains("\"content\":\"I was charged twice\""))
                .anySatisfy(line -> assertThat(line)
                        .contains("\"content\":\"The app crashes on startup\"")
                        .matches(".*\"custom_id\":\"[0-9A-F]+-[0-9]+\".*"));
    }

    @Test
    void shouldAggregateMessagesIntoASpooledBatch() throws Exception {
        MockEndpoint aggregated = getMockEndpoint("mock:aggregated");
        aggregated.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:aggregateSpooled", "I was charged twice", OpenAIConstants.BATCH_CUSTOM_ID,
                "ticket-1");
        template.sendBodyAndHeader("direct:aggregateSpooled", Map.of("model", "o3-mini", "messages", List.of()),
                OpenAIConstants.BATCH_CUSTOM_ID, "ticket-2");

        aggregated.assertIsSatisfied();
        assertThat(openAIMock.getBatchStore().getUploadedFile().lines())
                .hasSize(2)
                .anySatisfy(line -> assertThat(line)
                        .contains("\"custom_id\":\"ticket-1\"")
                        .contains("\"content\":\"I was charged twice\""))
                .anySatisfy(line -> assertThat(line)
                        .contains("\"custom_id\":\"ticket-2\"")
                        .contains("\"model\":\"o3-mini\""));
        // the spool is consumed by the batch, so nothing is left in the directory
        try (Stream<Path> files = Files.list(spoolDirectory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldReportStatusAndCountsWhilePolling() {
        String batchId = createBatch();

        // the mock advances one step of validating, in_progress, finalizing, completed per retrieve
        assertThat(retrieve(batchId).getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("in_progress");
        assertThat(retrieve(batchId).getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("finalizing");

        Exchange completed = retrieve(batchId);
        assertThat(completed.getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("completed");
        assertThat(completed.getMessage().getHeader(OpenAIConstants.BATCH_REQUEST_COUNT_TOTAL)).isEqualTo(3L);
        assertThat(completed.getMessage().getHeader(OpenAIConstants.BATCH_REQUEST_COUNT_COMPLETED)).isEqualTo(2L);
        assertThat(completed.getMessage().getHeader(OpenAIConstants.BATCH_REQUEST_COUNT_FAILED)).isEqualTo(1L);
        assertThat(completed.getMessage().getHeader(OpenAIConstants.BATCH_OUTPUT_FILE_ID, String.class)).isNotBlank();
        assertThat(completed.getMessage().getHeader(OpenAIConstants.BATCH_ERROR_FILE_ID, String.class)).isNotBlank();
    }

    @Test
    void shouldCancelABatch() {
        String batchId = createBatch();

        Exchange cancelling = template.request("direct:cancel",
                e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        assertThat(cancelling.getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("cancelling");
        assertThat(retrieve(batchId).getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("cancelled");
    }

    @Test
    void shouldStreamTheOutputFileAndCorrelateByCustomId() throws Exception {
        String batchId = completedBatch();
        MockEndpoint results = getMockEndpoint("mock:results");
        results.expectedMessageCount(2);

        template.request("direct:results", e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        results.assertIsSatisfied();
        // each line is a map, so the route reads it with simple or jsonpath without parsing anything
        List<Map<String, Object>> lines = results.getExchanges().stream()
                .map(e -> e.getMessage().getBody(Map.class))
                .map(m -> (Map<String, Object>) m)
                .toList();
        assertThat(lines.get(0)).containsEntry("custom_id", "ticket-1");
        assertThat(lines.get(0).toString()).contains("billing");
        assertThat(lines.get(1)).containsEntry("custom_id", "ticket-2");
        assertThat(lines.get(1).toString()).contains("bug");
    }

    @Test
    void shouldReadTheErrorFileSeparately() throws Exception {
        String batchId = completedBatch();
        MockEndpoint errors = getMockEndpoint("mock:errors");
        errors.expectedMessageCount(1);

        template.request("direct:errors", e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        errors.assertIsSatisfied();
        Map<String, Object> line = errors.getExchanges().get(0).getMessage().getBody(Map.class);
        assertThat(line).containsEntry("custom_id", "ticket-3");
        assertThat(line.toString()).contains("rate_limit_exceeded");
    }

    @Test
    void shouldFailWhenTheResultsAreNotReadyYet() {
        String batchId = createBatch();

        Exchange exchange = template.request("direct:results",
                e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        assertThat(exchange.getException()).hasMessageContaining("has no output file yet");
    }

    @Test
    void shouldFailWhenTheBatchIdIsMissing() {
        Exchange exchange = template.request("direct:retrieve", e -> e.getIn().setBody("no id"));

        assertThat(exchange.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OpenAIConstants.BATCH_ID);
    }

    private String createBatch() {
        Map<String, Object> prompts = new LinkedHashMap<>();
        prompts.put("ticket-1", "I was charged twice");
        prompts.put("ticket-2", "The app crashes on startup");
        prompts.put("ticket-3", "Where is my order");
        Exchange created = template.request("direct:create", e -> e.getIn().setBody(prompts));
        return created.getMessage().getHeader(OpenAIConstants.BATCH_ID, String.class);
    }

    private String completedBatch() {
        String batchId = createBatch();
        for (int i = 0; i < 3; i++) {
            retrieve(batchId);
        }
        return batchId;
    }

    private Exchange retrieve(String batchId) {
        return template.request("direct:retrieve", e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));
    }

}
