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
import java.util.List;

import javax.net.ssl.SSLSession;

import io.spiffe.exception.JwtSvidException;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.security.AuthorizationDecisionEvent;
import org.apache.camel.support.security.AuthorizationDecisionEvent.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiffeAuthorizationObservationTest {
    @Test
    void observesVerifiedMtlsAndMissingIdentityWithoutRecordingThePeer() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var policy = new SpiffeSecurityPolicy();
            policy.setAcceptAnySpiffeId(true);
            var processor = new SpiffeSecurityProcessor(exchange -> {
            }, policy);
            Exchange exchange = new DefaultExchange(fixture.context);
            assertThatThrownBy(() -> processor.authorize(exchange)).isInstanceOf(CamelAuthorizationException.class);
            X509Certificate certificate = mock(X509Certificate.class);
            when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(6, "spiffe://example.org/secret-peer")));
            SSLSession session = mock(SSLSession.class);
            when(session.getPeerCertificates()).thenReturn(new Certificate[] { certificate });
            exchange.getMessage().setHeader(SpiffeSecurityPolicy.DEFAULT_SSL_SESSION_HEADER, session);
            processor.authorize(exchange);
            assertThat(exchange.getProperty(SpiffeConstants.PEER_SPIFFE_ID)).isEqualTo("spiffe://example.org/secret-peer");
            assertThat(fixture.events).extracting(AuthorizationDecisionEvent::getOutcome).containsExactly(Outcome.DENY,
                    Outcome.ALLOW);
            assertThat(fixture.events).allSatisfy(event -> {
                assertThat(event.getOperation()).isEqualTo(AuthorizationDecisionEvent.Operation.MTLS);
                assertThat(event.asJSon().toString()).doesNotContain("secret-peer");
                assertThat(event.getExchangeId()).isEqualTo(exchange.getExchangeId());
            });
        }
    }

    @Test
    void observesJwtValidationSuccessDenialAndWorkloadApiUnavailable() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            JwtSvid svid = mock(JwtSvid.class);
            when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/secret-peer"));
            when(client.validateJwtSvid("secret-token", "audience")).thenReturn(svid)
                    .thenThrow(new JwtSvidException("secret-rejected-token"))
                    .thenThrow(new JwtSvidException("secret-transport", io.grpc.Status.UNAVAILABLE.asRuntimeException()));
            SpiffeConfiguration configuration = new SpiffeConfiguration();
            configuration.setWorkloadApiClient(client);
            configuration.setOperation(SpiffeOperation.validateJwtSvid);
            configuration.setAudience("audience");
            var endpoint = new SpiffeEndpoint("spiffe:validate", new SpiffeComponent(fixture.context), configuration);
            endpoint.start();
            var producer = new SpiffeProducer(endpoint);
            producer.start();
            try {
                for (int index = 0; index < 3; index++) {
                    Exchange exchange = new DefaultExchange(fixture.context);
                    exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "secret-token");
                    if (index == 0) {
                        producer.process(exchange);
                    } else {
                        assertThatThrownBy(() -> producer.process(exchange)).isInstanceOf(JwtSvidException.class);
                    }
                }
                assertThat(fixture.events).extracting(AuthorizationDecisionEvent::getOutcome)
                        .containsExactly(Outcome.ALLOW, Outcome.DENY, Outcome.UNAVAILABLE);
                assertThat(fixture.events).allSatisfy(event -> {
                    assertThat(event.getOperation()).isEqualTo(AuthorizationDecisionEvent.Operation.VALIDATE_JWT);
                    assertThat(event.asJSon().toString()).doesNotContain("secret-peer", "secret-token", "secret-transport");
                });
            } finally {
                producer.stop();
                endpoint.stop();
            }
        }
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final List<AuthorizationDecisionEvent> events = new ArrayList<>();

        Fixture() throws Exception {
            context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
                @Override
                public boolean isEnabled(CamelEvent event) {
                    return event instanceof AuthorizationDecisionEvent;
                }

                @Override
                public void notify(CamelEvent event) {
                    events.add((AuthorizationDecisionEvent) event);
                }
            });
            context.start();
        }

        @Override
        public void close() throws Exception {
            context.close();
        }
    }
}
