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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An in-memory implementation of a saga coordinator.
 */
public class InMemorySagaCoordinator implements CamelSagaCoordinator {

    private enum Status {
        RUNNING,
        COMPENSATING,
        COMPENSATED,
        COMPLETING,
        COMPLETED,
        // the compensation or completion could not be done for every step, after all the retries
        FAILED
    }

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySagaCoordinator.class);
    // set on the exchanges that compensate or complete a step, to the id of their saga
    private static final String FINALIZING_SAGA = "CamelInMemorySagaFinalizing";

    private final CamelContext camelContext;
    private final InMemorySagaService sagaService;
    private final String sagaId;
    private final List<StepEnlistment> enlistments;
    private final List<ScheduledFuture<?>> timeoutFutures;
    private final AtomicReference<Status> currentStatus;
    // makes the status check and the enlistment of a step atomic with the change of status that ends the saga
    private final Lock lock = new ReentrantLock();
    // the compensation or completion that ended the saga, set with that change of status: a caller that finds the saga
    // already being compensated or completed gets its outcome, instead of a success before it is known
    private volatile Finalization finalization;

    public InMemorySagaCoordinator(CamelContext camelContext, InMemorySagaService sagaService, String sagaId) {
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.sagaService = ObjectHelper.notNull(sagaService, "sagaService");
        this.sagaId = ObjectHelper.notNull(sagaId, "sagaId");
        this.enlistments = new CopyOnWriteArrayList<>();
        this.timeoutFutures = new CopyOnWriteArrayList<>();
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

        Map<String, Object> values = new HashMap<>();
        if (!step.getOptions().isEmpty()) {
            for (Map.Entry<String, Expression> entry : step.getOptions().entrySet()) {
                Expression expression = entry.getValue();
                if (expression != null) {
                    try {
                        Object value = expression.evaluate(exchange, Object.class);
                        if (value != null) {
                            values.put(entry.getKey(), value);
                        }
                    } catch (Exception ex) {
                        return CompletableFuture.failedFuture(
                                new RuntimeCamelException(
                                        "Cannot evaluate saga option '" + entry.getKey() + "'", ex));
                    }
                }
            }
        }

        lock.lock();
        try {
            // check again, as the saga may have been completed, compensated or timed out while the options were
            // evaluated, and then the step would not be finalized
            status = currentStatus.get();
            if (status != Status.RUNNING) {
                CompletableFuture<Void> res = new CompletableFuture<>();
                res.completeExceptionally(new IllegalStateException("Cannot begin: status is " + status));
                return res;
            }
            this.enlistments.add(new StepEnlistment(step, values));

            if (step.getTimeoutInMilliseconds().isPresent()) {
                ScheduledFuture<?> timeoutFuture = sagaService.getExecutorService().schedule(() -> {
                    List<StepEnlistment> steps = end(Status.COMPENSATING);
                    if (steps != null) {
                        finish(doCompensate(exchange, steps));
                    }
                }, step.getTimeoutInMilliseconds().get(), TimeUnit.MILLISECONDS);
                timeoutFutures.add(timeoutFuture);
            }
        } finally {
            lock.unlock();
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> compensate(Exchange exchange) {
        List<StepEnlistment> steps = end(Status.COMPENSATING);

        if (steps != null) {
            cancelTimeouts();
            return finish(doCompensate(exchange, steps)).thenApply(res -> {
                if (!res) {
                    throw new RuntimeCamelException(
                            "Unable to compensate all required steps of the saga " + sagaId);
                }
                return null;
            });
        }

        return afterFinalization(exchange, Status.COMPENSATING, Status.COMPENSATED, "compensate");
    }

    @Override
    public CompletableFuture<Void> complete(Exchange exchange) {
        List<StepEnlistment> steps = end(Status.COMPLETING);

        if (steps != null) {
            cancelTimeouts();
            return finish(doComplete(exchange, steps)).thenApply(res -> {
                if (!res) {
                    throw new RuntimeCamelException(
                            "Unable to complete all required steps of the saga " + sagaId);
                }
                return null;
            });
        }

        return afterFinalization(exchange, Status.COMPLETING, Status.COMPLETED, "complete");
    }

    /**
     * The answer to a {@code compensate()} or {@code complete()} that found the saga no longer running: once the
     * compensation or completion that ended the saga is done, success if it is of the kind asked for and succeeded,
     * otherwise an {@link IllegalStateException} with the status.
     * <p>
     * A call from the compensation or completion of this saga itself (its route calls {@code saga:compensate}, say) is
     * answered at once, as before: it would otherwise wait for the very finalization it is part of.
     */
    private CompletableFuture<Void> afterFinalization(
            Exchange exchange, Status running, Status succeeded, String action) {
        Finalization current = finalization;
        boolean finalizing = exchange != null && sagaId.equals(exchange.getProperty(FINALIZING_SAGA, String.class));
        if (current == null || current.kind() != running || current.outcome().isDone() || finalizing) {
            return statusAnswer(running, succeeded, action);
        }
        // the status is set to the final one before the outcome is completed (see doCompensate and doComplete)
        return current.outcome().handle((res, ex) -> null)
                .thenCompose(ignored -> statusAnswer(running, succeeded, action));
    }

    private CompletableFuture<Void> statusAnswer(Status running, Status succeeded, String action) {
        Status status = currentStatus.get();
        if (status != running && status != succeeded) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot " + action + ": status is " + status));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Completes the outcome of the finalization that ended the saga with the result of the compensation or completion.
     */
    private CompletableFuture<Boolean> finish(CompletableFuture<Boolean> result) {
        CompletableFuture<Boolean> outcome = finalization.outcome();
        result.whenComplete((res, ex) -> {
            if (ex != null) {
                outcome.completeExceptionally(ex);
            } else {
                outcome.complete(res);
            }
        });
        return result;
    }

    /**
     * Changes the status from RUNNING to the given status, and returns the steps enlisted at that time, or
     * <code>null</code> if the saga is not running. This holds the lock that beginStep holds while it checks the status
     * and enlists a step, so every step that began is in the returned list, and is finalized.
     */
    private List<StepEnlistment> end(Status status) {
        lock.lock();
        try {
            if (currentStatus.compareAndSet(Status.RUNNING, status)) {
                finalization = new Finalization(status, new CompletableFuture<>());
                return new ArrayList<>(enlistments);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<Boolean> doCompensate(final Exchange exchange) {
        return doCompensate(exchange, enlistments);
    }

    private CompletableFuture<Boolean> doCompensate(final Exchange exchange, List<StepEnlistment> steps) {
        return doFinalize(exchange, steps, CamelSagaStep::getCompensation, "compensation")
                .whenComplete((res, ex) -> {
                    boolean failed = ex != null || !Boolean.TRUE.equals(res);
                    if (failed) {
                        LOG.warn("Saga {} compensation did not fully succeed — manual intervention may be needed", sagaId);
                    }
                    currentStatus.set(failed ? Status.FAILED : Status.COMPENSATED);
                    sagaService.removeSaga(sagaId);
                });
    }

    public CompletableFuture<Boolean> doComplete(final Exchange exchange) {
        return doComplete(exchange, enlistments);
    }

    private CompletableFuture<Boolean> doComplete(final Exchange exchange, List<StepEnlistment> steps) {
        return doFinalize(exchange, steps, CamelSagaStep::getCompletion, "completion")
                .whenComplete((res, ex) -> {
                    boolean failed = ex != null || !Boolean.TRUE.equals(res);
                    if (failed) {
                        LOG.warn("Saga {} completion did not fully succeed — manual intervention may be needed", sagaId);
                    }
                    currentStatus.set(failed ? Status.FAILED : Status.COMPLETED);
                    sagaService.removeSaga(sagaId);
                });
    }

    public CompletableFuture<Boolean> doFinalize(
            final Exchange exchange,
            Function<CamelSagaStep, Optional<Endpoint>> endpointExtractor, String description) {
        return doFinalize(exchange, enlistments, endpointExtractor, description);
    }

    private CompletableFuture<Boolean> doFinalize(
            final Exchange exchange, List<StepEnlistment> steps,
            Function<CamelSagaStep, Optional<Endpoint>> endpointExtractor, String description) {
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(true);
        for (StepEnlistment enlistment : reversed(steps)) {
            Optional<Endpoint> endpoint = endpointExtractor.apply(enlistment.step);
            if (endpoint.isPresent()) {
                result = result.thenCompose(
                        prevResult -> doFinalize(exchange, endpoint.get(), enlistment, 0, description)
                                .thenApply(res -> prevResult && res));
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

    private CompletableFuture<Boolean> doFinalize(
            Exchange exchange, Endpoint endpoint, StepEnlistment enlistment, int doneAttempts, String description) {
        Exchange target = createExchange(exchange, endpoint, enlistment);

        return CompletableFuture.supplyAsync(() -> {
            Exchange res = sagaService.getProducerTemplate().send(endpoint, target);
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
                    doFinalize(target, endpoint, enlistment, currentAttempt, description).whenComplete((res, ex) -> {
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

    @SuppressWarnings("deprecation")
    private Exchange createExchange(Exchange parent, Endpoint endpoint, StepEnlistment enlistment) {
        Exchange answer = endpoint.createExchange();
        answer.getMessage().setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, getId());
        answer.getExchangeExtension().setSagaLongRunningAction(getId());
        answer.setProperty(FINALIZING_SAGA, getId());

        // preserve span from parent, so we can link this new exchange to the parent span for distributed tracing
        Object span = parent != null ? parent.getProperty(ExchangePropertyKey.OTEL_ACTIVE_SPAN) : null;
        if (span != null) {
            answer.setProperty(ExchangePropertyKey.OTEL_ACTIVE_SPAN, span);
        }

        for (Map.Entry<String, Object> entry : enlistment.optionValues.entrySet()) {
            answer.getMessage().setHeader(entry.getKey(), entry.getValue());
        }
        return answer;
    }

    private void cancelTimeouts() {
        for (ScheduledFuture<?> future : timeoutFutures) {
            future.cancel(false);
        }
        timeoutFutures.clear();
    }

    private <T> List<T> reversed(List<T> list) {
        List<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    private record Finalization(Status kind, CompletableFuture<Boolean> outcome) {
    }

    private static class StepEnlistment {
        final CamelSagaStep step;
        final Map<String, Object> optionValues;

        StepEnlistment(CamelSagaStep step, Map<String, Object> optionValues) {
            this.step = step;
            this.optionValues = optionValues;
        }
    }
}
