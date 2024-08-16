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

    private boolean timestampEnabled;

    @Override
    public boolean isTimestampEnabled() {
        return timestampEnabled;
    }

    @Override
    public void setTimestampEnabled(boolean timestampEnabled) {
        this.timestampEnabled = timestampEnabled;
    }

    @Override
    public CamelEvent createCamelContextInitializingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextInitializingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextInitializedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextInitializedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextStartingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextStartingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextStartedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextStartedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextStoppingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextStoppingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextStoppedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextStoppedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextRoutesStartingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextRoutesStartingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextRoutesStartedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextRoutesStartedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextRoutesStoppingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextRoutesStoppingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextRoutesStoppedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextRoutesStoppedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextStartupFailureEvent(CamelContext context, Throwable cause) {
        CamelEvent answer = new CamelContextStartupFailureEvent(context, cause);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextStopFailureEvent(CamelContext context, Throwable cause) {
        CamelEvent answer = new CamelContextStopFailureEvent(context, cause);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextReloading(CamelContext context, Object source) {
        CamelEvent answer = new CamelContextReloadingEvent(context, source);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextReloadFailure(CamelContext context, Object source, Throwable cause) {
        CamelEvent answer = new CamelContextReloadFailureEvent(context, source, cause);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextReloaded(CamelContext context, Object source) {
        CamelEvent answer = new CamelContextReloadedEvent(context, source);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createServiceStartupFailureEvent(CamelContext context, Object service, Throwable cause) {
        CamelEvent answer = new ServiceStartupFailureEvent(context, service, cause);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createServiceStopFailureEvent(CamelContext context, Object service, Throwable cause) {
        CamelEvent answer = new ServiceStopFailureEvent(context, service, cause);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createRouteStartingEvent(Route route) {
        CamelEvent answer = new RouteStartingEvent(route);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createRouteStartedEvent(Route route) {
        CamelEvent answer = new RouteStartedEvent(route);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createRouteStoppingEvent(Route route) {
        CamelEvent answer = new RouteStoppingEvent(route);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createRouteStoppedEvent(Route route) {
        CamelEvent answer = new RouteStoppedEvent(route);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;

    }

    @Override
    public CamelEvent createRouteAddedEvent(Route route) {
        CamelEvent answer = new RouteAddedEvent(route);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;

    }

    @Override
    public CamelEvent createRouteRemovedEvent(Route route) {
        CamelEvent answer = new RouteRemovedEvent(route);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;

    }

    @Override
    public CamelEvent createRouteReloaded(Route route, int index, int total) {
        CamelEvent answer = new RouteReloadedEvent(route, index, total);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeCreatedEvent(Exchange exchange) {
        CamelEvent answer = new ExchangeCreatedEvent(exchange);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeCompletedEvent(Exchange exchange) {
        CamelEvent answer = new ExchangeCompletedEvent(exchange);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeFailedEvent(Exchange exchange) {
        CamelEvent answer = new ExchangeFailedEvent(exchange);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeFailureHandlingEvent(
            Exchange exchange, Processor failureHandler, boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor delegateProcessor) {
            handler = delegateProcessor.getProcessor();
        }
        CamelEvent answer = new ExchangeFailureHandlingEvent(exchange, handler, deadLetterChannel, deadLetterUri);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeFailureHandledEvent(
            Exchange exchange, Processor failureHandler,
            boolean deadLetterChannel, String deadLetterUri) {
        // unwrap delegate processor
        Processor handler = failureHandler;
        if (handler instanceof DelegateProcessor delegateProcessor) {
            handler = delegateProcessor.getProcessor();
        }
        CamelEvent answer = new ExchangeFailureHandledEvent(exchange, handler, deadLetterChannel, deadLetterUri);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeRedeliveryEvent(Exchange exchange, int attempt) {
        CamelEvent answer = new ExchangeRedeliveryEvent(exchange, attempt);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeSendingEvent(Exchange exchange, Endpoint endpoint) {
        CamelEvent answer = new ExchangeSendingEvent(exchange, endpoint);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createExchangeSentEvent(Exchange exchange, Endpoint endpoint, long timeTaken) {
        CamelEvent answer = new ExchangeSentEvent(exchange, endpoint, timeTaken);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createStepStartedEvent(Exchange exchange, String stepId) {
        CamelEvent answer = new StepStartedEvent(exchange, stepId);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createStepCompletedEvent(Exchange exchange, String stepId) {
        CamelEvent answer = new StepCompletedEvent(exchange, stepId);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createStepFailedEvent(Exchange exchange, String stepId) {
        CamelEvent answer = new StepFailedEvent(exchange, stepId);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextSuspendingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextSuspendingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextSuspendedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextSuspendedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextResumingEvent(CamelContext context) {
        CamelEvent answer = new CamelContextResumingEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextResumedEvent(CamelContext context) {
        CamelEvent answer = new CamelContextResumedEvent(context);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelContextResumeFailureEvent(CamelContext context, Throwable cause) {
        CamelEvent answer = new CamelContextResumeFailureEvent(context, cause);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }

    @Override
    public CamelEvent createCamelExchangeAsyncProcessingStartedEvent(Exchange exchange) {
        CamelEvent answer = new ExchangeAsyncProcessingStartedEvent(exchange);
        if (timestampEnabled) {
            answer.setTimestamp(System.currentTimeMillis());
        }
        return answer;
    }
}
