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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Helper for easily sending event notifcations in a single line of code
 *
 * @version $Revision$
 */
public final class EventHelper {

    private static final Log LOG = LogFactory.getLog(EventHelper.class);

    private EventHelper() {
    }

    public static void notifyCamelContextStarting(CamelContext context) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createCamelContextStartingEvent(context);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyCamelContextStarted(CamelContext context) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createCamelContextStartedEvent(context);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyCamelContextStartingFailedEvent(CamelContext context, Exception cause) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createCamelContextStartingFailedEvent(context, cause);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyCamelContextStopping(CamelContext context) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createCamelContextStoppingEvent(context);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyCamelContextStopped(CamelContext context) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createCamelContextStoppedEvent(context);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyRouteStarted(CamelContext context, Route route) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createRouteStartedEvent(route);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyRouteStopped(CamelContext context, Route route) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createRouteStoppedEvent(route);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyExchangeCreated(CamelContext context, Exchange exchange) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createExchangeCreatedEvent(exchange);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyExchangeDone(CamelContext context, Exchange exchange) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createExchangeCompletedEvent(exchange);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyExchangeFailed(CamelContext context, Exchange exchange) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createExchangeFailedEvent(exchange);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    public static void notifyExchangeFailureHandled(CamelContext context, Exchange exchange, Processor failureHandler,
                                                    boolean deadLetterChannel) {
        EventNotifier notifier = context.getManagementStrategy().getEventNotifier();
        if (notifier == null) {
            return;
        }
        EventFactory factory = context.getManagementStrategy().getEventFactory();
        if (factory == null) {
            return;
        }
        EventObject event = factory.createExchangeFailureHandledEvent(exchange, failureHandler, deadLetterChannel);
        if (event == null) {
            return;
        }
        doNotifyEvent(notifier, event);
    }

    private static void doNotifyEvent(EventNotifier notifier, EventObject event) {
        if (!notifier.isEnabled(event)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Notification of event is disabled: " + event);
            }
            return;
        }

        try {
            notifier.notify(event);
        } catch (Exception e) {
            LOG.warn("Error notifying event " + event + ". This exception will be ignored. ", e);
        }
    }

}
