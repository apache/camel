/**
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
package org.apache.camel.processor.saga;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;

/**
 * Saga processor implementing the NOT_SUPPORTED propagation mode.
 */
public class NotSupportedSagaProcessor extends SagaProcessor {

    public NotSupportedSagaProcessor(CamelContext camelContext, Processor childProcessor, CamelSagaService sagaService, SagaCompletionMode completionMode, CamelSagaStep step) {
        super(camelContext, childProcessor, sagaService, completionMode, step);
        if (!step.isEmpty()) {
            throw new IllegalArgumentException("Saga configuration is not allowed when propagation is set to NOT_SUPPORTED");
        }
        if (completionMode != null && completionMode != SagaCompletionMode.defaultCompletionMode()) {
            throw new IllegalArgumentException("CompletionMode cannot be specified when propagation is NOT_SUPPORTED");
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        getCurrentSagaCoordinator(exchange).whenComplete((coordinator, ex) -> ifNotException(ex, exchange, callback, () -> {
            setCurrentSagaCoordinator(exchange, null);

            super.process(exchange, doneSync -> {
                // Restore existing coordinator
                setCurrentSagaCoordinator(exchange, coordinator);
                callback.done(false);
            });
        }));
        return false;
    }
}
