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
package org.apache.camel.component.openfga;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientBatchCheckRequest;
import dev.openfga.sdk.api.client.model.ClientBatchCheckResponse;
import dev.openfga.sdk.api.client.model.ClientBatchCheckSingleResponse;
import dev.openfga.sdk.api.configuration.ClientBatchCheckOptions;
import dev.openfga.sdk.api.model.CheckError;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFgaServerBatchCheckTest extends CamelTestSupport {

    private static final String URI = "openfga:batchCheck?openFgaClient=#fgaClient&storeId=01HQMVAJXYZ0000000000000"
                                      + "&relation=reader&user=user:anne&batchCheckMode=server";

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private static ClientBatchCheckSingleResponse result(String correlation, boolean allowed) {
        // Correlation, rather than a repeated copy of the request, is sufficient to pair an answer with its input.
        return new ClientBatchCheckSingleResponse(allowed, null, correlation, null);
    }

    private Exchange request(String options, List<String> objects) {
        return template.request(URI + options, exchange -> exchange.getMessage().setBody(objects));
    }

    @Test
    void correlatesShuffledResultsAndPreservesInputOrderAndDuplicates() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("3", true), result("1", false), result("0", true), result("2", true)))));

        Exchange out = request("", List.of("document:budget", "document:secret", "document:roadmap", "document:roadmap"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class))
                .containsExactly("document:budget", "document:roadmap", "document:roadmap");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
        verify(client, never()).clientBatchCheck(anyList(), any());
        ArgumentCaptor<ClientBatchCheckRequest> captor = ArgumentCaptor.captor();
        verify(client).batchCheck(captor.capture(), any());
        assertThat(captor.getValue().getChecks()).extracting(item -> item.getCorrelationId())
                .containsExactly("0", "1", "2", "3");
    }

    @Test
    void keepsEachDuplicatePairedWithItsOwnDecision() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("1", false), result("0", true)))));

        Exchange out = request("", List.of("document:budget", "document:budget"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget");
    }

    @Test
    void propagatesChunkingModelConsistencyAndContextOptions() throws Exception {
        context.getRegistry().bind("requestContext", Map.of("region", "eu"));
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("0", true), result("1", true)))));

        Exchange out = request("&maxBatchSize=1&maxParallelRequests=2&authorizationModelId=model-v1"
                               + "&consistency=HIGHER_CONSISTENCY&conditionContext=#requestContext"
                               + "&contextualTuples=user:anne,member,team:engineering",
                List.of("document:budget", "document:roadmap"));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientBatchCheckRequest> requests = ArgumentCaptor.captor();
        ArgumentCaptor<ClientBatchCheckOptions> options = ArgumentCaptor.captor();
        verify(client).batchCheck(requests.capture(), options.capture());
        assertThat(options.getValue().getMaxBatchSize()).isEqualTo(1);
        assertThat(options.getValue().getMaxParallelRequests()).isEqualTo(2);
        assertThat(options.getValue().getAuthorizationModelId()).isEqualTo("model-v1");
        assertThat(options.getValue().getConsistency()).isEqualTo(ConsistencyPreference.HIGHER_CONSISTENCY);
        assertThat(requests.getValue().getChecks()).allSatisfy(item -> {
            assertThat(item.getUser()).isEqualTo("user:anne");
            assertThat(item.getRelation()).isEqualTo("reader");
            assertThat(item.getContext()).isEqualTo(Map.of("region", "eu"));
            assertThat(item.getContextualTuples()).singleElement().satisfies(tuple -> {
                assertThat(tuple.getUser()).isEqualTo("user:anne");
                assertThat(tuple.getRelation()).isEqualTo("member");
                assertThat(tuple.getObject()).isEqualTo("team:engineering");
            });
        });
    }

    @Test
    void rejectsAPartialItemFailureEvenWhenFailOpenIsEnabled() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("0", true), new ClientBatchCheckSingleResponse(
                        false, null, "1",
                        new CheckError().message("evaluation failed"))))));

        Exchange out = request("&failOpen=true", List.of("document:budget", "document:secret"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget", "document:secret");
    }

    @Test
    void rejectsMissingResults() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("0", true)))));

        Exchange out = request("", List.of("document:budget", "document:secret"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class)
                .hasMessageContaining("incomplete batch check");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
    }

    @Test
    void rejectsRepeatedCorrelations() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("0", true), result("0", true)))));

        Exchange out = request("", List.of("document:budget", "document:secret"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class)
                .hasMessageContaining("unexpected batch check correlation");
    }

    @Test
    void rejectsUnknownCorrelations() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("unknown", true)))));

        Exchange out = request("", List.of("document:budget"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class)
                .hasMessageContaining("unexpected batch check correlation");
    }

    @Test
    void rejectsAnUnavailableServerEvenWhenFailOpenIsEnabled() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("connection refused")));

        Exchange out = request("&failOpen=true", List.of("document:budget"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
    }

    @Test
    void skipsInvalidObjectsBeforeAssigningCorrelations() throws Exception {
        when(client.batchCheck(any(), any())).thenReturn(CompletableFuture.completedFuture(new ClientBatchCheckResponse(
                List.of(result("0", true)))));

        Exchange out = request("", List.of("document:", "document:budget"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget");
        ArgumentCaptor<ClientBatchCheckRequest> captor = ArgumentCaptor.captor();
        verify(client).batchCheck(captor.capture(), any());
        assertThat(captor.getValue().getChecks()).singleElement().satisfies(item -> {
            assertThat(item.getObject()).isEqualTo("document:budget");
            assertThat(item.getCorrelationId()).isEqualTo("0");
        });
    }

    @Test
    @Timeout(10)
    void cancelsAnUncompletedServerBatchAfterItsBoundedWait() throws Exception {
        CompletableFuture<ClientBatchCheckResponse> pending = new CompletableFuture<>();
        when(client.batchCheck(any(), any())).thenReturn(pending);

        Exchange out = request("&connectTimeout=1&readTimeout=1&maxRetries=0", List.of("document:budget"));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class).hasMessageContaining("Timed out");
        assertThat(pending.isCancelled()).isTrue();
    }
}
