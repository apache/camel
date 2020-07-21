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
package org.apache.camel.impl.event;

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

    @Override
    public CamelEvent createCamelContextInitializingEvent(CamelContext context) {
        return new CamelContextInitializingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextInitializedEvent(CamelContext context) {
        return new CamelContextInitializedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextStartingEvent(CamelContext context) {
        return new CamelContextStartingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextStartedEvent(CamelContext context) {
        return new CamelContextStartedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextStoppingEvent(CamelContext context) {
        return new CamelContextStoppingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextStoppedEvent(CamelContext context) {
        return new CamelContextStoppedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextRoutesStartingEvent(CamelContext context) {
        return new CamelContextRoutesStartingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextRoutesStartedEvent(CamelContext context) {
        return new CamelContextRoutesStartedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextRoutesStoppingEvent(CamelContext context) {
        return new CamelContextRoutesStoppingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextRoutesStoppedEvent(CamelContext context) {
        return new CamelContextRoutesStoppedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextStartupFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextStartupFailureEvent(context, cause);
    }

    @Override
    public CamelEvent createCamelContextStopFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextStopFailureEvent(context, cause);
    }

    @Override
    public CamelEvent createServiceStartupFailureEvent(CamelContext context, Object service, Throwable cause) {
        return new ServiceStartupFailureEvent(context, service, cause);
    }

    @Override
    public CamelEvent createServiceStopFailureEvent(CamelContext context, Object service, Throwable cause) {
        return new ServiceStopFailureEvent(context, service, cause);
    }

    @Override
    public CamelEvent createRouteStartedEvent(Route route) {
        return new RouteStartedEvent(route);
    }

    @Override
    public CamelEvent createRouteStoppedEvent(Route route) {
        return new RouteStoppedEvent(route);
    }

    @Override
    public CamelEvent createRouteAddedEvent(Route route) {
        return new RouteAddedEvent(route);
    }

    @Override
    public CamelEvent createRouteRemovedEvent(Route route) {
        return new RouteRemovedEvent(route);
    }

    @Override
    public CamelEvent createExchangeCreatedEvent(Exchange exchange) {
        return new ExchangeCreatedEvent(exchange);
    }

    @Override
    public CamelEvent createExchangeCompletedEvent(Exchange exchange) {
        return new ExchangeCompletedEvent(exchange);
    }

    @Override
    public CamelEvent createExchangeFailedEvent(Exchange exchange) {
        return new ExchangeFailedEvent(exchange);
    }

    @Override
    public CamelEvent createExchangeFailureHandlingEvent(Exchange exchange, Processor failureHandler, boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor) {
            handler = ((DelegateProcessor) handler).getProcessor();
        }
        return new ExchangeFailureHandlingEvent(exchange, handler, deadLetterChannel, deadLetterUri);
    }

    @Override
    public CamelEvent createExchangeFailureHandledEvent(Exchange exchange, Processor failureHandler,
                                                        boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor) {
            handler = ((DelegateProcessor) handler).getProcessor();
        }
        return new ExchangeFailureHandledEvent(exchange, handler, deadLetterChannel, deadLetterUri);
    }

    @Override
    public CamelEvent createExchangeRedeliveryEvent(Exchange exchange, int attempt) {
        return new ExchangeRedeliveryEvent(exchange, attempt);
    }

    @Override
    public CamelEvent createExchangeSendingEvent(Exchange exchange, Endpoint endpoint) {
        return new ExchangeSendingEvent(exchange, endpoint);
    }

    @Override
    public CamelEvent createExchangeSentEvent(Exchange exchange, Endpoint endpoint, long timeTaken) {
        return new ExchangeSentEvent(exchange, endpoint, timeTaken);
    }

    @Override
    public CamelEvent createStepStartedEvent(Exchange exchange, String stepId) {
        return new StepStartedEvent(exchange, stepId);
    }

    @Override
    public CamelEvent createStepCompletedEvent(Exchange exchange, String stepId) {
        return new StepCompletedEvent(exchange, stepId);
    }

    @Override
    public CamelEvent createStepFailedEvent(Exchange exchange, String stepId) {
        return new StepFailedEvent(exchange, stepId);
    }

    @Override
    public CamelEvent createCamelContextSuspendingEvent(CamelContext context) {
        return new CamelContextSuspendingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextSuspendedEvent(CamelContext context) {
        return new CamelContextSuspendedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextResumingEvent(CamelContext context) {
        return new CamelContextResumingEvent(context);
    }

    @Override
    public CamelEvent createCamelContextResumedEvent(CamelContext context) {
        return new CamelContextResumedEvent(context);
    }

    @Override
    public CamelEvent createCamelContextResumeFailureEvent(CamelContext context, Throwable cause) {
        return new CamelContextResumeFailureEvent(context, cause);
    }
}
