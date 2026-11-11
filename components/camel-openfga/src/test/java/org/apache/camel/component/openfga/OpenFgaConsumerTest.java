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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadChangesRequest;
import dev.openfga.sdk.api.client.model.ClientReadChangesResponse;
import dev.openfga.sdk.api.configuration.ClientReadChangesOptions;
import dev.openfga.sdk.api.model.TupleChange;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleOperation;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFgaConsumerTest {
    @Test
    void acknowledgesWholePageOnlyAfterProcessingAndResumesOnRestart() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("cursor-1", true)));
        AtomicInteger delivered = new AtomicInteger();
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
            assertThat(repository.token).isNull();
            assertThat(exchange.getMessage().getBody(List.class)).hasSize(2);
            assertThat(exchange.getMessage().getHeader(OpenFgaConstants.CONTINUATION_TOKEN)).isEqualTo("cursor-1");
            delivered.incrementAndGet();
        })) {
            assertThat(fixture.consumer.poll()).isEqualTo(2);
            assertThat(repository.token).isEqualTo("cursor-1");
        }
        assertThat(delivered).hasValue(1);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("cursor-1", false)));
        try (Fixture fixture = new Fixture(client, repository, exchange -> delivered.incrementAndGet())) {
            assertThat(fixture.consumer.poll()).isZero();
        }
        ArgumentCaptor<ClientReadChangesOptions> options = ArgumentCaptor.forClass(ClientReadChangesOptions.class);
        verify(client, org.mockito.Mockito.times(2)).readChanges(any(), options.capture());
        assertThat(options.getAllValues().get(0).getContinuationToken()).isNull();
        assertThat(options.getAllValues().get(1).getContinuationToken()).isEqualTo("cursor-1");
        assertThat(delivered).hasValue(1);
        assertThat(repository.saved).containsExactly("cursor-1");
    }

    @Test
    void processingFailureReplaysTheCompletePage() throws Exception {
        Repository repository = new Repository();
        repository.token = "before";
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", true)));
        AtomicInteger delivered = new AtomicInteger();
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
            delivered.incrementAndGet();
            exchange.setException(new IOException("projection unavailable"));
        })) {
            assertThatThrownBy(fixture.consumer::poll).hasMessage("projection unavailable");
            assertThatThrownBy(fixture.consumer::poll).hasMessage("projection unavailable");
        }
        assertThat(delivered).hasValue(2);
        assertThat(repository.token).isEqualTo("before");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void thrownProcessorFailureIsReportedToUnitOfWorkWithoutAcknowledging() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", true)));
        AtomicInteger failedCompletion = new AtomicInteger();
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
            exchange.getUnitOfWork().addSynchronization(new org.apache.camel.spi.Synchronization() {
                @Override
                public void onComplete(org.apache.camel.Exchange completed) {
                    throw new AssertionError("A thrown processor failure must not complete successfully");
                }

                @Override
                public void onFailure(org.apache.camel.Exchange failed) {
                    failedCompletion.incrementAndGet();
                }
            });
            throw new IOException("projection failed");
        })) {
            assertThatThrownBy(fixture.consumer::poll).hasMessage("projection failed");
        }
        assertThat(failedCompletion).hasValue(1);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void rollbackPreventsCheckpointEvenWithoutAnException() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", true)));
        try (Fixture fixture = new Fixture(client, repository, exchange -> exchange.setRollbackOnly(true))) {
            assertThatThrownBy(fixture.consumer::poll).hasMessageContaining("rolled back");
        }
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void checkpointFailureReplaysInsteadOfUsingAnUncommittedCursor() throws Exception {
        Repository repository = new Repository();
        repository.token = "before";
        repository.failStore = true;
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", true)));
        AtomicInteger delivered = new AtomicInteger();
        try (Fixture fixture = new Fixture(client, repository, exchange -> delivered.incrementAndGet())) {
            assertThatThrownBy(fixture.consumer::poll).hasMessage("checkpoint unavailable");
            assertThat(repository.token).isEqualTo("before");
            repository.failStore = false;
            assertThat(fixture.consumer.poll()).isEqualTo(2);
        }
        ArgumentCaptor<ClientReadChangesOptions> options = ArgumentCaptor.forClass(ClientReadChangesOptions.class);
        verify(client, org.mockito.Mockito.times(2)).readChanges(any(), options.capture());
        assertThat(options.getAllValues()).allSatisfy(option -> assertThat(option.getContinuationToken()).isEqualTo("before"));
        assertThat(delivered).hasValue(2);
        assertThat(repository.token).isEqualTo("after");
    }

    @Test
    void repositoryLoadFailureDoesNotReadOrProcess() throws Exception {
        Repository repository = new Repository();
        repository.failLoad = true;
        OpenFgaClient client = mock(OpenFgaClient.class);
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
            throw new AssertionError("Must not process");
        })) {
            assertThatThrownBy(fixture.consumer::poll).hasMessage("checkpoint unavailable");
        }
        verify(client, never()).readChanges(any(), any());
    }

    @Test
    void emptyTailRetainsTheSameCursorAndDoesNotDeliverAnEmptyPage() throws Exception {
        Repository repository = new Repository();
        repository.token = "tail";
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("tail", false)));
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
            throw new AssertionError("Must not deliver an empty tail");
        })) {
            assertThat(fixture.consumer.poll()).isZero();
            assertThat(fixture.consumer.poll()).isZero();
        }
        assertThat(repository.token).isEqualTo("tail");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void emptyPageCanAdvanceATailingCursorButMissingTokenNeverErasesIt() throws Exception {
        Repository repository = new Repository();
        repository.token = "before";
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", false)))
                .thenReturn(CompletableFuture.completedFuture(page(null, false)));
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
        })) {
            assertThat(fixture.consumer.poll()).isZero();
            assertThat(fixture.consumer.poll()).isZero();
        }
        assertThat(repository.token).isEqualTo("after");
        assertThat(repository.saved).containsExactly("after");
    }

    @Test
    void serverFailureDoesNotCommitAndNextPollRetriesTheSameBookmark() throws Exception {
        Repository repository = new Repository();
        repository.token = "before";
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.failedFuture(new IOException("offline")))
                .thenReturn(CompletableFuture.completedFuture(page("after", true)));
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
        })) {
            assertThatThrownBy(fixture.consumer::poll).hasRootCauseMessage("offline");
            assertThat(repository.token).isEqualTo("before");
            assertThat(fixture.consumer.poll()).isEqualTo(2);
        }
    }

    @Test
    void pendingPageHasAFinitePollDeadlineAndIsCancelled() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientReadChangesResponse> pending = new CompletableFuture<>();
        when(client.readChanges(any(), any())).thenReturn(pending);
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
        })) {
            fixture.endpoint.getConfiguration().setChangePollTimeout(50);
            assertThatThrownBy(fixture.consumer::poll).hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(pending.isCancelled()).isTrue();
            assertThat(repository.saved).isEmpty();
        }
    }

    @Test
    void stopCancelsPendingPageWithoutWaitingForItsDeadline() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        CompletableFuture<ClientReadChangesResponse> pending = new CompletableFuture<>();
        CountDownLatch requested = new CountDownLatch(1);
        when(client.readChanges(any(), any())).thenAnswer(invocation -> {
            requested.countDown();
            return pending;
        });
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
        })) {
            CompletableFuture<Void> poll = CompletableFuture
                    .runAsync(() -> assertThatThrownBy(fixture.consumer::poll).isInstanceOf(Exception.class));
            assertThat(requested.await(2, TimeUnit.SECONDS)).isTrue();
            fixture.consumer.stop();
            poll.get(2, TimeUnit.SECONDS);
            assertThat(pending.isCancelled()).isTrue();
            assertThat(repository.saved).isEmpty();
        }
    }

    @Test
    void stopDuringProcessingDoesNotCheckpoint() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", true)));
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
            processing.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
        })) {
            CompletableFuture<Integer> poll = CompletableFuture.supplyAsync(() -> {
                try {
                    return fixture.consumer.poll();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            try {
                assertThat(processing.await(2, TimeUnit.SECONDS)).isTrue();
                fixture.consumer.stop();
            } finally {
                release.countDown();
            }
            assertThat(poll.get(2, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(repository.saved).isEmpty();
        }
    }

    @Test
    void initialStartTimeAndFilterArePreservedButCheckpointWinsOnRestart() throws Exception {
        Repository repository = new Repository();
        OpenFgaClient client = mock(OpenFgaClient.class);
        when(client.readChanges(any(), any())).thenReturn(CompletableFuture.completedFuture(page("after", false)));
        try (Fixture fixture = new Fixture(client, repository, exchange -> {
        })) {
            fixture.consumer.poll();
            fixture.consumer.poll();
        }
        ArgumentCaptor<ClientReadChangesRequest> requests = ArgumentCaptor.forClass(ClientReadChangesRequest.class);
        ArgumentCaptor<ClientReadChangesOptions> options = ArgumentCaptor.forClass(ClientReadChangesOptions.class);
        verify(client, org.mockito.Mockito.times(2)).readChanges(requests.capture(), options.capture());
        assertThat(requests.getAllValues().get(0).getStartTime()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        assertThat(requests.getAllValues().get(1).getStartTime()).isNull();
        assertThat(requests.getAllValues()).allSatisfy(request -> assertThat(request.getType()).isEqualTo("document"));
        assertThat(options.getAllValues()).allSatisfy(option -> assertThat(option.getPageSize()).isEqualTo(20));
    }

    @Test
    void consumerRequiresDurableRepositoryKeyAndTheReadChangesOperation() throws Exception {
        try (var context = new DefaultCamelContext()) {
            OpenFgaConfiguration configuration = new OpenFgaConfiguration();
            var endpoint = new OpenFgaEndpoint("openfga:readChanges", new OpenFgaComponent(context), configuration);
            endpoint.setOperation(OpenFgaOperation.readChanges);
            assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
            })).hasMessageContaining("durable checkpointRepository");
            configuration.setCheckpointRepository(new Repository());
            assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
            })).hasMessageContaining("checkpointKey");
            configuration.setCheckpointKey("projection");
            endpoint.setOperation(OpenFgaOperation.check);
            assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
            })).hasMessageContaining("Only readChanges");
        }
    }

    private static ClientReadChangesResponse page(String cursor, boolean populated) {
        var response = new dev.openfga.sdk.api.model.ReadChangesResponse().continuationToken(cursor);
        response.changes(populated
                ? List.of(
                        new TupleChange().tupleKey(new TupleKey().user("user:anne").relation("reader")._object("document:a"))
                                .operation(TupleOperation.WRITE),
                        new TupleChange().tupleKey(new TupleKey().user("user:anne").relation("reader")._object("document:b"))
                                .operation(TupleOperation.DELETE))
                : List.of());
        return new ClientReadChangesResponse(
                new dev.openfga.sdk.api.client.ApiResponse<>(200, java.util.Map.of(), "", response));
    }

    private static class Repository implements OpenFgaCheckpointRepository {
        private String token;
        private boolean failLoad;
        private boolean failStore;
        private final List<String> saved = new ArrayList<>();

        @Override
        public String load(String key) throws Exception {
            if (failLoad) {
                throw new IOException("checkpoint unavailable");
            }
            return token;
        }

        @Override
        public void store(String key, String token) throws Exception {
            if (failStore) {
                throw new IOException("checkpoint unavailable");
            }
            saved.add(token);
            this.token = token;
        }
    }

    private static class Fixture implements AutoCloseable {
        private final DefaultCamelContext context = new DefaultCamelContext();
        private final OpenFgaEndpoint endpoint;
        private final OpenFgaConsumer consumer;

        Fixture(OpenFgaClient client, Repository repository, Processor processor) throws Exception {
            context.setInflightRepository(new org.apache.camel.impl.engine.DefaultInflightRepository());
            context.start();
            var unitOfWorkFactory = new org.apache.camel.impl.engine.DefaultUnitOfWorkFactory();
            unitOfWorkFactory.afterPropertiesConfigured(context);
            context.getCamelContextExtension().addContextPlugin(org.apache.camel.spi.UnitOfWorkFactory.class,
                    unitOfWorkFactory);
            OpenFgaConfiguration configuration = new OpenFgaConfiguration();
            configuration.setOpenFgaClient(client);
            configuration.setCheckpointRepository(repository);
            configuration.setCheckpointKey("projection");
            configuration.setType("document");
            configuration.setPageSize(20);
            configuration.setStartTime("2026-10-01T00:00:00Z");
            endpoint = new OpenFgaEndpoint("openfga:readChanges", new OpenFgaComponent(context), configuration);
            endpoint.setOperation(OpenFgaOperation.readChanges);
            endpoint.setStartScheduler(false);
            endpoint.start();
            consumer = (OpenFgaConsumer) endpoint.createConsumer(processor);
            consumer.start();
        }

        @Override
        public void close() throws Exception {
            consumer.stop();
            endpoint.stop();
            context.close();
        }
    }
}
