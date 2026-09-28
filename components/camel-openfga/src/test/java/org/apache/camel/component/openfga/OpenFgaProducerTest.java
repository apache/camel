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
import java.util.concurrent.CompletableFuture;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFgaProducerTest extends CamelTestSupport {

    private static final String STORE = "01HQMVAJXYZ0000000000000";
    private static final String ENDPOINT = "openfga:check?openFgaClient=#fgaClient&storeId=" + STORE
                                           + "&relation=reader&user=user:${header.subject}"
                                           + "&object=document:${header.documentId}";

    @BindToRegistry("fgaClient")
    private final OpenFgaClient client = mock(OpenFgaClient.class);

    private void givenVerdict(Boolean allowed) throws Exception {
        ClientCheckResponse response = mock(ClientCheckResponse.class);
        when(response.getAllowed()).thenReturn(allowed);
        when(client.check(any(ClientCheckRequest.class), any())).thenReturn(CompletableFuture.completedFuture(response));
    }

    private void givenServerIsUnreachable() throws Exception {
        when(client.check(any(ClientCheckRequest.class), any()))
                .thenReturn(CompletableFuture.failedFuture(new IOException("connection refused")));
    }

    private Exchange request(String endpoint) {
        return template.request(endpoint, e -> {
            e.getMessage().setHeader("subject", "anne");
            e.getMessage().setHeader("documentId", "budget");
        });
    }

    @Test
    void allowsWhenTheRelationshipExists() throws Exception {
        givenVerdict(Boolean.TRUE);

        Exchange out = request(ENDPOINT);

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.USER)).isEqualTo("user:anne");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.OBJECT)).isEqualTo("document:budget");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.RELATION)).isEqualTo("reader");
        assertThat(out.getMessage().getHeader(OpenFgaConstants.STORE_ID)).isEqualTo(STORE);
    }

    @Test
    void sendsTheSubjectRelationAndObjectTheExpressionsResolved() throws Exception {
        givenVerdict(Boolean.TRUE);

        request(ENDPOINT);

        ArgumentCaptor<ClientCheckRequest> captor = ArgumentCaptor.forClass(ClientCheckRequest.class);
        verify(client).check(captor.capture(), any());
        assertThat(captor.getValue().getUser()).isEqualTo("user:anne");
        assertThat(captor.getValue().getRelation()).isEqualTo("reader");
        assertThat(captor.getValue().getObject()).isEqualTo("document:budget");
    }

    @Test
    void deniesWhenTheRelationshipDoesNotExist() throws Exception {
        givenVerdict(Boolean.FALSE);

        Exchange out = request(ENDPOINT);

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("denied");
    }

    @Test
    void deniesWhenTheAnswerCarriesNoVerdict() throws Exception {
        // an answer with a null allowed is not an answer; treating it as a deny keeps failOpen from reading a
        // malformed response as permission
        givenVerdict(null);

        Exchange out = request(ENDPOINT);

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
    }

    @Test
    void neverLetsAVerdictTheMessageArrivedWithSurvive() throws Exception {
        givenVerdict(Boolean.FALSE);

        Exchange out = template.request(ENDPOINT, e -> {
            e.getMessage().setHeader("subject", "anne");
            e.getMessage().setHeader("documentId", "budget");
            // a caller claiming to have been allowed already
            e.getMessage().setHeader(OpenFgaConstants.ALLOWED, true);
            e.getMessage().setHeader(OpenFgaConstants.DENY_REASON, "none");
        });

        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("denied");
    }

    @Test
    void failsClosedWhenTheServerCannotBeReached() throws Exception {
        givenServerIsUnreachable();

        Exchange out = request(ENDPOINT);

        assertThat(out.getException()).isInstanceOf(OpenFgaEvaluationException.class);
        // no verdict was reached, so the route must not find one on the message either
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
    }

    @Test
    void proceedsWhenTheServerCannotBeReachedAndFailOpenIsEnabled() throws Exception {
        givenServerIsUnreachable();

        Exchange out = request(ENDPOINT + "&failOpen=true");

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
    }

    @Test
    void deniesAnExchangeWithNoIdentityEvenWhenFailOpenIsEnabled() throws Exception {
        givenVerdict(Boolean.TRUE);

        // no subject header, so user:${header.subject} resolves to "user:" - a type with no id, which is what an
        // exchange that carried no identity looks like. That is a decision, not a failure to reach OpenFGA, so
        // failOpen must not turn it into an allow
        Exchange out = template.request(
                "openfga:check?openFgaClient=#fgaClient&storeId=" + STORE
                                        + "&relation=reader&user=user:${header.subject}&object=document:budget"
                                        + "&failOpen=true",
                e -> {
                });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("missing-user");
        verify(client, never()).check(any(ClientCheckRequest.class), any());
    }

    @Test
    void deniesAWildcardSubjectWithoutAskingTheServer() throws Exception {
        givenVerdict(Boolean.TRUE);

        // user:* is "everyone", and OpenFGA answers true for it wherever a public-access tuple exists - so an
        // expression that resolved to one would hand out every publicly shared object
        Exchange out = template.request(
                "openfga:check?openFgaClient=#fgaClient&storeId=" + STORE
                                        + "&relation=reader&user=user:${header.subject}&object=document:budget",
                e -> e.getMessage().setHeader("subject", "*"));

        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("wildcard-subject");
        verify(client, never()).check(any(ClientCheckRequest.class), any());
    }

    @Test
    void deniesAnIdentifierThatCouldForgeALogLine() throws Exception {
        givenVerdict(Boolean.TRUE);

        Exchange out = template.request(ENDPOINT, e -> {
            e.getMessage().setHeader("subject", "anne\nadmin");
            e.getMessage().setHeader("documentId", "budget");
        });

        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("invalid-identifier");
        verify(client, never()).check(any(ClientCheckRequest.class), any());
    }

    @Test
    void reportsTheMostFundamentalReasonWhenSeveralPartsAreUnusable() throws Exception {
        givenVerdict(Boolean.TRUE);

        // neither the subject nor the object resolves; the subject is the more fundamental of the two, and it must
        // not be masked by whichever part happened to be validated last
        Exchange out = template.request(ENDPOINT, e -> {
        });

        assertThat(out.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpenFgaConstants.DENY_REASON)).isEqualTo("missing-user");
        verify(client, never()).check(any(ClientCheckRequest.class), any());
    }

    @Test
    void leavesTheBodyAlone() throws Exception {
        givenVerdict(Boolean.TRUE);

        Exchange out = template.request(ENDPOINT, e -> {
            e.getMessage().setHeader("subject", "anne");
            e.getMessage().setHeader("documentId", "budget");
            e.getMessage().setBody("the original payload");
        });

        assertThat(out.getMessage().getBody()).isEqualTo("the original payload");
    }
}
