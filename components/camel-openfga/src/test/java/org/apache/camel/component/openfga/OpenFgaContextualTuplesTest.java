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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import dev.openfga.sdk.api.client.model.ClientListObjectsRequest;
import dev.openfga.sdk.api.client.model.ClientListObjectsResponse;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFgaContextualTuplesTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";
    private static final String BASE = "?openFgaClient=#fgaClient&storeId=" + STORE;

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    @BindToRegistry("myContext")
    private final Map<String, Object> conditionContext = Map.of("hour", 14, "onCorpNetwork", true);

    private void givenVerdict(Boolean allowed) throws Exception {
        ClientCheckResponse response = mock(ClientCheckResponse.class);
        when(response.getAllowed()).thenReturn(allowed);
        when(client.check(any(ClientCheckRequest.class), any())).thenReturn(CompletableFuture.completedFuture(response));
    }

    private ClientCheckRequest captureCheck() throws Exception {
        ArgumentCaptor<ClientCheckRequest> captor = ArgumentCaptor.forClass(ClientCheckRequest.class);
        verify(client).check(captor.capture(), any());
        return captor.getValue();
    }

    private static ClientBatchCheckClientResponse batchItem(String object, Boolean allowed) {
        ClientBatchCheckClientResponse response = mock(ClientBatchCheckClientResponse.class);
        when(response.getRequest()).thenReturn(new ClientCheckRequest()._object(object));
        when(response.getAllowed()).thenReturn(allowed);
        when(response.getThrowable()).thenReturn(null);
        return response;
    }

    @Test
    void sendsTheConfiguredContextualTuplesWithTheCheck() throws Exception {
        givenVerdict(Boolean.TRUE);

        Exchange out = template.request(
                "openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget"
                                        + "&contextualTuples=user:anne,member,team:eng;user:anne,on_network,network:corp",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(captureCheck().getContextualTuples())
                .extracting(ClientTupleKey::getUser, ClientTupleKey::getRelation, ClientTupleKey::getObject)
                .containsExactly(
                        tuple("user:anne", "member", "team:eng"),
                        tuple("user:anne", "on_network", "network:corp"));
    }

    @Test
    void evaluatesEachPartOfAContextualTuplePerExchange() throws Exception {
        givenVerdict(Boolean.TRUE);

        template.request(
                "openfga:check" + BASE + "&relation=reader&user=user:${header.sub}&object=document:budget"
                         + "&contextualTuples=user:${header.sub},member,team:${header.team}",
                e -> {
                    e.getMessage().setHeader("sub", "anne");
                    e.getMessage().setHeader("team", "eng");
                });

        assertThat(captureCheck().getContextualTuples()).singleElement()
                .satisfies(tuple -> {
                    assertThat(tuple.getUser()).isEqualTo("user:anne");
                    assertThat(tuple.getObject()).isEqualTo("team:eng");
                });
    }

    @Test
    void sendsNoContextualTuplesWhenNoneAreConfigured() throws Exception {
        givenVerdict(Boolean.TRUE);

        template.request("openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget", e -> {
        });

        // null rather than an empty list, so the request looks exactly as it did before this option existed
        assertThat(captureCheck().getContextualTuples()).isNull();
    }

    @Test
    void deniesWhenAConfiguredContextualTupleDoesNotResolve() throws Exception {
        givenVerdict(Boolean.TRUE);

        // the team header is absent, so the tuple's object resolves to "team:" - the endpoint asked for a tuple it did
        // not get, and answering a different question than the one configured is worse than refusing
        Exchange out = template.request(
                "openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget"
                                        + "&contextualTuples=user:anne,member,team:${header.team}",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("invalid-contextual-tuple");
        verify(client, never()).check(any(ClientCheckRequest.class), any());
    }

    @Test
    void acceptsATypedWildcardInAContextualTuple() throws Exception {
        givenVerdict(Boolean.TRUE);

        // unlike the check subject: "user:* reader document:x" is how a route says this one is public for this
        // request, and the route author wrote it
        Exchange out = template.request(
                "openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget"
                                        + "&contextualTuples=user:*,reader,document:budget",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(captureCheck().getContextualTuples()).singleElement()
                .satisfies(tuple -> assertThat(tuple.getUser()).isEqualTo("user:*"));
    }

    @Test
    void sendsTheConditionContextFromTheRegistry() throws Exception {
        givenVerdict(Boolean.TRUE);

        template.request(
                "openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget"
                         + "&conditionContext=#myContext",
                e -> {
                });

        assertThat(captureCheck().getContext()).isEqualTo(Map.of("hour", 14, "onCorpNetwork", true));
    }

    @Test
    void refusesAMalformedContextualTupleWhenTheEndpointStarts() {
        // a bad option must stop the endpoint rather than deny every message at runtime with something that reads
        // like a policy decision
        assertThatThrownBy(() -> {
            OpenFgaEndpoint endpoint = context.getEndpoint(
                    "openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget"
                                                           + "&contextualTuples=user:anne,member",
                    OpenFgaEndpoint.class);
            endpoint.start();
        }).hasStackTraceContaining("Each contextual tuple must be user,relation,object")
                .hasStackTraceContaining("has 2 part(s)");
    }

    @Test
    void batchCheckSendsTheContextWithEveryItemOfTheBatch() throws Exception {
        List<ClientBatchCheckClientResponse> answers = List.of(
                batchItem("document:budget", true),
                batchItem("document:roadmap", false));
        when(client.clientBatchCheck(anyList(), any())).thenReturn(CompletableFuture.completedFuture(answers));

        Exchange out = template.request(
                "openfga:batchCheck" + BASE + "&relation=reader&user=user:anne"
                                        + "&contextualTuples=user:anne,member,team:eng&conditionContext=#myContext",
                e -> e.getMessage().setBody(List.of("document:budget", "document:roadmap")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).containsExactly("document:budget");

        // the batch fans out into one check per object, so the context has to be on EVERY item - putting it on the
        // first alone would silently judge the rest against a different set of facts
        ArgumentCaptor<List<ClientCheckRequest>> captor = ArgumentCaptor.captor();
        verify(client).clientBatchCheck(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(request -> {
            assertThat(request.getContextualTuples()).singleElement()
                    .satisfies(tuple -> assertThat(tuple.getObject()).isEqualTo("team:eng"));
            assertThat(request.getContext()).isEqualTo(Map.of("hour", 14, "onCorpNetwork", true));
        });
    }

    @Test
    void batchCheckDeniesEverythingWhenAContextualTupleDoesNotResolve() throws Exception {
        // same rule as the single check: the endpoint asked for a tuple it did not get, so no object is allowed.
        // Filtering against a weaker set of facts than configured would hand back more than the route sanctioned
        Exchange out = template.request(
                "openfga:batchCheck" + BASE + "&relation=reader&user=user:anne"
                                        + "&contextualTuples=user:anne,member,team:${header.team}",
                e -> e.getMessage().setBody(List.of("document:budget", "document:roadmap")));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(List.class)).isEmpty();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("invalid-contextual-tuple");
        verify(client, never()).clientBatchCheck(anyList(), any());
    }

    @Test
    void refusesAContextualTupleWhoseExpressionContainsAComma() {
        // the option is split on ',' before the parts are compiled, so a comma inside a Simple expression breaks the
        // triple. What matters is that it cannot pass silently: here the split happens to yield three parts, and the
        // first of them is not a parsable expression, so the endpoint still refuses to start rather than checking
        // something other than what was written
        assertThatThrownBy(() -> {
            OpenFgaEndpoint endpoint = context.getEndpoint(
                    "openfga:check" + BASE + "&relation=reader&user=user:anne&object=document:budget"
                                                           + "&contextualTuples=user:${bean:ids.pick(1,2)},member",
                    OpenFgaEndpoint.class);
            endpoint.start();
        }).hasStackTraceContaining("missing } to close the function");
    }

    @Test
    void appliesContextualTuplesToTheListOperationsToo() throws Exception {
        ClientListObjectsResponse response = mock(ClientListObjectsResponse.class);
        when(response.getObjects()).thenReturn(List.of("document:budget"));
        when(client.listObjects(any(), any())).thenReturn(CompletableFuture.completedFuture(response));

        template.request(
                "openfga:listObjects" + BASE + "&relation=reader&user=user:anne&type=document"
                         + "&contextualTuples=user:anne,member,team:eng&conditionContext=#myContext",
                e -> {
                });

        ArgumentCaptor<ClientListObjectsRequest> captor = ArgumentCaptor.forClass(ClientListObjectsRequest.class);
        verify(client).listObjects(captor.capture(), any());
        assertThat(captor.getValue().getContextualTupleKeys()).singleElement()
                .satisfies(tuple -> assertThat(tuple.getObject()).isEqualTo("team:eng"));
        assertThat(captor.getValue().getContext()).isEqualTo(Map.of("hour", 14, "onCorpNetwork", true));
    }
}
