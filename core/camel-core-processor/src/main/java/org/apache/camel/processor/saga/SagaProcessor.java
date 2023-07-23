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
package org.apache.camel.processor.saga;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.util.ObjectHelper;

/**
 * Processor for handling sagas.
 */
public abstract class SagaProcessor extends DelegateAsyncProcessor implements Traceable, IdAware, RouteIdAware {

    protected final CamelSagaService sagaService;

    protected final CamelSagaStep step;

    protected final SagaCompletionMode completionMode;

    private String id;
    private String routeId;

    public SagaProcessor(CamelContext camelContext, Processor childProcessor, CamelSagaService sagaService,
                         SagaCompletionMode completionMode, CamelSagaStep step) {
        super(ObjectHelper.notNull(childProcessor, "childProcessor"));
        this.sagaService = ObjectHelper.notNull(sagaService, "sagaService");
        this.completionMode = ObjectHelper.notNull(completionMode, "completionMode");
        this.step = ObjectHelper.notNull(step, "step");
    }

    protected CompletableFuture<CamelSagaCoordinator> getCurrentSagaCoordinator(Exchange exchange) {
        String currentSaga = exchange.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION, String.class);
        if (currentSaga != null) {
            return sagaService.getSaga(currentSaga);
        }

        return CompletableFuture.completedFuture(null);
    }

    protected void setCurrentSagaCoordinator(Exchange exchange, CamelSagaCoordinator coordinator) {
        if (coordinator != null) {
            exchange.getIn().setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, coordinator.getId());
        } else {
            exchange.getIn().removeHeader(Exchange.SAGA_LONG_RUNNING_ACTION);
            exchange.getMessage().removeHeader(Exchange.SAGA_LONG_RUNNING_ACTION);
        }
    }

    protected void handleSagaCompletion(
            Exchange exchange, CamelSagaCoordinator coordinator, CamelSagaCoordinator previousCoordinator,
            AsyncCallback callback) {
        if (this.completionMode == SagaCompletionMode.AUTO) {
            if (exchange.getException() != null) {
                coordinator.compensate().whenComplete((done, ex) -> ifNotException(ex, exchange, callback, () -> {
                    setCurrentSagaCoordinator(exchange, previousCoordinator);
                    callback.done(false);
                }));
            } else {
                coordinator.complete().whenComplete((done, ex) -> ifNotException(ex, exchange, callback, () -> {
                    setCurrentSagaCoordinator(exchange, previousCoordinator);
                    callback.done(false);
                }));
            }
        } else if (this.completionMode == SagaCompletionMode.MANUAL) {
            // Completion will be handled manually by the user
            callback.done(false);
        } else {
            throw new IllegalStateException("Unsupported completion mode: " + this.completionMode);
        }
    }

    public CamelSagaService getSagaService() {
        return sagaService;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String toString() {
        return "id";
    }

    @Override
    public String getTraceLabel() {
        return "saga";
    }

    protected void ifNotException(Throwable ex, Exchange exchange, AsyncCallback callback, Runnable code) {
        ifNotException(ex, exchange, false, null, null, callback, code);
    }

    protected void ifNotException(
            Throwable ex, Exchange exchange, boolean handleCompletion, CamelSagaCoordinator coordinator,
            CamelSagaCoordinator previousCoordinator, AsyncCallback callback, Runnable code) {
        if (ex != null) {
            exchange.setException(ex);
            if (handleCompletion) {
                handleSagaCompletion(exchange, coordinator, previousCoordinator, callback);
            } else {
                callback.done(false);
            }
        } else {
            code.run();
        }
    }

}
