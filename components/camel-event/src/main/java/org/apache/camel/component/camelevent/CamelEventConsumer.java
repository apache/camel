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
package org.apache.camel.component.camelevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that registers as an {@link org.apache.camel.spi.EventNotifier} to receive Camel internal events and
 * dispatches them as exchanges to the route.
 */
public class CamelEventConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CamelEventConsumer.class);

    private final EventNotifierSupport eventNotifier;
    private ExecutorService executorService;
    private BlockingQueue<CamelEvent> eventQueue;
    private ScheduledExecutorService batchScheduler;
    private final List<CamelEvent> batchBuffer = new ArrayList<>();

    public CamelEventConsumer(CamelEventEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.eventNotifier = new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                onEvent(event);
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return isEventAccepted(event) || isImplicitlyNeeded(event);
            }
        };
    }

    @Override
    public CamelEventEndpoint getEndpoint() {
        return (CamelEventEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // Configure the notifier here (not in constructor) because doInit() on the endpoint
        // has now been called, so getEventTypes() returns the parsed types
        configureEventNotifier();
        // Create thread pool for async processing
        if (getEndpoint().isAsync()) {
            int poolSize = getEndpoint().getAsyncPoolSize();
            int queueSize = getEndpoint().getAsyncQueueSize();
            eventQueue = new ArrayBlockingQueue<>(queueSize);
            executorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newFixedThreadPool(this, "CamelEventConsumer", poolSize);
            // Start consumer threads that drain the queue
            for (int i = 0; i < poolSize; i++) {
                executorService.submit(this::drainQueue);
            }
        }
        // Set up batch scheduler if batching is enabled
        if (getEndpoint().getBatchSize() > 1) {
            batchScheduler = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newScheduledThreadPool(this, "CamelEventBatch", 1);
            long timeout = getEndpoint().getBatchTimeout();
            batchScheduler.scheduleAtFixedRate(this::flushBatch, timeout, timeout, TimeUnit.MILLISECONDS);
        }
        getEndpoint().getCamelContext().getManagementStrategy().addEventNotifier(eventNotifier);
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().getCamelContext().getManagementStrategy().removeEventNotifier(eventNotifier);
        if (batchScheduler != null) {
            // Flush remaining events before stopping
            flushBatch();
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(batchScheduler);
            batchScheduler = null;
        }
        if (eventQueue != null) {
            eventQueue.clear();
        }
        if (executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
        eventQueue = null;
        super.doStop();
    }

    /**
     * Consumer thread loop that drains events from the queue and processes them.
     */
    private void drainQueue() {
        while (isRunAllowed()) {
            try {
                CamelEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    if (getEndpoint().getBatchSize() > 1) {
                        addToBatch(event);
                    } else {
                        processEvent(event);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing event", e);
            }
        }
    }

    /**
     * Adds an event to the batch buffer and flushes when the batch is full.
     */
    private void addToBatch(CamelEvent event) {
        List<CamelEvent> toFlush = null;
        synchronized (batchBuffer) {
            batchBuffer.add(event);
            if (batchBuffer.size() >= getEndpoint().getBatchSize()) {
                toFlush = new ArrayList<>(batchBuffer);
                batchBuffer.clear();
            }
        }
        if (toFlush != null) {
            processBatch(toFlush);
        }
    }

    /**
     * Flushes any accumulated events in the batch buffer (called by the scheduled timer).
     */
    private void flushBatch() {
        List<CamelEvent> toFlush = null;
        synchronized (batchBuffer) {
            if (!batchBuffer.isEmpty()) {
                toFlush = new ArrayList<>(batchBuffer);
                batchBuffer.clear();
            }
        }
        if (toFlush != null) {
            processBatch(toFlush);
        }
    }

    /**
     * Processes a batch of events by creating a single exchange with a List body.
     */
    private void processBatch(List<CamelEvent> events) {
        try {
            Exchange exchange = createExchange(true);
            exchange.getExchangeExtension().setNotifyEvent(true);
            try {
                exchange.getIn().setBody(events);
                exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_BATCH_SIZE, events.size());
                // Use the first event's type for the header
                if (!events.isEmpty()) {
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_TYPE, events.get(0).getType().name());
                }
                getProcessor().process(exchange);
            } finally {
                exchange.getExchangeExtension().setNotifyEvent(false);
                releaseExchange(exchange, false);
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing event batch", e);
        }
    }

    /**
     * Configures the event notifier to enable only the event categories that are needed based on the subscribed event
     * types.
     */
    private void configureEventNotifier() {
        Set<CamelEvent.Type> types = getEndpoint().getEventTypes();
        if (types == null || types.isEmpty()) {
            return;
        }

        // Start by ignoring everything
        eventNotifier.setIgnoreCamelContextInitEvents(true);
        eventNotifier.setIgnoreCamelContextEvents(true);
        eventNotifier.setIgnoreRouteEvents(true);
        eventNotifier.setIgnoreServiceEvents(true);
        eventNotifier.setIgnoreExchangeEvents(true);
        eventNotifier.setIgnoreExchangeCreatedEvent(true);
        eventNotifier.setIgnoreExchangeCompletedEvent(true);
        eventNotifier.setIgnoreExchangeFailedEvents(true);
        eventNotifier.setIgnoreExchangeRedeliveryEvents(true);
        eventNotifier.setIgnoreExchangeSendingEvents(true);
        eventNotifier.setIgnoreExchangeSentEvents(true);
        eventNotifier.setIgnoreStepEvents(true);

        // Enable only the categories that contain the requested event types
        for (CamelEvent.Type type : types) {
            switch (type) {
                case CamelContextInitializing:
                case CamelContextInitialized:
                    eventNotifier.setIgnoreCamelContextInitEvents(false);
                    break;
                case CamelContextResumed:
                case CamelContextResumeFailure:
                case CamelContextResuming:
                case CamelContextStarted:
                case CamelContextStarting:
                case CamelContextStartupFailure:
                case CamelContextStopFailure:
                case CamelContextStopped:
                case CamelContextStopping:
                case CamelContextSuspended:
                case CamelContextSuspending:
                case CamelContextReloading:
                case CamelContextReloaded:
                case CamelContextReloadFailure:
                case RoutesStarting:
                case RoutesStarted:
                case RoutesStopping:
                case RoutesStopped:
                    eventNotifier.setIgnoreCamelContextEvents(false);
                    break;
                case RouteAdded:
                case RouteRemoved:
                case RouteReloaded:
                case RouteStarting:
                case RouteStarted:
                case RouteStopping:
                case RouteStopped:
                case RouteRestarting:
                case RouteRestartingFailure:
                    eventNotifier.setIgnoreRouteEvents(false);
                    break;
                case ServiceStartupFailure:
                case ServiceStopFailure:
                    eventNotifier.setIgnoreServiceEvents(false);
                    break;
                case ExchangeCreated:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreExchangeCreatedEvent(false);
                    break;
                case ExchangeCompleted:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreExchangeCompletedEvent(false);
                    break;
                case ExchangeFailed:
                case ExchangeFailureHandled:
                case ExchangeFailureHandling:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreExchangeFailedEvents(false);
                    break;
                case ExchangeRedelivery:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreExchangeRedeliveryEvents(false);
                    break;
                case ExchangeSending:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreExchangeSendingEvents(false);
                    break;
                case ExchangeSent:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreExchangeSentEvents(false);
                    // ExchangeSending must also be enabled for the framework to generate ExchangeSent events
                    eventNotifier.setIgnoreExchangeSendingEvents(false);
                    break;
                case StepStarted:
                case StepCompleted:
                case StepFailed:
                    eventNotifier.setIgnoreExchangeEvents(false);
                    eventNotifier.setIgnoreStepEvents(false);
                    break;
                case Custom:
                    // Custom events are CamelContextEvents, so we need to enable context events
                    eventNotifier.setIgnoreCamelContextEvents(false);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Checks whether the event is not explicitly subscribed but is needed for framework mechanics. For example,
     * ExchangeSending events must be accepted for the framework to generate ExchangeSent events (the framework uses
     * ExchangeSending to start a StopWatch whose result is used for ExchangeSent).
     */
    private boolean isImplicitlyNeeded(CamelEvent event) {
        Set<CamelEvent.Type> types = getEndpoint().getEventTypes();
        if (types == null) {
            return false;
        }
        // ExchangeSending must be accepted if ExchangeSent is subscribed
        if (event.getType() == CamelEvent.Type.ExchangeSending && types.contains(CamelEvent.Type.ExchangeSent)) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether the given event matches the subscribed event types and filters.
     */
    boolean isEventAccepted(CamelEvent event) {
        Set<CamelEvent.Type> types = getEndpoint().getEventTypes();
        if (types == null || types.isEmpty()) {
            return false;
        }

        // Check if the event type matches
        if (!types.contains(event.getType())) {
            return false;
        }

        // Apply custom event class filter
        Class<?> customClazz = getEndpoint().getCustomEventClazz();
        if (customClazz != null && !customClazz.isInstance(event)) {
            return false;
        }

        // Apply route ID filters
        String routeId = extractRouteId(event);

        // Apply include filter if configured
        Set<String> includes = getEndpoint().getIncludeValues();
        if (includes != null && !includes.isEmpty()) {
            if (routeId != null && !includes.contains(routeId)) {
                return false;
            }
            // If we can't extract a route ID, don't filter (allow the event through)
        }

        // Apply exclude filter if configured
        Set<String> excludes = getEndpoint().getExcludeValues();
        if (excludes != null && !excludes.isEmpty()) {
            if (routeId != null && excludes.contains(routeId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts the route ID from a Camel event, if available.
     */
    private String extractRouteId(CamelEvent event) {
        if (event instanceof CamelEvent.RouteEvent routeEvent) {
            Route route = routeEvent.getRoute();
            return route != null ? route.getRouteId() : null;
        }
        if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
            Exchange exchange = exchangeEvent.getExchange();
            if (exchange != null) {
                return exchange.getFromRouteId();
            }
        }
        return null;
    }

    /**
     * Called when a matching Camel event is received. Creates an exchange and processes it.
     */
    private void onEvent(CamelEvent event) throws Exception {
        // Skip events that are only implicitly needed (e.g., ExchangeSending accepted for ExchangeSent framework mechanics)
        if (!isEventAccepted(event)) {
            return;
        }

        if (eventQueue != null) {
            // Async mode: enqueue with backpressure policy
            enqueueEvent(event);
        } else if (getEndpoint().getBatchSize() > 1) {
            // Synchronous batching (no async)
            addToBatch(event);
        } else {
            processEvent(event);
        }
    }

    /**
     * Enqueues an event into the async queue, applying the configured backpressure policy when the queue is full.
     */
    private void enqueueEvent(CamelEvent event) throws Exception {
        BackpressurePolicy policy = getEndpoint().getBackpressurePolicy();
        switch (policy) {
            case Block:
                try {
                    eventQueue.put(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                break;
            case Drop:
                if (!eventQueue.offer(event)) {
                    LOG.debug("Event queue full, dropping event: {}", event.getType());
                }
                break;
            case Fail:
                if (!eventQueue.offer(event)) {
                    throw new IllegalStateException(
                            "Event queue full (capacity: " + getEndpoint().getAsyncQueueSize()
                                                    + "), cannot enqueue event: " + event.getType());
                }
                break;
        }
    }

    /**
     * Processes a Camel event by creating an exchange, setting headers, and dispatching to the processor.
     */
    private void processEvent(CamelEvent event) throws Exception {
        Exchange exchange = createExchange(true);
        // Mark exchange as a notify event to prevent the framework from generating
        // recursive events for this exchange (same pattern as PublishEventNotifier)
        exchange.getExchangeExtension().setNotifyEvent(true);
        try {
            exchange.getIn().setBody(event);
            exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_TYPE, event.getType().name());
            if (event.getTimestamp() > 0) {
                exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_TIMESTAMP, event.getTimestamp());
            }

            // Add exception header for any failure event (uses FailureEvent.getCause() which is
            // more reliable than checking exchange.getException())
            if (event instanceof CamelEvent.FailureEvent failureEvent) {
                Throwable cause = failureEvent.getCause();
                if (cause != null) {
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_EXCEPTION, cause.getMessage());
                }
            }

            // Add route-specific headers for route events
            if (event instanceof CamelEvent.RouteEvent routeEvent) {
                Route route = routeEvent.getRoute();
                if (route != null) {
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_ROUTE_ID, route.getRouteId());
                }
            }

            // Add enrichment headers for exchange events
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                Exchange sourceExchange = exchangeEvent.getExchange();
                if (sourceExchange != null) {
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_EXCHANGE_ID,
                            sourceExchange.getExchangeId());
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_ROUTE_ID,
                            sourceExchange.getFromRouteId());
                    Endpoint fromEndpoint = sourceExchange.getFromEndpoint();
                    if (fromEndpoint != null) {
                        exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_ENDPOINT_URI,
                                fromEndpoint.getEndpointUri());
                    }
                }
            }

            // Add step ID for step events
            if (event instanceof CamelEvent.StepEvent stepEvent) {
                exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_STEP_ID, stepEvent.getStepId());
            }

            // Add redelivery attempt for redelivery events
            if (event instanceof CamelEvent.ExchangeRedeliveryEvent redeliveryEvent) {
                exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_REDELIVERY_ATTEMPT,
                        redeliveryEvent.getAttempt());
            }

            // Add specific headers for ExchangeSendingEvent
            if (event instanceof CamelEvent.ExchangeSendingEvent sendingEvent) {
                Endpoint ep = sendingEvent.getEndpoint();
                if (ep != null) {
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_ENDPOINT_URI, ep.getEndpointUri());
                }
            }

            // Add specific headers for ExchangeSentEvent
            if (event instanceof CamelEvent.ExchangeSentEvent sentEvent) {
                Endpoint ep = sentEvent.getEndpoint();
                if (ep != null) {
                    exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_ENDPOINT_URI, ep.getEndpointUri());
                }
                exchange.getIn().setHeader(CamelEventConstants.HEADER_EVENT_DURATION, sentEvent.getTimeTaken());
            }

            getProcessor().process(exchange);
        } finally {
            exchange.getExchangeExtension().setNotifyEvent(false);
            releaseExchange(exchange, false);
        }
    }
}
