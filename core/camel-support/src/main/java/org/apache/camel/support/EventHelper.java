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
package org.apache.camel.support;

import java.util.List;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for easily sending event notifications in a single line of code
 */
@SuppressWarnings("UnusedReturnValue")
public final class EventHelper {

    // This implementation has been optimized to be as fast and not create unnecessary objects or lambdas.
    // Therefore there is some code that seems duplicated. But this code is used frequently during routing and should
    // be left as-is.

    private static final Logger LOG = LoggerFactory.getLogger(EventHelper.class);

    private EventHelper() {
    }

    /**
     * Checks whether event notifications is applicable or not
     */
    public static boolean eventsApplicable(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }
        // is there any notifiers that would receive exchange events
        boolean exchange = false;
        for (EventNotifier en : notifiers) {
            exchange |= !en.isIgnoreExchangeEvents();
        }
        return exchange;
    }

    public static boolean notifyCamelContextInitializing(CamelContext context) {
        return notifyCamelContext(context, EventFactory::createCamelContextInitializingEvent, true);
    }

    public static boolean notifyCamelContextInitialized(CamelContext context) {
        return notifyCamelContext(context, EventFactory::createCamelContextInitializedEvent, true);
    }

    public static boolean notifyCamelContextStarting(CamelContext context) {
        return notifyCamelContext(context, EventFactory::createCamelContextStartingEvent, false);
    }

    public static boolean notifyCamelContextStarted(CamelContext context) {
        return notifyCamelContext(context, EventFactory::createCamelContextStartedEvent, false);
    }

    public static boolean notifyCamelContextStartupFailed(CamelContext context, Throwable cause) {
        return notifyCamelContext(context, (ef, ctx) -> ef.createCamelContextStartupFailureEvent(ctx, cause), false);
    }

    public static boolean notifyCamelContextStopping(CamelContext context) {
        return notifyCamelContext(context, EventFactory::createCamelContextStoppingEvent, false);
    }

    public static boolean notifyCamelContextStopped(CamelContext context) {
        return notifyCamelContext(context, EventFactory::createCamelContextStoppedEvent, false);
    }

    public static boolean notifyCamelContextStopFailed(CamelContext context, Throwable cause) {
        return notifyCamelContext(context, (ef, ctx) -> ef.createCamelContextStopFailureEvent(ctx, cause), false);
    }

    private static boolean notifyCamelContext(
            CamelContext context, BiFunction<EventFactory, CamelContext, CamelEvent> eventSupplier, boolean init) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        // init camel context events are triggered before event notifiers is started so get those pre-started notifiers
        // so we can emit those special init events
        List<EventNotifier> notifiers = init ? management.getEventNotifiers() : management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }
            if (init && notifier.isIgnoreCamelContextInitEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = eventSupplier.apply(factory, context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyServiceStopFailure(CamelContext context, Object service, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreServiceEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createServiceStopFailureEvent(context, service, cause);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyServiceStartupFailure(CamelContext context, Object service, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreServiceEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createServiceStartupFailureEvent(context, service, cause);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteStarting(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteStartingEvent(route);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteStarted(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteStartedEvent(route);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteStopping(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteStoppingEvent(route);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteStopped(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteStoppedEvent(route);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteAdded(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteAddedEvent(route);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteRemoved(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteRemovedEvent(route);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyRouteReloaded(CamelContext context, Route route, int index, int total) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createRouteReloaded(route, index, total);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyContextReloading(CamelContext context, Object source) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextReloading(context, source);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyContextReloaded(CamelContext context, Object source) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextReloaded(context, source);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyContextReloadFailure(CamelContext context, Object source, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextReloadFailure(context, source, cause);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeCreated(CamelContext context, Exchange exchange) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeCreatedEvent()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeCreatedEvent(exchange);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeDone(CamelContext context, Exchange exchange) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeCompletedEvent()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeCompletedEvent(exchange);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeFailed(CamelContext context, Exchange exchange) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);

            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeFailedEvent(exchange);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeFailureHandling(
            CamelContext context, Exchange exchange, Processor failureHandler,
            boolean deadLetterChannel, String deadLetterUri) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);

            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeFailureHandlingEvent(exchange, failureHandler, deadLetterChannel, deadLetterUri);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeFailureHandled(
            CamelContext context, Exchange exchange, Processor failureHandler,
            boolean deadLetterChannel, String deadLetterUri) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeFailureHandledEvent(exchange, failureHandler, deadLetterChannel, deadLetterUri);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeRedelivery(CamelContext context, Exchange exchange, int attempt) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);

            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeRedeliveryEvent(exchange, attempt);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeSending(CamelContext context, Exchange exchange, Endpoint endpoint) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeSendingEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeSendingEvent(exchange, endpoint);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeSent(CamelContext context, Exchange exchange, Endpoint endpoint, long timeTaken) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeSentEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createExchangeSentEvent(exchange, endpoint, timeTaken);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextSuspending(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextSuspendingEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextSuspended(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextSuspendedEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextResuming(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextResumingEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextResumed(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextResumedEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextResumeFailed(CamelContext context, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextResumeFailureEvent(context, cause);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextRoutesStarting(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextRoutesStartingEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextRoutesStarted(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextRoutesStartedEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextRoutesStopping(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextRoutesStoppingEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextRoutesStopped(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextRoutesStoppedEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyStepStarted(CamelContext context, Exchange exchange, String stepId) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreStepEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createStepStartedEvent(exchange, stepId);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyStepDone(CamelContext context, Exchange exchange, String stepId) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreStepEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createStepCompletedEvent(exchange, stepId);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyStepFailed(CamelContext context, Exchange exchange, String stepId) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreStepEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createStepFailedEvent(exchange, stepId);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyExchangeAsyncProcessingStartedEvent(CamelContext context, Exchange exchange) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return false;
        }

        EventFactory factory = management.getEventFactory();
        if (factory == null) {
            return false;
        }

        List<EventNotifier> notifiers = management.getStartedEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        if (exchange.getExchangeExtension().isNotifyEvent()) {
            // do not generate events for an notify event
            return false;
        }

        boolean answer = false;
        CamelEvent event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (isDisabledOrIgnored(notifier) || notifier.isIgnoreExchangeAsyncProcessingStartedEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelExchangeAsyncProcessingStartedEvent(exchange);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    private static boolean isDisabledOrIgnored(EventNotifier notifier) {
        return notifier.isDisabled() || notifier.isIgnoreExchangeEvents();
    }

    private static boolean doNotifyEvent(EventNotifier notifier, CamelEvent event) {
        if (!notifier.isEnabled(event)) {
            return false;
        }

        try {
            notifier.notify(event);
        } catch (Throwable e) {
            LOG.warn("Error notifying event {}. This exception will be ignored.", event, e);
        }

        return true;
    }
}
