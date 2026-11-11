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
package org.apache.camel.component.opa;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.styra.opa.OPAClient;
import com.styra.opa.OPAException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpaAsyncProducerTest {
    @Test
    void releasesCallerWhileBlockingSdkRunsAndCompletesExactlyOnce() throws Exception {
        OPAClient client = mock(OPAClient.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.evaluate(eq("authz/allow"), anyMap(), eq(Object.class))).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            try {
                assertThat(fixture.producer.process(exchange, doneSync -> {
                    assertThat(doneSync).isFalse();
                    calls.incrementAndGet();
                    completed.countDown();
                })).isFalse();
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(calls).hasValue(0);
            } finally {
                release.countDown();
            }
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        }
    }

    @Test
    void synchronousProducerTemplateFacadeAndFailedOpenSemanticsArePreserved() throws Exception {
        OPAClient client = mock(OPAClient.class);
        when(client.evaluate(eq("authz/allow"), anyMap(), eq(Object.class))).thenReturn(true);
        try (Fixture fixture = new Fixture(client); var template = fixture.context.createProducerTemplate()) {
            Exchange exchange = template.request(fixture.endpoint, request -> request.getMessage().setBody("body"));
            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getMessage().getBody()).isEqualTo("body");
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        }
    }

    @Test
    void futureCancellationFailsClosedEvenWhenFailedOpenIsEnabled() throws Exception {
        OPAClient client = mock(OPAClient.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(client.evaluate(eq("authz/allow"), anyMap(), eq(Object.class))).thenAnswer(invocation -> {
            entered.countDown();
            new CountDownLatch(1).await(5, TimeUnit.SECONDS);
            return true;
        });
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true);
            var future = fixture.producer.processAsync(exchange);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.cancel(true)).isTrue();
            assertThat(exchange.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isNull();
        }
    }

    @Test
    void interruptedSynchronousFacadeCancelsWorkAndPreservesTheInterruptFlag() throws Exception {
        OPAClient client = mock(OPAClient.class);
        CountDownLatch entered = new CountDownLatch(1);
        when(client.evaluate(eq("authz/allow"), anyMap(), eq(Object.class))).thenAnswer(invocation -> {
            entered.countDown();
            new CountDownLatch(1).await(5, TimeUnit.SECONDS);
            return true;
        });
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            CountDownLatch callerDone = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure
                    = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
            Thread caller = Thread.startVirtualThread(() -> {
                try {
                    fixture.producer.process(exchange);
                } catch (Exception e) {
                    failure.set(e);
                    interrupted.set(Thread.currentThread().isInterrupted());
                } finally {
                    callerDone.countDown();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            assertThat(callerDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isInstanceOf(InterruptedException.class);
            assertThat(interrupted).isTrue();
            assertThat(exchange.getException()).isInstanceOf(InterruptedException.class);
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
        }
    }

    @Test
    void sdkUnavailabilityStillUsesTheConfiguredFailedOpenPolicy() throws Exception {
        OPAClient client = mock(OPAClient.class);
        when(client.evaluate(eq("authz/allow"), anyMap(), eq(Object.class)))
                .thenThrow(new OPAException("offline", new java.io.IOException("connection lost")));
        try (Fixture fixture = new Fixture(client)) {
            Exchange result = fixture.producer.processAsync(new DefaultExchange(fixture.context)).get(5, TimeUnit.SECONDS);
            assertThat(result.getException()).isNull();
            assertThat(result.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
            assertThat(result.getMessage().getHeader(OpaConstants.DECISION_FAILED_OPEN)).isEqualTo(true);
        }
    }

    @Test
    void futureCannotClaimCancellationAfterResultCommitAlreadyWon() throws Exception {
        OPAClient client = mock(OPAClient.class);
        when(client.evaluate(eq("authz/allow"), anyMap(), eq(Object.class))).thenReturn(true);
        CountDownLatch copying = new CountDownLatch(1);
        CountDownLatch releaseCopy = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.setIn(new org.apache.camel.support.DefaultMessage(fixture.context) {
                @Override
                public void copyFrom(org.apache.camel.Message source) {
                    copying.countDown();
                    try {
                        assertThat(releaseCopy.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    super.copyFrom(source);
                }
            });
            var result = fixture.producer.processAsync(exchange);
            try {
                assertThat(copying.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.cancel(true)).isFalse();
            } finally {
                releaseCopy.countDown();
            }
            result.get(5, TimeUnit.SECONDS);
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        }
    }

    @Test
    void stoppedProducerClearsClaimedDecisionBeforeReturningFailure() throws Exception {
        try (Fixture fixture = new Fixture(mock(OPAClient.class))) {
            fixture.producer.stop();
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(OpaConstants.DECISION_ALLOW, true);
            exchange.getMessage().setHeader(OpaConstants.DECISION, true);
            AtomicInteger callbacks = new AtomicInteger();
            assertThat(fixture.producer.process(exchange, ignored -> callbacks.incrementAndGet())).isTrue();
            assertThat(callbacks.get()).isEqualTo(1);
            assertThat(exchange.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
            assertThat(exchange.getMessage().getHeader(OpaConstants.DECISION)).isNull();
        }
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final OpaEndpoint endpoint;
        final OpaProducer producer;

        Fixture(OPAClient client) throws Exception {
            context.start();
            var configuration = new OpaConfiguration();
            configuration.setOpaClient(client);
            configuration.setAsyncProcessing(true);
            configuration.setFailOpen(true);
            endpoint = new OpaEndpoint("opa:authz/allow", new OpaComponent(context), configuration);
            endpoint.setPolicyPath("authz/allow");
            endpoint.start();
            producer = new OpaProducer(endpoint);
            producer.start();
        }

        @Override
        public void close() throws Exception {
            producer.stop();
            endpoint.stop();
            context.close();
        }
    }
}
