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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.spiffe.exception.JwtSvidException;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.jwtsvid.JwtSvid;
import io.spiffe.workloadapi.DefaultWorkloadApiClient;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiffeJwtValidationTest {
    private static final String CALLER = "spiffe://example.org/caller";
    private static final String AUDIENCE = "spiffe://example.org/api";
    private static final String TOKEN = "dummy-token";

    @Test
    void preservesAPojoAndPublishesATokenFreeIdentitySnapshot() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            JwtSvid svid = svid(CALLER);
            List<String> roles = new ArrayList<>(List.of("reader"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("roles", roles);
            when(svid.getClaims()).thenReturn(claims);
            when(svid.getToken()).thenReturn("private-token-value");
            when(client.validateJwtSvid(TOKEN, AUDIENCE)).thenReturn(svid);
            Object body = new Object();
            Exchange exchange = exchange(context, body, true);
            producer(context, client, true, AUDIENCE).process(exchange);

            SpiffeJwtIdentity identity = exchange.getProperty(SpiffeConstants.JWT_IDENTITY, SpiffeJwtIdentity.class);
            assertThat(exchange.getMessage().getBody()).isSameAs(body);
            assertThat(identity.getSpiffeId()).isEqualTo(CALLER);
            assertThat(identity.getAudience()).containsExactly(AUDIENCE);
            assertThat(identity.getExpiry()).isEqualTo(svid.getExpiry().toInstant());
            roles.add("writer");
            claims.put("extra", "secret");
            assertThat(identity.getClaims()).containsOnlyKeys("roles");
            assertThat(identity.getClaims().get("roles")).isEqualTo(List.of("reader"));
            assertThat(identity.toString()).doesNotContain("reader", "roles", "private-token-value");
            assertThatThrownBy(() -> identity.getClaims().put("extra", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void preservesAStreamWithAndWithoutAnAuthorizationHeader() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            doReturn(svid(CALLER)).when(client).validateJwtSvid(TOKEN, AUDIENCE);
            byte[] payload = "application payload".getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream body = new ByteArrayInputStream(payload);
            Exchange exchange = exchange(context, body, true);
            SpiffeProducer producer = producer(context, client, true, AUDIENCE);
            producer.process(exchange);
            assertThat(exchange.getMessage().getBody()).isSameAs(body);
            assertThat(body.available()).isEqualTo(payload.length);

            exchange.getMessage().removeHeader("Authorization");
            assertThatThrownBy(() -> producer.process(exchange)).isInstanceOf(IllegalArgumentException.class);
            assertThat(exchange.getMessage().getBody()).isSameAs(body);
            assertThat(body.available()).isEqualTo(payload.length);
            assertThat(exchange.getProperty(SpiffeConstants.JWT_IDENTITY)).isNull();
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isNull();
        }
    }

    @Test
    void retainsTheExistingDefaultBodyResponseAndTokenBodyFallback() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            JwtSvid svid = svid(CALLER);
            when(client.validateJwtSvid(TOKEN, AUDIENCE)).thenReturn(svid);
            Exchange exchange = exchange(context, TOKEN, false);
            producer(context, client, false, AUDIENCE).process(exchange);
            assertThat(exchange.getMessage().getBody()).isSameAs(svid);
            assertThat(exchange.getProperty(SpiffeConstants.JWT_IDENTITY, SpiffeJwtIdentity.class).getSpiffeId())
                    .isEqualTo(CALLER);
        }
    }

    @Test
    void rejectedTokensClearEarlierVerifiedOutputsWithoutChangingTheBody() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            SpiffeProducer producer = producer(context, client, true, AUDIENCE);
            for (String reason : List.of("invalid signature", "expired token", "wrong audience")) {
                doThrow(new JwtSvidException(reason)).when(client).validateJwtSvid(TOKEN, AUDIENCE);
                Object body = new Object();
                Exchange exchange = exchange(context, body, true);
                exchange.setProperty(SpiffeConstants.JWT_IDENTITY, "stale");
                exchange.getMessage().setHeader(SpiffeConstants.SPIFFE_ID, CALLER);
                exchange.getMessage().setHeader(SpiffeConstants.EXPIRY, new Date());
                assertThatThrownBy(() -> producer.process(exchange)).isInstanceOf(JwtSvidException.class);
                assertThat(exchange.getMessage().getBody()).isSameAs(body);
                assertThat(exchange.getProperty(SpiffeConstants.JWT_IDENTITY)).isNull();
                assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isNull();
                assertThat(exchange.getMessage().getHeader(SpiffeConstants.EXPIRY)).isNull();
            }
        }
    }

    @Test
    void triesConfiguredAudiencesAndIgnoresTheMessageAudience() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            when(client.validateJwtSvid(TOKEN, "first")).thenThrow(new JwtSvidException("wrong audience"));
            doReturn(svid(CALLER)).when(client).validateJwtSvid(TOKEN, AUDIENCE);
            Exchange exchange = exchange(context, "payload", true);
            exchange.getMessage().setHeader(SpiffeConstants.AUDIENCE, "message-selected-audience");
            producer(context, client, true, "first, " + AUDIENCE).process(exchange);
            verify(client).validateJwtSvid(TOKEN, "first");
            verify(client).validateJwtSvid(TOKEN, AUDIENCE);
            verify(client, never()).validateJwtSvid(TOKEN, "message-selected-audience");
            assertThat(exchange.getMessage().getBody()).isEqualTo("payload");
        }
    }

    @Test
    void policyPreservesBodyAndStopsTheGuardedProcessorOnEveryDenial() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            SpiffeJwtSecurityPolicy policy = new SpiffeJwtSecurityPolicy();
            policy.setAudience(AUDIENCE);
            policy.setAcceptedSpiffeIds(CALLER);
            policy.setWorkloadApiClient(client);
            Route route = mock(Route.class);
            when(route.getCamelContext()).thenReturn(context);
            policy.beforeWrap(route, null);
            AtomicInteger received = new AtomicInteger();
            Processor wrapped = policy.wrap(route, e -> received.incrementAndGet());
            ServiceHelper.startService(wrapped);
            try {
                doReturn(svid(CALLER)).when(client).validateJwtSvid(TOKEN, AUDIENCE);
                Exchange allowed = exchange(context, "payload", true);
                ((AsyncProcessor) wrapped).process(allowed, done -> {
                });
                assertThat(received).hasValue(1);
                assertThat(allowed.getMessage().getBody()).isEqualTo("payload");
                assertThat(allowed.getProperty(SpiffeConstants.JWT_IDENTITY)).isInstanceOf(SpiffeJwtIdentity.class);

                for (String reason : List.of("missing", "unlisted", "invalid", "expired", "wrong-audience")) {
                    Exchange denied = exchange(context, "payload", !reason.equals("missing"));
                    denied.setProperty(SpiffeConstants.JWT_IDENTITY, "stale");
                    if (reason.equals("unlisted")) {
                        doReturn(svid("spiffe://example.org/other")).when(client).validateJwtSvid(TOKEN, AUDIENCE);
                    } else if (!reason.equals("missing")) {
                        doThrow(new JwtSvidException(reason)).when(client).validateJwtSvid(TOKEN, AUDIENCE);
                    }
                    ((AsyncProcessor) wrapped).process(denied, done -> {
                    });
                    assertThat(denied.getException()).isInstanceOf(CamelAuthorizationException.class);
                    assertThat(denied.getProperty(SpiffeConstants.JWT_IDENTITY)).isNull();
                    assertThat(denied.getMessage().getBody()).isEqualTo("payload");
                    assertThat(received).hasValue(1);
                }
            } finally {
                ServiceHelper.stopService(wrapped);
            }
            verify(client, never()).close();
        }
    }

    @Test
    void policyRequiresAudienceAndAnExplicitIdentityRule() {
        SpiffeJwtSecurityPolicy policy = new SpiffeJwtSecurityPolicy();
        assertThatThrownBy(() -> policy.beforeWrap(null, null)).isInstanceOf(IllegalArgumentException.class);
        policy.setAudience(AUDIENCE);
        assertThatThrownBy(() -> policy.beforeWrap(null, null)).isInstanceOf(IllegalStateException.class);
        policy.setAcceptAnySpiffeId(true);
        policy.beforeWrap(null, null);
        policy.setAcceptedSpiffeIds(CALLER);
        assertThatThrownBy(() -> policy.beforeWrap(null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void policyClosesItsOwnedClientAndRecreatesItOnRestart() throws Exception {
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultWorkloadApiClient> factory = mockStatic(DefaultWorkloadApiClient.class)) {
            WorkloadApiClient first = mock(WorkloadApiClient.class);
            WorkloadApiClient second = mock(WorkloadApiClient.class);
            factory.when(DefaultWorkloadApiClient::newClient).thenReturn(first, second);
            SpiffeJwtSecurityPolicy policy = new SpiffeJwtSecurityPolicy();
            policy.setAudience(AUDIENCE);
            policy.setAcceptAnySpiffeId(true);
            Route route = mock(Route.class);
            when(route.getCamelContext()).thenReturn(context);
            policy.beforeWrap(route, null);
            Processor wrapped = policy.wrap(route, e -> {
            });

            ServiceHelper.startService(wrapped);
            ServiceHelper.stopService(wrapped);
            verify(first).close();
            ServiceHelper.startService(wrapped);
            ServiceHelper.stopService(wrapped);
            verify(first).close();
            verify(second).close();
        }
    }

    private static JwtSvid svid(String caller) {
        JwtSvid svid = mock(JwtSvid.class);
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse(caller));
        when(svid.getExpiry()).thenReturn(new Date(2000000000000L));
        when(svid.getAudience()).thenReturn(Set.of(AUDIENCE));
        return svid;
    }

    private static Exchange exchange(CamelContext context, Object body, boolean authorization) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        if (authorization) {
            exchange.getMessage().setHeader("Authorization", "Bearer " + TOKEN);
        }
        return exchange;
    }

    private static SpiffeProducer producer(CamelContext context, WorkloadApiClient client, boolean preserve, String audience) {
        SpiffeConfiguration configuration = new SpiffeConfiguration();
        configuration.setOperation(SpiffeOperation.validateJwtSvid);
        configuration.setAudience(audience);
        configuration.setPreserveBody(preserve);
        SpiffeEndpoint endpoint = new SpiffeEndpoint("spiffe:validation", new SpiffeComponent(context), configuration) {
            @Override
            public WorkloadApiClient getWorkloadApiClient() {
                return client;
            }
        };
        return new SpiffeProducer(endpoint);
    }
}
