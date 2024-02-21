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
package org.apache.camel.component.aws2.athena;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;

/**
 * Package-private class to encapsulate the logic of running queries, waiting for completion states, retrying, etc.
 */
class Athena2QueryHelper {
    private static final Logger LOG = LoggerFactory.getLogger(Athena2QueryHelper.class);

    // configuration ======================
    private final Clock clock = Clock.systemUTC();
    private final long waitTimeout;
    private final long delay;
    private final Set<String> retry;
    private final int maxAttempts;
    private final boolean resetWaitTimeoutOnAttempt;
    private final long absoluteStartMs;

    // state ==============================
    private long currentDelay;
    private int attempts;
    private boolean isFailure;
    private boolean isSuccess;
    private boolean isRetry;
    private long startMs;
    private boolean interrupted;

    Athena2QueryHelper(Exchange exchange, Athena2Configuration configuration) {
        this.waitTimeout = determineWaitTimeout(exchange, configuration);
        this.delay = determineDelay(exchange, configuration);
        this.maxAttempts = determineMaxAttempts(exchange, configuration);
        this.retry = determineRetry(exchange, configuration);
        this.resetWaitTimeoutOnAttempt = determineResetWaitTimeoutOnRetry(exchange, configuration);
        this.absoluteStartMs = now();

        this.currentDelay = determineInitialDelay(exchange, configuration);
    }

    private long now() {
        return clock.millis();
    }

    long getElapsedMillis() {
        return now() - this.absoluteStartMs;
    }

    /**
     * Record that a query attempt was made. This is relevant b/c only so many attempts are permitted.
     */
    void markAttempt() {
        if (attempts == 0) {
            this.startMs = now();
        } else {
            if (resetWaitTimeoutOnAttempt) {
                this.startMs = now();
            }
        }
        ++attempts;

        this.isFailure = false;
        this.isSuccess = false;
        this.isRetry = false;
    }

    int getAttempts() {
        return this.attempts;
    }

    /**
     * Should another query attempt be made?
     */
    boolean shouldAttempt() {
        if (this.attempts >= this.maxAttempts) {
            LOG.trace("AWS Athena start query execution used all {} attempts", this.maxAttempts);
            return false;
        }

        if (this.interrupted) {
            LOG.trace("AWS Athena start query execution thread was interrupted, will try no more");
            return false;
        }

        if (this.isFailure) {
            LOG.trace("AWS Athena start query execution detected permanent failure");
            return false;
        }

        if (this.isSuccess) {
            LOG.trace("AWS Athena start query execution detected success, will try no more");
            return false;
        }

        // if this.isRetry, return true

        return true;
    }

    /**
     * Should there be a wait for the query to complete?
     */
    boolean shouldWait() {
        long now = now();
        long millisWaited = now - this.startMs;
        if (millisWaited >= this.waitTimeout) {
            LOG.trace("AWS Athena start query execution waited for {}, which exceeded wait timeout of {}",
                    millisWaited,
                    this.waitTimeout);
            return false;
        }

        if (this.interrupted) {
            LOG.trace("AWS Athena start query execution thread was interrupted, will wait no longer");
            return false;
        }

        if (this.isFailure) {
            LOG.trace("AWS Athena start query execution detected failure, will wait no longer");
            return false;
        }

        if (this.isSuccess) {
            LOG.trace("AWS Athena start query execution detected success, will wait no longer");
            return false;
        }

        if (this.isRetry) {
            LOG.trace("AWS Athena start query execution detected retry, will immediately attempt retry");
            return false;
        }

        return true;
    }

    void doWait() {
        try {
            Thread.sleep(this.currentDelay);
        } catch (InterruptedException e) {
            this.interrupted = Thread.interrupted(); // store, then clear, interrupt status
            LOG.trace(
                    "AWS Athena start query execution wait thread was interrupted; will return at earliest opportunity");

            Thread.currentThread().interrupt();
        }
        this.currentDelay = this.delay;
    }

    /**
     * Has the query completed (does not imply success, only completion).
     */
    boolean isComplete(GetQueryExecutionResponse getQueryExecutionResponse) {
        QueryExecutionState state = getQueryExecutionResponse.queryExecution().status().state();
        return state == QueryExecutionState.SUCCEEDED
                || state == QueryExecutionState.FAILED
                || state == QueryExecutionState.CANCELLED
                || state == QueryExecutionState.UNKNOWN_TO_SDK_VERSION;
    }

