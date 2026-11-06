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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.EventNotifierSupport;

/**
 * Counts per route, for the report, the redelivery attempts of the error handlers. A redelivery counts in the route
 * that runs the step being redelivered. It counts only while there are rules, and asks for no exchange events
 * otherwise. (The completed and failed exchanges are counted by the chaos route policy of each route.)
 * <p/>
 * It also marks an exchange while the dead letter channel delivers it, so the send hook tells the send of the dead
 * letter channel apart (rules reach it only with {@code at=dlq}), and counts a message as lost when such a rule failed
 * or dropped that send and the dead letter channel then handled the exchange (as with
 * {@code deadLetterHandleNewException}, the default).
 */
final class ChaosEventNotifier extends EventNotifierSupport {

    private final ChaosManager manager;

    ChaosEventNotifier(ChaosManager manager) {
        this.manager = manager;
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        if (event instanceof CamelEvent.ExchangeFailureHandlingEvent handling) {
            if (handling.isDeadLetterChannel()) {
                ChaosExchangeState.of(handling.getExchange()).deadLetter = true;
            }
            return;
        }
        if (event instanceof CamelEvent.ExchangeFailureHandledEvent handled) {
            if (handled.isDeadLetterChannel()) {
                deadLetterDone(handled.getExchange());
            }
            return;
        }
        if (event instanceof CamelEvent.ExchangeRedeliveryEvent redelivery && manager.isCounting()) {
            Exchange exchange = redelivery.getExchange();
            UnitOfWork uow = exchange.getUnitOfWork();
            Route route = uow != null ? uow.getRoute() : null;
            String routeId = route != null ? route.getRouteId() : exchange.getFromRouteId();
            if (routeId != null) {
                manager.counts(routeId).redeliveries.increment();
            }
        }
    }

    private static void deadLetterDone(Exchange exchange) {
        ChaosExchangeState state = ChaosExchangeState.peek(exchange);
        if (state == null) {
            return;
        }
        state.deadLetter = false;
        ChaosRule fault = state.deadLetterFault;
        state.deadLetterFault = null;
        ExchangeExtension ee = exchange.getExchangeExtension();
        if (fault != null && ee.isErrorHandlerHandledSet() && ee.isErrorHandlerHandled()) {
            // the dead letter channel handled the exchange without delivering it
            fault.lostAtDlq();
        }
    }

    @Override
    public boolean isIgnoreExchangeEvents() {
        return !manager.isCounting();
    }

    @Override
    public boolean isIgnoreCamelContextInitEvents() {
        return true;
    }

    @Override
    public boolean isIgnoreCamelContextEvents() {
        return true;
    }

    @Override
    public boolean isIgnoreRouteEvents() {
        return true;
    }

    @Override
    public boolean isIgnoreServiceEvents() {
        return true;
    }

    @Override
    public boolean isIgnoreExchangeCompletedEvent() {
        return true;
    }

    @Override
    public boolean isIgnoreExchangeFailedEvents() {
        // the failure handling events of the dead letter channel are exchange failed events
        return false;
    }

    @Override
    public boolean isIgnoreExchangeCreatedEvent() {
        return true;
    }

    @Override
    public boolean isIgnoreExchangeSendingEvents() {
        return true;
    }

    @Override
    public boolean isIgnoreExchangeSentEvents() {
        return true;
    }

    @Override
    public boolean isIgnoreStepEvents() {
        return true;
    }

    @Override
    public String toString() {
        return "ChaosEventNotifier";
    }
}
