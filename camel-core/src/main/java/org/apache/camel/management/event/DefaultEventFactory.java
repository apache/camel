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

import org.apache.camel.CamelContext;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventFactory;

/**
 * Default implementation of the {@link org.apache.camel.spi.EventFactory}.
 */
public class DefaultEventFactory implements EventFactory {

    public CamelEvent createCamelContextStartingEvent(CamelContext context) {
        return new CamelContextStartingEvent(context);
    }

    public CamelEvent createCamelContextStartedEvent(CamelContext context) {
        return new CamelContextStartedEvent(context);
    }

    public CamelEvent createCamelContextStoppingEvent(CamelContext context) {
        return new CamelContextStoppingEvent(context);
    }

    public CamelEvent createCamelContextStoppedEvent(CamelContext context) {
        return new CamelContextStoppedEvent(context);
    }

    public CamelEvent createCamelContextStartupFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextStartupFailureEvent(context, cause);
    }

    public CamelEvent createCamelContextStopFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextStopFailureEvent(context, cause);
    }

    public CamelEvent createServiceStartupFailureEvent(CamelContext context, Object service, Throwable cause) {
        return new ServiceStartupFailureEvent(context, service, cause);
    }

    public CamelEvent createServiceStopFailureEvent(CamelContext context, Object service, Throwable cause) {
        return new ServiceStopFailureEvent(context, service, cause);
    }

    public CamelEvent createRouteStartedEvent(Route route) {
        return new RouteStartedEvent(route);
    }

    public CamelEvent createRouteStoppedEvent(Route route) {
        return new RouteStoppedEvent(route);
    }

    public CamelEvent createRouteAddedEvent(Route route) {
        return new RouteAddedEvent(route);
    }

    public CamelEvent createRouteRemovedEvent(Route route) {
        return new RouteRemovedEvent(route);
    }

    public CamelEvent createExchangeCreatedEvent(Exchange exchange) {
        return new ExchangeCreatedEvent(exchange);
    }

    public CamelEvent createExchangeCompletedEvent(Exchange exchange) {
        return new ExchangeCompletedEvent(exchange);
    }

    public CamelEvent createExchangeFailedEvent(Exchange exchange) {
        return new ExchangeFailedEvent(exchange);
    }

    public CamelEvent createExchangeFailureHandlingEvent(Exchange exchange, Processor failureHandler, boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor) {
            handler = ((DelegateProcessor) handler).getProcessor();
        }
        return new ExchangeFailureHandlingEvent(exchange, handler, deadLetterChannel, deadLetterUri);
    }

    public CamelEvent createExchangeFailureHandledEvent(Exchange exchange, Processor failureHandler,
                                                         boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor) {
            handler = ((DelegateProcessor) handler).getProcessor();
        }
        return new ExchangeFailureHandledEvent(exchange, handler, deadLetterChannel, deadLetterUri);
    }

    public CamelEvent createExchangeRedeliveryEvent(Exchange exchange, int attempt) {
        return new ExchangeRedeliveryEvent(exchange, attempt);
    }

    public CamelEvent createExchangeSendingEvent(Exchange exchange, Endpoint endpoint) {
        return new ExchangeSendingEvent(exchange, endpoint);
    }

    public CamelEvent createExchangeSentEvent(Exchange exchange, Endpoint endpoint, long timeTaken) {
        return new ExchangeSentEvent(exchange, endpoint, timeTaken);
    }

    public CamelEvent createCamelContextSuspendingEvent(CamelContext context) {
        return new CamelContextSuspendingEvent(context);
    }

    public CamelEvent createCamelContextSuspendedEvent(CamelContext context) {
        return new CamelContextSuspendedEvent(context);
    }

    public CamelEvent createCamelContextResumingEvent(CamelContext context) {
        return new CamelContextResumingEvent(context);
    }

    public CamelEvent createCamelContextResumedEvent(CamelContext context) {
        return new CamelContextResumedEvent(context);
    }

    public CamelEvent createCamelContextResumeFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextResumeFailureEvent(context, cause);
    }
}
