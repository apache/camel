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
 * This interface is implemented by all events.
 */
public interface CamelEvent {

    enum Type {
        CamelContextInitializing,
        CamelContextInitialized,
        CamelContextResumed,
        CamelContextResumeFailure,
        CamelContextResuming,
        CamelContextStarted,
        CamelContextStarting,
        CamelContextStartupFailure,
        CamelContextStopFailure,
        CamelContextStopped,
        CamelContextStopping,
        CamelContextSuspended,
        CamelContextSuspending,
        CamelContextReloading,
        CamelContextReloaded,
        CamelContextReloadFailure,
        ExchangeCompleted,
        ExchangeCreated,
        ExchangeFailed,
        ExchangeFailureHandled,
        ExchangeFailureHandling,
        ExchangeRedelivery,
        ExchangeSending,
        ExchangeSent,
        ExchangeAsyncProcessingStarted,
        RoutesStarting,
        RoutesStarted,
        RoutesStopping,
        RoutesStopped,
        RouteAdded,
        RouteRemoved,
        RouteReloaded,
        RouteStarting,
        RouteStarted,
        RouteStopping,
        RouteStopped,
        ServiceStartupFailure,
        ServiceStopFailure,
        StepStarted,
        StepCompleted,
        StepFailed,
        Custom
    }

    Type getType();

    Object getSource();

    /**
     * Timestamp for each event, when the event occurred. By default, the timestamp is not included and this method
     * returns 0.
     */
    long getTimestamp();

    void setTimestamp(long timestamp);

    /**
     * This interface is implemented by all events that contain an exception and is used to retrieve the exception in a
     * universal way.
     */
    interface FailureEvent extends CamelEvent {

        Throwable getCause();

    }

    interface CamelContextEvent extends CamelEvent {

        CamelContext getContext();

        @Override
        default Object getSource() {
            return getContext();
        }

    }

    interface CamelContextInitializingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextInitializing;
        }
    }

    interface CamelContextInitializedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextInitialized;
        }
    }

    interface CamelContextResumedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextResumed;
        }
    }

    interface CamelContextResumeFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextResumeFailure;
        }
    }

    interface CamelContextResumingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextResuming;
        }
    }

    interface CamelContextStartedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStarted;
        }
    }

    interface CamelContextStartingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStarting;
        }
    }

    interface CamelContextStartupFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStartupFailure;
        }
    }

    interface CamelContextStopFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStopFailure;
        }
    }

    interface CamelContextStoppedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStopped;
        }
    }

    interface CamelContextStoppingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStopping;
        }
    }

    interface CamelContextSuspendedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextSuspended;
        }
    }

    interface CamelContextSuspendingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextSuspending;
        }
    }

    interface CamelContextRoutesStartingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStarting;
        }
    }

    interface CamelContextRoutesStartedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStarted;
        }
    }

    interface CamelContextRoutesStoppingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStopping;
        }
    }

    interface CamelContextRoutesStoppedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStopped;
        }
    }

    interface CamelContextReloadingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextReloading;
        }
    }

    interface CamelContextReloadedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextReloaded;
        }
    }

    interface CamelContextReloadFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextReloadFailure;
        }
    }

    interface ExchangeEvent extends CamelEvent {

        Exchange getExchange();

        @Override
        default Object getSource() {
            return getExchange();
        }
    }

    interface ExchangeCompletedEvent extends ExchangeEvent {
        @Override
        default Type getType() {
            return Type.ExchangeCompleted;
        }
    }

    interface ExchangeCreatedEvent extends ExchangeEvent {
        @Override
        default Type getType() {
            return Type.ExchangeCreated;
        }
    }

    interface ExchangeFailedEvent extends ExchangeEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.ExchangeFailed;
        }
    }

    interface ExchangeFailureEvent extends ExchangeEvent {

        Processor getFailureHandler();

        boolean isDeadLetterChannel();

        String getDeadLetterUri();

    }

    interface ExchangeFailureHandledEvent extends ExchangeFailureEvent {
        @Override
        default Type getType() {
            return Type.ExchangeFailureHandled;
        }
    }

    interface ExchangeFailureHandlingEvent extends ExchangeFailureEvent {
        @Override
        default Type getType() {
            return Type.ExchangeFailureHandling;
        }
    }

    interface ExchangeRedeliveryEvent extends ExchangeEvent {

        int getAttempt();

        @Override
        default Type getType() {
            return Type.ExchangeRedelivery;
        }
    }

    interface ExchangeSendingEvent extends ExchangeEvent {

        Endpoint getEndpoint();

        @Override
        default Type getType() {
            return Type.ExchangeSending;
        }
    }

    interface ExchangeSentEvent extends ExchangeEvent {

        Endpoint getEndpoint();

        long getTimeTaken();

        @Override
        default Type getType() {
            return Type.ExchangeSent;
        }
    }

    interface StepEvent extends ExchangeEvent {
        String getStepId();
    }

    interface StepStartedEvent extends StepEvent {
        @Override
        default Type getType() {
            return Type.StepStarted;
        }
    }

    interface StepCompletedEvent extends StepEvent {
        @Override
        default Type getType() {
            return Type.StepCompleted;
        }
    }

    interface StepFailedEvent extends StepEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.StepFailed;
        }
    }

    interface RouteEvent extends CamelEvent {

        Route getRoute();

        @Override
        default Object getSource() {
            return getRoute();
        }
    }

    interface RouteAddedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteAdded;
        }
    }

    interface RouteRemovedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteRemoved;
        }
    }

    interface RouteReloadedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteReloaded;
        }

        /**
         * The route index in this batch (starts from 1)
         */
        int getIndex();

        /**
         * Total number of routes being reloaded in this batch
         */
        int getTotal();
    }

    interface RouteStartingEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStarting;
        }
    }

    interface RouteStartedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStarted;
        }
    }

    interface RouteStoppingEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStopping;
        }
    }

    interface RouteStoppedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStopped;
        }
    }

    interface ServiceEvent extends CamelEvent {

        Object getService();

        @Override
        default Object getSource() {
            return getService();
        }
    }

    interface ServiceStartupFailureEvent extends ServiceEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.ServiceStartupFailure;
        }
    }

    interface ServiceStopFailureEvent extends ServiceEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.ServiceStopFailure;
        }
    }

    /**
     * Special event only in use for camel-tracing / camel-opentelemetry. This event is NOT (by default) in use.
     */
    interface ExchangeAsyncProcessingStartedEvent extends ExchangeEvent {
        @Override
        default Type getType() {
            return Type.ExchangeAsyncProcessingStarted;
        }
    }
}
