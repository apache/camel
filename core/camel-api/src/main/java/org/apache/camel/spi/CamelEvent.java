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
        CamelContextRoutesStarting,
        CamelContextRoutesStarted,
        CamelContextRoutesStopping,
        CamelContextRoutesStopped,
        ExchangeCompleted,
        ExchangeCreated,
        ExchangeFailed,
        ExchangeFailureHandled,
        ExchangeFailureHandling,
        ExchangeRedelivery,
        ExchangeSending,
        ExchangeSent,
        StepStarted,
        StepCompleted,
        StepFailed,
        RouteAdded,
        RouteRemoved,
        RouteStarted,
        RouteStopped,
        ServiceStartupFailure,
        ServiceStopFailure,
        Custom
    }

    Type getType();

    Object getSource();

    /**
     * This interface is implemented by all events that contain an exception and is used to
     * retrieve the exception in a universal way.
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
            return Type.CamelContextRoutesStarting;
        }
    }

    interface CamelContextRoutesStartedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextRoutesStarted;
        }
    }

    interface CamelContextRoutesStoppingEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextRoutesStopping;
        }
    }

    interface CamelContextRoutesStoppedEvent extends CamelContextEvent {
        @Override
        default Type getType() {
            return Type.CamelContextRoutesStopped;
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

    interface RouteStartedEvent extends RouteEvent {
        @Override
        default Type getType() {
            return Type.RouteStarted;
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

}
