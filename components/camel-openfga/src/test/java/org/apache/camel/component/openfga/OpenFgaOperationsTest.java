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
import dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsResponse;
import dev.openfga.sdk.api.client.model.ClientListRelationsResponse;
import dev.openfga.sdk.api.client.model.ClientListUsersResponse;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteResponse;
import dev.openfga.sdk.api.model.FgaObject;
import dev.openfga.sdk.api.model.TypedWildcard;
import dev.openfga.sdk.api.model.User;
import dev.openfga.sdk.api.model.UsersetUser;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFgaOperationsTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";
    private static final String BASE = "?openFgaClient=#fgaClient&storeId=" + STORE;

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private static ClientBatchCheckClientResponse batchItem(String object, Boolean allowed, Throwable failure) {
        ClientBatchCheckClientResponse response = mock(ClientBatchCheckClientResponse.class);
        when(response.getRequest()).thenReturn(new ClientCheckRequest()._object(object));
        when(response.getAllowed()).thenReturn(allowed);
        when(response.getThrowable()).thenReturn(failure);
        return response;
    }

    @Test
    void batchCheckKeepsOnlyTheObjectsTheCheckAllowed() throws Exception {
        List<ClientBatchCheckClientResponse> answers = List.of(
                batchItem("document:budget", true, null),
                batchItem("document:secret", false, null),
                batchItem("document:roadmap", true, null));
        when(client.clientBatchCheck(anyList(), any())).thenReturn(CompletableFuture.completedFuture(answers));

        Exchange out = template.request("openfga:batchCheck" + BASE + "&relation=reader&user=user:anne",
                e -> e.getMessage().setBody(List.of("document:budget", "document:secret", "document:roadmap")));

        assertThat(out.getException()).isNull();
        // in the order the body asked in, not the order the parallel checks happened to answer in
        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget", "document:roadmap");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
    }

    @Test
    void batchCheckKeepsTheOrderTheBodyAskedIn() throws Exception {
        // the SDK fans the checks out in parallel and returns them in completion order, so the answers arrive
        // shuffled relative to the request
        List<ClientBatchCheckClientResponse> answers = List.of(
                batchItem("document:roadmap", true, null),
                batchItem("document:budget", true, null));
        when(client.clientBatchCheck(anyList(), any())).thenReturn(CompletableFuture.completedFuture(answers));

        Exchange out = template.request("openfga:batchCheck" + BASE + "&relation=reader&user=user:anne",
                e -> e.getMessage().setBody(List.of("document:budget", "document:roadmap")));

        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget", "document:roadmap");
    }

    @Test
    void batchCheckFailsRatherThanPresentAPartialFilterAsAWholeOne() throws Exception {
        List<ClientBatchCheckClientResponse> answers = List.of(
                batchItem("document:budget", true, null),
                batchItem("document:secret", null, new IOException("connection reset")));
        when(client.clientBatchCheck(anyList(), any())).thenReturn(CompletableFuture.completedFuture(answers));

        Exchange out = template.request("openfga:batchCheck" + BASE + "&relation=reader&user=user:anne",
                e -> e.getMessage().setBody(List.of("document:budget", "document:secret")));

        // returning document:budget alone would look like a complete answer, and the caller would never know that
        // one object was never judged
        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
    }

    @Test
    void batchCheckNeverFailsOpen() throws Exception {
        when(client.clientBatchCheck(anyList(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IOException("connection refused")));

        Exchange out = template.request("openfga:batchCheck" + BASE + "&relation=reader&user=user:anne&failOpen=true",
                e -> e.getMessage().setBody(List.of("document:budget")));

        // "proceed" for a filter would mean handing back everything unfiltered, so failOpen does not reach here
        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
    }

    @Test
    void batchCheckDropsAnEntryThatCouldNotBeAnObject() throws Exception {
        List<ClientBatchCheckClientResponse> answers = List.of(batchItem("document:budget", true, null));
        when(client.clientBatchCheck(anyList(), any())).thenReturn(CompletableFuture.completedFuture(answers));

        Exchange out = template.request("openfga:batchCheck" + BASE + "&relation=reader&user=user:anne",
                e -> e.getMessage().setBody(List.of("document:budget", "not-an-identifier")));

        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget");
        ArgumentCaptor<List<ClientCheckRequest>> captor = ArgumentCaptor.captor();
        verify(client).clientBatchCheck(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void batchCheckAllowsNothingWhenTheSubjectDidNotResolve() throws Exception {
        Exchange out = template.request("openfga:batchCheck" + BASE + "&relation=reader&user=${header.subject}",
                e -> e.getMessage().setBody(List.of("document:budget")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).isEmpty();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("missing-user");
        verify(client, never()).clientBatchCheck(anyList(), any());
    }

    @Test
    void listObjectsReplacesTheBodyWithTheObjectsTheSubjectCanReach() throws Exception {
        ClientListObjectsResponse response = mock(ClientListObjectsResponse.class);
        when(response.getObjects()).thenReturn(List.of("document:budget", "document:public"));
        when(client.listObjects(any(), any())).thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request(
                "openfga:listObjects" + BASE + "&relation=reader&user=user:anne&type=document", e -> {
                });

        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget", "document:public");
    }

    @Test
    void listRelationsReplacesTheBodyWithTheRelationsTheSubjectHolds() throws Exception {
        when(client.listRelations(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new ClientListRelationsResponse(List.of("reader", "owner"))));

        Exchange out = template.request(
                "openfga:listRelations" + BASE + "&user=user:anne&object=document:budget&relations=reader,writer,owner",
                e -> {
                });

        assertThat(out.getMessage().getBody(List.class)).containsExactly("reader", "owner");
    }

    @Test
    void listUsersRendersEveryKindOfSubjectOpenFgaCanAnswerWith() throws Exception {
        ClientListUsersResponse response = mock(ClientListUsersResponse.class);
        when(response.getUsers()).thenReturn(List.of(
                new User()._object(new FgaObject().type("user").id("anne")),
                new User().userset(new UsersetUser().type("team").id("eng").relation("member")),
                new User().wildcard(new TypedWildcard().type("user"))));
        when(client.listUsers(any(), any())).thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request(
                "openfga:listUsers" + BASE + "&object=document:budget&relation=reader", e -> {
                });

        assertThat(out.getMessage().getBody(List.class))
                .containsExactly("user:anne", "team:eng#member", "user:*");
    }

    @Test
    void writeTuplesTakesTheTuplesFromTheBody() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(List.of(
                Map.of("user", "user:anne", "relation", "owner", "object", "document:budget"),
                Map.of("user", "user:bob", "relation", "reader", "object", "document:budget"))));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(2);
        ArgumentCaptor<List<ClientTupleKey>> captor = ArgumentCaptor.captor();
        verify(client).writeTuples(captor.capture());
        assertThat(captor.getValue()).extracting(ClientTupleKey::getUser).containsExactly("user:anne", "user:bob");
    }

    @Test
    void writeTuplesFallsBackToTheConfiguredTripleWhenTheBodyCarriesNone() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        // the shape a route that just created a resource wants: grant access to it without assembling a payload
        Exchange out = template.request(
                "openfga:writeTuples" + BASE
                                        + "&user=user:${header.subject}&relation=owner&object=document:${header.documentId}",
                e -> {
                    e.getMessage().setHeader("subject", "anne");
                    e.getMessage().setHeader("documentId", "q3-report");
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(1);
        ArgumentCaptor<List<ClientTupleKey>> captor = ArgumentCaptor.captor();
        verify(client).writeTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(tuple -> {
                    assertThat(tuple.getUser()).isEqualTo("user:anne");
                    assertThat(tuple.getRelation()).isEqualTo("owner");
                    assertThat(tuple.getObject()).isEqualTo("document:q3-report");
                });
    }

    @Test
    void writeTuplesAcceptsAWildcardSubjectBecauseThatIsHowSomethingIsSharedPublicly() throws Exception {
        when(client.writeTuples(anyList())).thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(
                Map.of("user", "user:*", "relation", "reader", "object", "document:announcement")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(1);
    }

    @Test
    void writeTuplesNamesTheOffendingPartOfAnUnusableTuple() {
        Exchange out = template.request("openfga:writeTuples" + BASE, e -> e.getMessage().setBody(
                Map.of("user", "anne", "relation", "owner", "object", "document:budget")));

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(out.getException()).hasMessageContaining("unusable user");
    }

    @Test
    void deleteTuplesTakesTheTuplesFromTheBody() throws Exception {
        when(client.deleteTuples(anyList()))
                .thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange out = template.request("openfga:deleteTuples" + BASE, e -> e.getMessage().setBody(
                Map.of("user", "user:bob", "relation", "reader", "object", "document:budget")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);
        ArgumentCaptor<List<ClientTupleKeyWithoutCondition>> captor = ArgumentCaptor.captor();
        verify(client).deleteTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(tuple -> assertThat(tuple.getUser()).isEqualTo("user:bob"));
    }
}
