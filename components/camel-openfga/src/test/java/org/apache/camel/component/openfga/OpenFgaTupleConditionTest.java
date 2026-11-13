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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientReadChangesResponse;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.client.model.ClientRelationshipCondition;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteResponse;
import dev.openfga.sdk.api.model.RelationshipCondition;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleChange;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleOperation;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A relationship tuple may carry a condition (a condition name plus an optional context) on top of its
 * user/relation/object identity. These tests pin that the condition is carried through faithfully on the write path and
 * surfaced on the read path, while a delete stays by identity alone.
 */
class OpenFgaTupleConditionTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";
    private static final String BASE = "?openFgaClient=#fgaClient&storeId=" + STORE;

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private static ClientRelationshipCondition clientCondition() {
        return new ClientRelationshipCondition().name("in_allowed_region").context(Map.of("region", "emea"));
    }

    @Test
    void writeTuplesPreservesTheConditionOnASingleClientTupleKey() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(
                new ClientTupleKey().user("user:maya").relation("viewer")._object("document:roadmap")
                        .condition(clientCondition())));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<List<ClientTupleKey>> captor = ArgumentCaptor.captor();
        verify(client).writeTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(key -> {
                    assertThat(key.getUser()).isEqualTo("user:maya");
                    assertThat(key.getRelation()).isEqualTo("viewer");
                    assertThat(key.getObject()).isEqualTo("document:roadmap");
                    assertThat(key.getCondition()).isNotNull();
                    assertThat(key.getCondition().getName()).isEqualTo("in_allowed_region");
                    assertThat(key.getCondition().getContext()).isEqualTo(Map.of("region", "emea"));
                });
    }

    @Test
    void writeTuplesPreservesConditionsAcrossACollection() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(List.of(
                new ClientTupleKey().user("user:maya").relation("viewer")._object("document:roadmap")
                        .condition(clientCondition()),
                new ClientTupleKey().user("user:leo").relation("editor")._object("document:ledger"))));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<List<ClientTupleKey>> captor = ArgumentCaptor.captor();
        verify(client).writeTuples(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        // the conditioned tuple keeps its condition, and the plain one stays unconditioned
        assertThat(captor.getValue().get(0).getCondition()).isNotNull()
                .satisfies(condition -> assertThat(condition.getName()).isEqualTo("in_allowed_region"));
        assertThat(captor.getValue().get(1).getCondition()).isNull();
    }

    @Test
    void writeTuplesPreservesAConditionGivenAsAMap() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(Map.of(
                "user", "user:maya", "relation", "viewer", "object", "document:roadmap",
                "condition", Map.of("name", "in_allowed_region", "context", Map.of("region", "emea")))));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<List<ClientTupleKey>> captor = ArgumentCaptor.captor();
        verify(client).writeTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(key -> {
                    assertThat(key.getCondition()).isNotNull();
                    assertThat(key.getCondition().getName()).isEqualTo("in_allowed_region");
                    assertThat(key.getCondition().getContext()).isEqualTo(Map.of("region", "emea"));
                });
    }

    @Test
    void writeTuplesWithoutAConditionStaysUnconditioned() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(Map.of(
                "user", "user:maya", "relation", "viewer", "object", "document:roadmap")));

        assertThat(out.getException()).isNull();
        ArgumentCaptor<List<ClientTupleKey>> captor = ArgumentCaptor.captor();
        verify(client).writeTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(key -> assertThat(key.getCondition()).isNull());
    }

    @Test
    void deleteTuplesStaysIdentityOnly() throws Exception {
        when(client.deleteTuples(anyList()))
                .thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        // a condition on the body is read but a delete is keyed by identity, so the delete key must not carry it
        Exchange out = template.request("openfga:deleteTuples" + BASE, e -> e.getMessage().setBody(Map.of(
                "user", "user:maya", "relation", "viewer", "object", "document:roadmap",
                "condition", Map.of("name", "in_allowed_region", "context", Map.of("region", "emea")))));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);
        ArgumentCaptor<List<ClientTupleKeyWithoutCondition>> captor = ArgumentCaptor.captor();
        verify(client).deleteTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(key -> {
                    assertThat(key.getUser()).isEqualTo("user:maya");
                    assertThat(key.getObject()).isEqualTo("document:roadmap");
                    // the identity-only type, never the ClientTupleKey subtype that would carry a condition
                    assertThat(key).isExactlyInstanceOf(ClientTupleKeyWithoutCondition.class);
                });
    }

    @Test
    void readTuplesProjectsAStoredCondition() throws Exception {
        ClientReadResponse response = mock(ClientReadResponse.class);
        when(response.getTuples()).thenReturn(List.of(new Tuple()
                .key(new TupleKey().user("user:maya").relation("viewer")._object("document:roadmap")
                        .condition(new RelationshipCondition().name("in_allowed_region")
                                .context(Map.of("region", "emea"))))
                .timestamp(OffsetDateTime.parse("2026-10-01T10:00:00Z"))));
        when(response.getContinuationToken()).thenReturn(null);
        when(client.read(any(ClientReadRequest.class), any())).thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request("openfga:readTuples" + BASE, e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).singleElement()
                .satisfies(entry -> {
                    Map<?, ?> tuple = (Map<?, ?>) entry;
                    assertThat(tuple.get("condition"))
                            .isEqualTo(Map.of("name", "in_allowed_region", "context", Map.of("region", "emea")));
                });
    }

    @Test
    void readChangesProjectsAStoredCondition() throws Exception {
        ClientReadChangesResponse response = mock(ClientReadChangesResponse.class);
        when(response.getChanges()).thenReturn(List.of(new TupleChange()
                .tupleKey(new TupleKey().user("user:maya").relation("viewer")._object("document:roadmap")
                        .condition(new RelationshipCondition().name("in_allowed_region")
                                .context(Map.of("region", "emea"))))
                .operation(TupleOperation.WRITE)
                .timestamp(OffsetDateTime.parse("2026-10-01T10:00:00Z"))));
        when(response.getContinuationToken()).thenReturn(null);
        when(client.readChanges(any(ClientReadChangesRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request("openfga:readChanges" + BASE, e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).singleElement()
                .satisfies(entry -> {
                    Map<?, ?> change = (Map<?, ?>) entry;
                    assertThat(change.get("operation")).isEqualTo("WRITE");
                    assertThat(change.get("condition"))
                            .isEqualTo(Map.of("name", "in_allowed_region", "context", Map.of("region", "emea")));
                });
    }
}
