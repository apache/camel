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
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.SendHook;

/**
 * What the faults do to an exchange: the exception of {@code error}, and what a fault at a send returns to the send
 * hook.
 */
final class ChaosFaults {

    private ChaosFaults() {
    }

    /**
     * The exception of an {@code error} rule: an instance of {@code exception=}, or else a {@link ChaosException}, with
     * {@code message=} or a default message.
     */
    static Throwable newException(ChaosRule rule) {
        String message = rule.message != null ? rule.message : "Chaos error of rule " + rule.id;
        if (rule.exceptionConstructor == null) {
            return new ChaosException(message);
        }
        try {
            return rule.exceptionTakesMessage
                    ? rule.exceptionConstructor.newInstance(message) : rule.exceptionConstructor.newInstance();
        } catch (Exception | LinkageError e) {
            return new ChaosException(
                    "Chaos rule " + rule.id + " could not create the exception " + rule.exceptionType.getName(), e);
        }
    }

    /**
     * Raises the fault of a rule that fired at a send.
     *
     * @return the value for {@link SendHook#beforeSend(Exchange, org.apache.camel.Endpoint)}
     */
    static long atSend(ChaosRule rule, Exchange exchange) {
        switch (rule.fault) {
            case ERROR -> {
                exchange.setException(newException(rule));
                rule.fired(Hook.SEND);
                return SendHook.PROCEED;
            }
            case DELAY -> {
                rule.fired(Hook.SEND);
                return rule.duration;
            }
            case DROP -> {
                rule.fired(Hook.SEND);
                return SendHook.SKIP;
            }
            default -> throw new IllegalStateException("Unknown fault " + rule.fault);
        }
    }
}
