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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaAsyncSecurityPolicyTest {
    @Test
    void guardReleasesCallerAndPreservesDownstreamAsyncCallback() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        java.util.concurrent.CompletableFuture<ClientCheckResponse> pending = new java.util.concurrent.CompletableFuture<>();
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
        when(client.check(any(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(denied));
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            CountDownLatch completed = new CountDownLatch(1);
            fixture.processor.process(exchange, doneSync -> completed.countDown());
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exchange.getException()).isInstanceOf(CamelAuthorizationException.class);
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.openfga.OpenFgaConstants.ALLOWED))
                    .isEqualTo(false);
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.openfga.OpenFgaConstants.DENY_REASON))
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
            exchange.getMessage().setHeader(org.apache.camel.component.openfga.OpenFgaConstants.ALLOWED, true);
            exchange.getMessage().setHeader(org.apache.camel.component.openfga.OpenFgaConstants.FAILED_OPEN, true);
            AtomicInteger callbacks = new AtomicInteger();
            assertThat(fixture.processor.process(exchange, sync -> callbacks.incrementAndGet())).isTrue();
            assertThat(callbacks).hasValue(1);
            assertThat(exchange.getException()).isInstanceOf(CamelAuthorizationException.class);
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.openfga.OpenFgaConstants.ALLOWED)).isNull();
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.openfga.OpenFgaConstants.FAILED_OPEN))
                    .isNull();
            assertThat(fixture.forwarded.get()).isNull();
            org.mockito.Mockito.verifyNoInteractions(client);
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
}
