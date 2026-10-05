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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
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

class SpiffeSecurityPolicyTest extends CamelTestSupport {

    private static final String FRONTEND = "spiffe://example.org/frontend";
    private static final String BACKEND = "spiffe://example.org/backend";

    private final SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy();

    @Override
    protected RouteBuilder createRouteBuilder() {
        policy.setAcceptedSpiffeIds(FRONTEND);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .policy(policy)
                        .to("mock:authorized");
            }
        };
    }

    private static X509Certificate certWithUriSans(String... uris) throws Exception {
        Collection<List<?>> sans = new ArrayList<>();
        for (String uri : uris) {
            // 6 == uniformResourceIdentifier GeneralName tag
            sans.add(List.of(6, uri));
        }
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectAlternativeNames()).thenReturn(sans);
        return cert;
    }

    private static SSLSession sessionPresenting(Certificate... chain) throws Exception {
        SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(chain);
        return session;
    }

    private Exchange sendWithSession(SSLSession session) {
        return template.request("direct:start", e -> {
            if (session != null) {
                e.getMessage().setHeader(SpiffeSecurityPolicy.DEFAULT_SSL_SESSION_HEADER, session);
            }
            e.getMessage().setBody("payload");
        });
    }

    @Test
    void authorizesAListedPeerAndExposesTheIdAsAnExchangeProperty() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(1);

        Exchange out = sendWithSession(sessionPresenting(certWithUriSans(FRONTEND)));

        assertThat(out.getException()).isNull();
        authorized.assertIsSatisfied();
        assertThat(out.getProperty(SpiffeConstants.PEER_SPIFFE_ID)).isEqualTo(FRONTEND);
    }

    @Test
    void deniesAPeerThatIsNotOnTheAllowList() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        Exchange out = sendWithSession(sessionPresenting(certWithUriSans(BACKEND)));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining(BACKEND);
        assertThat(out.getMessage().getHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID))
                .isEqualTo("SpiffeSecurityPolicy");
        assertThat(out.getProperty(SpiffeConstants.PEER_SPIFFE_ID)).isNull();
        authorized.assertIsSatisfied();
    }

    @Test
    void deniesWhenThereIsNoTlsSessionAtAll() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        Exchange out = sendWithSession(null);

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining("No verified SPIFFE peer identity");
        authorized.assertIsSatisfied();
    }

    @Test
    void deniesWhenThePeerPresentedNoVerifiedCertificate() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("no peer certificate"));

        Exchange out = sendWithSession(session);

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class);
        authorized.assertIsSatisfied();
    }

    @Test
    void deniesACertificateThatCarriesNoSpiffeId() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        Exchange out = sendWithSession(sessionPresenting(certWithUriSans("https://example.org/not-a-spiffe-id")));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining("No verified SPIFFE peer identity");
        authorized.assertIsSatisfied();
    }

    @Test
    void deniesACertificateWithMoreThanOneSpiffeId() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        // a certificate with two SPIFFE URI SANs is not a valid SVID; the policy must not guess which identity to trust
        Exchange out = sendWithSession(sessionPresenting(certWithUriSans(FRONTEND, BACKEND)));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining("No verified SPIFFE peer identity");
        authorized.assertIsSatisfied();
    }

    @Test
    void deniesACertificateWithASpiffeAndANonSpiffeUriSan() throws Exception {
        MockEndpoint authorized = getMockEndpoint("mock:authorized");
        authorized.expectedMessageCount(0);

        // a valid X509-SVID has exactly one URI SAN in total; a second, non-spiffe URI means it is not an SVID, even
        // though one of the two URIs is the expected SPIFFE ID
        Exchange out = sendWithSession(sessionPresenting(certWithUriSans("https://example.org/web", FRONTEND)));

        assertThat(out.getException()).isInstanceOf(CamelAuthorizationException.class)
                .hasMessageContaining("No verified SPIFFE peer identity");
        authorized.assertIsSatisfied();
    }
}
