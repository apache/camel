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

package org.apache.camel.impl.engine;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelInternalProcessorAdvice;

final class AdviceIterator {
    private AdviceIterator() {

    }

    static void runAfterTasks(List<? extends CamelInternalProcessorAdvice> advices, Object[] states, Exchange exchange) {
        runAfterTasks(advices, advices.size(), states, states.length, exchange);
    }

    /**
     * Runs the after of the first advices in reverse order.
     *
     * @param advices    the advices
     * @param count      number of advices (from the start) to run after for, such as those whose before was run
     * @param states     the states
     * @param stateCount number of states (from the start) that belongs to these advices
     * @param exchange   the exchange
     */
    static void runAfterTasks(
            List<? extends CamelInternalProcessorAdvice> advices, int count, Object[] states, int stateCount,
            Exchange exchange) {
        int stateIndex = stateCount - 1;

        for (int i = count - 1; i >= 0; i--) {
            CamelInternalProcessorAdvice task = advices.get(i);
            Object state = null;
            if (task.hasState()) {
                state = states[stateIndex--];
            }
            runAfterTask(task, state, exchange);
        }
    }

    static void runAfterTask(CamelInternalProcessorAdvice task, Object state, Exchange exchange) {
        try {
            task.after(exchange, state);
        } catch (Exception e) {
            // allow all advices to complete even if there was an exception,
            // and do not lose the exception the exchange already failed with
            Exception existing = exchange.getException();
            if (existing == null) {
                exchange.setException(e);
            } else if (existing != e) {
                existing.addSuppressed(e);
            }
        }
    }
}
