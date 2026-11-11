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
import java.util.stream.IntStream;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.client.model.ClientWriteResponse;
import dev.openfga.sdk.api.configuration.ClientDeleteTuplesOptions;
import dev.openfga.sdk.api.configuration.ClientWriteOptions;
import dev.openfga.sdk.api.configuration.ClientWriteTuplesOptions;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import dev.openfga.sdk.api.model.WriteRequestWrites;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenFgaMutationOptionsTest extends CamelTestSupport {

    private static final String BASE = "?openFgaClient=#fgaClient&storeId=01HQMVAJXYZ0000000000000";

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private static Map<String, String> tuple(String user, String relation, String object) {
        return Map.of("user", user, "relation", relation, "object", object);
    }

    private Exchange request(String operation, String options, Object body) {
        return template.request("openfga:" + operation + BASE + options, e -> e.getMessage().setBody(body));
    }

    private static ClientWriteResponse success() {
        return new ClientWriteResponse(List.of(), List.of());
    }

    @Test
    void passesDuplicateWriteConflictModeToTheSdk() throws Exception {
        when(client.writeTuples(anyList(), any())).thenReturn(CompletableFuture.completedFuture(success()));

        Exchange out = request("writeTuples", "&onDuplicate=ignore", tuple("user:anne", "reader", "document:budget"));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientWriteTuplesOptions> options = ArgumentCaptor.captor();
        verify(client).writeTuples(anyList(), options.capture());
        assertThat(options.getValue().getOnDuplicate()).isEqualTo(WriteRequestWrites.OnDuplicateEnum.IGNORE);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(1);
    }

    @Test
    void passesMissingDeleteConflictModeToTheSdk() throws Exception {
        when(client.deleteTuples(anyList(), any())).thenReturn(CompletableFuture.completedFuture(success()));

        Exchange out = request("deleteTuples", "&onMissing=ignore", tuple("user:anne", "reader", "document:budget"));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientDeleteTuplesOptions> options = ArgumentCaptor.captor();
        verify(client).deleteTuples(anyList(), options.capture());
        assertThat(options.getValue().getOnMissing()).isEqualTo(WriteRequestDeletes.OnMissingEnum.IGNORE);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);
    }

    @Test
    void preservesTheExistingDefaultWriteCall() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(success()));

        Exchange out = request("writeTuples", "", tuple("user:anne", "reader", "document:budget"));

        assertThat(out.getException()).isNull();
        verify(client).writeTuples(anyList());
        verify(client, never()).writeTuples(anyList(), any());
    }

    @Test
    void preservesTheExistingDefaultDeleteCall() throws Exception {
        when(client.deleteTuples(anyList())).thenReturn(CompletableFuture.completedFuture(success()));

        Exchange out = request("deleteTuples", "", tuple("user:anne", "reader", "document:budget"));

        assertThat(out.getException()).isNull();
        verify(client).deleteTuples(anyList());
        verify(client, never()).deleteTuples(anyList(), any());
    }

    @Test
    void sendsCombinedMutationsAsOnePinnedTransactionAndPreservesTheBody() throws Exception {
        when(client.write(any(), any())).thenReturn(CompletableFuture.completedFuture(success()));
        Map<String, Object> body = Map.of(
                "writes", List.of(tuple("user:anne", "writer", "document:budget")),
                "deletes", List.of(tuple("user:anne", "reader", "document:budget")));

        Exchange out = request("write", "&authorizationModelId=model-v1&onDuplicate=ignore&onMissing=ignore", body);

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody()).isSameAs(body);
        ArgumentCaptor<ClientWriteRequest> request = ArgumentCaptor.captor();
        ArgumentCaptor<ClientWriteOptions> options = ArgumentCaptor.captor();
        verify(client).write(request.capture(), options.capture());
        assertThat(request.getValue().getWrites()).singleElement().satisfies(key -> {
            assertThat(key.getUser()).isEqualTo("user:anne");
            assertThat(key.getRelation()).isEqualTo("writer");
            assertThat(key.getObject()).isEqualTo("document:budget");
        });
        assertThat(request.getValue().getDeletes()).singleElement().satisfies(key -> {
            assertThat(key.getUser()).isEqualTo("user:anne");
            assertThat(key.getRelation()).isEqualTo("reader");
            assertThat(key.getObject()).isEqualTo("document:budget");
        });
        assertThat(options.getValue().isTransactionsEnabled()).isTrue();
        assertThat(options.getValue().getAuthorizationModelId()).isEqualTo("model-v1");
        assertThat(options.getValue().getOnDuplicate()).isEqualTo(WriteRequestWrites.OnDuplicateEnum.IGNORE);
        assertThat(options.getValue().getOnMissing()).isEqualTo(WriteRequestDeletes.OnMissingEnum.IGNORE);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(1);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);
        verify(client, never()).writeTuples(anyList());
        verify(client, never()).deleteTuples(anyList());
    }

    @Test
    void defaultsCombinedConflictModesToError() throws Exception {
        when(client.write(any(), any())).thenReturn(CompletableFuture.completedFuture(success()));

        Exchange out = request("write", "", Map.of("writes", List.of(tuple("user:anne", "reader", "document:budget"))));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientWriteOptions> options = ArgumentCaptor.captor();
        verify(client).write(any(), options.capture());
        assertThat(options.getValue().isTransactionsEnabled()).isTrue();
        assertThat(options.getValue().getOnDuplicate()).isEqualTo(WriteRequestWrites.OnDuplicateEnum.ERROR);
        assertThat(options.getValue().getOnMissing()).isEqualTo(WriteRequestDeletes.OnMissingEnum.ERROR);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(0);
    }

    @Test
    void refusesAnOversizedCombinedTransactionWithoutSplittingIt() {
        List<Map<String, String>> writes = IntStream.range(0, 60)
                .mapToObj(i -> tuple("user:anne", "reader", "document:" + i)).toList();
        List<Map<String, String>> deletes = IntStream.range(60, 101)
                .mapToObj(i -> tuple("user:anne", "reader", "document:" + i)).toList();

        Exchange out = request("write", "", Map.of("writes", writes, "deletes", deletes));

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 and 100");
        verifyNoInteractions(client);
    }

    @Test
    void acceptsTheCombinedTransactionLimit() throws Exception {
        when(client.write(any(), any())).thenReturn(CompletableFuture.completedFuture(success()));
        List<Map<String, String>> writes = IntStream.range(0, 100)
                .mapToObj(i -> tuple("user:anne", "reader", "document:" + i)).toList();

        Exchange out = request("write", "", Map.of("writes", writes));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientWriteRequest> request = ArgumentCaptor.captor();
        verify(client).write(request.capture(), any());
        assertThat(request.getValue().getWrites()).hasSize(100);
        assertThat(request.getValue().getDeletes()).isEmpty();
    }

    @Test
    void refusesEmptyCombinedMutations() {
        Exchange out = request("write", "", Map.of("writes", List.of(), "deletes", List.of()));

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 and 100");
        verifyNoInteractions(client);
    }

    @Test
    void refusesUnsupportedEntryFieldsRatherThanIgnoringThem() {
        Exchange out = request("write", "", Map.of("writes", List.of(Map.of(
                "user", "user:anne", "relation", "reader", "object", "document:budget", "extra", "value"))));

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly");
        verifyNoInteractions(client);
    }

    @Test
    void keepsTheConfiguredTupleAheadOfCombinedBodyMutations() throws Exception {
        when(client.write(any(), any())).thenReturn(CompletableFuture.completedFuture(success()));
        Object body = Map.of("writes", List.of(tuple("user:bob", "owner", "document:roadmap")),
                "deletes", List.of(tuple("user:anne", "reader", "document:budget")));

        Exchange out = request("write", "&user=user:anne&relation=reader&object=document:budget", body);

        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientWriteRequest> request = ArgumentCaptor.captor();
        verify(client).write(request.capture(), any());
        assertThat(request.getValue().getWrites()).singleElement().satisfies(key -> {
            assertThat(key.getUser()).isEqualTo("user:anne");
            assertThat(key.getObject()).isEqualTo("document:budget");
        });
        assertThat(request.getValue().getDeletes()).isEmpty();
    }

    @Test
    void neverFallsBackToTheBodyWhenAConfiguredTupleResolvesToNothing() {
        Exchange out = request("write", "&user=${header.missing}&relation=reader&object=document:budget",
                Map.of("writes", List.of(tuple("user:bob", "owner", "document:roadmap"))));

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resolved to nothing");
        verifyNoInteractions(client);
    }

    @Test
    void keepsCombinedFailuresAsFailuresEvenUnderFailOpen() throws Exception {
        when(client.write(any(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("transaction failed")));

        Exchange out = request("write", "&failOpen=true",
                Map.of("writes", List.of(tuple("user:anne", "reader", "document:budget"))));

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isNull();
    }

    @Test
    void rejectsUnknownConflictModes() {
        assertThatThrownBy(() -> context.getEndpoint("openfga:write" + BASE + "&onDuplicate=skip"))
                .hasStackTraceContaining("skip");
    }
}
