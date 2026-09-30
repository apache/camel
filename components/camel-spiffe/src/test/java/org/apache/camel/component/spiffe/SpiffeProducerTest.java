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
package org.apache.camel.component.spiffe;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.WorkloadApiClient;
import io.spiffe.workloadapi.X509Context;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiffeProducerTest extends CamelTestSupport {

    @BindToRegistry("client")
    private final WorkloadApiClient client = mock(WorkloadApiClient.class);

    private SpiffeId spiffeId(String id) {
        SpiffeId spiffeId = mock(SpiffeId.class);
        when(spiffeId.toString()).thenReturn(id);
        return spiffeId;
    }

    private final Date notAfter = new Date();
    private final List<X509Certificate> chain = List.of(mock(X509Certificate.class));

    private X509Svid mockX509Svid(String id) throws Exception {
        // build the nested mocks first: stubbing one inside another when(...) call trips Mockito
        SpiffeId sid = spiffeId(id);
        X509Certificate leaf = mock(X509Certificate.class);
        when(leaf.getNotAfter()).thenReturn(notAfter);
        X509Svid svid = mock(X509Svid.class);
        when(svid.getSpiffeId()).thenReturn(sid);
        when(svid.getChain()).thenReturn(chain);
        when(svid.getLeaf()).thenReturn(leaf);
        X509Context ctx = mock(X509Context.class);
        when(ctx.getDefaultSvid()).thenReturn(svid);
        when(client.fetchX509Context()).thenReturn(ctx);
        return svid;
    }

    @Test
    void fetchX509Svid() throws Exception {
        mockX509Svid("spiffe://example.org/workload");

        Exchange out = template.request("spiffe:test?workloadApiClient=#client&operation=fetchX509Svid", e -> {
        });

        // default response is the certificate chain: no private key on the body unless x509Response=svid is set
        assertThat(out.getMessage().getBody()).isSameAs(chain);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/workload");
        assertThat(out.getMessage().getHeader(SpiffeConstants.EXPIRY)).isEqualTo(notAfter);
    }

    @Test
    void fetchX509SvidSvidReturnsTheFullSvid() throws Exception {
        X509Svid svid = mockX509Svid("spiffe://example.org/workload");

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=fetchX509Svid&x509Response=svid", e -> {
                });

        // opt-in: the whole SVID, which carries the private key
        assertThat(out.getMessage().getBody()).isSameAs(svid);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/workload");
        assertThat(out.getMessage().getHeader(SpiffeConstants.EXPIRY)).isEqualTo(notAfter);
    }

    @Test
    void fetchX509SvidChainOmitsThePrivateKey() throws Exception {
        mockX509Svid("spiffe://example.org/workload");

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=fetchX509Svid&x509Response=chain", e -> {
                });

        // the certificate chain, not the SVID: no private key in the body
        assertThat(out.getMessage().getBody()).isSameAs(chain);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/workload");
        assertThat(out.getMessage().getHeader(SpiffeConstants.EXPIRY)).isEqualTo(notAfter);
    }

    @Test
    void fetchX509SvidIdLeavesTheBodyUntouched() throws Exception {
        mockX509Svid("spiffe://example.org/workload");

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=fetchX509Svid&x509Response=id",
                e -> e.getIn().setBody("original-body"));

        // body untouched: the identity is exposed only through the headers, so no key material is handled
        assertThat(out.getMessage().getBody()).isEqualTo("original-body");
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/workload");
        assertThat(out.getMessage().getHeader(SpiffeConstants.EXPIRY)).isEqualTo(notAfter);
    }

    @Test
    void fetchJwtSvid() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/workload");
        Date expiry = new Date();
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getToken()).thenReturn("the-jwt-token");
        when(svid.getSpiffeId()).thenReturn(id);
        when(svid.getExpiry()).thenReturn(expiry);
        when(client.fetchJwtSvid("my-audience")).thenReturn(svid);

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=fetchJwtSvid&audience=my-audience", e -> {
                });

        assertThat(out.getMessage().getBody(String.class)).isEqualTo("the-jwt-token");
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/workload");
        assertThat(out.getMessage().getHeader(SpiffeConstants.EXPIRY)).isEqualTo(expiry);
    }

    @Test
    void fetchJwtSvidAudienceFromHeader() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/workload");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getToken()).thenReturn("tok");
        when(svid.getSpiffeId()).thenReturn(id);
        when(client.fetchJwtSvid("aud-from-header")).thenReturn(svid);

        Exchange out = template.request("spiffe:test?workloadApiClient=#client&operation=fetchJwtSvid",
                e -> e.getIn().setHeader(SpiffeConstants.AUDIENCE, "aud-from-header"));

        assertThat(out.getMessage().getBody(String.class)).isEqualTo("tok");
    }

    @Test
    void validateJwtSvid() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/client");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        when(client.validateJwtSvid("incoming-token", "my-audience")).thenReturn(svid);

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> e.getIn().setHeader(SpiffeConstants.TOKEN, "incoming-token"));

        assertThat(out.getMessage().getBody()).isSameAs(svid);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/client");
    }

    @Test
    void fetchJwtSvidWithoutAudienceFails() {
        Exchange out = template.request("spiffe:test?workloadApiClient=#client&operation=fetchJwtSvid", e -> {
        });
        assertThat(out.isFailed()).isTrue();
        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchJwtSvidMultipleAudiences() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/workload");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getToken()).thenReturn("multi-tok");
        when(svid.getSpiffeId()).thenReturn(id);
        // the additional audiences are passed as varargs after the first one; the comma-separated
        // header value is split and trimmed by the producer, and blank entries (the ", ," below) are dropped
        when(client.fetchJwtSvid("aud1", "aud2", "aud3")).thenReturn(svid);

        Exchange out = template.request("spiffe:test?workloadApiClient=#client&operation=fetchJwtSvid",
                e -> e.getIn().setHeader(SpiffeConstants.AUDIENCE, "aud1, , aud2, aud3"));

        assertThat(out.getMessage().getBody(String.class)).isEqualTo("multi-tok");
    }

    @Test
    void validateJwtSvidTokenFromBody() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/client");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        when(client.validateJwtSvid("body-token", "my-audience")).thenReturn(svid);

        // no CamelSpiffeToken header -> the producer falls back to the message body
        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> e.getIn().setBody("body-token"));

        assertThat(out.getMessage().getBody()).isSameAs(svid);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/client");
    }

    @Test
    void validateJwtSvidTokenFromAuthorizationHeader() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/client");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        when(client.validateJwtSvid("auth-token", "my-audience")).thenReturn(svid);

        // no CamelSpiffeToken header and no body -> the producer falls back to the Authorization: Bearer header
        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> e.getIn().setHeader("Authorization", "Bearer auth-token"));

        assertThat(out.getMessage().getBody()).isSameAs(svid);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/client");
    }

    @Test
    void validateJwtSvidBearerSchemeIsCaseInsensitive() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/client");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        when(client.validateJwtSvid("ci-token", "my-audience")).thenReturn(svid);

        // scheme matched case-insensitively and the token trimmed
        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> e.getIn().setHeader("Authorization", "bearer   ci-token"));

        assertThat(out.getMessage().getBody()).isSameAs(svid);
    }

    @Test
    void validateJwtSvidHeaderTokenWinsOverAuthorization() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/client");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        // only the CamelSpiffeToken value is stubbed; if the Authorization value were used the mock returns null
        when(client.validateJwtSvid("explicit-token", "my-audience")).thenReturn(svid);

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> {
                    e.getIn().setHeader(SpiffeConstants.TOKEN, "explicit-token");
                    e.getIn().setHeader("Authorization", "Bearer ignored-token");
                });

        assertThat(out.getMessage().getBody()).isSameAs(svid);
    }

    @Test
    void validateJwtSvidNonBearerAuthorizationFails() {
        // a non-bearer Authorization value is not a token source; the request falls through to the token-required error
        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> e.getIn().setHeader("Authorization", "Basic dXNlcjpwYXNz"));

        assertThat(out.isFailed()).isTrue();
        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateJwtSvidAuthorizationWinsOverBody() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/client");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        // only the Authorization token is stubbed: a POST/PUT request payload in the body must not be taken as the token
        when(client.validateJwtSvid("bearer-token", "my-audience")).thenReturn(svid);

        Exchange out = template.request(
                "spiffe:test?workloadApiClient=#client&operation=validateJwtSvid&audience=my-audience",
                e -> {
                    e.getIn().setBody("the-request-payload");
                    e.getIn().setHeader("Authorization", "Bearer bearer-token");
                });

        assertThat(out.getMessage().getBody()).isSameAs(svid);
    }
}
