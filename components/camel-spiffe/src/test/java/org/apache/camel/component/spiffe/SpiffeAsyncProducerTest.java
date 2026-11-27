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

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.spiffe.exception.JwtSvidException;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.security.AuthorizationDecisionEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiffeAsyncProducerTest {
    @Test
    void asyncJwtFetchUsesTheConfiguredLocalIdentity() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        SpiffeId subject = SpiffeId.parse("spiffe://example.org/local");
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(subject);
        when(svid.getToken()).thenReturn("minted-token");
        when(client.fetchJwtSvid(subject, "audience")).thenReturn(svid);
        try (Fixture fixture = new Fixture(client, configuration -> {
            configuration.setLocalSpiffeId(subject.toString());
            configuration.setOperation(SpiffeOperation.fetchJwtSvid);
        })) {
            Exchange exchange = new DefaultExchange(fixture.context);
            fixture.producer.processAsync(exchange).get(5, TimeUnit.SECONDS);
            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getMessage().getBody()).isEqualTo("minted-token");
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo(subject.toString());
            verify(client).fetchJwtSvid(subject, "audience");
            verify(client, never()).fetchJwtSvid("audience");
        }
    }

    @Test
    void preserveBodyRetainsPayloadAndClearsPreviousIdentityOnFailedValidation() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/peer"));
        when(client.validateJwtSvid("token", "audience")).thenReturn(svid);
        try (Fixture fixture = new Fixture(client)) {
            fixture.endpoint.getConfiguration().setPreserveBody(true);
            Exchange exchange = new DefaultExchange(fixture.context);
            Object payload = List.of("request", "payload");
            exchange.getMessage().setBody(payload);
            exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token");
            fixture.producer.processAsync(exchange).get(5, TimeUnit.SECONDS);
            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getMessage().getBody()).isSameAs(payload);
            assertThat(exchange.getProperty(SpiffeConstants.JWT_IDENTITY)).isInstanceOf(SpiffeJwtIdentity.class);
            when(client.validateJwtSvid("token", "audience")).thenThrow(new JwtSvidException("invalid"));
            fixture.producer.processAsync(exchange).get(5, TimeUnit.SECONDS);
            assertThat(exchange.getException()).isInstanceOf(JwtSvidException.class);
            assertThat(exchange.getProperty(SpiffeConstants.JWT_IDENTITY)).isNull();
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isNull();
            assertThat(exchange.getMessage().getBody()).isSameAs(payload);
        }
    }

    @Test
    void capturesTokenAndAudienceBeforeQueueingAndCompletesOffTheCaller() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/peer"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.validateJwtSvid("token-before", "audience")).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return svid;
        });
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token-before");
            var result = fixture.producer.processAsync(exchange);
            exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token-after");
            fixture.endpoint.getConfiguration().setAudience("changed-after-admission");
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(exchange);
            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getMessage().getBody()).isSameAs(svid);
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo("spiffe://example.org/peer");
        }
    }

    @Test
    void validationFailureCompletesCallbackExactlyOnce() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        when(client.validateJwtSvid("token", "audience")).thenThrow(new JwtSvidException("invalid token"));
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token");
            CountDownLatch callback = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            assertThat(fixture.producer.process(exchange, doneSync -> {
                calls.incrementAndGet();
                callback.countDown();
            })).isFalse();
            assertThat(callback.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(exchange.getException()).isInstanceOf(JwtSvidException.class).hasMessage("invalid token");
        }
    }

    @Test
    void synchronousTemplateFacadeRemainsUsable() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/peer"));
        when(client.validateJwtSvid("token", "audience")).thenReturn(svid);
        try (Fixture fixture = new Fixture(client); var template = fixture.context.createProducerTemplate()) {
            Exchange result = template.request(fixture.endpoint,
                    exchange -> exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token"));
            assertThat(result.getException()).isNull();
            assertThat(result.getMessage().getBody()).isSameAs(svid);
        }
    }

    @Test
    void stoppingProducerCompletesPendingValidationOnceWithoutPublishingIdentity() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(client.validateJwtSvid("token", "audience")).thenAnswer(invocation -> {
            entered.countDown();
            new CountDownLatch(1).await(5, TimeUnit.SECONDS);
            throw new JwtSvidException("validation interrupted");
        });
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token");
            CountDownLatch callback = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            fixture.producer.process(exchange, doneSync -> {
                calls.incrementAndGet();
                callback.countDown();
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            fixture.producer.stop();
            assertThat(callback.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(exchange.getException()).isInstanceOf(CancellationException.class);
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isNull();
        }
    }

    @Test
    void asynchronousValidationPublishesAJwtValidationEvent() throws Exception {
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse("spiffe://example.org/peer"));
        when(client.validateJwtSvid("token", "audience")).thenReturn(svid);
        try (Fixture fixture = new Fixture(client)) {
            List<AuthorizationDecisionEvent> events = observe(fixture.context);
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(SpiffeConstants.TOKEN, "token");
            fixture.producer.processAsync(exchange).get(5, TimeUnit.SECONDS);
            assertThat(exchange.getException()).isNull();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getEngine()).isEqualTo(AuthorizationDecisionEvent.Engine.SPIFFE);
            assertThat(events.get(0).getOperation()).isEqualTo(AuthorizationDecisionEvent.Operation.VALIDATE_JWT);
            assertThat(events.get(0).getOutcome()).isEqualTo(AuthorizationDecisionEvent.Outcome.ALLOW);
            assertThat(events.get(0).getExchangeId()).isEqualTo(exchange.getExchangeId());
        }
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final SpiffeEndpoint endpoint;
        final SpiffeProducer producer;

        Fixture(WorkloadApiClient client) throws Exception {
            this(client, configuration -> {
            });
        }

        Fixture(WorkloadApiClient client, Consumer<SpiffeConfiguration> configure) throws Exception {
            context.start();
            SpiffeConfiguration configuration = new SpiffeConfiguration();
            configuration.setWorkloadApiClient(client);
            configuration.setOperation(SpiffeOperation.validateJwtSvid);
            configuration.setAudience("audience");
            configuration.setAsyncProcessing(true);
            configure.accept(configuration);
            endpoint = new SpiffeEndpoint("spiffe:validate", new SpiffeComponent(context), configuration);
            endpoint.start();
            producer = new SpiffeProducer(endpoint);
            producer.start();
        }

        @Override
        public void close() throws Exception {
            producer.stop();
            endpoint.stop();
            context.close();
        }
    }

    private static List<AuthorizationDecisionEvent> observe(CamelContext context) {
        List<AuthorizationDecisionEvent> events = new CopyOnWriteArrayList<>();
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
        return events;
    }
}
