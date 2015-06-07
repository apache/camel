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
package org.apache.camel.management.event;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
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

    public EventObject createRouteAddedEvent(Route route) {
        return new RouteAddedEvent(route);
    }

    public EventObject createRouteRemovedEvent(Route route) {
        return new RouteRemovedEvent(route);
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

    public EventObject createExchangeFailureHandlingEvent(Exchange exchange, Processor failureHandler, boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor) {
            handler = ((DelegateProcessor) handler).getProcessor();
        }
        return new ExchangeFailureHandlingEvent(exchange, handler, deadLetterChannel, deadLetterUri);
    }

    public EventObject createExchangeFailureHandledEvent(Exchange exchange, Processor failureHandler,
                                                         boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor) {
            handler = ((DelegateProcessor) handler).getProcessor();
        }
        return new ExchangeFailureHandledEvent(exchange, handler, deadLetterChannel, deadLetterUri);
    }

    public EventObject createExchangeRedeliveryEvent(Exchange exchange, int attempt) {
        return new ExchangeRedeliveryEvent(exchange, attempt);
    }

    public EventObject createExchangeSendingEvent(Exchange exchange, Endpoint endpoint) {
        return new ExchangeSendingEvent(exchange, endpoint);
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