    /**
     * Did the query complete successfully?
     */
    boolean wasSuccessful(GetQueryExecutionResponse getQueryExecutionResponse) {
        QueryExecutionState state = getQueryExecutionResponse.queryExecution().status().state();
        return state == QueryExecutionState.SUCCEEDED;
    }

    /**
     * Determine status based on the outcome of the query.
     */
    void setStatusFrom(GetQueryExecutionResponse getQueryExecutionResponse) {
        if (isComplete(getQueryExecutionResponse)) {
            if (wasSuccessful(getQueryExecutionResponse)) {
                this.isSuccess = true;

            } else if (shouldRetry(getQueryExecutionResponse)) {
                this.isRetry = true;

            } else {
                LOG.trace("AWS Athena start query execution detected failure ({})",
                        getQueryExecutionResponse.queryExecution().status().state());
                this.isFailure = true;
            }
        }
    }

    /**
     * Decide if it'd be worthwhile to retry a failed query. Depending on the value of {@code retry}, this may
     * {@code always} or {@code never} retry. But some of the other values allow for retrying on certain query failure
     * conditions.
     */
    boolean shouldRetry(GetQueryExecutionResponse getQueryExecutionResponse) {
        String stateChangeReason = getQueryExecutionResponse.queryExecution().status().stateChangeReason();

        if (this.retry.contains("never")) {
            LOG.trace("AWS Athena start query execution detected error ({}), marked as not retryable",
                    stateChangeReason);
            return false;
        }

        if (this.retry.contains("always")) {
            LOG.trace("AWS Athena start query execution detected error ({}), marked as retryable", stateChangeReason);
            return true;
        }

        // Generic errors happen sometimes in Athena.  It's possible that a retry will fix the problem.
        if (stateChangeReason != null && stateChangeReason.contains("GENERIC_INTERNAL_ERROR")
                && (this.retry.contains("generic") || this.retry.contains("retryable"))) {
            LOG.trace("AWS Athena start query execution detected generic error ({}), marked as retryable",
                    stateChangeReason);
            return true;
        }

        // Resource exhaustion happens sometimes in Athena.  It's possible that a retry will fix the problem.
        if (stateChangeReason != null && stateChangeReason.contains("exhausted resources at this scale factor")
                && (this.retry.contains("exhausted") || this.retry.contains("retryable"))) {
            LOG.trace("AWS Athena start query execution detected resource exhaustion error ({}), marked as retryable",
                    stateChangeReason);
            return true;
        }

        return false;
    }

    /**
     * Max time to wait for a query to complete.
     */
    private long determineWaitTimeout(final Exchange exchange, Athena2Configuration configuration) {
        Long waitTimeout = exchange.getIn().getHeader(Athena2Constants.WAIT_TIMEOUT, Long.class);

        if (ObjectHelper.isEmpty(waitTimeout)) {
            waitTimeout = configuration.getWaitTimeout();
            LOG.trace("AWS Athena wait timeout is missing, using default one [{}]", waitTimeout);
        }

        if (ObjectHelper.isEmpty(waitTimeout)) {
            throw new IllegalArgumentException("AWS Athena wait timeout required.");
        }

        if (waitTimeout < 0) {
            throw new IllegalArgumentException("AWS Athena wait timeout must be >= 0");
        }

        return waitTimeout;
    }

    /**
     * Delay between status polls.
     */
    private long determineDelay(final Exchange exchange, Athena2Configuration configuration) {
        Long delay = exchange.getIn().getHeader(Athena2Constants.DELAY, Long.class);

        if (ObjectHelper.isEmpty(delay)) {
            delay = configuration.getDelay();
            LOG.trace("AWS Athena delay is missing, using default one [{}]", delay);
        }

        if (ObjectHelper.isEmpty(delay)) {
            throw new IllegalArgumentException("AWS Athena delay is required.");
        }

        if (delay < 0) {
            throw new IllegalArgumentException("AWS Athena delay must be >= 0");
        }

        return delay;
    }

    private long determineInitialDelay(final Exchange exchange, Athena2Configuration configuration) {
        Long initialDelay = exchange.getIn().getHeader(Athena2Constants.INITIAL_DELAY, Long.class);

        if (ObjectHelper.isEmpty(initialDelay)) {
            initialDelay = configuration.getInitialDelay();
        }

        if (ObjectHelper.isEmpty(initialDelay)) {
            initialDelay = determineDelay(exchange, configuration);
        }

        if (initialDelay < 0) {
            throw new IllegalArgumentException("AWS Athena initial delay must be >= 0");
        }

        return initialDelay;
    }

