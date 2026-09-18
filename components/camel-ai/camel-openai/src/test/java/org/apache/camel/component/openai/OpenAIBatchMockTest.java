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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
                        .to("openai:batch?batchEndpoint=/v1/unsupported");
                from("direct:retrieve")
                        .to("openai:batch-retrieve");
                from("direct:cancel")
                        .to("openai:batch-cancel");
                from("direct:results")
                        .noStreamCaching()
                        .to("openai:batch-results")
                        .split(body().tokenize("\n")).streaming()
                        .to("mock:results")
                        .end();
                from("direct:errors")
                        .noStreamCaching()
                        .to("openai:batch-results?batchResultsFile=error")
                        .split(body().tokenize("\n")).streaming()
                        .to("mock:errors")
                        .end();
            }
        };
    }

    @Override
    protected org.apache.camel.CamelContext createCamelContext() throws Exception {
        org.apache.camel.CamelContext context = super.createCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        component.setBaseUrl(openAIMock.getBaseUrl());
        component.setApiKey("dummy");
        context.addComponent("openai", component);
        return context;
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
    void shouldRejectAnUnsupportedEndpointBeforeUploading() {
        assertThatThrownBy(() -> template.requestBody("direct:createInvalidEndpoint", Map.of("a", "b")))
                .isInstanceOf(CamelExecutionException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported batch endpoint: /v1/unsupported");
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

        Exchange cancelled = template.request("direct:cancel",
                e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        assertThat(cancelled.getMessage().getHeader(OpenAIConstants.BATCH_STATUS)).isEqualTo("cancelling");
    }

    @Test
    void shouldStreamTheOutputFileAndCorrelateByCustomId() throws Exception {
        String batchId = completedBatch();
        MockEndpoint results = getMockEndpoint("mock:results");
        results.expectedMessageCount(2);

        template.request("direct:results", e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        results.assertIsSatisfied();
        List<String> lines = results.getExchanges().stream()
                .map(e -> e.getMessage().getBody(String.class)).toList();
        assertThat(lines.get(0)).contains("\"custom_id\":\"ticket-1\"").contains("billing");
        assertThat(lines.get(1)).contains("\"custom_id\":\"ticket-2\"").contains("bug");
    }

    @Test
    void shouldReadTheErrorFileSeparately() throws Exception {
        String batchId = completedBatch();
        MockEndpoint errors = getMockEndpoint("mock:errors");
        errors.expectedMessageCount(1);

        template.request("direct:errors", e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        errors.assertIsSatisfied();
        assertThat(errors.getExchanges().get(0).getMessage().getBody(String.class))
                .contains("\"custom_id\":\"ticket-3\"")
                .contains("rate_limit_exceeded");
    }

    @Test
    void shouldFailWhenTheResultsAreNotReadyYet() {
        String batchId = createBatch();

        Exchange exchange = template.request("direct:results",
                e -> e.getIn().setHeader(OpenAIConstants.BATCH_ID, batchId));

        assertThat(exchange.getException()).hasMessageContaining("its results are available once it is completed");
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
