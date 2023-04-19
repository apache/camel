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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;

/**
 * Factory to create {@link CamelEvent events} that are emitted when such an event occur.
 * <p/>
 * For example when an {@link Exchange} is being created and then later when its done.
 */
public interface EventFactory {

    /**
     * Whether to include timestamp for each event, when the event occurred. This is by default false.
     */
    boolean isTimestampEnabled();

    /**
     * Whether to include timestamp for each event, when the event occurred.
     */
    void setTimestampEnabled(boolean timestampEnabled);

    /**
     * Creates an {@link CamelEvent} for Camel is initializing.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextInitializingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel has been initialized successfully.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextInitializedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel is starting.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextStartingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel has been started successfully.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextStartedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel failing to start
     *
     * @param  context camel context
     * @param  cause   the cause exception
     * @return         the created event
     */
    CamelEvent createCamelContextStartupFailureEvent(CamelContext context, Throwable cause);

    /**
     * Creates an {@link CamelEvent} for Camel failing to stop cleanly
     *
     * @param  context camel context
     * @param  cause   the cause exception
     * @return         the created event
     */
    CamelEvent createCamelContextStopFailureEvent(CamelContext context, Throwable cause);

    /**
     * Creates an {@link CamelEvent} for Camel is stopping.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextStoppingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel has been stopped successfully.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextStoppedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel routes starting.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextRoutesStartingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel routes started.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextRoutesStartedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel routes stopping.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextRoutesStoppingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel routes stopped.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextRoutesStoppedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for {@link CamelContext} being reloaded.
     *
     * @param  context camel context
     * @param  source  the source triggered reload
     * @return         the reloading event
     */
    CamelEvent createCamelContextReloading(CamelContext context, Object source);

    /**
     * Creates an {@link CamelEvent} for {@link CamelContext} has been reloaded successfully.
     *
     * @param  context camel context
     * @param  source  the source triggered reload
     * @return         the reloaded event
     */
    CamelEvent createCamelContextReloaded(CamelContext context, Object source);

    /**
     * Creates an {@link CamelEvent} for {@link CamelContext} failed reload.
     *
     * @param  context camel context
     * @param  source  the source triggered reload
     * @param  cause   the caused of the failure
     * @return         the reloaded failed event
     */
    CamelEvent createCamelContextReloadFailure(CamelContext context, Object source, Throwable cause);

    /**
     * Creates an {@link CamelEvent} for a Service failed to start cleanly
     *
     * @param  context camel context
     * @param  service the service
     * @param  cause   the cause exception
     * @return         the created event
     */
    CamelEvent createServiceStartupFailureEvent(CamelContext context, Object service, Throwable cause);

    /**
     * Creates an {@link CamelEvent} for a Service failed to stop cleanly
     *
     * @param  context camel context
     * @param  service the service
     * @param  cause   the cause exception
     * @return         the created event
     */
    CamelEvent createServiceStopFailureEvent(CamelContext context, Object service, Throwable cause);

    /**
     * Creates an {@link CamelEvent} for {@link Route} is starting.
     *
     * @param  route the route
     * @return       the created event
     */
    CamelEvent createRouteStartingEvent(Route route);

    /**
     * Creates an {@link CamelEvent} for {@link Route} has been started successfully.
     *
     * @param  route the route
     * @return       the created event
     */
    CamelEvent createRouteStartedEvent(Route route);

    /**
     * Creates an {@link CamelEvent} for {@link Route} is stopping.
     *
     * @param  route the route
     * @return       the created event
     */
    CamelEvent createRouteStoppingEvent(Route route);

    /**
     * Creates an {@link CamelEvent} for {@link Route} has been stopped successfully.
     *
     * @param  route the route
     * @return       the created event
     */
    CamelEvent createRouteStoppedEvent(Route route);

    /**
     * Creates an {@link CamelEvent} for {@link Route} has been added successfully.
     *
     * @param  route the route
     * @return       the created event
     */
    CamelEvent createRouteAddedEvent(Route route);

    /**
     * Creates an {@link CamelEvent} for {@link Route} has been removed successfully.
     *
     * @param  route the route
     * @return       the created event
     */
    CamelEvent createRouteRemovedEvent(Route route);

