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
import org.apache.camel.spi.SendHook;
import org.apache.camel.util.TimeSource;

/**
 * Raises the faults of the rules at the sends to an endpoint: {@code error} (the producer is not called), {@code delay}
 * (the sending thread waits, through {@link TimeSource}, before it calls the producer) and {@code drop} (the send
 * reports success without calling the producer). The first rule that fires decides the fault of a send; the rules are
 * asked in the order they were added.
 */
final class ChaosSendHook implements SendHook {

    private final ChaosManager manager;

    ChaosSendHook(ChaosManager manager) {
        this.manager = manager;
    }

    @Override
    public long beforeSend(Exchange exchange, Endpoint endpoint) {
        ChaosRule[] rules = manager.ruleSet().send;
        if (rules.length == 0) {
            return PROCEED;
        }
        long now = TimeSource.currentTimeMillis();
        for (ChaosRule rule : rules) {
            if (now < rule.endsAt && rule.matchesEndpoint(endpoint) && rule.fires(now)) {
                return ChaosFaults.atSend(rule, exchange);
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
