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
package org.apache.camel.management;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.management.event.CamelContextResumeFailureEvent;
import org.apache.camel.management.event.CamelContextResumedEvent;
import org.apache.camel.management.event.CamelContextResumingEvent;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.management.event.CamelContextStartupFailureEvent;
import org.apache.camel.management.event.CamelContextStopFailureEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.management.event.CamelContextSuspendedEvent;
import org.apache.camel.management.event.CamelContextSuspendingEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeFailureHandledEvent;
import org.apache.camel.management.event.ExchangeRedeliveryEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.management.event.RouteStoppedEvent;
import org.apache.camel.management.event.ServiceStartupFailureEvent;
import org.apache.camel.management.event.ServiceStopFailureEvent;
import org.apache.camel.spi.EventFactory;

/**
 * Default implementation of the {@link org.apache.camel.spi.EventFactory}.
 *
 * @version 
 */
public class DefaultEventFactory implements EventFactory {

    public EventObject createCamelContextStartingEvent(CamelContext context) {
        return new CamelContextStartingEvent(context);
    }

    public EventObject createCamelContextStartedEvent(CamelContext context) {
        return new CamelContextStartedEvent(context);
    }

    public EventObject createCamelContextStoppingEvent(CamelContext context) {
        return new CamelContextStoppingEvent(context);
    }

    public EventObject createCamelContextStoppedEvent(CamelContext context) {
        return new CamelContextStoppedEvent(context);
    }

    public EventObject createCamelContextStartupFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextStartupFailureEvent(context, cause);
    }

    public EventObject createCamelContextStopFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextStopFailureEvent(context, cause);
    }

    public EventObject createServiceStartupFailureEvent(CamelContext context, Object service, Throwable cause) {
        return new ServiceStartupFailureEvent(context, service, cause);
    }

    public EventObject createServiceStopFailureEvent(CamelContext context, Object service, Throwable cause) {
        return new ServiceStopFailureEvent(context, service, cause);
    }

    public EventObject createRouteStartedEvent(Route route) {
        return new RouteStartedEvent(route);
    }

    public EventObject createRouteStoppedEvent(Route route) {
        return new RouteStoppedEvent(route);
    }

    public EventObject createExchangeCreatedEvent(Exchange exchange) {
        return new ExchangeCreatedEvent(exchange);
    }

    public EventObject createExchangeCompletedEvent(Exchange exchange) {
        return new ExchangeCompletedEvent(exchange);
    }

    public EventObject createExchangeFailedEvent(Exchange exchange) {
        return new ExchangeFailedEvent(exchange);
    }

    public EventObject createExchangeFailureHandledEvent(Exchange exchange, Processor failureHandler, boolean deadLetterChannel) {
        return new ExchangeFailureHandledEvent(exchange, failureHandler, deadLetterChannel);
    }

    public EventObject createExchangeRedeliveryEvent(Exchange exchange, int attempt) {
        return new ExchangeRedeliveryEvent(exchange, attempt);
    }

    public EventObject createExchangeSentEvent(Exchange exchange, Endpoint endpoint, long timeTaken) {
        return new ExchangeSentEvent(exchange, endpoint, timeTaken);
    }

    public EventObject createCamelContextSuspendingEvent(CamelContext context) {
        return new CamelContextSuspendingEvent(context);
    }

    public EventObject createCamelContextSuspendedEvent(CamelContext context) {
        return new CamelContextSuspendedEvent(context);
    }

    public EventObject createCamelContextResumingEvent(CamelContext context) {
        return new CamelContextResumingEvent(context);
    }

    public EventObject createCamelContextResumedEvent(CamelContext context) {
        return new CamelContextResumedEvent(context);
    }

    public EventObject createCamelContextResumeFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextResumeFailureEvent(context, cause);
    }
}
