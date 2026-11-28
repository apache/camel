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
package org.apache.camel.component.azure.servicebus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.azure.core.amqp.exception.AmqpErrorCondition;
import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.azure.servicebus.client.ServiceBusClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Camel lock renewer of the consumer runs on a thread of its own, not on the shared timer of the
 * PeriodTaskScheduler: a stop ends it (the scheduler has no way to remove a task), and another periodic task cannot
 * hold up a renewal. At most one renewal of a lock is in flight, and a late answer does not move the lock expiry
 * backwards. The renewer is set up before the processor client starts, a failed renewal is retried by the next run, and
 * a run that throws does not end the later runs.
 */
class ServiceBusConsumerLockRenewerTest {

    private static final String QUEUE = "lock-renewer-queue";
    private static final String RENEWER_THREAD = "ServiceBusLockRenewer[" + QUEUE + "]";

    private final ServiceBusConfiguration configuration = mock();
    private final ServiceBusClientFactory clientFactory = mock();
    private final ServiceBusProcessorClient client = mock();
    private final ServiceBusComponent component = mock();
    private final ServiceBusEndpoint endpoint = mock();
    private final ServiceBusReceivedMessageContext messageContext = mock();
    private final ServiceBusReceivedMessage message = mock();
    private final AsyncProcessor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory ef = mock();
    private final HeaderFilterStrategy headerFilterStrategy = mock();
    private final UuidGenerator uuidGenerator = mock();
    private final ServiceBusReceiverAsyncClient renewalClient = mock();
    private final PeriodTaskScheduler periodTaskScheduler = mock();
    private final ExecutorServiceManager executorServiceManager = mock();
    private final ScheduledExecutorService lockRenewerExecutor = mock();
    private final ArgumentCaptor<Consumer<ServiceBusReceivedMessageContext>> processMessageCaptor = ArgumentCaptor.captor();
    private final ArgumentCaptor<Consumer<ServiceBusErrorContext>> processErrorCaptor = ArgumentCaptor.captor();

    private DefaultCamelContext realContext;

