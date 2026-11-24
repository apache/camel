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
package org.apache.camel.component.openfga.security;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.component.openfga.OpenFgaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.security.AuthorizationDecisionEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenFgaAsyncSecurityPolicyTest {
    @Test
    void guardReleasesCallerAndPreservesDownstreamAsyncCallback() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientCheckResponse> pending = new CompletableFuture<>();
        when(client.check(any(), any())).thenReturn(pending);
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            AtomicInteger callbacks = new AtomicInteger();
            assertThat(fixture.processor.process(exchange, doneSync -> {
                assertThat(doneSync).isFalse();
                callbacks.incrementAndGet();
            })).isFalse();
            assertThat(callbacks).hasValue(0);
            ClientCheckResponse allowed = mock(ClientCheckResponse.class);
            when(allowed.getAllowed()).thenReturn(true);
            pending.complete(allowed);
            assertThat(fixture.downstream.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(callbacks).hasValue(0);
            assertThat(fixture.forwarded.get()).isSameAs(exchange);
            fixture.downstreamCallback.get().done(false);
            assertThat(callbacks).hasValue(1);
            assertThat(exchange.getException()).isNull();
        } finally {
            pending.cancel(true);
        }
    }

    @Test
    void deniedGuardFailsClosedWithoutCallingDownstream() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientCheckResponse denied = mock(ClientCheckResponse.class);
        when(denied.getAllowed()).thenReturn(false);
        when(client.check(any(), any())).thenReturn(CompletableFuture.completedFuture(denied));
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            CountDownLatch completed = new CountDownLatch(1);
            fixture.processor.process(exchange, doneSync -> completed.countDown());
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exchange.getException()).isInstanceOf(CamelAuthorizationException.class);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.ALLOWED))
                    .isEqualTo(false);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.DENY_REASON))
                    .isEqualTo("denied");
            assertThat(exchange.getMessage().getHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID))
                    .isEqualTo("OpenFgaSecurityPolicy");
            assertThat(fixture.forwarded.get()).isNull();
        }
    }

    @Test
    void stoppedPolicyClearsClaimedDecisionHeadersAndNeverCallsDownstream() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        try (Fixture fixture = new Fixture(client)) {
            fixture.processor.stop();
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(OpenFgaConstants.ALLOWED, true);
            exchange.getMessage().setHeader(OpenFgaConstants.FAILED_OPEN, true);
            AtomicInteger callbacks = new AtomicInteger();
            assertThat(fixture.processor.process(exchange, sync -> callbacks.incrementAndGet())).isTrue();
            assertThat(callbacks).hasValue(1);
            assertThat(exchange.getException()).isInstanceOf(CamelAuthorizationException.class);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.FAILED_OPEN))
                    .isNull();
            assertThat(fixture.forwarded.get()).isNull();
            verifyNoInteractions(client);
        }
    }

    @Test
    void interruptedSynchronousCallerGetsTheDecisionOnItsOwnThread() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientCheckResponse> pending = new CompletableFuture<>();
        ClientCheckResponse allowed = mock(ClientCheckResponse.class);
        when(allowed.getAllowed()).thenReturn(true);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        when(client.check(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            return checks.getAndIncrement() == 0 ? pending : CompletableFuture.completedFuture(allowed);
        });
        try (Fixture fixture = new Fixture(client)) {
            List<AuthorizationDecisionEvent> events = observe(fixture.context);
            Exchange exchange = new DefaultExchange(fixture.context);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    fixture.processor.process(exchange);
                } catch (Throwable e) {
                    thrown.set(e);
                }
            }, "interrupted-caller");
            caller.start();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(5));
            assertThat(caller.isAlive()).isFalse();
            assertThat(thrown.get()).isNull();
            // the caller's thread recorded the denied decision before process(Exchange) returned
            Throwable failure = exchange.getException();
            assertThat(failure).isInstanceOf(CamelAuthorizationException.class)
                    .hasMessageContaining("Could not ask OpenFGA")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(exchange.getMessage().getHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID))
                    .isEqualTo("OpenFgaSecurityPolicy");
            assertThat(fixture.forwarded.get()).isNull();
            // the claimed task cancels its request and frees its capacity without writing to the exchange again
            Exchange next = new DefaultExchange(fixture.context);
            assertThat(fixture.processor.process(next, doneSync -> {
            })).isFalse();
            assertThat(fixture.downstream.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.forwarded.get()).isSameAs(next);
            assertThat(pending.isCancelled()).isTrue();
            assertThat(exchange.getException()).isSameAs(failure);
            // the claimed task told the check it was abandoned: one interrupted decision, then the next one's event
            assertThat(events).hasSize(2);
            assertThat(events.get(0).getOutcome()).isEqualTo(AuthorizationDecisionEvent.Outcome.ERROR);
            assertThat(events.get(0).getFailure()).isEqualTo(AuthorizationDecisionEvent.Failure.INTERRUPTED);
            assertThat(events.get(0).getExchangeId()).isEqualTo(exchange.getExchangeId());
        } finally {
            pending.cancel(true);
        }
    }

    @Test
    void asynchronousGuardPublishesADecisionEvent() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientCheckResponse allowed = mock(ClientCheckResponse.class);
        when(allowed.getAllowed()).thenReturn(true);
        when(client.check(any(), any())).thenReturn(CompletableFuture.completedFuture(allowed));
        try (Fixture fixture = new Fixture(client)) {
            List<AuthorizationDecisionEvent> events = observe(fixture.context);
            Exchange exchange = new DefaultExchange(fixture.context);
            assertThat(fixture.processor.process(exchange, doneSync -> {
            })).isFalse();
            assertThat(fixture.downstream.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getEngine()).isEqualTo(AuthorizationDecisionEvent.Engine.OPENFGA);
            assertThat(events.get(0).getOperation()).isEqualTo(AuthorizationDecisionEvent.Operation.DECISION);
            assertThat(events.get(0).getOutcome()).isEqualTo(AuthorizationDecisionEvent.Outcome.ALLOW);
            assertThat(events.get(0).getExchangeId()).isEqualTo(exchange.getExchangeId());
        }
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final CountDownLatch downstream = new CountDownLatch(1);
        final AtomicReference<Exchange> forwarded = new AtomicReference<>();
        final AtomicReference<AsyncCallback> downstreamCallback = new AtomicReference<>();
        final OpenFgaSecurityProcessor processor;

        Fixture(OpenFgaClient client) throws Exception {
            context.start();
            var policy = new OpenFgaSecurityPolicy();
            policy.setOpenFgaClient(client);
            policy.setStoreId("store");
            policy.setUser("user:anne");
            policy.setRelation("reader");
            policy.setObject("document:a");
            policy.setHealthCheckEnabled(false);
            policy.setAsyncProcessing(true);
            Route route = mock(Route.class);
            when(route.getCamelContext()).thenReturn(context);
            when(route.getRouteId()).thenReturn("guard");
            policy.beforeWrap(route, null);
            var next = new AsyncProcessorSupport() {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    forwarded.set(exchange);
                    downstreamCallback.set(callback);
                    downstream.countDown();
                    return false;
                }
            };
            processor = new OpenFgaSecurityProcessor(next, policy);
            processor.start();
        }

        @Override
        public void close() throws Exception {
            processor.stop();
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
