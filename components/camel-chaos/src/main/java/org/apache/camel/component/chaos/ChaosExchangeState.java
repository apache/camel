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
package org.apache.camel.component.chaos;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.SafeCopyProperty;

/**
 * What chaos keeps of one exchange: how each route it went through is done with it, so a route counts the exchange once
 * however often it enters the route (a {@code direct} route called in a loop, or again by a redelivery), and whether
 * the dead letter channel is delivering it, so the send of the dead letter channel is told apart from the other sends.
 * <p/>
 * It is a {@link SafeCopyProperty} of the exchange: internal to Camel, not one of the properties of the exchange. A
 * copy of the exchange (a wire tap, a split, a seda queue) is another exchange, and starts with a state of its own. An
 * exchange is processed by one thread at a time, so the state needs no lock.
 */
final class ChaosExchangeState implements SafeCopyProperty {

    static final String KEY = "CamelChaosExchangeState";

    /** How a route was done with the exchange the last time, for its counts. */
    enum Outcome {
        // entered the route (the entry faults were asked), not done with yet
        ENTERED,
        COMPLETED,
        // completed after the error handler handled a failure
        HANDLED,
        FAILED,
        // failed, and the consumer of the route reads the message again
        FAILED_KEPT;

        boolean completed() {
            return this == COMPLETED || this == HANDLED;
        }
    }

    /** A route the exchange went through. */
    static final class RouteState {
        Outcome outcome = Outcome.ENTERED;
        // the counts of the route the outcome is counted in: other counts (after a reset of the report) have not
        // counted the exchange
        ChaosManager.RouteCounts counts;
        // the expectations of the route counted the exchange as completed (once, also when it completes again)
        boolean expected;
    }

    private final Map<String, RouteState> routes = new HashMap<>(4);
    // the dead letter channel is delivering the exchange
    boolean deadLetter;
    // the rule that failed or dropped that delivery, which loses the message when the dead letter channel then handles
    // it
    ChaosRule deadLetterFault;

    /** The state of the exchange, created when there is none. */
    static ChaosExchangeState of(Exchange exchange) {
        ExchangeExtension ee = exchange.getExchangeExtension();
        ChaosExchangeState state = ee.getSafeCopyProperty(KEY, ChaosExchangeState.class);
        if (state == null) {
            state = new ChaosExchangeState();
            ee.setSafeCopyProperty(KEY, state);
        }
        return state;
    }

    /** The state of the exchange, or null when chaos has not seen it. */
    static ChaosExchangeState peek(Exchange exchange) {
        return exchange.getExchangeExtension().getSafeCopyProperty(KEY, ChaosExchangeState.class);
    }

    /**
     * The exchange enters the route.
     *
     * @return whether it is the first time, so the entry faults are asked once per exchange
     */
    boolean enter(String routeId) {
        return routes.putIfAbsent(routeId, new RouteState()) == null;
    }

    /** The state of the exchange in the route, created when the exchange enters it. */
    RouteState route(String routeId) {
        return routes.computeIfAbsent(routeId, k -> new RouteState());
    }

    @Override
    public SafeCopyProperty safeCopy() {
        // a copy is another exchange: it is counted, and goes through the dead letter channel, on its own
        return new ChaosExchangeState();
    }
}
