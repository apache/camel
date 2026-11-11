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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientCheckResponse;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaAsyncProducerTest {
    @Test
    void oneSubmissionWorkerCanHaveMultipleSdkRequestsPendingWithIndependentCapturedInputs() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        var first = new CompletableFuture<ClientCheckResponse>();
        var second = new CompletableFuture<ClientCheckResponse>();
        CountDownLatch submitted = new CountDownLatch(2);
        java.util.concurrent.ConcurrentHashMap<String, String> users = new java.util.concurrent.ConcurrentHashMap<>();
        when(client.check(any(), any())).thenAnswer(invocation -> {
            ClientCheckRequest request = invocation.getArgument(0);
            users.put(request.getObject(), request.getUser());
            submitted.countDown();
            return request.getObject().equals("document:first") ? first : second;
        });
        try (Fixture fixture = new Fixture(client, configuration -> {
            configuration.setUser("${header.subject}");
            configuration.setObject("${header.resource}");
        })) {
            Exchange a = fixture.exchange();
            a.getMessage().setHeader("subject", "user:anne");
            a.getMessage().setHeader("resource", "document:first");
            Exchange b = fixture.exchange();
            b.getMessage().setHeader("subject", "user:bob");
            b.getMessage().setHeader("resource", "document:second");
            CountDownLatch completed = new CountDownLatch(2);
            AtomicInteger calls = new AtomicInteger();
            assertThat(fixture.producer.process(a, doneSync -> {
                calls.incrementAndGet();
                completed.countDown();
            })).isFalse();
            assertThat(fixture.producer.process(b, doneSync -> {
                calls.incrementAndGet();
                completed.countDown();
            })).isFalse();
            a.getMessage().setHeader("subject", "user:changed-after-admission");
            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(0);
            assertThat(users).containsEntry("document:first", "user:anne").containsEntry("document:second", "user:bob");
            second.complete(answer(false));
            first.complete(answer(true));
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(2);
            assertThat(a.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
            assertThat(b.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(false);
        }
    }

    @Test
    void sdkFailureAndFailedOpenRemainEquivalentToTheSynchronousDecision() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.check(any(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("offline")));
        try (Fixture fixture = new Fixture(client, configuration -> configuration.setFailOpen(true))) {
            Exchange result = fixture.producer.processAsync(fixture.exchange()).get(5, TimeUnit.SECONDS);
            assertThat(result.getException()).isNull();
            assertThat(result.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
            assertThat(result.getMessage().getHeader(OpenFgaConstants.FAILED_OPEN)).isEqualTo(true);
        }
        try (Fixture fixture = new Fixture(client, configuration -> {
        })) {
            Exchange result = fixture.producer.processAsync(fixture.exchange()).get(5, TimeUnit.SECONDS);
            assertThat(result.getException()).isInstanceOf(OpenFgaEvaluationException.class).hasRootCauseMessage("offline");
            assertThat(result.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
        }
    }

    @Test
    void explicitCancellationPropagatesThroughMappedStagesToTheSdkFuture() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientCheckResponse> pending = new CompletableFuture<>();
        CountDownLatch submitted = new CountDownLatch(1);
        when(client.check(any(), any())).thenAnswer(invocation -> {
            submitted.countDown();
            return pending;
        });
        try (Fixture fixture = new Fixture(client, configuration -> {
        })) {
            Exchange exchange = fixture.exchange();
            var result = fixture.producer.processAsync(exchange);
            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(result.cancel(true)).isTrue();
            org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS).until(pending::isCancelled);
            assertThat(exchange.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
        }
    }

    @Test
    void totalDeadlineFailsClosedAndBoundsOutstandingRequests() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientCheckResponse> pending = new CompletableFuture<>();
        when(client.check(any(), any())).thenReturn(pending);
        try (Fixture fixture = new Fixture(client, configuration -> {
            configuration.setAsyncTimeout(200);
            configuration.setFailOpen(true);
        })) {
            Exchange exchange = fixture.producer.processAsync(fixture.exchange()).get(5, TimeUnit.SECONDS);
            assertThat(exchange.getException()).isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.FAILED_OPEN)).isNull();
            assertThat(pending.isCancelled()).isTrue();
        }
    }

    @Test
    void pendingFuturesConsumeBoundedCapacityAndStopCancelsEachExactlyOnce() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        var a = new CompletableFuture<ClientCheckResponse>();
        var b = new CompletableFuture<ClientCheckResponse>();
        when(client.check(any(), any())).thenReturn(a).thenReturn(b);
        try (Fixture fixture = new Fixture(client, configuration -> configuration.setAsyncQueueSize(1))) {
            CountDownLatch completed = new CountDownLatch(2);
            AtomicInteger callbacks = new AtomicInteger();
            Exchange first = fixture.exchange();
            Exchange second = fixture.exchange();
            fixture.producer.process(first, doneSync -> {
                callbacks.incrementAndGet();
                completed.countDown();
            });
            fixture.producer.process(second, doneSync -> {
                callbacks.incrementAndGet();
                completed.countDown();
            });
            Exchange third = fixture.exchange();
            AtomicInteger rejected = new AtomicInteger();
            assertThat(fixture.producer.process(third, doneSync -> rejected.incrementAndGet())).isTrue();
            assertThat(third.getException()).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            fixture.producer.stop();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(callbacks).hasValue(2);
            assertThat(rejected).hasValue(1);
            assertThat(first.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
            assertThat(second.getException()).isInstanceOf(java.util.concurrent.CancellationException.class);
        }
    }

    @Test
    void synchronousTemplateFacadeUsesTheFutureBridge() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        ClientCheckResponse allowed = answer(true);
        when(client.check(any(), any())).thenReturn(CompletableFuture.completedFuture(allowed));
        try (Fixture fixture = new Fixture(client, configuration -> {
        }); var template = fixture.context.createProducerTemplate()) {
            Exchange result = template.request(fixture.endpoint, exchange -> exchange.getMessage().setBody("payload"));
            assertThat(result.getException()).isNull();
            assertThat(result.getMessage().getBody()).isEqualTo("payload");
            assertThat(result.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isEqualTo(true);
        }
    }

    @Test
    void queryAndMutationOperationsPreserveTheirResponseContracts() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        var objects = mock(dev.openfga.sdk.api.client.model.ClientListObjectsResponse.class);
        when(objects.getObjects()).thenReturn(List.of("document:a"));
        when(client.listObjects(any(), any())).thenReturn(CompletableFuture.completedFuture(objects));
        var relations = mock(dev.openfga.sdk.api.client.model.ClientListRelationsResponse.class);
        when(relations.getRelations()).thenReturn(List.of("writer", "reader"));
        when(client.listRelations(any(), any())).thenReturn(CompletableFuture.completedFuture(relations));
        var users = mock(dev.openfga.sdk.api.client.model.ClientListUsersResponse.class);
        var user = mock(dev.openfga.sdk.api.model.User.class);
        when(user.getObject()).thenReturn(new dev.openfga.sdk.api.model.FgaObject().type("user").id("anne"));
        when(users.getUsers()).thenReturn(List.of(user));
        when(client.listUsers(any(), any())).thenReturn(CompletableFuture.completedFuture(users));
        var tuples = mock(dev.openfga.sdk.api.client.model.ClientReadResponse.class);
        when(tuples.getTuples()).thenReturn(List.of(new dev.openfga.sdk.api.model.Tuple()
                .key(new dev.openfga.sdk.api.model.TupleKey().user("user:anne").relation("reader")._object("document:a"))));
        when(tuples.getContinuationToken()).thenReturn("tuple-next");
        when(client.read(any(), any())).thenReturn(CompletableFuture.completedFuture(tuples));
        var changes = mock(dev.openfga.sdk.api.client.model.ClientReadChangesResponse.class);
        when(changes.getChanges()).thenReturn(List.of(new dev.openfga.sdk.api.model.TupleChange()
                .tupleKey(new dev.openfga.sdk.api.model.TupleKey().user("user:anne").relation("reader")._object("document:a"))
                .operation(dev.openfga.sdk.api.model.TupleOperation.WRITE)));
        when(changes.getContinuationToken()).thenReturn("changes-next");
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(changes));
        var expanded = mock(dev.openfga.sdk.api.client.model.ClientExpandResponse.class);
        var tree = new dev.openfga.sdk.api.model.UsersetTree();
        when(expanded.getTree()).thenReturn(tree);
        when(client.expand(any(), any())).thenReturn(CompletableFuture.completedFuture(expanded));
        var write = mock(dev.openfga.sdk.api.client.model.ClientWriteResponse.class);
        when(client.writeTuples(any())).thenReturn(CompletableFuture.completedFuture(write));
        when(client.deleteTuples(any())).thenReturn(CompletableFuture.completedFuture(write));
        for (OpenFgaOperation operation : List.of(OpenFgaOperation.listObjects, OpenFgaOperation.listRelations,
                OpenFgaOperation.listUsers, OpenFgaOperation.readTuples, OpenFgaOperation.readChanges,
                OpenFgaOperation.expand, OpenFgaOperation.writeTuples, OpenFgaOperation.deleteTuples)) {
            try (Fixture fixture = new Fixture(client, configuration -> {
            }, operation)) {
                Exchange exchange = fixture.exchange();
                exchange.getMessage().setBody("unchanged");
                Exchange result = fixture.producer.processAsync(exchange).get(5, TimeUnit.SECONDS);
                assertThat(result.getException()).as(operation.name()).isNull();
                switch (operation) {
                    case listObjects -> assertThat(result.getMessage().getBody()).isEqualTo(List.of("document:a"));
                    case listRelations -> assertThat(result.getMessage().getBody()).isEqualTo(List.of("reader", "writer"));
                    case listUsers -> assertThat(result.getMessage().getBody()).isEqualTo(List.of("user:anne"));
                    case readTuples -> {
                        assertThat(result.getMessage().getBody(List.class)).hasSize(1);
                        assertThat(result.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN)).isEqualTo("tuple-next");
                    }
                    case readChanges -> {
                        assertThat(result.getMessage().getBody(List.class)).hasSize(1);
                        assertThat(result.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN))
                                .isEqualTo("changes-next");
                    }
                    case expand -> assertThat(result.getMessage().getBody()).isSameAs(tree);
                    case writeTuples -> {
                        assertThat(result.getMessage().getBody()).isEqualTo("unchanged");
                        assertThat(result.getMessage().getHeader(OpenFgaConstants.WRITTEN_TUPLES)).isEqualTo(1);
                    }
                    case deleteTuples -> {
                        assertThat(result.getMessage().getBody()).isEqualTo("unchanged");
                        assertThat(result.getMessage().getHeader(OpenFgaConstants.DELETED_TUPLES)).isEqualTo(1);
                    }
                    default -> throw new AssertionError(operation);
                }
            }
        }
    }

    @Test
    void asynchronousBatchPreservesDuplicatesAndFailsOnPartialSdkFailure() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        var permitted = mock(dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse.class);
        when(permitted.getRequest()).thenReturn(new ClientCheckRequest()._object("document:a"));
        when(permitted.getAllowed()).thenReturn(true);
        var denied = mock(dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse.class);
        when(denied.getRequest()).thenReturn(new ClientCheckRequest()._object("document:b"));
        when(denied.getAllowed()).thenReturn(false);
        var failed = mock(dev.openfga.sdk.api.client.model.ClientBatchCheckClientResponse.class);
        when(failed.getThrowable()).thenReturn(new IOException("partial failure"));
        when(client.clientBatchCheck(org.mockito.ArgumentMatchers.anyList(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(denied, permitted)))
                .thenReturn(CompletableFuture.completedFuture(List.of(permitted, failed)));
        try (Fixture fixture = new Fixture(client, configuration -> {
        }, OpenFgaOperation.batchCheck)) {
            Exchange first = fixture.exchange();
            first.getMessage().setBody(List.of("document:a", "document:b", "document:a"));
            fixture.producer.processAsync(first).get(5, TimeUnit.SECONDS);
            assertThat(first.getException()).isNull();
            assertThat(first.getMessage().getBody()).isEqualTo(List.of("document:a", "document:a"));
            Exchange second = fixture.exchange();
            second.getMessage().setBody(List.of("document:a", "document:b"));
            fixture.producer.processAsync(second).get(5, TimeUnit.SECONDS);
            assertThat(second.getException()).isInstanceOf(OpenFgaEvaluationException.class)
                    .hasRootCauseMessage("partial failure");
            assertThat(second.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
        }
    }

    @Test
    void sdkCompletionCannotOverwriteAnAlreadyInterruptedCamelWait() throws Exception {
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientCheckResponse> pending = new CompletableFuture<>();
        CountDownLatch submitted = new CountDownLatch(1);
        when(client.check(any(), any())).thenAnswer(invocation -> {
            submitted.countDown();
            return pending;
        });
        try (Fixture fixture = new Fixture(client, configuration -> {
        })) {
            Exchange exchange = fixture.exchange();
            var completion = fixture.producer.processAsync(exchange);
            assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
            exchange.setException(new InterruptedException("Caller stopped waiting"));
            pending.complete(answer(true));
            completion.get(5, TimeUnit.SECONDS);
            assertThat(exchange.getException()).isInstanceOf(InterruptedException.class);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.ALLOWED)).isNull();
        }
    }

    private static ClientCheckResponse answer(boolean allowed) {
        ClientCheckResponse answer = mock(ClientCheckResponse.class);
        when(answer.getAllowed()).thenReturn(allowed);
        return answer;
    }

    private static class Fixture implements AutoCloseable {
        final DefaultCamelContext context = new DefaultCamelContext();
        final OpenFgaEndpoint endpoint;
        final OpenFgaProducer producer;

        Fixture(OpenFgaClient client, java.util.function.Consumer<OpenFgaConfiguration> configure) throws Exception {
            this(client, configure, OpenFgaOperation.check);
        }

        Fixture(OpenFgaClient client, java.util.function.Consumer<OpenFgaConfiguration> configure,
                OpenFgaOperation operation) throws Exception {
            context.start();
            var configuration = new OpenFgaConfiguration();
            configuration.setOpenFgaClient(client);
            configuration.setAsyncProcessing(true);
            configuration.setAsyncPoolSize(1);
            configuration.setAsyncQueueSize(2);
            configuration.setUser("user:anne");
            configuration.setRelation("reader");
            configuration.setObject("document:a");
            configuration.setType("document");
            configuration.setRelations("reader,writer");
            configure.accept(configuration);
            endpoint = new OpenFgaEndpoint("openfga:" + operation, new OpenFgaComponent(context), configuration);
            endpoint.setOperation(operation);
            endpoint.start();
            producer = new OpenFgaProducer(endpoint);
            producer.start();
        }

        Exchange exchange() {
            return new DefaultExchange(context);
        }

        @Override
        public void close() throws Exception {
            producer.stop();
            endpoint.stop();
            context.close();
        }
    }
}
