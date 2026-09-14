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

import io.spiffe.exception.JwtSvidException;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A configured audience list means "any of these is acceptable", so validation must try each rather than silently
 * enforcing only the first.
 */
class SpiffeMultiAudienceTest extends CamelTestSupport {

    private static final String FIRST = "spiffe://example.org/first";
    private static final String SECOND = "spiffe://example.org/second";

    @BindToRegistry("client")
    private final WorkloadApiClient client = mock(WorkloadApiClient.class);

    private static JwtSvid svid() {
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/caller"));
        return svid;
    }

    private String endpoint() {
        return "spiffe:v?workloadApiClient=#client&operation=validateJwtSvid&audience=" + FIRST + "," + SECOND;
    }

    private Exchange validate() {
        return template.request(endpoint(), e -> e.getIn().setHeader(SpiffeConstants.TOKEN, "a-token"));
    }

    @Test
    void acceptsATokenMatchingTheSecondConfiguredAudience() throws Exception {
        JwtSvid stub = svid();
        when(client.validateJwtSvid("a-token", FIRST)).thenThrow(new JwtSvidException("wrong audience"));
        when(client.validateJwtSvid("a-token", SECOND)).thenReturn(stub);

        Exchange out = validate();

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody()).isSameAs(stub);
        verify(client).validateJwtSvid("a-token", FIRST);
        verify(client).validateJwtSvid("a-token", SECOND);
    }

    @Test
    void stopsAtTheFirstAudienceThatMatches() throws Exception {
        JwtSvid stub = svid();
        when(client.validateJwtSvid("a-token", FIRST)).thenReturn(stub);

        Exchange out = validate();

        assertThat(out.getException()).isNull();
        // no point asking the Workload API again once one audience has accepted the token
        verify(client).validateJwtSvid("a-token", FIRST);
        verify(client, never()).validateJwtSvid("a-token", SECOND);
    }

    @Test
    void failsWhenNoConfiguredAudienceMatches() throws Exception {
        when(client.validateJwtSvid("a-token", FIRST)).thenThrow(new JwtSvidException("wrong audience"));
        when(client.validateJwtSvid("a-token", SECOND)).thenThrow(new JwtSvidException("wrong audience"));

        Exchange out = validate();

        assertThat(out.getException()).isInstanceOf(JwtSvidException.class);
        verify(client).validateJwtSvid("a-token", FIRST);
        verify(client).validateJwtSvid("a-token", SECOND);
    }
}
