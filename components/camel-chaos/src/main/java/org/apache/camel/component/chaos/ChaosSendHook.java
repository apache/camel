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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.SendHook;
import org.apache.camel.util.TimeSource;

/**
 * Raises the faults of the rules at the sends to an endpoint: {@code error} (the producer is not called), {@code delay}
 * (the sending thread waits, through {@link TimeSource}, before it calls the producer), {@code drop} (the send reports
 * success without calling the producer), {@code corrupt} (the message that is sent) and {@code duplicate} (the message
 * is sent twice). The first rule that fires decides the fault of a send; the rules are asked in the order they were
 * added. The send of the dead letter channel meets only the rules with {@code at=dlq}: the event notifier marks the
 * exchange while the channel delivers it. The rules with {@code at=reply} are asked after the producer succeeded:
 * {@code error} fails the send after its side effect happened, and {@code corrupt} changes the reply.
 */
final class ChaosSendHook implements SendHook, Ordered {

    private final ChaosManager manager;

    ChaosSendHook(ChaosManager manager) {
        this.manager = manager;
    }

    @Override
    public long beforeSend(Exchange exchange, Endpoint endpoint) {
        ChaosManager.RuleSet ruleSet = manager.ruleSet();
        if (ruleSet.lifecycle.length > 0) {
            sentToDownRoute(ruleSet.lifecycle, exchange, endpoint);
        }
        if (ruleSet.send.length == 0 && ruleSet.dlq.length == 0) {
            return PROCEED;
        }
        long answer = fire(ruleSet, exchange, endpoint);
        if (answer == SKIP) {
            // the producer is not called, so there is no reply for the rules with at=reply (afterSend comes right away,
            // with no other send of the exchange in between)
            ChaosExchangeState.of(exchange).skippedSend = true;
        }
        return answer;
    }

    private static long fire(ChaosManager.RuleSet ruleSet, Exchange exchange, Endpoint endpoint) {
        // the send of the dead letter channel meets only the rules with at=dlq, the other sends the rest
        ChaosExchangeState state = ChaosExchangeState.peek(exchange);
        boolean deadLetter = state != null && state.deadLetter;
        ChaosRule[] rules = deadLetter ? ruleSet.dlq : ruleSet.send;
        if (rules.length == 0) {
            return PROCEED;
        }
        long now = TimeSource.currentTimeMillis();
        String uri = ChaosPatterns.endpointUri(endpoint);
        for (ChaosRule rule : rules) {
            if (rule.live(now) && rule.matchesUri(uri) && rule.matchesIn(exchange) && rule.matchesWhen(exchange)
                    && rule.fires(now)) {
                if (deadLetter && (rule.fault == ChaosRule.Fault.ERROR || rule.fault == ChaosRule.Fault.DROP)) {
                    // the message does not reach the dead letter channel: lost, when the channel then handles it
                    state.deadLetterFault = rule;
                }
                return ChaosFaults.atSend(rule, exchange, endpoint, deadLetter ? Hook.DLQ : Hook.SEND);
            }
        }
        return PROCEED;
    }

    /**
     * An exchange sent to the endpoint of a route that a lifecycle rule has down is marked with the rule, and counts in
     * the effect of the rule (such as one that goes to the dead letter channel as the route is stopped). It is not a
     * fault of its own: the rule fired when it took the route down.
     */
    private static void sentToDownRoute(ChaosRule[] rules, Exchange exchange, Endpoint endpoint) {
        String uri = null;
        for (ChaosRule rule : rules) {
            String down = rule.lifecycle.downEndpointUri();
            if (down != null) {
                if (uri == null) {
                    // with or without the options of the endpoint (direct:shipping?block=false)
                    uri = endpoint.getEndpointBaseUri();
                }
                if (down.equals(uri)) {
                    ChaosExchangeState.faulted(rule, exchange, rule.lifecycle.downRouteId());
                }
            }
        }
    }

    @Override
    public void afterSend(Exchange exchange, Endpoint endpoint) {
        ChaosExchangeState state = ChaosExchangeState.peek(exchange);
        if (state != null && state.skippedSend) {
            // chaos skipped the send (drop): the producer did not run
            state.skippedSend = false;
            return;
        }
        ChaosRule[] rules = manager.ruleSet().reply;
        if (rules.length == 0 || exchange.getException() != null || state != null && state.deadLetter) {
            // the reply rules are asked only after the producer succeeded, and not at the send of the dead letter
            // channel
            return;
        }
        long now = TimeSource.currentTimeMillis();
        String uri = ChaosPatterns.endpointUri(endpoint);
        for (ChaosRule rule : rules) {
            if (rule.live(now) && rule.matchesUri(uri) && rule.matchesIn(exchange) && rule.matchesWhen(exchange)
                    && rule.fires(now)) {
                ChaosFaults.atReply(rule, exchange, endpoint);
                return;
            }
        }
    }

    /**
     * The lowest order: the hook runs innermost, after the other hooks, right next to the producer.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST;
    }

    @Override
    public String toString() {
        return "ChaosSendHook";
    }
}
