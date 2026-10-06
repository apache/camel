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
package org.apache.camel.component.spiffe.integration;

import java.util.List;

import io.spiffe.exception.JwtSvidException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.spiffe.SpiffeConstants;
import org.apache.camel.test.infra.spiffe.services.SpiffeService;
import org.apache.camel.test.infra.spiffe.services.SpiffeServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the camel-spiffe producer against a real SPIRE Workload API: fetching an X509-SVID and a JWT-SVID, and
 * validating a JWT-SVID (accepting the configured audience, rejecting another).
 */
class SpiffeWorkloadApiIT extends CamelTestSupport {

    @RegisterExtension
    static SpiffeService service = SpiffeServiceFactory.createSingletonService();

    private static String socket() {
        return "RAW(" + service.getWorkloadApiSocketPath() + ")";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:x509")
                        .toF("spiffe:x509?operation=fetchX509Svid&spiffeSocketPath=%s", socket());
                from("direct:jwt")
                        .toF("spiffe:jwt?operation=fetchJwtSvid&audience=my-audience&spiffeSocketPath=%s", socket());
                from("direct:validateCorrectAudience")
                        .toF("spiffe:valOk?operation=validateJwtSvid&audience=my-audience&spiffeSocketPath=%s", socket());
                from("direct:validateWrongAudience")
                        .toF("spiffe:valWrong?operation=validateJwtSvid&audience=other-audience&spiffeSocketPath=%s",
                                socket());
            }
        };
    }

    @Test
    void fetchesAnX509SvidForThisWorkload() {
        Exchange out = template.request("direct:x509", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID))
                .isEqualTo(service.getWorkloadSpiffeId());
        // the default x509Response is the certificate chain, which must carry at least the leaf certificate
        assertThat(out.getMessage().getBody(List.class)).isNotEmpty();
    }

    @Test
    void fetchesAJwtSvidForThisWorkload() {
        Exchange out = template.request("direct:jwt", e -> {
        });

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID))
                .isEqualTo(service.getWorkloadSpiffeId());
        // a JWT-SVID is a compact JWT: three base64url parts separated by dots
        assertThat(out.getMessage().getBody(String.class).split("\\.")).hasSize(3);
    }

    @Test
    void validatesAJwtSvidAgainstItsAudience() {
        String token = template.requestBody("direct:jwt", null, String.class);

        Exchange out = template.request("direct:validateCorrectAudience",
                e -> e.getMessage().setHeader(SpiffeConstants.TOKEN, token));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(SpiffeConstants.SPIFFE_ID))
                .isEqualTo(service.getWorkloadSpiffeId());
    }

    @Test
    void rejectsAJwtSvidMintedForAnotherAudience() {
        String token = template.requestBody("direct:jwt", null, String.class);

        Exchange out = template.request("direct:validateWrongAudience",
                e -> e.getMessage().setHeader(SpiffeConstants.TOKEN, token));

        // the token is valid but minted for my-audience, so validation against other-audience must fail
        assertThat(out.getException()).isInstanceOf(JwtSvidException.class);
    }
}
