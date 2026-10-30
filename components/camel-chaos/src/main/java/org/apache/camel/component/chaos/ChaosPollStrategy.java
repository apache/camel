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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.TimeSource;

/**
 * Raises the faults of the rules at the polls of a scheduled poll consumer, around the poll strategy of the consumer:
 * {@code drop} skips the poll (the strategy of the consumer is not asked), {@code delay} makes the poll thread wait,
 * through {@link TimeSource}, before the poll, and {@code error} fails the poll after the strategy of the consumer
 * began it, so its {@code rollback} sees the failure, and the retry counter, as for a poll that failed by itself (an
 * exception that is not a {@link RuntimeException} is wrapped in a {@link RuntimeCamelException}). A poll that waited
 * is skipped when the consumer was stopped or suspended meanwhile. The first rule that fires decides the fault of a
 * poll.
 */
final class ChaosPollStrategy implements PollingConsumerPollStrategy {

    private final ChaosManager manager;
    private final PollingConsumerPollStrategy delegate;
    // the poll run on this thread (a run, with its retries, stays on one thread; a consumer with concurrentConsumers
    // runs several at the same time): the failure an error rule threw from begin, and the error rules that failed it
    private final ThreadLocal<Run> run = new ThreadLocal<>();
    // whether an error rule failed a poll of this consumer yet: until then commit and rollback do not look at the run
    private volatile boolean faulted;

    ChaosPollStrategy(ChaosManager manager, PollingConsumerPollStrategy delegate) {
        this.manager = manager;
        // a consumer that is not initialized yet has no strategy, and would use the default one
        this.delegate = delegate != null ? delegate : new DefaultPollingConsumerPollStrategy();
    }

    PollingConsumerPollStrategy getDelegate() {
        return delegate;
    }

    @Override
    public boolean begin(Consumer consumer, Endpoint endpoint) {
        ChaosRule[] rules = manager.ruleSet().poll;
        if (rules.length > 0) {
            long now = TimeSource.currentTimeMillis();
            for (ChaosRule rule : rules) {
                if (rule.live(now) && rule.matchesEndpoint(endpoint) && rule.fires(now)) {
                    return fault(rule, consumer, endpoint);
                }
            }
        }
        return beginDelegate(consumer, endpoint);
    }

    // the strategy of the consumer begins the poll; when it skips it, the poll run is done
    private boolean beginDelegate(Consumer consumer, Endpoint endpoint) {
        boolean begun = delegate.begin(consumer, endpoint);
        if (!begun) {
            endRun();
        }
        return begun;
    }

    private boolean fault(ChaosRule rule, Consumer consumer, Endpoint endpoint) {
        switch (rule.fault) {
            case DROP -> {
                rule.fired(Hook.POLL);
                endRun();
                return false;
            }
            case DELAY -> {
                rule.fired(Hook.POLL);
                try {
                    TimeSource.sleep(rule.duration);
                } catch (InterruptedException e) {
                    // the consumer is stopping: skip the poll, and keep the interrupted status
                    Thread.currentThread().interrupt();
                    endRun();
                    return false;
                }
                if (consumer instanceof ServiceSupport service
                        && (!service.isRunAllowed() || service.isSuspendingOrSuspended())) {
                    // the consumer was stopped or suspended while the poll waited
                    endRun();
                    return false;
                }
                return beginDelegate(consumer, endpoint);
            }
            case ERROR -> {
                if (!beginDelegate(consumer, endpoint)) {
                    // the strategy of the consumer skips this poll anyway
                    rule.skipped();
                    return false;
                }
                rule.fired(Hook.POLL);
                Throwable failure = ChaosFaults.newException(rule);
                // the strategy declares no checked exception: the consumer gets it wrapped, as its cause. An Error is
                // wrapped too, as the consumer handles only an Exception (it would stop polling for good otherwise)
                RuntimeException exception
                        = failure instanceof RuntimeException re ? re : new RuntimeCamelException(failure);
                faulted = true;
                Run current = run.get();
                if (current == null) {
                    current = new Run();
                    run.set(current);
                }
                current.thrown = exception;
                throw exception;
            }
            default -> throw new IllegalStateException("The fault " + rule.fault + " does not fire at polls");
        }
    }

    @Override
    public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
        delegate.commit(consumer, endpoint, polledMessages);
        // the run is done (a greedy consumer starts a new one)
        endRun();
    }

    /**
     * Gives the rollback to the strategy of the consumer, with the retry counter of the attempt as if the polls that an
     * error rule failed had failed by themselves. The consumer counts an attempt only when begin returns, and an error
     * rule fails begin: the consumer would pass -1, or the same count again for each retry, and a strategy that retries
     * while the counter is below a limit would retry for good.
     */
    @Override
    public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception {
        if (!faulted) {
            return delegate.rollback(consumer, endpoint, retryCounter, cause);
        }
        Run current = run.get();
        int attempt = retryCounter;
        if (current != null) {
            if (cause != null && cause == current.thrown) {
                // this attempt failed in begin, which the consumer did not count
                current.thrown = null;
                attempt = retryCounter + 1 + current.failures++;
            } else {
                attempt = retryCounter + current.failures;
            }
        }
        boolean retry = false;
        try {
            retry = delegate.rollback(consumer, endpoint, attempt, cause);
            return retry;
        } finally {
            if (!retry) {
                endRun();
            }
        }
    }

    // the poll run on this thread is done: the next one counts from 0 again. A run that the consumer ends without
    // telling the strategy (stopped or suspended between two retries) leaves its count on the thread until the next
    // run on it ends: that run then retries less, never more
    private void endRun() {
        if (faulted) {
            run.remove();
        }
    }

    /** A poll run on a thread that an error rule failed. */
    private static final class Run {
        Exception thrown;
        int failures;
    }

    @Override
    public String toString() {
        return "ChaosPollStrategy[" + delegate + "]";
    }
}
