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

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;

/**
 * Saga processor implementing the REQUIRED propagation mode.
 */
public class RequiredSagaProcessor extends SagaProcessor {

    public RequiredSagaProcessor(CamelContext camelContext, Processor childProcessor, CamelSagaService sagaService, SagaCompletionMode completionMode, CamelSagaStep step) {
        super(camelContext, childProcessor, sagaService, completionMode, step);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        getCurrentSagaCoordinator(exchange).whenComplete((existingCoordinator, ex) -> ifNotException(ex, exchange, callback, () -> {
            CompletableFuture<CamelSagaCoordinator> coordinatorFuture;
            final boolean inheritedCoordinator;
            if (existingCoordinator != null) {
                coordinatorFuture = CompletableFuture.completedFuture(existingCoordinator);
                inheritedCoordinator = true;
            } else {
                coordinatorFuture = sagaService.newSaga();
                inheritedCoordinator = false;
            }

            coordinatorFuture.whenComplete((coordinator, ex2) -> ifNotException(ex2, exchange, !inheritedCoordinator, coordinator, existingCoordinator, callback, () -> {
                setCurrentSagaCoordinator(exchange, coordinator);
                coordinator.beginStep(exchange, step).whenComplete((done, ex3) -> ifNotException(ex3, exchange, !inheritedCoordinator, coordinator, existingCoordinator, callback, () -> {
                    super.process(exchange, doneSync -> {
                        if (!inheritedCoordinator) {
                            // Saga starts and ends here
                            handleSagaCompletion(exchange, coordinator, null, callback);
                        } else {
                            callback.done(false);
                        }
                    });
                }));
            }));
        }));

        return false;
    }
}
