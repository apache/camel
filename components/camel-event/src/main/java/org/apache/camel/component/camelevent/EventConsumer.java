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

import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EventNotifierSupport;

/**
 * Consumer that registers as an {@link org.apache.camel.spi.EventNotifier} to receive Camel internal events and
 * dispatches them as exchanges to the route.
 */
public class EventConsumer extends DefaultConsumer {

    private final EventNotifierSupport eventNotifier;

    public EventConsumer(EventEndpoint endpoint, Processor processor) {
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
    public EventEndpoint getEndpoint() {
        return (EventEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // Configure the notifier here (not in constructor) because doInit() on the endpoint
        // has now been called, so getEventTypes() returns the parsed types
        configureEventNotifier();
        getEndpoint().getCamelContext().getManagementStrategy().addEventNotifier(eventNotifier);
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().getCamelContext().getManagementStrategy().removeEventNotifier(eventNotifier);
        super.doStop();
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

        // Apply filter if configured
        Set<String> filters = getEndpoint().getFilterValues();
        if (filters != null && !filters.isEmpty()) {
            String routeId = extractRouteId(event);
            if (routeId != null) {
                return filters.contains(routeId);
            }
            // If we can't extract a route ID, don't filter (allow the event through)
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

        Exchange exchange = createExchange(true);
        // Mark exchange as a notify event to prevent the framework from generating
        // recursive events for this exchange (same pattern as PublishEventNotifier)
        exchange.getExchangeExtension().setNotifyEvent(true);
        try {
            exchange.getIn().setBody(event);
            exchange.getIn().setHeader(EventConstants.HEADER_EVENT_TYPE, event.getType().name());
            if (event.getTimestamp() > 0) {
                exchange.getIn().setHeader(EventConstants.HEADER_EVENT_TIMESTAMP, event.getTimestamp());
            }

            // Add exception header for any failure event (uses FailureEvent.getCause() which is
            // more reliable than checking exchange.getException())
            if (event instanceof CamelEvent.FailureEvent failureEvent) {
                Throwable cause = failureEvent.getCause();
                if (cause != null) {
                    exchange.getIn().setHeader(EventConstants.HEADER_EVENT_EXCEPTION, cause.getMessage());
                }
            }

            // Add route-specific headers for route events
            if (event instanceof CamelEvent.RouteEvent routeEvent) {
                Route route = routeEvent.getRoute();
                if (route != null) {
                    exchange.getIn().setHeader(EventConstants.HEADER_EVENT_ROUTE_ID, route.getRouteId());
                }
            }

            // Add enrichment headers for exchange events
            if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
                Exchange sourceExchange = exchangeEvent.getExchange();
                if (sourceExchange != null) {
                    exchange.getIn().setHeader(EventConstants.HEADER_EVENT_EXCHANGE_ID, sourceExchange.getExchangeId());
                    exchange.getIn().setHeader(EventConstants.HEADER_EVENT_ROUTE_ID, sourceExchange.getFromRouteId());
                    Endpoint fromEndpoint = sourceExchange.getFromEndpoint();
                    if (fromEndpoint != null) {
                        exchange.getIn().setHeader(EventConstants.HEADER_EVENT_ENDPOINT_URI,
                                fromEndpoint.getEndpointUri());
                    }
                }
            }

            // Add step ID for step events
            if (event instanceof CamelEvent.StepEvent stepEvent) {
                exchange.getIn().setHeader(EventConstants.HEADER_EVENT_STEP_ID, stepEvent.getStepId());
            }

            // Add redelivery attempt for redelivery events
            if (event instanceof CamelEvent.ExchangeRedeliveryEvent redeliveryEvent) {
                exchange.getIn().setHeader(EventConstants.HEADER_EVENT_REDELIVERY_ATTEMPT,
                        redeliveryEvent.getAttempt());
            }

            // Add specific headers for ExchangeSendingEvent
            if (event instanceof CamelEvent.ExchangeSendingEvent sendingEvent) {
                Endpoint ep = sendingEvent.getEndpoint();
                if (ep != null) {
                    exchange.getIn().setHeader(EventConstants.HEADER_EVENT_ENDPOINT_URI, ep.getEndpointUri());
                }
            }

            // Add specific headers for ExchangeSentEvent
            if (event instanceof CamelEvent.ExchangeSentEvent sentEvent) {
                Endpoint ep = sentEvent.getEndpoint();
                if (ep != null) {
                    exchange.getIn().setHeader(EventConstants.HEADER_EVENT_ENDPOINT_URI, ep.getEndpointUri());
                }
                exchange.getIn().setHeader(EventConstants.HEADER_EVENT_DURATION, sentEvent.getTimeTaken());
            }

            getProcessor().process(exchange);
        } finally {
            exchange.getExchangeExtension().setNotifyEvent(false);
            releaseExchange(exchange, false);
        }
    }
}
