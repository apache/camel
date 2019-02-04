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
package org.apache.camel.support;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for easily sending event notifications in a single line of code
 */
public final class EventHelper {

    private static final Logger LOG = LoggerFactory.getLogger(EventHelper.class);

    private EventHelper() {
    }

    public static boolean notifyCamelContextStarting(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextStartingEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextStarted(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextStartedEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextStartupFailed(CamelContext context, Throwable cause) {
        return doNotify(context,
            factory -> factory.createCamelContextStartupFailureEvent(context, cause),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextStopping(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextStoppingEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextStopped(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextStoppedEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextStopFailure(CamelContext context, Throwable cause) {
        return doNotify(context,
            factory -> factory.createCamelContextStopFailureEvent(context, cause),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyServiceStopFailure(CamelContext context, Object service, Throwable cause) {
        return doNotify(context,
            factory -> factory.createServiceStopFailureEvent(context, service, cause),
            EventNotifier::isIgnoreServiceEvents);
    }

    public static boolean notifyServiceStartupFailure(CamelContext context, Object service, Throwable cause) {
        return doNotify(context,
            factory -> factory.createServiceStartupFailureEvent(context, service, cause),
            EventNotifier::isIgnoreServiceEvents);
    }

    public static boolean notifyRouteStarted(CamelContext context, Route route) {
        return doNotify(context,
            factory -> factory.createRouteStartedEvent(route),
            EventNotifier::isIgnoreRouteEvents);
    }

    public static boolean notifyRouteStopped(CamelContext context, Route route) {
        return doNotify(context,
            factory -> factory.createRouteStoppedEvent(route),
            EventNotifier::isIgnoreRouteEvents);
    }

    public static boolean notifyRouteAdded(CamelContext context, Route route) {
        return doNotify(context,
            factory -> factory.createRouteAddedEvent(route),
            EventNotifier::isIgnoreRouteEvents);
    }

    public static boolean notifyRouteRemoved(CamelContext context, Route route) {
        return doNotify(context,
            factory -> factory.createRouteRemovedEvent(route),
            EventNotifier::isIgnoreRouteEvents);
    }

    public static boolean notifyExchangeCreated(CamelContext context, Exchange exchange) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeCreatedEvent(exchange),
            EventNotifier::isIgnoreExchangeCreatedEvent);
    }

    public static boolean notifyExchangeDone(CamelContext context, Exchange exchange) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeCompletedEvent(exchange),
            EventNotifier::isIgnoreExchangeCompletedEvent);
    }

    public static boolean notifyExchangeFailed(CamelContext context, Exchange exchange) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeFailedEvent(exchange),
            EventNotifier::isIgnoreExchangeFailedEvents);
    }

    public static boolean notifyExchangeFailureHandling(CamelContext context, Exchange exchange, Processor failureHandler,
                                                     boolean deadLetterChannel, String deadLetterUri) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeFailureHandlingEvent(exchange, failureHandler, deadLetterChannel, deadLetterUri),
            EventNotifier::isIgnoreExchangeFailedEvents);
    }

    public static boolean notifyExchangeFailureHandled(CamelContext context, Exchange exchange, Processor failureHandler,
                                                    boolean deadLetterChannel, String deadLetterUri) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeFailureHandledEvent(exchange, failureHandler, deadLetterChannel, deadLetterUri),
            EventNotifier::isIgnoreExchangeFailedEvents);
    }

    public static boolean notifyExchangeRedelivery(CamelContext context, Exchange exchange, int attempt) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeRedeliveryEvent(exchange, attempt),
            EventNotifier::isIgnoreExchangeRedeliveryEvents);
    }

    public static boolean notifyExchangeSending(CamelContext context, Exchange exchange, Endpoint endpoint) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeSendingEvent(exchange, endpoint),
            EventNotifier::isIgnoreExchangeSendingEvents);
    }

    public static boolean notifyExchangeSent(CamelContext context, Exchange exchange, Endpoint endpoint, long timeTaken) {
        return doNotifyExchange(context, exchange,
            factory -> factory.createExchangeSentEvent(exchange, endpoint, timeTaken),
            EventNotifier::isIgnoreExchangeSentEvents);
    }

    public static boolean notifyCamelContextSuspending(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextSuspendingEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextSuspended(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextSuspendedEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextResuming(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextResumingEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextResumed(CamelContext context) {
        return doNotify(context,
            factory -> factory.createCamelContextResumedEvent(context),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    public static boolean notifyCamelContextResumeFailed(CamelContext context, Throwable cause) {
        return doNotify(context,
            factory -> factory.createCamelContextResumeFailureEvent(context, cause),
            EventNotifier::isIgnoreCamelContextEvents);
    }

    private static boolean doNotifyExchange(CamelContext context, Exchange exchange, Function<EventFactory, CamelEvent> eventSupplier, Predicate<EventNotifier> notifierFilter) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }
        return doNotify(context,
                eventSupplier,
                notifierFilter.or(EventNotifier::isIgnoreExchangeEvents));
    }

    private static boolean doNotify(CamelContext context, Function<EventFactory, CamelEvent> eventSupplier, Predicate<EventNotifier> notifierFilter) {
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

        boolean answer = false;
        CamelEvent event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifierFilter.test(notifier)) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = eventSupplier.apply(factory);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    private static boolean doNotifyEvent(EventNotifier notifier, CamelEvent event) {
        // only notify if notifier is started
        if (!ServiceHelper.isStarted(notifier)) {
            LOG.debug("Ignoring notifying event {}. The EventNotifier has not been started yet: {}", event, notifier);
            return false;
        }

        if (!notifier.isEnabled(event)) {
            LOG.trace("Notifier: {} is not enabled for the event: {}", notifier, event);
            return false;
        }

        try {
            notifier.notify(event);
        } catch (Throwable e) {
            LOG.warn("Error notifying event " + event + ". This exception will be ignored. ", e);
        }

        return true;
    }

}
