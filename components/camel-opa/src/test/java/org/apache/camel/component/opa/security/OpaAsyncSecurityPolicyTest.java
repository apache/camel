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
package org.apache.camel.component.opa.security;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.styra.opa.OPAClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpaAsyncSecurityPolicyTest {
    @Test
    void guardReleasesCallerAndPreservesDownstreamAsyncCallback() throws Exception {
        OPAClient client = mock(OPAClient.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.evaluate(eq("authz/allow"), org.mockito.ArgumentMatchers.anyMap(), eq(Object.class)))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return true;
                });
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            AtomicInteger callbacks = new AtomicInteger();
            assertThat(fixture.processor.process(exchange, doneSync -> {
                assertThat(doneSync).isFalse();
                callbacks.incrementAndGet();
            })).isFalse();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(callbacks).hasValue(0);
            release.countDown();
            assertThat(fixture.downstream.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(callbacks).hasValue(0);
            assertThat(fixture.forwarded.get()).isSameAs(exchange);
            fixture.downstreamCallback.get().done(false);
            assertThat(callbacks).hasValue(1);
            assertThat(exchange.getException()).isNull();
        } finally {
            release.countDown();
        }
    }

    @Test
    void deniedGuardFailsClosedWithoutCallingDownstream() throws Exception {
        OPAClient client = mock(OPAClient.class);
        when(client.evaluate(eq("authz/allow"), org.mockito.ArgumentMatchers.anyMap(), eq(Object.class))).thenReturn(false);
        try (Fixture fixture = new Fixture(client)) {
            Exchange exchange = new DefaultExchange(fixture.context);
            CountDownLatch completed = new CountDownLatch(1);
            fixture.processor.process(exchange, doneSync -> completed.countDown());
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exchange.getException()).isInstanceOf(CamelAuthorizationException.class);
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.opa.OpaConstants.DECISION_ALLOW))
                    .isEqualTo(false);
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.opa.OpaConstants.DECISION))
                    .isEqualTo(false);
            assertThat(exchange.getMessage().getHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID))
                    .isEqualTo("OpaSecurityPolicy");
            assertThat(fixture.forwarded.get()).isNull();
        }
    }

    @Test
    void stoppedGuardClearsClaimedDecisionAndCannotContinue() throws Exception {
        OPAClient client = mock(OPAClient.class);
        try (Fixture fixture = new Fixture(client)) {
            fixture.processor.stop();
            Exchange exchange = new DefaultExchange(fixture.context);
            exchange.getMessage().setHeader(org.apache.camel.component.opa.OpaConstants.DECISION_ALLOW, true);
            exchange.getMessage().setHeader(org.apache.camel.component.opa.OpaConstants.DECISION, true);
            java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
            assertThat(fixture.processor.process(exchange, ignored -> callbacks.incrementAndGet())).isTrue();
            assertThat(callbacks.get()).isEqualTo(1);
            assertThat(exchange.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.opa.OpaConstants.DECISION_ALLOW)).isNull();
            assertThat(exchange.getMessage().getHeader(org.apache.camel.component.opa.OpaConstants.DECISION)).isNull();
            assertThat(fixture.forwarded.get()).isNull();
        }
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final CountDownLatch downstream = new CountDownLatch(1);
        final AtomicReference<Exchange> forwarded = new AtomicReference<>();
        final AtomicReference<AsyncCallback> downstreamCallback = new AtomicReference<>();
        final OpaSecurityProcessor processor;

        Fixture(OPAClient client) throws Exception {
            context.start();
            var policy = new OpaSecurityPolicy();
            policy.setOpaClient(client);
            policy.setPolicyPath("authz/allow");
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
            processor = new OpaSecurityProcessor(next, policy);
            processor.start();
        }

        @Override
        public void close() throws Exception {
            processor.stop();
            context.close();
        }
    }
}
