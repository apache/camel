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
package org.apache.camel.component.kafka.consumer.support.streaming;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.EndpointAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaRecordStreamingProcessor extends KafkaRecordProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordStreamingProcessor.class);
    private static final Set<String> ASYNCHRONOUS_HANDOFF_SCHEMES
            = Set.of("seda", "vm", "disruptor", "disruptor-vm", "reactive-streams");
    private static final String ASYNCHRONOUS_HANDOFF_PROPERTY
            = "CamelKafkaExactlyOnceAsyncHandoff";
    private static final Map<CamelContext, Boolean> ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS
            = Collections.synchronizedMap(new WeakHashMap<>());
    private static final String WIRE_TAP_PROCESSOR_CLASS = "org.apache.camel.processor.WireTapProcessor";
    private static final Map<Route, Boolean> INSTRUMENTED_ROUTES
            = Collections.synchronizedMap(new WeakHashMap<>());

    private final boolean autoCommitEnabled;
    private final KafkaConfiguration configuration;
    private final Processor processor;
    private final CommitManager commitManager;

    public KafkaRecordStreamingProcessor(KafkaConfiguration configuration, Processor processor, CommitManager commitManager) {
        this.autoCommitEnabled = configuration.isAutoCommitEnable();
        this.configuration = configuration;
        this.processor = processor;
        this.commitManager = commitManager;
    }

    public ProcessingResult processExchange(
            KafkaConsumer camelKafkaConsumer, TopicPartition topicPartition, boolean partitionHasNext,
            boolean recordHasNext, ConsumerRecord<Object, Object> consumerRecord) {

        final Exchange exchange = camelKafkaConsumer.createExchange(false);

        Message message = exchange.getMessage();

        setupExchangeMessage(message, consumerRecord);

        propagateHeaders(configuration, consumerRecord, exchange);

        // if not auto commit then we have additional information on the exchange
        if (!autoCommitEnabled) {
            message.setHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, !recordHasNext);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
        }

        if (configuration.isAllowManualCommit()) {
            // allow Camel users to access the Kafka consumer API to be able to do for example manual commits
            KafkaManualCommit manual = commitManager.getManualCommit(exchange, topicPartition, consumerRecord);

            message.setHeader(KafkaConstants.MANUAL_COMMIT, manual);
            message.setHeader(KafkaConstants.LAST_POLL_RECORD, !recordHasNext && !partitionHasNext);
            if (configuration.isExactlyOnce()) {
                // Keep the source offsets outside message headers: removeHeaders() and message replacement must not
                // discard the information needed by the destination producer.
                exchange.setProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY,
                        KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual), exchange.getExchangeId()));
            }
        }

        try {
            if (configuration.isExactlyOnce()) {
                instrumentKafkaWireTaps(exchange);
            }
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        completeExactlyOnceProcessing(exchange);

        ProcessingResult result;
        if (exchange.getException() != null) {
            LOG.debug("An exception was thrown for consumerRecord at partition {} and offset {}",
                    consumerRecord.partition(), consumerRecord.offset());
            final ExceptionHandler exceptionHandler = camelKafkaConsumer.getExceptionHandler();

            boolean breakOnErrorExit = processException(exchange, topicPartition, consumerRecord, exceptionHandler);
            result = new ProcessingResult(
                    breakOnErrorExit, true, consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset());
        } else {
            result = new ProcessingResult(
                    false, exchange.getException() != null, consumerRecord.topic(), consumerRecord.partition(),
                    consumerRecord.offset());
        }

        if (!result.isBreakOnErrorHit()) {
            commitManager.recordOffset(topicPartition, consumerRecord.offset());
        }

        // success so release the exchange
        camelKafkaConsumer.releaseExchange(exchange, false);

        return result;
    }

    /**
     * Calculate a WireTap wait timeout with a proportional safety margin before Kafka's max.poll.interval.ms. Kafka's
     * default max.poll.interval.ms is 300000 ms.
     */
    static long wireTapWaitTimeoutMillis(Integer configuredMaxPollIntervalMs) {
        long maxPollIntervalMillis = configuredMaxPollIntervalMs != null
                ? configuredMaxPollIntervalMs.longValue() : 300_000L;
        return Math.max(1L, maxPollIntervalMillis * 4L / 5L);
    }

    /**
     * A route that did not claim the source metadata with an exactly-once producer still has to advance the source
     * offset (for example, a filter branch). Run this after route completion on the consumer polling thread.
     */
    private void completeExactlyOnceProcessing(Exchange exchange) {
        if (!configuration.isExactlyOnce()) {
            return;
        }

        KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
        if (metadata != null && exchange.getException() == null) {
            long waitTimeoutMillis = wireTapWaitTimeoutMillis(configuration.getMaxPollIntervalMs());
            try {
                if (!metadata.awaitAsynchronousWireTaps(waitTimeoutMillis)) {
                    exchange.setException(new IllegalStateException(
                            "Timed out waiting for a potentially Kafka-producing WireTap route to finish; "
                                                                    + "the source record must be replayed"));
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.setException(new IllegalStateException(
                        "Interrupted while waiting for a potentially Kafka-producing WireTap route to finish", e));
                return;
            }
        }
        if (metadata != null && metadata.isDeadLetterSendFailed()) {
            if (exchange.getException() == null) {
                exchange.setException(new IllegalStateException(
                        "A Kafka dead-letter send failed; the source record must be replayed"));
            }
            return;
        }
        if (metadata != null && metadata.isClaimRejected()) {
            if (exchange.getException() == null) {
                exchange.setException(new IllegalStateException(
                        "The Kafka exactlyOnce source metadata was rejected by another exchange or producer: "
                                                                + metadata.getClaimRejectionReason()));
            }
            return;
        }

        boolean transactionAborted = Boolean.TRUE.equals(
                exchange.getProperty(KafkaExactlyOnceMetadata.TRANSACTION_ABORTED_PROPERTY));
        if (transactionAborted || exchange.isRollbackOnly()) {
            if (exchange.getException() == null) {
                exchange.setException(new IllegalStateException(
                        "The Kafka exactlyOnce transaction was rolled back; the source record must be replayed"));
            }
            return;
        }
        if (exchange.getException() != null) {
            return;
        }

        Throwable sendFailure = metadata != null ? metadata.getSendFailure() : null;
        if (sendFailure != null && !metadata.isClaimed()) {
            // A producer failed before it could register a transaction synchronization. Do not mistake a handled
            // configuration/startup failure for a filter branch and commit its source offset.
            exchange.setException(new IllegalStateException(
                    "The exactlyOnce Kafka producer failed before it could claim the source offsets", sendFailure));
            return;
        }
        if (metadata == null) {
            exchange.setException(new IllegalStateException(
                    "exactlyOnce source metadata was not captured for the consumed record"));
            return;
        }
        if (!metadata.isClaimed()) {
            if (hasAsynchronousHandoff(exchange)) {
                exchange.setException(new IllegalStateException(
                        "An exactlyOnce Kafka source cannot commit offsets directly when the route hands the exchange "
                                                                + "to an asynchronous endpoint before an exactly-once Kafka producer claims it; "
                                                                + "invoke the destination synchronously from the source exchange"));
                return;
            }
            try {
                metadata.commitOffsets();
            } catch (Exception commitFailure) {
                exchange.setException(commitFailure);
            }
        }
    }

    /**
     * Detect only hand-offs that this source exchange actually performed.
     *
     * @implNote Asynchronous sends are recorded on the exchange as they happen using Camel's ExchangeSendingEvent. The
     *           final TO_ENDPOINT value is also checked as a fallback. Do not replace this exchange-local tracking with
     *           a route-tree scan, which can report asynchronous branches that this exchange did not execute. Extend
     *           the scheme list when another supported asynchronous endpoint is added.
     */
    boolean hasAsynchronousHandoff(Exchange exchange) {
        String toEndpoint = exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT, String.class);
        if (Boolean.TRUE.equals(exchange.getProperty(ASYNCHRONOUS_HANDOFF_PROPERTY))
                || isAsynchronousHandoffEndpoint(toEndpoint)) {
            return true;
        }
        KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
        return metadata != null && metadata.hasAsynchronousKafkaHandoff();
    }

    /**
     * Register once per CamelContext to record asynchronous endpoint sends on the exchange that performs the send.
     * Camel can overwrite TO_ENDPOINT when a later endpoint is invoked, so observing each send is necessary.
     */
    static void ensureAsynchronousHandoffTracking(CamelContext context) {
        synchronized (ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS) {
            if (ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.containsKey(context)) {
                return;
            }
            EventNotifierSupport notifier = new EventNotifierSupport() {
                @Override
                public void notify(CamelEvent event) {
                    if (event instanceof CamelEvent.ExchangeSendingEvent sendingEvent
                            && isAsynchronousHandoffEndpoint(sendingEvent.getEndpoint().getEndpointUri())) {
                        sendingEvent.getExchange().setProperty(ASYNCHRONOUS_HANDOFF_PROPERTY, Boolean.TRUE);
                    }
                }
            };
            notifier.setIgnoreCamelContextEvents(true);
            notifier.setIgnoreCamelContextInitEvents(true);
            notifier.setIgnoreRouteEvents(true);
            notifier.setIgnoreServiceEvents(true);
            notifier.setIgnoreStepEvents(true);
            notifier.setIgnoreExchangeCreatedEvent(true);
            notifier.setIgnoreExchangeCompletedEvent(true);
            notifier.setIgnoreExchangeFailedEvents(true);
            notifier.setIgnoreExchangeRedeliveryEvents(true);
            notifier.setIgnoreExchangeSentEvents(true);
            notifier.setIgnoreExchangeSendingEvents(false);
            context.getManagementStrategy().addEventNotifier(notifier);
            ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.put(context, Boolean.TRUE);
        }
    }

    private static boolean isAsynchronousHandoffEndpoint(String uri) {
        if (uri == null) {
            return false;
        }
        String endpoint = endpointKey(uri);
        int separator = endpoint.indexOf(':');
        return separator > 0 && ASYNCHRONOUS_HANDOFF_SCHEMES.contains(endpoint.substring(0, separator));
    }

    private void instrumentKafkaWireTaps(Exchange exchange) {
        CamelContext context = exchange.getContext();
        if (context == null) {
            return;
        }
        ensureAsynchronousHandoffTracking(context);
        String routeId = exchange.getFromRouteId();
        if (routeId == null) {
            return;
        }
        Route route = context.getRoute(routeId);
        if (route == null) {
            return;
        }
        synchronized (INSTRUMENTED_ROUTES) {
            if (INSTRUMENTED_ROUTES.containsKey(route)) {
                return;
            }
            // The lock serializes first-record initialization. Cache only after the whole graph was inspected,
            // so a reflection failure cannot leave this route looking successfully instrumented.
            instrumentKafkaWireTaps(route.navigate(), context);
            INSTRUMENTED_ROUTES.put(route, Boolean.TRUE);
        }
    }

    private static void instrumentKafkaWireTaps(Navigate<Processor> navigation, CamelContext context) {
        if (navigation == null || !navigation.hasNext()) {
            return;
        }
        List<Processor> children = navigation.next();
        if (children == null) {
            return;
        }
        for (Processor child : children) {
            Processor processor = child;
            while (processor instanceof Channel channel && channel.getNextProcessor() != null) {
                processor = channel.getNextProcessor();
            }
            if (processor == null) {
                continue;
            }
            if (isWireTapProcessor(processor)) {
                instrumentKafkaWireTap(processor, context);
            }
            if (processor instanceof Navigate<?> nested) {
                @SuppressWarnings("unchecked")
                Navigate<Processor> childNavigation = (Navigate<Processor>) nested;
                instrumentKafkaWireTaps(childNavigation, context);
            }
        }
    }

    private static boolean isWireTapProcessor(Processor processor) {
        for (Class<?> type = processor.getClass(); type != null; type = type.getSuperclass()) {
            if (WIRE_TAP_PROCESSOR_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void instrumentKafkaWireTap(Processor wireTap, CamelContext context) {
        try {
            String uri = (String) wireTap.getClass().getMethod("getUri").invoke(wireTap);
            if (!routesToKafka(context, uri, new HashSet<>())) {
                return;
            }
            Processor current = (Processor) wireTap.getClass().getMethod("getOnPrepare").invoke(wireTap);
            if (current instanceof KafkaWireTapMarker) {
                return;
            }
            var pendingCountMethod = wireTap.getClass().getMethod("getPendingExchangesSize");
            IntSupplier pendingCount = () -> {
                try {
                    return ((Number) pendingCountMethod.invoke(wireTap)).intValue();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to read pending WireTap task count", e);
                }
            };
            wireTap.getClass().getMethod("setOnPrepare", Processor.class)
                    .invoke(wireTap, new KafkaWireTapMarker(current, pendingCount));
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : e;
            throw new IllegalStateException(
                    "Unable to instrument an executed Kafka-directed wireTap for exactlyOnce", cause);
        }
    }

    /** Find possible Kafka destinations in the target graph; the selected branch is verified at runtime. */
    private static boolean routesToKafka(CamelContext context, String uri, Set<String> visited) {
        String target = endpointKey(uri);
        if (target.startsWith("kafka:")) {
            return true;
        }
        if (!visited.add(target)) {
            return false;
        }
        for (Route route : context.getRoutes()) {
            if (route.getEndpoint() != null
                    && endpointKey(route.getEndpoint().getEndpointUri()).equals(target)
                    && routeContainsKafkaSend(route.navigate(), context, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean routeContainsKafkaSend(
            Navigate<Processor> navigation, CamelContext context, Set<String> visited) {
        if (navigation == null || !navigation.hasNext()) {
            return false;
        }
        List<Processor> children = navigation.next();
        if (children == null) {
            return false;
        }
        for (Processor child : children) {
            Processor processor = child;
            while (processor instanceof Channel channel && channel.getNextProcessor() != null) {
                processor = channel.getNextProcessor();
            }
            if (processor == null) {
                continue;
            }
            if (processor instanceof EndpointAware endpointAware && endpointAware.getEndpoint() != null) {
                String endpointUri = endpointAware.getEndpoint().getEndpointUri();
                String endpoint = endpointKey(endpointUri);
                if (endpoint.startsWith("kafka:")) {
                    return true;
                }
                if ((endpoint.startsWith("direct:") || endpoint.startsWith("seda:"))
                        && routesToKafka(context, endpointUri, visited)) {
                    return true;
                }
            }
            // This scan is only a conservative preflight to decide whether the copied exchange must be tracked.
            // It does not mark the source as handed off. The actual Kafka producer call marks shared metadata, and
            // the source consumer waits for the copy to finish before deciding whether direct offset commit is safe.
            if (isDynamicDestinationProcessor(processor)) {
                return true;
            }
            if (processor instanceof Navigate<?> nested) {
                @SuppressWarnings("unchecked")
                Navigate<Processor> childNavigation = (Navigate<Processor>) nested;
                if (routeContainsKafkaSend(childNavigation, context, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Dynamic EIPs may resolve to Kafka only when the copied exchange is actually routed. */
    private static boolean isDynamicDestinationProcessor(Processor processor) {
        String name = processor.getClass().getName();
        return name.equals("org.apache.camel.processor.SendDynamicProcessor")
                || name.equals("org.apache.camel.processor.RecipientList")
                || name.equals("org.apache.camel.processor.RoutingSlip")
                || name.equals("org.apache.camel.processor.DynamicRouter");
    }

    private static String endpointKey(String uri) {
        if (uri == null) {
            return "";
        }
        int query = uri.indexOf('?');
        String value = query >= 0 ? uri.substring(0, query) : uri;
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return value;
        }
        String scheme = value.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
        String path = value.substring(colon + 1);
        while (path.startsWith("//")) {
            path = path.substring(2);
        }
        return scheme + ":" + path;
    }

    private static final class KafkaWireTapMarker implements Processor {
        private final Processor delegate;
        private final IntSupplier pendingCount;

        private KafkaWireTapMarker(Processor delegate, IntSupplier pendingCount) {
            this.delegate = delegate;
            this.pendingCount = pendingCount;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                    KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
            if (metadata != null) {
                // WireTap's onPrepare runs before submission. Its public task count is incremented before submit and
                // decremented only after the copied route's async callback completes.
                metadata.registerAsynchronousWireTap(pendingCount);
                exchange.setProperty(KafkaExactlyOnceMetadata.WIRE_TAP_COPY_PROPERTY, Boolean.TRUE);
                // A SEDA producer normally completes its WireTap task as soon as it enqueues the copy. Force SEDA
                // hops in this tracked route to wait for their consumer route, so the WireTap pending count covers
                // the actual conditional branch all the way through any SEDA handoff.
                exchange.setProperty(Exchange.ASYNC_WAIT, WaitForTaskToComplete.Always);
            }
            if (delegate != null) {
                delegate.process(exchange);
            }
        }
    }

    private boolean processException(
            Exchange exchange, TopicPartition topicPartition,
            ConsumerRecord<Object, Object> consumerRecord, ExceptionHandler exceptionHandler) {

        // processing failed due to an unhandled exception, what should we do
        if (configuration.isBreakOnFirstError()) {
            // we are failing and we should break out
            if (LOG.isWarnEnabled()) {
                Exception exc = exchange.getException();
                LOG.warn("Error during processing {} from topic: {} due to {}", exchange, topicPartition.topic(),
                        exc.getMessage());
                LOG.warn("Will seek consumer to offset {} on partition {} and start polling again.",
                        consumerRecord.offset(), consumerRecord.partition());
            }

            // we should just do a commit (vs the original forceCommit)
            // when route uses NOOP Commit Manager it will rely
            // on the route implementation to explicitly commit offset
            // when route uses Synch/Asynch Commit Manager it will
            // ALWAYS commit the offset for the failing record
            // and will ALWAYS retry it
            commitManager.commit(topicPartition);

            // continue to next partition
            return true;
        } else {
            // will handle/log the exception and then continue to next
            exceptionHandler.handleException("Error during processing", exchange, exchange.getException());
        }

        return false;
    }
}