    @BeforeEach
    void beforeEach() {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(ef.create(any(Endpoint.class), anyBoolean()))
                .thenAnswer(inv -> DefaultExchange.newFromEndpoint(inv.getArgument(0)));
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getServiceBusClientFactory()).thenReturn(clientFactory);
        when(clientFactory.createServiceBusProcessorClient(any(), processMessageCaptor.capture(), processErrorCaptor.capture()))
                .thenReturn(client);
        // an asynchronous route: the exchange stays in flight, only the Camel lock renewer keeps its lock
        when(processor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(false);
        when(configuration.getHeaderFilterStrategy()).thenReturn(headerFilterStrategy);
        when(context.getUuidGenerator()).thenReturn(uuidGenerator);
        when(uuidGenerator.generateExchangeUuid()).thenReturn("exchange-1");
        when(clientFactory.createServiceBusReceiverAsyncClient(any())).thenReturn(renewalClient);
        when(configuration.getTopicOrQueueName()).thenReturn(QUEUE);
        when(configuration.getServiceBusReceiveMode()).thenReturn(ServiceBusReceiveMode.PEEK_LOCK);
        when(configuration.getMaxAutoLockRenewDuration()).thenReturn(300000L);
        when(configuration.isSessionEnabled()).thenReturn(false);
        when(messageContext.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(Collections.emptyMap());
        // the period task scheduler of the context, which the renewer must not use
        when(ecc.getContextPlugin(PeriodTaskScheduler.class)).thenReturn(periodTaskScheduler);
    }

    @AfterEach
    void afterEach() {
        if (realContext != null) {
            realContext.stop();
        }
    }

    /**
     * The consumer runs with the thread pools and the period task scheduler of a real CamelContext.
     */
    private PeriodTaskScheduler useRealContext() {
        realContext = new DefaultCamelContext();
        realContext.start();
        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(realContext);
        when(ecc.getContextPlugin(PeriodTaskScheduler.class)).thenReturn(scheduler);
        when(context.getExecutorServiceManager()).thenReturn(realContext.getExecutorServiceManager());
        return scheduler;
    }

    /**
     * The consumer gets a mocked executor for its lock renewer, so a test runs the renewer itself.
     */
    private ScheduledExecutorService useMockedExecutor() {
        when(context.getExecutorServiceManager()).thenReturn(executorServiceManager);
        when(executorServiceManager.newSingleThreadScheduledExecutor(any(), anyString())).thenReturn(lockRenewerExecutor);
        return lockRenewerExecutor;
    }

    private static long lockRenewerTasks(PeriodTaskScheduler scheduler) {
        return scheduler.getTasks().stream()
                .filter(t -> t.getClass().getName().endsWith("ServiceBusConsumer$LockRenewer"))
                .count();
    }

    private static long lockRenewerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().endsWith(RENEWER_THREAD))
                .count();
    }

    @Test
    void stopEndsTheLockRenewerAndItsThread() throws Exception {
        PeriodTaskScheduler scheduler = useRealContext();

        ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor);
        for (int i = 0; i < 5; i++) {
            consumer.doStart();
            consumer.doStop();
        }
        // a fresh consumer per start, as after a context reload, which creates new consumers
        for (int i = 0; i < 3; i++) {
            ServiceBusConsumer c = new ServiceBusConsumer(endpoint, processor);
            c.doStart();
            c.doStop();
            c.doShutdown();
        }
        // the PeriodTaskScheduler cannot remove a task: a renewer registered there would stay until the context stops
        assertThat(lockRenewerTasks(scheduler)).isZero();
        await().atMost(10, TimeUnit.SECONDS).until(() -> lockRenewerThreads() == 0);

        // one thread while the consumer is started, none after it stopped
        consumer.doStart();
        assertThat(lockRenewerThreads()).isEqualTo(1);
        consumer.doStop();
        await().atMost(10, TimeUnit.SECONDS).until(() -> lockRenewerThreads() == 0);
        assertThat(lockRenewerTasks(scheduler)).isZero();
    }

    @Test
    void aBlockedSharedTimerDoesNotHoldUpTheRenewal() throws Exception {
        PeriodTaskScheduler scheduler = useRealContext();

        List<Instant> renewCalls = Collections.synchronizedList(new ArrayList<>());
        when(renewalClient.renewMessageLock(any(ServiceBusReceivedMessage.class))).thenAnswer(inv -> {
            renewCalls.add(Instant.now());
            return Mono.just(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60));
        });

        // another periodic task of the context hangs in a call (a vault refresh task, or the context reload it
        // starts) and holds the shared timer thread until the end of the test
        CountDownLatch blocking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        scheduler.schedulePeriodTask(() -> {
            blocking.countDown();
            try {
                release.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 100);
        assertThat(blocking.await(10, TimeUnit.SECONDS)).isTrue();

        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            // the lock has 15 seconds left: the first run of the renewer, 5 seconds after the start, renews it
            OffsetDateTime lockedUntil = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(15);
            when(message.getLockedUntil()).thenReturn(lockedUntil);
            processMessageCaptor.getValue().accept(messageContext);

            await().atMost(14, TimeUnit.SECONDS).until(() -> !renewCalls.isEmpty());
            assertThat(renewCalls.get(0)).isBefore(lockedUntil.toInstant());
            consumer.doStop();
        } finally {
            release.countDown();
        }
    }

    @Test
    void slowBrokerGetsOneRenewalAtATimeAndALateAnswerDoesNotMoveTheExpiryBack() throws Exception {
        useMockedExecutor();
        // the broker answers only when the test says so
        List<Sinks.One<OffsetDateTime>> pending = new ArrayList<>();
        when(renewalClient.renewMessageLock(any(ServiceBusReceivedMessage.class))).thenAnswer(inv -> {
            Sinks.One<OffsetDateTime> sink = Sinks.one();
            pending.add(sink);
            return sink.asMono();
        });
        Instant t0 = Instant.now();
        when(message.getLockedUntil()).thenReturn(OffsetDateTime.ofInstant(t0.plusSeconds(18), ZoneOffset.UTC));

        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            processMessageCaptor.getValue().accept(messageContext);

            // the lock expires within 20 seconds: renewed
            consumer.runLockRenewer(t0);
            verify(renewalClient, times(1)).renewMessageLock(message);

            // the next run, 5 seconds later: the renewal has not answered yet, no second one
            consumer.runLockRenewer(t0.plusSeconds(5));
            verify(renewalClient, times(1)).renewMessageLock(message);

            // two runs after it, the renewal still has not answered: it may hang until the SDK gives up on it, which
            // can be after the lock expired, so the lock is renewed once more
            consumer.runLockRenewer(t0.plusSeconds(10));
            verify(renewalClient, times(2)).renewMessageLock(message);
            assertThat(pending).hasSize(2);

            // the answers come newest first: the expiry moves forward only
            OffsetDateTime first = OffsetDateTime.ofInstant(t0.plusSeconds(60), ZoneOffset.UTC);
            OffsetDateTime second = first.plusSeconds(10);
            pending.get(1).tryEmitValue(second);
            assertThat(consumer.lockRenewerLockedUntil("exchange-1")).isEqualTo(second);
            pending.get(0).tryEmitValue(first);
            assertThat(consumer.lockRenewerLockedUntil("exchange-1")).isEqualTo(second);

            // both answered: no renewal until the lock expires within 20 seconds again
            consumer.runLockRenewer(t0.plusSeconds(15));
            verify(renewalClient, times(2)).renewMessageLock(message);
            consumer.runLockRenewer(t0.plusSeconds(51));
            verify(renewalClient, times(3)).renewMessageLock(message);
        }
        verifyNoInteractions(periodTaskScheduler);
    }

    @Test
    void aMessageDeliveredAsTheClientStartsIsTracked() throws Exception {
        useMockedExecutor();
        Instant t0 = Instant.now();
        OffsetDateTime lockedUntil = OffsetDateTime.ofInstant(t0.plusSeconds(18), ZoneOffset.UTC);
        when(message.getLockedUntil()).thenReturn(lockedUntil);
        when(renewalClient.renewMessageLock(any(ServiceBusReceivedMessage.class)))
                .thenReturn(Mono.just(OffsetDateTime.ofInstant(t0.plusSeconds(60), ZoneOffset.UTC)));
        // the processor client delivers a message as soon as it starts, before doStart returns
        doAnswer(inv -> {
            processMessageCaptor.getValue().accept(messageContext);
            return null;
        }).when(client).start();

        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();

            assertThat(consumer.lockRenewerLockedUntil("exchange-1")).isEqualTo(lockedUntil);
            consumer.runLockRenewer(t0);
            verify(renewalClient, times(1)).renewMessageLock(message);
        }
    }

    @Test
    void aFailedStartShutsTheLockRenewerDown() {
        ScheduledExecutorService executor = useMockedExecutor();
        when(configuration.getConnectionString()).thenReturn("Endpoint=sb://localhost/");
        doThrow(new IllegalStateException("cannot connect")).when(client).start();

        ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor);
        assertThatThrownBy(consumer::start).isInstanceOf(IllegalStateException.class).hasMessage("cannot connect");

        // the renewer was set up before the client failed to start; the stop that BaseService.start makes after the
        // failure shut it down
        verify(executorServiceManager, times(1)).newSingleThreadScheduledExecutor(any(), anyString());
        verify(executorServiceManager, times(1)).shutdownNow(executor);
        verify(renewalClient).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aFailedRenewalIsRetriedByTheNextRun(boolean callThrows) throws Exception {
        useMockedExecutor();
        Instant t0 = Instant.now();
        OffsetDateTime lockedUntil = OffsetDateTime.ofInstant(t0.plusSeconds(18), ZoneOffset.UTC);
        OffsetDateTime renewed = OffsetDateTime.ofInstant(t0.plusSeconds(60), ZoneOffset.UTC);
        when(message.getLockedUntil()).thenReturn(lockedUntil);
        if (callThrows) {
            // the call itself throws
            when(renewalClient.renewMessageLock(any(ServiceBusReceivedMessage.class)))
                    .thenThrow(new IllegalStateException("receiver closed"))
                    .thenReturn(Mono.just(renewed));
        } else {
            // the renewal answers with a transient error, as the SDK reports a busy server
            ServiceBusException busy = new ServiceBusException(
                    new AmqpException(true, AmqpErrorCondition.SERVER_BUSY_ERROR, "server busy", null),
                    ServiceBusErrorSource.RENEW_LOCK);
            when(renewalClient.renewMessageLock(any(ServiceBusReceivedMessage.class)))
                    .thenReturn(Mono.error(busy))
                    .thenReturn(Mono.just(renewed));
        }

        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            processMessageCaptor.getValue().accept(messageContext);

            consumer.runLockRenewer(t0);
            verify(renewalClient, times(1)).renewMessageLock(message);
            // the entry is kept (CAMEL-26969)
            assertThat(consumer.lockRenewerLockedUntil("exchange-1")).isEqualTo(lockedUntil);

            // the failed renewal is no longer in flight: the next run renews again, before the 10 seconds after which
            // a renewal that has not answered is sent again
            consumer.runLockRenewer(t0.plusSeconds(5));
            verify(renewalClient, times(2)).renewMessageLock(message);
            assertThat(consumer.lockRenewerLockedUntil("exchange-1")).isEqualTo(renewed);
        }
    }

    @Test
    void aRunThatThrowsDoesNotEndTheLaterRuns() throws Exception {
        ScheduledExecutorService executor = useMockedExecutor();
        when(uuidGenerator.generateExchangeUuid()).thenReturn("exchange-1", "exchange-2");
        when(renewalClient.renewMessageLock(any(ServiceBusReceivedMessage.class)))
                .thenReturn(Mono.just(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60)));

        try (ServiceBusConsumer consumer = new ServiceBusConsumer(endpoint, processor)) {
            consumer.doStart();
            ArgumentCaptor<Runnable> task = ArgumentCaptor.captor();
            verify(executor).scheduleWithFixedDelay(task.capture(), anyLong(), anyLong(), any());

            // a message without a lock expiry makes the run throw (a NullPointerException): it stands for anything
            // thrown in a run, which would end a task of a ScheduledExecutorService, and with it every later renewal
            when(message.getLockedUntil()).thenReturn(null);
            processMessageCaptor.getValue().accept(messageContext);
            ArgumentCaptor<Exchange> exchange = ArgumentCaptor.captor();
            verify(processor).process(exchange.capture(), any(AsyncCallback.class));
            assertThatCode(task.getValue()::run).doesNotThrowAnyException();

            // that exchange completes; a later message is renewed by the next run
            Exchange first = exchange.getValue();
            for (Synchronization sync : first.getExchangeExtension().handoverCompletions()) {
                sync.onComplete(first);
            }
            ServiceBusReceivedMessageContext secondContext = mock();
            ServiceBusReceivedMessage second = mock();
            when(secondContext.getMessage()).thenReturn(second);
            when(second.getApplicationProperties()).thenReturn(Collections.emptyMap());
            when(second.getLockedUntil()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(15));
            processMessageCaptor.getValue().accept(secondContext);

            assertThatCode(task.getValue()::run).doesNotThrowAnyException();
            verify(renewalClient, times(1)).renewMessageLock(second);
        }
    }
}
