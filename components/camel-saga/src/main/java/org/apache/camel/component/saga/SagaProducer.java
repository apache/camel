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
package org.apache.camel.component.saga;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultAsyncProducer;

/**
 * A producer that finalizes the current saga.
 */
public class SagaProducer extends DefaultAsyncProducer {

    private final boolean success;

    private CamelSagaService camelSagaService;

    public SagaProducer(SagaEndpoint endpoint, boolean success) {
        super(endpoint);
        this.success = success;

        CamelSagaService sagaService = endpoint.getCamelContext().hasService(CamelSagaService.class);
        if (sagaService == null) {
            sagaService = CamelContextHelper.mandatoryFindSingleByType(endpoint.getCamelContext(), CamelSagaService.class);
        }
        this.camelSagaService = sagaService;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String currentSaga = exchange.getIn().getHeader(SagaConstants.SAGA_LONG_RUNNING_ACTION, String.class);
        if (currentSaga == null) {
            exchange.setException(
                    new IllegalStateException("Current exchange is not bound to a saga context: cannot complete"));
            callback.done(true);
            return true;
        }

        camelSagaService.getSaga(currentSaga).thenApply(coordinator -> {
            if (coordinator == null) {
                throw new IllegalStateException("No coordinator found for saga id " + currentSaga);
            }
            return coordinator;
        }).thenCompose(coordinator -> {
            if (success) {
                return coordinator.complete(exchange);
            } else {
                return coordinator.compensate(exchange);
            }
        }).whenComplete((res, ex) -> {
            if (ex != null) {
                exchange.setException(ex);
            }
            callback.done(false);
        });
        return false;
    }

}
