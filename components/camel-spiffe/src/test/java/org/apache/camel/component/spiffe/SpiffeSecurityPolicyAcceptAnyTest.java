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

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLSession;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiffeSecurityPolicyAcceptAnyTest extends CamelTestSupport {

    private final SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy();

    @Override
    protected RouteBuilder createRouteBuilder() {
        policy.setAcceptAnySpiffeId(true);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .policy(policy)
                        .to("mock:authorized");
            }
        };
    }

    private static SSLSession sessionPresenting(String spiffeId) throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectAlternativeNames()).thenReturn(List.of(List.of(6, spiffeId)));
        SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(new Certificate[] { cert });
        return session;
    }

    @Test
    void authorizesAnyVerifiedPeerAndExposesItsId() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(1);

        Exchange out = template.request("direct:start", e -> {
            e.getMessage().setHeader(SpiffeSecurityPolicy.DEFAULT_SSL_SESSION_HEADER,
                    sessionPresenting("spiffe://other.org/some-workload"));
            e.getMessage().setBody("payload");
        });

        assertThat(out.getException()).isNull();
        authorized.assertIsSatisfied();
        assertThat(out.getProperty(SpiffeConstants.PEER_SPIFFE_ID)).isEqualTo("spiffe://other.org/some-workload");
    }

    @Test
    void stillFailsClosedWhenNoVerifiedPeerIsPresent() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        Exchange out = template.request("direct:start", e -> e.getMessage().setBody("payload"));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining("No verified SPIFFE peer identity");
        authorized.assertIsSatisfied();
    }
}
