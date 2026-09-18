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

import java.util.Date;

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

    @Test
    void fetchX509Svid() throws Exception {
        SpiffeId id = spiffeId("spiffe://example.org/workload");
        X509Svid svid = mock(X509Svid.class);
        when(svid.getSpiffeId()).thenReturn(id);
        X509Context ctx = mock(X509Context.class);
        when(ctx.getDefaultSvid()).thenReturn(svid);
        when(client.fetchX509Context()).thenReturn(ctx);

        Exchange out = template.request("spiffe:test?workloadApiClient=#client&operation=fetchX509Svid", e -> {
        });

        assertThat(out.getMessage().getBody()).isSameAs(svid);
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/workload");
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
}