    private Set<String> determineRetry(final Exchange exchange, Athena2Configuration configuration) {
        String retry = exchange.getIn().getHeader(Athena2Constants.RETRY, String.class);

        if (ObjectHelper.isEmpty(retry)) {
            retry = configuration.getRetry();
            LOG.trace("AWS Athena retry is missing, using default one [{}]", retry);
        }

        if (ObjectHelper.isEmpty(retry)) {
            retry = "never";
        }

        String[] parts = retry.split(",");
        Set<String> finalRetry = new HashSet<>();
        for (String part : parts) {
            if (ObjectHelper.isNotEmpty(part)) {
                finalRetry.add(part);
            }
        }

        if (finalRetry.size() > 1
                && (finalRetry.contains("retryable") || finalRetry.contains("always") || finalRetry.contains("never"))) {
            throw new IllegalArgumentException(
                    "AWS Athena retry is invalid - provide only one mutually exclusive option (retryable, always, never)");
        }

        List<String> valid = Arrays.asList("never", "always", "retryable", "exhausted", "generic");
        List<String> invalid = new ArrayList<>();
        for (String r : finalRetry) {
            if (!valid.contains(r)) {
                invalid.add(r);
            }
        }

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "AWS Athena retry is invalid - invalid values provided: " + invalid + ".  Valid values: " + valid);
        }

        return Collections.unmodifiableSet(finalRetry);
    }

    /**
     * Max number of times to try a query. Set to greater than 1 for retries.
     */
    private Integer determineMaxAttempts(final Exchange exchange, Athena2Configuration configuration) {
        Integer maxAttempts = exchange.getIn().getHeader(Athena2Constants.MAX_ATTEMPTS, Integer.class);

        if (ObjectHelper.isEmpty(maxAttempts)) {
            maxAttempts = configuration.getMaxAttempts();
            LOG.trace("AWS Athena max attempts is missing, using default one [{}]", maxAttempts);
        }

        if (ObjectHelper.isEmpty(maxAttempts)) {
            throw new IllegalArgumentException("AWS Athena max attempts is required.");
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("AWS Athena max attempts must be >= 1");
        }

        return maxAttempts;
    }

    /**
     * If the query is up for retry, should the wait timeout be reset? For example, if {@code waitTimeout} is set to
     * 60_000 ms, setting {@code resetWaitTimeoutOnRetry} to {@code true} would allow for another 60_000 ms to elapse
     * before timing out. With {@code resetWaitTimeoutOnRetry} set to {@code false}, the timer would not reset when
     * starting the query over again.
     */
    private boolean determineResetWaitTimeoutOnRetry(final Exchange exchange, Athena2Configuration configuration) {
        Boolean resetWaitTimeoutOnRetry
                = exchange.getIn().getHeader(Athena2Constants.RESET_WAIT_TIMEOUT_ON_RETRY, Boolean.class);

        if (ObjectHelper.isEmpty(resetWaitTimeoutOnRetry)) {
            resetWaitTimeoutOnRetry = configuration.isResetWaitTimeoutOnRetry();
            LOG.trace("AWS Athena reset wait timeout on retry is missing, using default one [{}]",
                    resetWaitTimeoutOnRetry);
        }

        if (ObjectHelper.isEmpty(resetWaitTimeoutOnRetry)) {
            throw new IllegalArgumentException("AWS Athena reset wait timeout on retry is required.");
        }

        return resetWaitTimeoutOnRetry;
    }

    // getters are visible for testing

    // configuration ======================
    long getWaitTimeout() {
        return waitTimeout;
    }

    long getDelay() {
        return delay;
    }

    Set<String> getRetry() {
        return retry;
    }

    int getMaxAttempts() {
        return maxAttempts;
    }

    boolean isResetWaitTimeoutOnAttempt() {
        return resetWaitTimeoutOnAttempt;
    }

    long getAbsoluteStartMs() {
        return absoluteStartMs;
    }

    // state ==============================
    long getCurrentDelay() {
        return currentDelay;
    }

    boolean isFailure() {
        return isFailure;
    }

    boolean isSuccess() {
        return isSuccess;
    }

    boolean isRetry() {
        return isRetry;
    }

    long getStartMs() {
        return startMs;
    }

    boolean isInterrupted() {
        return interrupted;
    }
}
