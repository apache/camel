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
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.SendHook;
import org.apache.camel.util.TimeSource;

/**
 * Raises the faults of the rules at the sends to an endpoint: {@code error} (the producer is not called), {@code delay}
 * (the sending thread waits, through {@link TimeSource}, before it calls the producer), {@code drop} (the send reports
 * success without calling the producer) and {@code corrupt} (the message that is sent). The first rule that fires
 * decides the fault of a send; the rules are asked in the order they were added. The send of the dead letter channel
 * meets only the rules with {@code at=dlq}: the event notifier marks the exchange while the channel delivers it.
 */
final class ChaosSendHook implements SendHook {

    private final ChaosManager manager;

    ChaosSendHook(ChaosManager manager) {
        this.manager = manager;
    }

    @Override
    public long beforeSend(Exchange exchange, Endpoint endpoint) {
        ChaosManager.RuleSet ruleSet = manager.ruleSet();
        if (ruleSet.send.length == 0 && ruleSet.dlq.length == 0) {
            return PROCEED;
        }
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
            if (rule.live(now) && rule.matchesUri(uri) && rule.fires(now)) {
                if (deadLetter && (rule.fault == ChaosRule.Fault.ERROR || rule.fault == ChaosRule.Fault.DROP)) {
                    // the message does not reach the dead letter channel: lost, when the channel then handles it
                    state.deadLetterFault = rule;
                }
                return ChaosFaults.atSend(rule, exchange, endpoint, deadLetter ? Hook.DLQ : Hook.SEND);
            }
        }
        return PROCEED;
    }

    @Override
    public void afterSend(Exchange exchange, Endpoint endpoint) {
        // the faults are raised before the send
    }

    @Override
    public String toString() {
        return "ChaosSendHook";
    }
}
