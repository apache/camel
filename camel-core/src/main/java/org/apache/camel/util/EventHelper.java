/**
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
package org.apache.camel.util;

import java.util.EventObject;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for easily sending event notifications in a single line of code
 *
 * @version 
 */
public final class EventHelper {

    private static final Logger LOG = LoggerFactory.getLogger(EventHelper.class);

    private EventHelper() {
    }

    public static void notifyCamelContextStarting(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextStartingEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextStarted(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextStartedEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextStartupFailed(CamelContext context, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextStartupFailureEvent(context, cause);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextStopping(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextStoppingEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextStopped(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextStoppedEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextStopFailed(CamelContext context, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextStopFailureEvent(context, cause);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyServiceStopFailure(CamelContext context, Object service, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreServiceEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createServiceStopFailureEvent(context, service, cause);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyServiceStartupFailure(CamelContext context, Object service, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreServiceEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createServiceStartupFailureEvent(context, service, cause);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyRouteStarted(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createRouteStartedEvent(route);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyRouteStopped(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createRouteStoppedEvent(route);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyRouteAdded(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createRouteAddedEvent(route);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyRouteRemoved(CamelContext context, Route route) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreRouteEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createRouteRemovedEvent(route);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeCreated(CamelContext context, Exchange exchange) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeCreatedEvent()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeCreatedEvent(exchange);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeDone(CamelContext context, Exchange exchange) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeCompletedEvent()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeCompletedEvent(exchange);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeFailed(CamelContext context, Exchange exchange) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeFailedEvent(exchange);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeFailureHandling(CamelContext context, Exchange exchange, Processor failureHandler,
                                                     boolean deadLetterChannel, String deadLetterUri) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeFailureHandlingEvent(exchange, failureHandler, deadLetterChannel, deadLetterUri);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeFailureHandled(CamelContext context, Exchange exchange, Processor failureHandler,
                                                    boolean deadLetterChannel, String deadLetterUri) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeFailureHandledEvent(exchange, failureHandler, deadLetterChannel, deadLetterUri);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeRedelivery(CamelContext context, Exchange exchange, int attempt) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeRedeliveryEvent(exchange, attempt);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeSending(CamelContext context, Exchange exchange, Endpoint endpoint) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeSentEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeSendingEvent(exchange, endpoint);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyExchangeSent(CamelContext context, Exchange exchange, Endpoint endpoint, long timeTaken) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return;
        }

        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeSentEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createExchangeSentEvent(exchange, endpoint, timeTaken);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextSuspending(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextSuspendingEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextSuspended(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextSuspendedEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextResuming(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextResumingEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextResumed(CamelContext context) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextResumedEvent(context);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    public static void notifyCamelContextResumeFailed(CamelContext context, Throwable cause) {
        ManagementStrategy management = context.getManagementStrategy();
        if (management == null) {
            return;
        }

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return;
        }

        for (EventNotifier notifier : notifiers) {
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            EventFactory factory = management.getEventFactory();
            if (factory == null) {
                return;
            }
            EventObject event = factory.createCamelContextResumeFailureEvent(context, cause);
            if (event == null) {
                return;
            }
            doNotifyEvent(notifier, event);
        }
    }

    private static void doNotifyEvent(EventNotifier notifier, EventObject event) {
        // only notify if notifier is started
        boolean started = true;
        if (notifier instanceof StatefulService) {
            started = ((StatefulService) notifier).isStarted();
        }
        if (!started) {
            LOG.debug("Ignoring notifying event {}. The EventNotifier has not been started yet: {}", event, notifier);
            return;
        }

        if (!notifier.isEnabled(event)) {
            LOG.trace("Notifier: {} is not enabled for the event: {}", notifier, event);
            return;
        }

        try {
            notifier.notify(event);
        } catch (Throwable e) {
            LOG.warn("Error notifying event " + event + ". This exception will be ignored. ", e);
        }
    }

}
