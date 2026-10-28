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
import org.apache.camel.component.chaos.ChaosRule.Fault;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.StepHook;
import org.apache.camel.util.TimeSource;

/**
 * Raises the faults of the {@code step=} rules at the steps of the routes: {@code error} (the processor of the step is
 * not called, and the step fails as when its processor fails, see {@link StepHook} for who handles that) and
 * {@code delay} (the thread that runs the step waits, through {@link TimeSource}, before it calls the processor). The
 * first rule that fires decides the fault of a step.
 */
final class ChaosStepHook implements StepHook {

    private final ChaosManager manager;

    ChaosStepHook(ChaosManager manager) {
        this.manager = manager;
    }

    @Override
    public long beforeStep(Exchange exchange, String nodeId) {
        ChaosRule[] rules = manager.ruleSet().step;
        if (rules.length == 0 || nodeId == null) {
            return PROCEED;
        }
        long now = TimeSource.currentTimeMillis();
        for (ChaosRule rule : rules) {
            if (now < rule.endsAt && rule.matchesId(nodeId) && rule.fires(now)) {
                rule.fired(Hook.STEP);
                if (rule.fault == Fault.DELAY) {
                    return rule.duration;
                }
                exchange.setException(ChaosFaults.newException(rule));
                return PROCEED;
            }
        }
        return PROCEED;
    }

    @Override
    public String toString() {
        return "ChaosStepHook";
    }
}
