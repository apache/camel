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
package org.apache.camel.component.openfga;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaAuthorizationObservationTest {
    @Test
    void observesDecisionsAndConfiguredModelWithoutSubjectsOrResources() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ClientCheckResponse allowed = mock(ClientCheckResponse.class);
            when(allowed.getAllowed()).thenReturn(true);
            ClientCheckResponse denied = mock(ClientCheckResponse.class);
            when(denied.getAllowed()).thenReturn(false);
            when(fixture.client.check(any(), any())).thenReturn(CompletableFuture.completedFuture(allowed))
                    .thenReturn(CompletableFuture.completedFuture(denied))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("secret-error")));
            OpenFgaAuthorizer authorizer = new OpenFgaAuthorizer(fixture.client, fixture.configuration, fixture.context);
            Exchange exchange = new DefaultExchange(fixture.context);
            assertThat(authorizer.check(exchange)).isTrue();
            assertThat(authorizer.check(exchange)).isFalse();
            assertThatThrownBy(() -> authorizer.check(exchange)).isInstanceOf(OpenFgaEvaluationException.class);
            assertThat(fixture.events).extracting(AuthorizationDecisionEvent::getOutcome)
                    .containsExactly(Outcome.ALLOW, Outcome.DENY, Outcome.ERROR);
            assertThat(fixture.events).allSatisfy(event -> {
                assertThat(event.getRevision()).isEqualTo("model-1");
                assertThat(event.asJSon().toString()).doesNotContain("user:secret", "document:secret", "secret-error");
                assertThat(event.getExchangeId()).isEqualTo(exchange.getExchangeId());
            });
        }
    }

    @Test
    void unavailableFailedOpenAndInputDenyAreDistinct() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.client.check(any(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("offline")));
            OpenFgaAuthorizer closed = new OpenFgaAuthorizer(fixture.client, fixture.configuration, fixture.context);
            assertThatThrownBy(() -> closed.check(new DefaultExchange(fixture.context)))
                    .isInstanceOf(OpenFgaEvaluationException.class);
            fixture.configuration.setFailOpen(true);
            OpenFgaAuthorizer open = new OpenFgaAuthorizer(fixture.client, fixture.configuration, fixture.context);
            assertThat(open.check(new DefaultExchange(fixture.context))).isTrue();
            fixture.configuration.setUser("${header.missing}");
            OpenFgaAuthorizer missing = new OpenFgaAuthorizer(fixture.client, fixture.configuration, fixture.context);
            assertThat(missing.check(new DefaultExchange(fixture.context))).isFalse();
            assertThat(fixture.events).extracting(AuthorizationDecisionEvent::getOutcome)
                    .containsExactly(Outcome.UNAVAILABLE, Outcome.FAILED_OPEN, Outcome.DENY);
        }
    }

    @Test
    void partialBatchFailureDoesNotClaimTheFilterWasSuccessful() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var a = item("document:a", true, null);
            var b = item("document:b", false, null);
            var c = item("document:c", false, new IllegalStateException("secret-error"));
            when(fixture.client.clientBatchCheck(anyList(), any()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(a, b, c)));
            var endpoint
                    = new OpenFgaEndpoint("openfga:batchCheck", new OpenFgaComponent(fixture.context), fixture.configuration);
            endpoint.setOperation(OpenFgaOperation.batchCheck);
            endpoint.start();
            var producer = new OpenFgaProducer(endpoint);
            producer.start();
            try {
                Exchange exchange = new DefaultExchange(fixture.context);
                exchange.getMessage().setBody(List.of("document:a", "document:b", "document:c"));
                assertThatThrownBy(() -> producer.process(exchange)).isInstanceOf(OpenFgaEvaluationException.class);
                assertThat(fixture.events).hasSize(1);
                assertThat(fixture.events.getFirst().getOutcome()).isEqualTo(Outcome.ERROR);
                assertThat(fixture.events.getFirst().getCounts())
                        .isEqualTo(new AuthorizationDecisionEvent.Counts(1, 1, 1, 0, 0));
            } finally {
                producer.stop();
                endpoint.stop();
            }
        }
    }

    private static ClientBatchCheckClientResponse item(String object, boolean allowed, Throwable error) {
        var item = mock(ClientBatchCheckClientResponse.class);
        when(item.getRequest()).thenReturn(new ClientCheckRequest()._object(object));
        when(item.getAllowed()).thenReturn(allowed);
        when(item.getThrowable()).thenReturn(error);
        return item;
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final OpenFgaClient client = mock(OpenFgaClient.class);
        final OpenFgaConfiguration configuration = new OpenFgaConfiguration();
        final List<AuthorizationDecisionEvent> events = new ArrayList<>();

        Fixture() throws Exception {
            configuration.setOpenFgaClient(client);
            configuration.setUser("user:secret");
            configuration.setRelation("reader");
            configuration.setObject("document:secret");
            configuration.setAuthorizationModelId("model-1");
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
