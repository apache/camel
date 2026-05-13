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
import org.jspecify.annotations.Nullable;

/**
 * Base interface for all Camel events used by the {@link EventNotifier} for notifications about
 * {@link org.apache.camel.Exchange}, {@link org.apache.camel.Route}, {@link org.apache.camel.CamelContext}, and
 * {@link org.apache.camel.Service} lifecycle changes.
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
        @Deprecated(since = "4.19.0")
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
        RouteRestarting,
        RouteRestartingFailure,
        ServiceStartupFailure,
        ServiceStopFailure,
        StepStarted,
        StepCompleted,
        StepFailed,
        Custom
    }

    /** Returns the type of this event. */
    Type getType();

    /** Returns the source of this event (typically the CamelContext, Exchange, Route, or Service). */
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

    /** Event related to {@link CamelContext} lifecycle changes. */
    interface CamelContextEvent extends CamelEvent {

        /** Returns the CamelContext. */
        CamelContext getContext();

        @Override
        default Object getSource() {
            return getContext();
        }

    }

    /** Fired when the CamelContext is initializing. */
    interface CamelContextInitializingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextInitializing;
        }
    }

    /** Fired when the CamelContext has been initialized. */
    interface CamelContextInitializedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextInitialized;
        }
    }

    /** Fired when the CamelContext has been resumed. */
    interface CamelContextResumedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextResumed;
        }
    }

    /** Fired when the CamelContext failed to resume. */
    interface CamelContextResumeFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextResumeFailure;
        }
    }

    /** Fired when the CamelContext is resuming. */
    interface CamelContextResumingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextResuming;
        }
    }

    /** Fired when the CamelContext has been fully started. */
    interface CamelContextStartedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStarted;
        }
    }

    /** Fired when the CamelContext is starting. */
    interface CamelContextStartingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStarting;
        }
    }

    /** Fired when the CamelContext failed to start. */
    interface CamelContextStartupFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStartupFailure;
        }
    }

    /** Fired when the CamelContext failed to stop cleanly. */
    interface CamelContextStopFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStopFailure;
        }
    }

    /** Fired when the CamelContext has been stopped. */
    interface CamelContextStoppedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStopped;
        }
    }

    /** Fired when the CamelContext is stopping. */
    interface CamelContextStoppingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextStopping;
        }
    }

    /** Fired when the CamelContext has been suspended. */
    interface CamelContextSuspendedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextSuspended;
        }
    }

    /** Fired when the CamelContext is suspending. */
    interface CamelContextSuspendingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextSuspending;
        }
    }

    /** Fired when routes are starting. */
    interface CamelContextRoutesStartingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStarting;
        }
    }

    /** Fired when routes have been started. */
    interface CamelContextRoutesStartedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStarted;
        }
    }

    /** Fired when routes are stopping. */
    interface CamelContextRoutesStoppingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStopping;
        }
    }

    /** Fired when routes have been stopped. */
    interface CamelContextRoutesStoppedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.RoutesStopped;
        }
    }

    /** Fired when the CamelContext is reloading. */
    interface CamelContextReloadingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextReloading;
        }
    }

    /** Fired when the CamelContext has been reloaded. */
    interface CamelContextReloadedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextReloaded;
        }
    }

    /** Fired when the CamelContext failed to reload. */
    interface CamelContextReloadFailureEvent extends CamelContextEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.CamelContextReloadFailure;
        }
    }

    /** Event related to {@link Exchange} processing. */
    interface ExchangeEvent extends CamelEvent {

        /** Returns the exchange. */
        Exchange getExchange();

        @Override
        default Object getSource() {
            return getExchange();
        }
    }

    /** Fired when an exchange has been completed successfully. */
    interface ExchangeCompletedEvent extends ExchangeEvent {
        @Override
        default Type getType() {
            return Type.ExchangeCompleted;
        }
    }

    /** Fired when a new exchange has been created. */
    interface ExchangeCreatedEvent extends ExchangeEvent {
        @Override
        default Type getType() {
            return Type.ExchangeCreated;
        }
    }

    /** Fired when an exchange has failed. */
    interface ExchangeFailedEvent extends ExchangeEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.ExchangeFailed;
        }
    }

    /** Event providing details about how a failure was handled during exchange processing. */
    interface ExchangeFailureEvent extends ExchangeEvent {

        /** Returns the processor that handled the failure. */
        Processor getFailureHandler();

        /** Whether the failure was handled by a dead letter channel. */
        boolean isDeadLetterChannel();

        /** Returns the dead letter channel endpoint URI, or null if not applicable. */
        @Nullable
        String getDeadLetterUri();

    }

    /** Fired after a failure has been handled by an error handler. */
    interface ExchangeFailureHandledEvent extends ExchangeFailureEvent {
        @Override
        default Type getType() {
            return Type.ExchangeFailureHandled;
        }
    }

    /** Fired when a failure is about to be handled by an error handler. */
    interface ExchangeFailureHandlingEvent extends ExchangeFailureEvent {
        @Override
        default Type getType() {
            return Type.ExchangeFailureHandling;
        }
    }

    /** Fired when an exchange is about to be redelivered. */
    interface ExchangeRedeliveryEvent extends ExchangeEvent {

        /** Returns the redelivery attempt number (starts from 1). */
        int getAttempt();

        @Override
        default Type getType() {
            return Type.ExchangeRedelivery;
        }
    }

    /** Fired when an exchange is about to be sent to an endpoint. */
    interface ExchangeSendingEvent extends ExchangeEvent {

        /** Returns the destination endpoint. */
        Endpoint getEndpoint();

        @Override
        default Type getType() {
            return Type.ExchangeSending;
        }
    }

    /** Fired when an exchange has been sent to an endpoint. */
    interface ExchangeSentEvent extends ExchangeEvent {

        /** Returns the destination endpoint. */
        Endpoint getEndpoint();

        /** Returns the time taken in milliseconds. */
        long getTimeTaken();

        @Override
        default Type getType() {
            return Type.ExchangeSent;
        }
    }

    /** Event related to a Step EIP processing. */
    interface StepEvent extends ExchangeEvent {
        /** Returns the Step EIP id. */
        String getStepId();
    }

    /** Fired when a Step EIP has started processing an exchange. */
    interface StepStartedEvent extends StepEvent {
        @Override
        default Type getType() {
            return Type.StepStarted;
        }
    }

    /** Fired when a Step EIP has completed processing an exchange. */
    interface StepCompletedEvent extends StepEvent {
        @Override
        default Type getType() {
            return Type.StepCompleted;
        }
    }

    /** Fired when a Step EIP has failed processing an exchange. */
    interface StepFailedEvent extends StepEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.StepFailed;
        }
    }

    /** Event related to {@link Route} lifecycle changes. */
    interface RouteEvent extends CamelEvent {

        /** Returns the route. */
        Route getRoute();

        @Override
        default Object getSource() {
            return getRoute();
        }
    }

    /** Fired when a route has been added. */
    interface RouteAddedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteAdded;
        }
    }

    /** Fired when a route has been removed. */
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

    /** Fired when a route is starting. */
    interface RouteStartingEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStarting;
        }
    }

    /** Fired when a route has been started. */
    interface RouteStartedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStarted;
        }
    }

    /** Fired when a route is stopping. */
    interface RouteStoppingEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStopping;
        }
    }

    /** Fired when a route has been stopped. */
    interface RouteStoppedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStopped;
        }
    }

    interface RouteRestartingEvent extends RouteEvent {

        /**
         * Restart attempt (0 = initial start, 1 = first restart attempt)
         */
        long getAttempt();

        @Override
        default Type getType() {
            return Type.RouteRestarting;
        }
    }

    interface RouteRestartingFailureEvent extends RouteEvent, FailureEvent {

        /**
         * Failure attempt (0 = initial start, 1 = first restart attempt)
         */
        long getAttempt();

        /**
         * Whether all restarts have failed and the route controller will not attempt to restart the route anymore due
         * to maximum attempts reached and being exhausted.
         */
        boolean isExhausted();

        @Override
        default Type getType() {
            return Type.RouteRestartingFailure;
        }
    }

    /** Event related to {@link org.apache.camel.Service} lifecycle changes. */
    interface ServiceEvent extends CamelEvent {

        /** Returns the service. */
        Object getService();

        @Override
        default Object getSource() {
            return getService();
        }
    }

    /** Fired when a service failed to start. */
    interface ServiceStartupFailureEvent extends ServiceEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.ServiceStartupFailure;
        }
    }

    /** Fired when a service failed to stop. */
    interface ServiceStopFailureEvent extends ServiceEvent, FailureEvent {
        @Override
        default Type getType() {
            return Type.ServiceStopFailure;
        }
    }

    /**
     * Special event only in use for camel-tracing / camel-opentelemetry. This event is NOT (by default) in use.
     */
    @Deprecated(since = "4.19.0")
    interface ExchangeAsyncProcessingStartedEvent extends ExchangeEvent {
        @Override
        default Type getType() {
            return Type.ExchangeAsyncProcessingStarted;
        }
    }
}
