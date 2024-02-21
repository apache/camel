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
package org.apache.camel.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A in-memory implementation of a saga coordinator.
 */
public class InMemorySagaCoordinator implements CamelSagaCoordinator {

    private enum Status {
        RUNNING,
        COMPENSATING,
        COMPENSATED,
        COMPLETING,
        COMPLETED
    }

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySagaCoordinator.class);

    private final CamelContext camelContext;
    private final InMemorySagaService sagaService;
    private final String sagaId;
    private final List<CamelSagaStep> steps;
    private final Map<CamelSagaStep, Map<String, Object>> optionValues;
    private final AtomicReference<Status> currentStatus;

    public InMemorySagaCoordinator(CamelContext camelContext, InMemorySagaService sagaService, String sagaId) {
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.sagaService = ObjectHelper.notNull(sagaService, "sagaService");
        this.sagaId = ObjectHelper.notNull(sagaId, "sagaId");
        this.steps = new CopyOnWriteArrayList<>();
        this.optionValues = new ConcurrentHashMap<>();
        this.currentStatus = new AtomicReference<>(Status.RUNNING);
    }

    @Override
    public String getId() {
        return sagaId;
    }

    @Override
    public CompletableFuture<Void> beginStep(Exchange exchange, CamelSagaStep step) {
        Status status = currentStatus.get();
        if (status != Status.RUNNING) {
            CompletableFuture<Void> res = new CompletableFuture<>();
            res.completeExceptionally(new IllegalStateException("Cannot begin: status is " + status));
            return res;
        }

        this.steps.add(step);

        if (!step.getOptions().isEmpty()) {
            optionValues.putIfAbsent(step, new ConcurrentHashMap<>());
            Map<String, Object> values = optionValues.get(step);
            for (String option : step.getOptions().keySet()) {
                Expression expression = step.getOptions().get(option);
                try {
                    values.put(option, expression.evaluate(exchange, Object.class));
                } catch (Exception ex) {
                    return CompletableFuture.supplyAsync(() -> {
                        throw new RuntimeCamelException("Cannot evaluate saga option '" + option + "'", ex);
                    });
                }
            }
        }

        if (step.getTimeoutInMilliseconds().isPresent()) {
            sagaService.getExecutorService().schedule(() -> {
                boolean doAction = currentStatus.compareAndSet(Status.RUNNING, Status.COMPENSATING);
                if (doAction) {
                    doCompensate();
                }
            }, step.getTimeoutInMilliseconds().get(), TimeUnit.MILLISECONDS);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> compensate(Exchange exchange) {
        boolean doAction = currentStatus.compareAndSet(Status.RUNNING, Status.COMPENSATING);

        if (doAction) {
            doCompensate();
        } else {
            Status status = currentStatus.get();
            if (status != Status.COMPENSATING && status != Status.COMPENSATED) {
                CompletableFuture<Void> res = new CompletableFuture<>();
                res.completeExceptionally(new IllegalStateException("Cannot compensate: status is " + status));
                return res;
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> complete(Exchange exchange) {
        boolean doAction = currentStatus.compareAndSet(Status.RUNNING, Status.COMPLETING);

        if (doAction) {
            doComplete();
        } else {
            Status status = currentStatus.get();
            if (status != Status.COMPLETING && status != Status.COMPLETED) {
                CompletableFuture<Void> res = new CompletableFuture<>();
                res.completeExceptionally(new IllegalStateException("Cannot complete: status is " + status));
                return res;
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> doCompensate() {
        return doFinalize(CamelSagaStep::getCompensation, "compensation")
                .thenApply(res -> {
                    currentStatus.set(Status.COMPENSATED);
                    return res;
                });
    }

    public CompletableFuture<Boolean> doComplete() {
        return doFinalize(CamelSagaStep::getCompletion, "completion")
                .thenApply(res -> {
                    currentStatus.set(Status.COMPLETED);
                    return res;
                });
    }

    public CompletableFuture<Boolean> doFinalize(
            Function<CamelSagaStep, Optional<Endpoint>> endpointExtractor, String description) {
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(true);
        for (CamelSagaStep step : reversed(steps)) {
            Optional<Endpoint> endpoint = endpointExtractor.apply(step);
            if (endpoint.isPresent()) {
                result = result.thenCompose(
                        prevResult -> doFinalize(endpoint.get(), step, 0, description).thenApply(res -> prevResult && res));
            }
        }
        return result.whenComplete((done, ex) -> {
            if (ex != null) {
                LOG.error("Cannot finalize {} the saga", description, ex);
            } else if (!done) {
                LOG.warn("Unable to finalize {} for all required steps of the saga {}", description, sagaId);
            }
        });
    }

    private CompletableFuture<Boolean> doFinalize(Endpoint endpoint, CamelSagaStep step, int doneAttempts, String description) {
        Exchange exchange = createExchange(endpoint, step);

        return CompletableFuture.supplyAsync(() -> {
            Exchange res = camelContext.createFluentProducerTemplate().to(endpoint).withExchange(exchange).send();
            Exception ex = res.getException();
            if (ex != null) {
                throw new RuntimeCamelException(res.getException());
            }
            return true;
        }, sagaService.getExecutorService()).exceptionally(ex -> {
            LOG.warn("Exception thrown during {} at {}. Attempt {} of {}", description, endpoint.getEndpointUri(),
                    doneAttempts + 1, sagaService.getMaxRetryAttempts(), ex);
            return false;
        }).thenCompose(executed -> {
            int currentAttempt = doneAttempts + 1;
            if (executed) {
                return CompletableFuture.completedFuture(true);
            } else if (currentAttempt >= sagaService.getMaxRetryAttempts()) {
                return CompletableFuture.completedFuture(false);
            } else {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                sagaService.getExecutorService().schedule(() -> {
                    doFinalize(endpoint, step, currentAttempt, description).whenComplete((res, ex) -> {
                        if (ex != null) {
                            future.completeExceptionally(ex);
                        } else {
                            future.complete(res);
                        }
                    });
                }, sagaService.getRetryDelayInMilliseconds(), TimeUnit.MILLISECONDS);
                return future;
            }
        });
    }

    private Exchange createExchange(Endpoint endpoint, CamelSagaStep step) {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, getId());

        Map<String, Object> values = optionValues.get(step);
        if (values != null) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                exchange.getIn().setHeader(entry.getKey(), entry.getValue());
            }
        }
        return exchange;
    }

    private <T> List<T> reversed(List<T> list) {
        List<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }
}
