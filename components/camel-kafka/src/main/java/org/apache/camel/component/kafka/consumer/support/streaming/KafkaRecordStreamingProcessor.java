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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.EndpointAware;
import org.apache.camel.EndpointSending;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RoutingSlip;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.LanguageSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaRecordStreamingProcessor extends KafkaRecordProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRecordStreamingProcessor.class);
    private static final Set<String> ASYNCHRONOUS_HANDOFF_SCHEMES
            = Set.of("seda", "vm", "disruptor", "disruptor-vm", "reactive-streams");
    private static final Map<CamelContext, HandoffTrackingRegistration> ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS
            = new WeakHashMap<>();

    private static final class HandoffTrackingRegistration {
        private final EventNotifierSupport notifier;
        private final ConcurrentMap<String, Boolean> asynchronousKafkaTargets;
        private int references = 1;

        private HandoffTrackingRegistration(
                                            EventNotifierSupport notifier,
                                            ConcurrentMap<String, Boolean> asynchronousKafkaTargets) {
            this.notifier = notifier;
            this.asynchronousKafkaTargets = asynchronousKafkaTargets;
        }
    }

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
     * A route that did not claim the source metadata with an exactly-once producer still has to advance the source
     * offset (for example, a filter branch). Run this after route completion on the consumer polling thread.
     */
    private void completeExactlyOnceProcessing(Exchange exchange) {
        if (!configuration.isExactlyOnce()) {
            return;
        }

        KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
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
     * Detect asynchronous hand-offs whose target route can reach Kafka. The event notifier marks the shared metadata
     * object, so the original source observes a Kafka-directed hand-off even when Camel emits the event for a copied
     * exchange. A statically non-Kafka asynchronous target must not affect the source exchange.
     */
    boolean hasAsynchronousHandoff(Exchange exchange) {
        String toEndpoint = exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT, String.class);
        KafkaExactlyOnceMetadata metadata = exchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
        return asynchronousEndpointCanReachKafka(exchange.getContext(), toEndpoint)
                || metadata != null && metadata.hasAsynchronousKafkaHandoff();
    }

    /**
     * Register an event notifier while at least one exactly-once Kafka source consumer is active in this context.
     * Events without exactly-once metadata return before URI inspection, avoiding work for unrelated routes.
     */
    static void registerAsynchronousHandoffTracking(CamelContext context) {
        if (context == null) {
            return;
        }
        synchronized (ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS) {
            HandoffTrackingRegistration existing = ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.get(context);
            if (existing != null) {
                existing.references++;
                return;
            }

            ConcurrentMap<String, Boolean> asynchronousKafkaTargets = new ConcurrentHashMap<>();
            EventNotifierSupport notifier = new EventNotifierSupport() {
                @Override
                public void notify(CamelEvent event) {
                    if (event instanceof CamelEvent.RouteEvent) {
                        // Route changes can alter the graph behind any cached asynchronous endpoint.
                        asynchronousKafkaTargets.clear();
                        return;
                    }

                    if (event instanceof CamelEvent.ExchangeSendingEvent sendingEvent) {
                        Exchange exchange = sendingEvent.getExchange();
                        KafkaExactlyOnceMetadata metadata = metadata(exchange);
                        if (metadata == null) {
                            return;
                        }
                        String uri = sendingEvent.getEndpoint().getEndpointUri();
                        if (asynchronousEndpointCanReachKafka(context, uri, asynchronousKafkaTargets)) {
                            // The metadata object is shared by shallow exchange copies. Mark it only when this
                            // asynchronous target can reach Kafka; a non-Kafka WireTap must not poison its source.
                            metadata.markAsynchronousKafkaHandoff();
                        }
                        return;
                    }

                    if (event instanceof CamelEvent.ExchangeFailureHandlingEvent handlingEvent) {
                        Exchange exchange = handlingEvent.getExchange();
                        if (metadata(exchange) == null) {
                            return;
                        }
                        Object current = exchange.getProperty(
                                KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_DEPTH_PROPERTY);
                        int depth = current instanceof Integer value ? value : 0;
                        exchange.setProperty(
                                KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_DEPTH_PROPERTY, depth + 1);
                        exchange.setProperty(
                                KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_PROPERTY, Boolean.TRUE);
                        return;
                    }

                    if (event instanceof CamelEvent.ExchangeFailureHandledEvent handledEvent) {
                        Exchange exchange = handledEvent.getExchange();
                        if (metadata(exchange) == null) {
                            return;
                        }
                        Object current = exchange.getProperty(
                                KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_DEPTH_PROPERTY);
                        int depth = current instanceof Integer value ? value : 0;
                        if (depth <= 1) {
                            exchange.removeProperty(
                                    KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_DEPTH_PROPERTY);
                            exchange.removeProperty(
                                    KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_PROPERTY);
                        } else {
                            exchange.setProperty(
                                    KafkaExactlyOnceMetadata.FAILURE_HANDLER_DELIVERY_DEPTH_PROPERTY, depth - 1);
                        }
                    }
                }
            };
            notifier.setIgnoreCamelContextEvents(true);
            notifier.setIgnoreCamelContextInitEvents(true);
            notifier.setIgnoreRouteEvents(false);
            notifier.setIgnoreServiceEvents(true);
            notifier.setIgnoreStepEvents(true);
            notifier.setIgnoreExchangeCreatedEvent(true);
            notifier.setIgnoreExchangeCompletedEvent(true);
            // Camel emits these events around error-handler failure processors and CatchProcessor bodies. Kafka
            // sends inside doCatch therefore use the failure-handler delivery path; sends after doCatch or after
            // onException(...).continued(true) resumes are outside the event window.
            notifier.setIgnoreExchangeFailedEvents(false);
            notifier.setIgnoreExchangeRedeliveryEvents(true);
            notifier.setIgnoreExchangeSentEvents(true);
            notifier.setIgnoreExchangeSendingEvents(false);
            context.getManagementStrategy().addEventNotifier(notifier);
            ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.put(
                    context, new HandoffTrackingRegistration(notifier, asynchronousKafkaTargets));
        }
    }

    /** Remove the notifier when the final exactly-once Kafka source consumer stops. */
    static void unregisterAsynchronousHandoffTracking(CamelContext context) {
        if (context == null) {
            return;
        }
        synchronized (ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS) {
            HandoffTrackingRegistration registration = ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.get(context);
            if (registration == null) {
                return;
            }
            if (--registration.references == 0) {
                context.getManagementStrategy().removeEventNotifier(registration.notifier);
                ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.remove(context);
            }
        }
    }

    private static boolean asynchronousEndpointCanReachKafka(CamelContext context, String uri) {
        if (!isAsynchronousHandoffEndpoint(uri)) {
            return false;
        }
        if (context == null) {
            // Without a context the target graph cannot be inspected, so fail closed.
            return true;
        }

        HandoffTrackingRegistration registration;
        synchronized (ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS) {
            registration = ASYNCHRONOUS_HANDOFF_TRACKED_CONTEXTS.get(context);
        }
        if (registration == null) {
            return routesToKafka(context, uri, new HashSet<>());
        }
        return asynchronousEndpointCanReachKafka(context, uri, registration.asynchronousKafkaTargets);
    }

    /**
     * Cache positive graph results only. A negative result must be recalculated because a route can be added at runtime
     * and turn a previously non-Kafka asynchronous target into one that reaches Kafka. Route lifecycle events clear
     * positive entries as well, so removed or reloaded routes are reflected by the next lookup.
     */
    static boolean asynchronousEndpointCanReachKafka(
            CamelContext context, String uri, ConcurrentMap<String, Boolean> cache) {
        if (!isAsynchronousHandoffEndpoint(uri)) {
            return false;
        }
        if (context == null) {
            return true;
        }
        String target = endpointKey(uri);
        if (Boolean.TRUE.equals(cache.get(target))) {
            return true;
        }

        // Never cache false: dynamic route additions can change a safe target into a Kafka-directed hand-off.
        boolean canReachKafka = routesToKafka(context, uri, new HashSet<>());
        if (canReachKafka) {
            cache.putIfAbsent(target, Boolean.TRUE);
        }
        return canReachKafka;
    }

    private static KafkaExactlyOnceMetadata metadata(Exchange exchange) {
        return exchange.getProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class);
    }

    /**
     * Validate WireTap targets before a source consumer starts polling. A WireTap runs on a copied exchange and cannot
     * participate in the source transaction. Static target graphs are followed through route endpoints; dynamic WireTap
     * URIs and dynamic send EIPs in a target graph are rejected conservatively because their destination is unknowable
     * at startup.
     */
    static void validateNoKafkaWireTaps(CamelContext context, String routeId) {
        if (context == null || routeId == null) {
            throw new IllegalArgumentException(
                    "exactlyOnce requires a route id and CamelContext to validate WireTap destinations");
        }
        Route sourceRoute = context.getRoute(routeId);
        if (sourceRoute == null) {
            throw new IllegalArgumentException(
                    "exactlyOnce cannot validate WireTap destinations because route " + routeId + " is not available");
        }
        if (containsKafkaWireTap(sourceRoute.navigate(), context, new HashSet<>())) {
            throw new IllegalArgumentException(
                    "Kafka exactlyOnce does not support a WireTap whose target can reach a Kafka producer; "
                                               + "send to Kafka synchronously from the original source exchange (route "
                                               + routeId + ")");
        }
    }

    private static boolean containsKafkaWireTap(
            Navigate<Processor> navigation, CamelContext context, Set<String> visitedRoutes) {
        if (navigation == null || !navigation.hasNext()) {
            return false;
        }
        List<Processor> children = navigation.next();
        if (children == null) {
            return false;
        }
        for (Processor child : children) {
            Processor processor = unwrapChannel(child);
            if (processor == null) {
                continue;
            }
            if (processor instanceof WireTapProcessor wireTap
                    && (isDynamicKafkaWireTap(wireTap) || routesToKafka(context, wireTap.getUri(), visitedRoutes))) {
                return true;
            } else if (processor instanceof EndpointAware endpointAware && endpointAware.getEndpoint() != null) {
                String endpointUri = endpointAware.getEndpoint().getEndpointUri();
                if (isRoutedEndpoint(endpointKey(endpointUri))
                        && routeContainsKafkaWireTap(context, endpointUri, visitedRoutes)) {
                    return true;
                }
            }
            if (processor instanceof Navigate<?> nested) {
                @SuppressWarnings("unchecked")
                Navigate<Processor> childNavigation = (Navigate<Processor>) nested;
                if (containsKafkaWireTap(childNavigation, context, visitedRoutes)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean routeContainsKafkaWireTap(CamelContext context, String uri, Set<String> visitedRoutes) {
        String target = endpointKey(uri);
        if (target.isEmpty() || !visitedRoutes.add("wiretap-route:" + target)) {
            return false;
        }

        // Runtime routes are useful once they have started.
        for (Route route : context.getRoutes()) {
            if (route.getEndpoint() != null
                    && endpointKey(route.getEndpoint().getEndpointUri()).equals(target)
                    && containsKafkaWireTap(route.navigate(), context, visitedRoutes)) {
                return true;
            }
        }

        // During incremental startup, a declared route may not be in context.getRoutes() yet.
        for (RouteDefinition route : routeDefinitions(context)) {
            if (route.getInput() != null
                    && endpointKey(route.getInput().getEndpointUri()).equals(target)
                    && containsKafkaWireTap(route, context, visitedRoutes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean routesToKafka(CamelContext context, String uri, Set<String> visitedRoutes) {
        String target = endpointKey(uri);
        if (target.startsWith("kafka:") || target.startsWith("kamelet:")) {
            return true;
        }
        if (target.isEmpty() || !visitedRoutes.add(target)) {
            return false;
        }

        boolean routeFound = false;
        // First inspect routes that have already been started.
        for (Route route : context.getRoutes()) {
            if (route.getEndpoint() != null
                    && endpointKey(route.getEndpoint().getEndpointUri()).equals(target)) {
                routeFound = true;
                if (routeMayReachKafka(route.navigate(), context, visitedRoutes)) {
                    return true;
                }
            }
        }

        // Also inspect the model: a declared direct/seda route may not be active yet.
        for (RouteDefinition route : routeDefinitions(context)) {
            if (route.getInput() != null
                    && endpointKey(route.getInput().getEndpointUri()).equals(target)) {
                routeFound = true;
                if (routeDefinitionMayReachKafka(route, context, visitedRoutes)) {
                    return true;
                }
            }
        }

        // Preserve the conservative behaviour for unresolved routed endpoints.
        return !routeFound && isRoutedEndpoint(target);
    }

    private static List<RouteDefinition> routeDefinitions(CamelContext context) {
        Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
        return model != null ? List.copyOf(model.getRouteDefinitions()) : List.of();
    }

    private static boolean isDynamicKafkaWireTap(WireTapDefinition<?> wireTap) {
        String dynamicUri = wireTap.getDynamicUri();
        return Boolean.parseBoolean(wireTap.getIgnoreInvalidEndpoint())
                || (dynamicUri == null || Boolean.parseBoolean(dynamicUri))
                        && LanguageSupport.hasSimpleFunction(wireTap.getUri());
    }

    private static boolean containsKafkaWireTap(
            ProcessorDefinition<?> definition, CamelContext context, Set<String> visitedRoutes) {
        for (ProcessorDefinition<?> child : definition.getOutputs()) {
            if (child instanceof WireTapDefinition<?> wireTap) {
                if (isDynamicKafkaWireTap(wireTap)
                        || routesToKafka(context, wireTap.getUri(), visitedRoutes)) {
                    return true;
                }
            } else if (child instanceof ToDefinition to) {
                String endpointUri = to.getEndpointUri();
                if (isRoutedEndpoint(endpointKey(endpointUri))
                        && routeContainsKafkaWireTap(context, endpointUri, visitedRoutes)) {
                    return true;
                }
            }

            if (containsKafkaWireTap(child, context, visitedRoutes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean routeDefinitionMayReachKafka(
            ProcessorDefinition<?> definition, CamelContext context, Set<String> visitedRoutes) {
        for (ProcessorDefinition<?> child : definition.getOutputs()) {
            // WireTapDefinition extends ToDynamicDefinition, so it must be checked first.
            if (child instanceof WireTapDefinition<?> wireTap) {
                if (isDynamicKafkaWireTap(wireTap)
                        || routesToKafka(context, wireTap.getUri(), visitedRoutes)) {
                    return true;
                }
            } else if (child instanceof RecipientListDefinition<?>
                    || child instanceof RoutingSlipDefinition<?>
                    || child instanceof DynamicRouterDefinition<?>
                    || child instanceof ToDynamicDefinition) {
                return true;
            } else if (child instanceof ToDefinition to) {
                String endpoint = endpointKey(to.getEndpointUri());
                if (endpoint.startsWith("kafka:") || endpoint.startsWith("kamelet:")) {
                    return true;
                }
                if (isRoutedEndpoint(endpoint)
                        && routesToKafka(context, to.getEndpointUri(), visitedRoutes)) {
                    return true;
                }
            }

            if (routeDefinitionMayReachKafka(child, context, visitedRoutes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean routeMayReachKafka(
            Navigate<Processor> navigation, CamelContext context, Set<String> visitedRoutes) {
        if (navigation == null || !navigation.hasNext()) {
            return false;
        }
        List<Processor> children = navigation.next();
        if (children == null) {
            return false;
        }
        for (Processor child : children) {
            Processor processor = unwrapChannel(child);
            if (processor == null) {
                continue;
            }
            if (processor instanceof WireTapProcessor wireTap) {
                if (isDynamicKafkaWireTap(wireTap) || routesToKafka(context, wireTap.getUri(), visitedRoutes)) {
                    return true;
                }
            } else if (processor instanceof RecipientList || processor instanceof RoutingSlip
                    || processor instanceof EndpointSending && !(processor instanceof SendProcessor)) {
                // RecipientList, RoutingSlip (including DynamicRouter), and dynamic send processors can resolve
                // destinations at runtime; reject them conservatively inside a WireTap target.
                return true;
            } else if (processor instanceof EndpointAware endpointAware && endpointAware.getEndpoint() != null) {
                String endpointUri = endpointAware.getEndpoint().getEndpointUri();
                String endpoint = endpointKey(endpointUri);
                if (endpoint.startsWith("kafka:") || endpoint.startsWith("kamelet:")) {
                    // Kamelets can hide Kafka sends behind route templates and cannot be proven safe here.
                    return true;
                }
                if (isRoutedEndpoint(endpoint) && routesToKafka(context, endpointUri, visitedRoutes)) {
                    return true;
                }
            } else if (processor instanceof EndpointSending) {
                // A dynamic send can resolve to Kafka at runtime; fail safe for Kafka-directed WireTap graphs.
                return true;
            }

            if (processor instanceof Navigate<?> nested) {
                @SuppressWarnings("unchecked")
                Navigate<Processor> childNavigation = (Navigate<Processor>) nested;
                if (routeMayReachKafka(childNavigation, context, visitedRoutes)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determine whether the WireTap actually uses a dynamic sender.
     *
     * The dynamicUri option defaults to true, but a static URI without a Simple expression is still optimized to a
     * normal SendProcessor by WireTapReifier. ignoreInvalidEndpoint also requires a dynamic sender.
     */
    private static boolean isDynamicKafkaWireTap(WireTapProcessor wireTap) {
        return wireTap.isIgnoreInvalidEndpoint()
                || wireTap.isDynamicUri() && LanguageSupport.hasSimpleFunction(wireTap.getUri());
    }

    private static Processor unwrapChannel(Processor processor) {
        while (processor instanceof Channel channel && channel.getNextProcessor() != null) {
            processor = channel.getNextProcessor();
        }
        return processor;
    }

    private static boolean isRoutedEndpoint(String endpoint) {
        int separator = endpoint.indexOf(':');
        if (separator <= 0) {
            return false;
        }
        String scheme = endpoint.substring(0, separator);
        return scheme.equals("direct") || ASYNCHRONOUS_HANDOFF_SCHEMES.contains(scheme);
    }

    static boolean isAsynchronousHandoffEndpoint(String uri) {
        String endpoint = endpointKey(uri);
        int separator = endpoint.indexOf(':');
        return separator > 0 && ASYNCHRONOUS_HANDOFF_SCHEMES.contains(endpoint.substring(0, separator));
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
        String scheme = value.substring(0, colon).toLowerCase(Locale.ROOT);
        String path = value.substring(colon + 1);
        while (path.startsWith("//")) {
            path = path.substring(2);
        }
        return scheme + ":" + path;
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
