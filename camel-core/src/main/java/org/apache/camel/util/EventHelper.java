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

    public static boolean notifyCamelContextStarting(CamelContext context) {
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
        EventObject event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextStartingEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextStarted(CamelContext context) {
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
        EventObject event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextStartedEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextStartupFailed(CamelContext context, Throwable cause) {
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
        EventObject event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextStartupFailureEvent(context, cause);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextStopping(CamelContext context) {
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
        EventObject event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextStoppingEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextStopped(CamelContext context) {
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
        EventObject event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextStoppedEvent(context);
                if (event == null) {
                    // factory could not create event so exit
                    return false;
                }
            }
            answer |= doNotifyEvent(notifier, event);
        }
        return answer;
    }

    public static boolean notifyCamelContextStopFailed(CamelContext context, Throwable cause) {
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
        EventObject event = null;
        for (EventNotifier notifier : notifiers) {
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreCamelContextEvents()) {
                continue;
            }

            if (event == null) {
                // only create event once
                event = factory.createCamelContextStopFailureEvent(context, cause);
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

    public static boolean notifyRouteStarted(CamelContext context, Route route) {
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
        EventObject event = null;
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

    public static boolean notifyRouteStopped(CamelContext context, Route route) {
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
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

    public static boolean notifyExchangeCreated(CamelContext context, Exchange exchange) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
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
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeCompletedEvent()) {
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
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
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

    public static boolean notifyExchangeFailureHandling(CamelContext context, Exchange exchange, Processor failureHandler,
                                                     boolean deadLetterChannel, String deadLetterUri) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
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

    public static boolean notifyExchangeFailureHandled(CamelContext context, Exchange exchange, Processor failureHandler,
                                                    boolean deadLetterChannel, String deadLetterUri) {
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
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
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeFailedEvents()) {
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
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeSentEvents()) {
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
        if (exchange.getProperty(Exchange.NOTIFY_EVENT, false, Boolean.class)) {
            // do not generate events for an notify event
            return false;
        }

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
        EventObject event = null;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0; i < notifiers.size(); i++) {
            EventNotifier notifier = notifiers.get(i);
            if (notifier.isDisabled()) {
                continue;
            }
            if (notifier.isIgnoreExchangeEvents() || notifier.isIgnoreExchangeSentEvents()) {
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

        List<EventNotifier> notifiers = management.getEventNotifiers();
        if (notifiers == null || notifiers.isEmpty()) {
            return false;
        }

        boolean answer = false;
        EventObject event = null;
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

    private static boolean doNotifyEvent(EventNotifier notifier, EventObject event) {
        // only notify if notifier is started
        boolean started = true;
        if (notifier instanceof StatefulService) {
            started = ((StatefulService) notifier).isStarted();
        }
        if (!started) {
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
