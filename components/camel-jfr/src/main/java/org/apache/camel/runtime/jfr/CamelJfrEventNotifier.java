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
package org.apache.camel.runtime.jfr;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeRedeliveryEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.URISupport;

public class CamelJfrEventNotifier extends EventNotifierSupport {

    static final String PROP_EXCHANGE_EVENT = "CamelJfrExchangeEvent";
    static final String PROP_SEND_STACK = "CamelJfrSendStack";

    private static final int MAX_MESSAGE_LEN = 256;

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof ExchangeCreatedEvent
                || event instanceof ExchangeCompletedEvent
                || event instanceof ExchangeFailedEvent
                || event instanceof ExchangeSendingEvent
                || event instanceof ExchangeSentEvent
                || event instanceof ExchangeRedeliveryEvent;
    }

    @Override
    public void notify(CamelEvent event) {
        if (event instanceof ExchangeCreatedEvent e) {
            onCreated(e.getExchange());
        } else if (event instanceof ExchangeCompletedEvent e) {
            onExchangeDone(e.getExchange());
        } else if (event instanceof ExchangeFailedEvent e) {
            onFailed(e);
            onExchangeDone(e.getExchange());
        } else if (event instanceof ExchangeSendingEvent e) {
            onSending(e);
        } else if (event instanceof ExchangeSentEvent e) {
            onSent(e.getExchange());
        } else if (event instanceof ExchangeRedeliveryEvent e) {
            onRedelivery(e);
        }
    }

    private void onCreated(Exchange exchange) {
        CamelExchangeEvent jfr = new CamelExchangeEvent();
        if (jfr.isEnabled()) {
            jfr.exchangeId = exchange.getExchangeId();
            jfr.routeId = exchange.getFromRouteId();
            jfr.begin();
            exchange.setProperty(PROP_EXCHANGE_EVENT, jfr);
        }
    }

    private void onExchangeDone(Exchange exchange) {
        CamelExchangeEvent jfr = exchange.getProperty(PROP_EXCHANGE_EVENT, CamelExchangeEvent.class);
        if (jfr != null) {
            jfr.failed = exchange.isFailed();
            jfr.end();
            jfr.commit();
            exchange.removeProperty(PROP_EXCHANGE_EVENT);
        }
    }

    @SuppressWarnings("unchecked")
    private void onSending(ExchangeSendingEvent event) {
        CamelExchangeSendEvent jfr = new CamelExchangeSendEvent();
        if (jfr.isEnabled()) {
            Exchange exchange = event.getExchange();
            jfr.exchangeId = exchange.getExchangeId();
            jfr.endpointUri = URISupport.sanitizeUri(event.getEndpoint().getEndpointUri());
            jfr.begin();
            Deque<CamelExchangeSendEvent> stack = exchange.getProperty(PROP_SEND_STACK, Deque.class);
            if (stack == null) {
                stack = new ArrayDeque<>();
                exchange.setProperty(PROP_SEND_STACK, stack);
            }
            stack.push(jfr);
        }
    }

    @SuppressWarnings("unchecked")
    private void onSent(Exchange exchange) {
        Deque<CamelExchangeSendEvent> stack = exchange.getProperty(PROP_SEND_STACK, Deque.class);
        if (stack != null && !stack.isEmpty()) {
            CamelExchangeSendEvent jfr = stack.pop();
            jfr.failed = exchange.isFailed();
            jfr.end();
            jfr.commit();
        }
    }

    private void onFailed(ExchangeFailedEvent event) {
        CamelExchangeFailedEvent jfr = new CamelExchangeFailedEvent();
        if (jfr.isEnabled()) {
            Exchange exchange = event.getExchange();
            jfr.exchangeId = exchange.getExchangeId();
            jfr.routeId = exchange.getFromRouteId();
            Throwable cause = event.getCause();
            if (cause != null) {
                jfr.exceptionType = cause.getClass().getName();
                jfr.exceptionMessage = truncate(cause.getMessage());
            }
            jfr.commit();
        }
    }

    private void onRedelivery(ExchangeRedeliveryEvent event) {
        CamelRedeliveryEvent jfr = new CamelRedeliveryEvent();
        if (jfr.isEnabled()) {
            Exchange exchange = event.getExchange();
            jfr.exchangeId = exchange.getExchangeId();
            jfr.routeId = exchange.getFromRouteId();
            jfr.attempt = event.getAttempt();
            Integer max = exchange.getProperty(Exchange.REDELIVERY_MAX_COUNTER, Integer.class);
            jfr.maxAttempts = max != null ? max : 0;
            jfr.commit();
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_MESSAGE_LEN ? s : s.substring(0, MAX_MESSAGE_LEN);
    }
}