    /**
     * Creates an {@link CamelEvent} for {@link Route} has been reloaded successfully.
     *
     * @param  route the route
     * @param  index the route index in this batch
     * @param  total total number of routes being reloaded in this batch
     * @return       the reloaded event
     */
    CamelEvent createRouteReloaded(Route route, int index, int total);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} has been created
     *
     * @param  exchange the exchange
     * @return          the created event
     */
    CamelEvent createExchangeCreatedEvent(Exchange exchange);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} has been completed successfully
     *
     * @param  exchange the exchange
     * @return          the created event
     */
    CamelEvent createExchangeCompletedEvent(Exchange exchange);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} has failed
     *
     * @param  exchange the exchange
     * @return          the created event
     */
    CamelEvent createExchangeFailedEvent(Exchange exchange);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} has failed but is being handled by the
     * Camel error handlers such as an dead letter channel, or a doTry .. doCatch block.
     * <p/>
     * This event is triggered <b>before</b> sending the failure handler, where as
     * <tt>createExchangeFailureHandledEvent</tt> if the event <b>after</b>.
     *
     * @param  exchange          the exchange
     * @param  failureHandler    the failure handler such as moving the message to a dead letter queue
     * @param  deadLetterChannel whether it was a dead letter channel or not handling the failure
     * @param  deadLetterUri     the dead letter uri, if its a dead letter channel
     * @return                   the created event
     */
    CamelEvent createExchangeFailureHandlingEvent(
            Exchange exchange, Processor failureHandler,
            boolean deadLetterChannel, String deadLetterUri);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} has failed but was handled by the Camel
     * error handlers such as an dead letter channel, or a doTry .. doCatch block.
     * <p/>
     * This event is triggered <b>after</b> the exchange was sent to failure handler, where as
     * <tt>createExchangeFailureHandlingEvent</tt> if the event <b>before</b>.
     *
     * @param  exchange          the exchange
     * @param  failureHandler    the failure handler such as moving the message to a dead letter queue
     * @param  deadLetterChannel whether it was a dead letter channel or not handling the failure
     * @param  deadLetterUri     the dead letter uri, if its a dead letter channel
     * @return                   the created event
     */
    CamelEvent createExchangeFailureHandledEvent(
            Exchange exchange, Processor failureHandler,
            boolean deadLetterChannel, String deadLetterUri);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} is about to be redelivered
     *
     * @param  exchange the exchange
     * @param  attempt  the current redelivery attempt (starts from 1)
     * @return          the created event
     */
    CamelEvent createExchangeRedeliveryEvent(Exchange exchange, int attempt);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} is about to be sent to the endpoint (eg
     * before).
     *
     * @param  exchange the exchange
     * @param  endpoint the destination
     * @return          the created event
     */
    CamelEvent createExchangeSendingEvent(Exchange exchange, Endpoint endpoint);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} asynchronous processing has been started.
     * This is guaranteed to run on the same thread on which {@code RoutePolicySupport.onExchangeBegin} was called
     * and/or {@code ExchangeSendingEvent} was fired.
     *
     * Special event only in use for camel-tracing / camel-opentelemetry. This event is NOT (by default) in use.
     *
     * @param  exchange the exchange
     * @return          the created event
     */
    CamelEvent createCamelExchangeAsyncProcessingStartedEvent(Exchange exchange);

    /**
     * Creates an {@link CamelEvent} when an {@link org.apache.camel.Exchange} has completely been sent to the endpoint
     * (eg after).
     *
     * @param  exchange  the exchange
     * @param  endpoint  the destination
     * @param  timeTaken time in millis taken
     * @return           the created event
     */
    CamelEvent createExchangeSentEvent(Exchange exchange, Endpoint endpoint, long timeTaken);

    /**
     * Creates an {@link CamelEvent} when a step has been started
     *
     * @param  exchange the exchange
     * @param  stepId   the step id
     * @return          the created event
     */
    CamelEvent createStepStartedEvent(Exchange exchange, String stepId);

    /**
     * Creates an {@link CamelEvent} when a step has been completed successfully
     *
     * @param  exchange the exchange
     * @param  stepId   the step id
     * @return          the created event
     */
    CamelEvent createStepCompletedEvent(Exchange exchange, String stepId);

    /**
     * Creates an {@link CamelEvent} when a step has failed
     *
     * @param  exchange the exchange
     * @param  stepId   the step id
     * @return          the created event
     */
    CamelEvent createStepFailedEvent(Exchange exchange, String stepId);

    /**
     * Creates an {@link CamelEvent} for Camel is suspending.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextSuspendingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel has been suspended successfully.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextSuspendedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel is resuming.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextResumingEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel has been resumed successfully.
     *
     * @param  context camel context
     * @return         the created event
     */
    CamelEvent createCamelContextResumedEvent(CamelContext context);

    /**
     * Creates an {@link CamelEvent} for Camel failing to resume
     *
     * @param  context camel context
     * @param  cause   the cause exception
     * @return         the created event
     */
    CamelEvent createCamelContextResumeFailureEvent(CamelContext context, Throwable cause);
}
