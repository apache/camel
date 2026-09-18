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

import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers which message headers may steer the producer.
 * <p/>
 * The operation decides whether the endpoint validates a token or mints one, and for a validation the audience is the
 * check that binds the token to this workload - neither may be chosen by the message unless the route opts in.
 */
class SpiffeHeaderOverrideTest extends CamelTestSupport {

    private static final String CONFIGURED_AUDIENCE = "spiffe://example.org/my-service";
    private static final String OTHER_AUDIENCE = "spiffe://example.org/other-service";

    @BindToRegistry("client")
    private final WorkloadApiClient client = mock(WorkloadApiClient.class);

    private static JwtSvid svid() {
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/workload"));
        when(svid.getToken()).thenReturn("minted-token");
        return svid;
    }

    private String validator() {
        return "spiffe:v?workloadApiClient=#client&operation=validateJwtSvid&audience=" + CONFIGURED_AUDIENCE;
    }

    @Test
    void validationIgnoresTheAudienceHeader() throws Exception {
        JwtSvid stub = svid();
        when(client.validateJwtSvid(anyString(), anyString())).thenReturn(stub);

        Exchange out = template.request(validator(), e -> {
            e.getIn().setHeader(SpiffeConstants.TOKEN, "a-token");
            e.getIn().setHeader(SpiffeConstants.AUDIENCE, OTHER_AUDIENCE);
        });

        assertThat(out.getException()).isNull();
        ArgumentCaptor<String> audience = ArgumentCaptor.forClass(String.class);
        verify(client).validateJwtSvid(anyString(), audience.capture());
        assertThat(audience.getValue()).isEqualTo(CONFIGURED_AUDIENCE);
    }

    @Test
    void aFetchStillHonoursTheAudienceHeader() throws Exception {
        JwtSvid stub = svid();
        when(client.fetchJwtSvid(anyString())).thenReturn(stub);

        template.request("spiffe:v?workloadApiClient=#client&operation=fetchJwtSvid&audience=" + CONFIGURED_AUDIENCE,
                e -> e.getIn().setHeader(SpiffeConstants.AUDIENCE, OTHER_AUDIENCE));

        // minting a token for a named audience is a parameter, not a check
        verify(client).fetchJwtSvid(OTHER_AUDIENCE);
    }

    @Test
    void theOperationHeaderCannotTurnAValidatorIntoAMinter() throws Exception {
        Exchange out = template.request(validator(),
                e -> e.getIn().setHeader(SpiffeConstants.OPERATION, "fetchJwtSvid"));

        // the endpoint stays a validator: no token is minted, and the missing token is what fails
        verify(client, never()).fetchJwtSvid(anyString());
        assertThat(out.getException()).isInstanceOf(IllegalArgumentException.class);
        assertThat(out.getException().getMessage()).contains("JWT-SVID token is required");
    }

    @Test
    void theOperationHeaderWorksWhenTheRouteOptsIn() throws Exception {
        JwtSvid stub = svid();
        when(client.fetchJwtSvid(anyString())).thenReturn(stub);

        Exchange out = template.request(validator() + "&allowOperationHeader=true",
                e -> e.getIn().setHeader(SpiffeConstants.OPERATION, "fetchJwtSvid"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody()).isEqualTo("minted-token");
        verify(client).fetchJwtSvid(CONFIGURED_AUDIENCE);
    }
}
