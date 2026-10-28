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
import dev.openfga.sdk.api.client.model.ClientExpandRequest;
import dev.openfga.sdk.api.client.model.ClientExpandResponse;
import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientReadChangesResponse;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteResponse;
import dev.openfga.sdk.api.configuration.ClientReadChangesOptions;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.model.Node;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleChange;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleOperation;
import dev.openfga.sdk.api.model.UsersetTree;
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
import static org.mockito.Mockito.when;

class OpenFgaReadOperationsTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";
    private static final String BASE = "?openFgaClient=#fgaClient&storeId=" + STORE;

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private static Tuple storedTuple(String user, String relation, String object) {
        return new Tuple()
                .key(new TupleKey().user(user).relation(relation)._object(object))
                .timestamp(OffsetDateTime.parse("2026-10-01T10:00:00Z"));
    }

    private void givenStoredTuples(String token, Tuple... tuples) throws Exception {
        ClientReadResponse response = mock(ClientReadResponse.class);
        when(response.getTuples()).thenReturn(List.of(tuples));
        when(response.getContinuationToken()).thenReturn(token);
        when(client.read(any(ClientReadRequest.class), any())).thenReturn(CompletableFuture.completedFuture(response));
    }

    @Test
    void readTuplesReturnsTheShapeTheWriteOperationsAccept() throws Exception {
        givenStoredTuples(null, storedTuple("user:anne", "owner", "document:budget"));

        Exchange out = template.request("openfga:readTuples" + BASE, e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).singleElement()
                .satisfies(entry -> {
                    Map<?, ?> tuple = (Map<?, ?>) entry;
                    assertThat(tuple.get("user")).isEqualTo("user:anne");
                    assertThat(tuple.get("relation")).isEqualTo("owner");
                    assertThat(tuple.get("object")).isEqualTo("document:budget");
                    assertThat(tuple.get("timestamp")).isEqualTo(OffsetDateTime.parse("2026-10-01T10:00:00Z"));
                });
    }

    @Test
    void whatReadTuplesReturnsCanBeRevokedWithoutReshaping() throws Exception {
        // the point of using user/relation/object as the keys: a read feeds a revoke directly
        givenStoredTuples(null, storedTuple("user:bob", "reader", "document:roadmap"));
        when(client.deleteTuples(anyList()))
                .thenReturn(CompletableFuture.completedFuture(mock(ClientWriteResponse.class)));

        Exchange read = template.request("openfga:readTuples" + BASE, e -> {
        });
        Exchange revoked = template.request("openfga:deleteTuples" + BASE,
                e -> e.getMessage().setBody(read.getMessage().getBody()));

        assertThat(revoked.getException()).isNull();
        assertThat(revoked.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);
        ArgumentCaptor<List<ClientTupleKeyWithoutCondition>> captor = ArgumentCaptor.captor();
        verify(client).deleteTuples(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(tuple -> {
                    assertThat(tuple.getUser()).isEqualTo("user:bob");
                    assertThat(tuple.getObject()).isEqualTo("document:roadmap");
                });
    }

    @Test
    void readTuplesAppliesTheConfiguredFilterAndPaging() throws Exception {
        givenStoredTuples("tok-2", storedTuple("user:anne", "owner", "document:budget"));

        Exchange out = template.request(
                "openfga:readTuples" + BASE + "&user=user:anne&relation=owner&object=document:&pageSize=25"
                                        + "&continuationToken=${header.resume}",
                e -> e.getMessage().setHeader("resume", "tok-1"));

        ArgumentCaptor<ClientReadRequest> request = ArgumentCaptor.forClass(ClientReadRequest.class);
        ArgumentCaptor<ClientReadOptions> options = ArgumentCaptor.forClass(ClientReadOptions.class);
        verify(client).read(request.capture(), options.capture());
        assertThat(request.getValue().getUser()).isEqualTo("user:anne");
        assertThat(request.getValue().getRelation()).isEqualTo("owner");
        assertThat(request.getValue().getObject()).isEqualTo("document:");
        assertThat(options.getValue().getPageSize()).isEqualTo(25);
        assertThat(options.getValue().getContinuationToken()).isEqualTo("tok-1");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN)).isEqualTo("tok-2");
    }

    @Test
    void readTuplesRemovesAStaleTokenOnTheLastPage() throws Exception {
        givenStoredTuples(null, storedTuple("user:anne", "owner", "document:budget"));

        // a route looping on the header would otherwise read the final page for ever
        Exchange out = template.request("openfga:readTuples" + BASE,
                e -> e.getMessage().setHeader(OpenFgaConstants.CONTINUATION_TOKEN, "tok-from-the-previous-page"));

        assertThat(out.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN)).isNull();
    }

    @Test
    void readTuplesRefusesAFilterOpenFgaWouldReject() {
        // measured against OpenFGA 1.21.0: a user-only filter comes back as HTTP 400 because the Read API wants an
        // object type as soon as any filter is given. Catching it here names the option to change
        Exchange userOnly = template.request("openfga:readTuples" + BASE + "&user=user:bob", e -> {
        });
        assertThat(userOnly.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(userOnly.getException()).hasMessageContaining("must include object");

        Exchange typeOnly = template.request("openfga:readTuples" + BASE + "&object=document:", e -> {
        });
        assertThat(typeOnly.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(typeOnly.getException()).hasMessageContaining("object type alone is not enough");
    }

    @Test
    void readTuplesAcceptsATypeOnlyObjectAlongsideAUser() throws Exception {
        givenStoredTuples(null, storedTuple("user:bob", "reader", "document:roadmap"));

        Exchange out = template.request("openfga:readTuples" + BASE + "&object=document:&user=user:bob", e -> {
        });

        // document: is a legitimate read filter though it is not a legitimate identifier anywhere else
        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).hasSize(1);
    }

    @Test
    void readTuplesRefusesAFilterThatCouldNotBeATupleValue() throws Exception {
        Exchange out = template.request("openfga:readTuples" + BASE + "&object=not-an-identifier", e -> {
        });

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(out.getException()).hasMessageContaining("must name a type");
    }

    @Test
    void readTuplesRefusesAFilterPartThatWentMissing() throws Exception {
        givenStoredTuples(null, storedTuple("user:bob", "reader", "document:budget"));

        // object=document:budget on its own is a legal filter, so dropping an unresolved user would quietly read
        // every tuple on that document - and the documented readTuples -> deleteTuples route would revoke them all
        Exchange out = template.request("openfga:readTuples" + BASE + "&object=document:budget&user=${header.who}",
                e -> {
                });

        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(out.getException()).hasMessageContaining("user is configured for the readTuples operation");
        verify(client, never()).read(any(ClientReadRequest.class), any());
    }

    @Test
    void readTuplesWithNoFilterConfiguredStillReadsEverything() throws Exception {
        givenStoredTuples(null, storedTuple("user:bob", "reader", "document:budget"));

        Exchange out = template.request("openfga:readTuples" + BASE, e -> {
        });

        // an option that was never set is the only thing that means "do not filter on this"
        assertThat(out.getException()).isNull();
        ArgumentCaptor<ClientReadRequest> request = ArgumentCaptor.forClass(ClientReadRequest.class);
        verify(client).read(request.capture(), any());
        assertThat(request.getValue().getUser()).isNull();
        assertThat(request.getValue().getRelation()).isNull();
        assertThat(request.getValue().getObject()).isNull();
    }

    @Test
    void readChangesReportsTheOperationAsItsName() throws Exception {
        ClientReadChangesResponse response = mock(ClientReadChangesResponse.class);
        when(response.getChanges()).thenReturn(List.of(
                new TupleChange()
                        .tupleKey(new TupleKey().user("user:anne").relation("owner")._object("document:budget"))
                        .operation(TupleOperation.WRITE)
                        .timestamp(OffsetDateTime.parse("2026-10-01T10:00:00Z")),
                new TupleChange()
                        .tupleKey(new TupleKey().user("user:bob").relation("reader")._object("document:roadmap"))
                        .operation(TupleOperation.DELETE)
                        .timestamp(OffsetDateTime.parse("2026-10-01T11:00:00Z"))));
        when(response.getContinuationToken()).thenReturn("tok-9");
        when(client.readChanges(any(ClientReadChangesRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request(
                "openfga:readChanges" + BASE + "&type=document&pageSize=50&startTime=2026-10-01T00:00:00Z", e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).hasSize(2);
        assertThat(((Map<?, ?>) out.getMessage().getBody(List.class).get(0)).get("operation")).isEqualTo("WRITE");
        assertThat(((Map<?, ?>) out.getMessage().getBody(List.class).get(1)).get("operation")).isEqualTo("DELETE");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN)).isEqualTo("tok-9");

        ArgumentCaptor<ClientReadChangesRequest> request = ArgumentCaptor.forClass(ClientReadChangesRequest.class);
        ArgumentCaptor<ClientReadChangesOptions> options = ArgumentCaptor.forClass(ClientReadChangesOptions.class);
        verify(client).readChanges(request.capture(), options.capture());
        assertThat(request.getValue().getType()).isEqualTo("document");
        assertThat(request.getValue().getStartTime()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        assertThat(options.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void readChangesKeepsTheTokenWhenNothingHasChanged() throws Exception {
        ClientReadChangesResponse response = mock(ClientReadChangesResponse.class);
        when(response.getChanges()).thenReturn(List.of());
        when(response.getContinuationToken()).thenReturn("tok-9");
        when(client.readChanges(any(ClientReadChangesRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request("openfga:readChanges" + BASE + "&continuationToken=tok-9", e -> {
        });

        // measured against OpenFGA 1.21.0: readChanges hands the token straight back with an empty page rather than
        // dropping it, so an empty body - not an absent header - is what says the log has been read up to date. A
        // route that looped until the header disappeared would never end, which is why the docs split the two
        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).isEmpty();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN)).isEqualTo("tok-9");
    }

    @Test
    void refusesAStartTimeThatIsNotATimestampWhenTheEndpointStarts() {
        assertThatThrownBy(() -> {
            OpenFgaEndpoint endpoint
                    = context.getEndpoint("openfga:readChanges" + BASE + "&startTime=yesterday", OpenFgaEndpoint.class);
            endpoint.start();
        }).hasStackTraceContaining("startTime 'yesterday' is not an ISO-8601 timestamp");
    }

    @Test
    void expandPutsTheUsersetTreeOnTheBody() throws Exception {
        UsersetTree tree = new UsersetTree().root(new Node().name("document:budget#reader"));
        ClientExpandResponse response = mock(ClientExpandResponse.class);
        when(response.getTree()).thenReturn(tree);
        when(client.expand(any(ClientExpandRequest.class), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        Exchange out = template.request(
                "openfga:expand" + BASE + "&object=document:budget&relation=reader", e -> {
                });

        assertThat(out.getException()).isNull();
        // the one operation that does not leave a List<String>: flattening a tree destroys what it exists to show
        assertThat(out.getMessage().getBody()).isSameAs(tree);
        assertThat(out.getMessage().getBody(UsersetTree.class).getRoot().getName())
                .isEqualTo("document:budget#reader");
    }

    @Test
    void expandRequiresAnObjectAndARelation() {
        assertThatThrownBy(() -> {
            OpenFgaEndpoint endpoint
                    = context.getEndpoint("openfga:expand" + BASE + "&object=document:budget", OpenFgaEndpoint.class);
            endpoint.start();
        }).hasStackTraceContaining("relation is required for the expand operation");
    }
}
