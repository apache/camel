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
package org.apache.camel.processor.aggregate;

import java.util.Random;

/**
 * Class to control how failed optimistic locks are tried. This policy supports random and exponential back-off delays.
 * <p/>
 * If {@code randomBackOff} is enabled and a value is supplied for {@code retryDelay} the value will be ignored.
 * <p/>
 * If {@code randomBackOff} is enabled and no value is set for {@code maximumRetryDelay}, a default value of 1000ms will
 * be used, the random delay will be between 0 and 1000 milliseconds.
 * <p/>
 * If both {@code randomBackOff} and {@code exponentialBackOff} are enabled, {@code exponentialBackOff} will take precedence.
 * <p/>
 * If {@code exponentialBackOff} is enabled and a value is set for {@code maximumRetryDelay}, the retry delay will keep
 * doubling in value until it reaches or exceeds {@code maximumRetryDelay}. After it has reached or exceeded {@code maximumRetryDelay}
 * the value of {@code maximumRetryDelay} will be used as the retry delay.
 * <p/>
 * If both {@code exponentialBackOff} and {@code randomBackOff} are disabled, the value of {@code retryDelay} will be used
 * as the retry delay and remain constant through all the retry attempts.
 * <p/>
 * If the value of {@code maximumRetries} is set above zero, retry attempts will stop at the value specified.
 * <p/>
 * The default behaviour of this policy is to retry forever and exponentially increase the back-off delay starting with 50ms.
 *
 * @version
 */
public class OptimisticLockRetryPolicy {
    private static final long DEFAULT_MAXIMUM_RETRY_DELAY = 1000L;

    private int maximumRetries;
    private long retryDelay = 50L;
    private long maximumRetryDelay;
    private boolean exponentialBackOff = true;
    private boolean randomBackOff;

    public OptimisticLockRetryPolicy() {
    }

    public boolean shouldRetry(final int retryCounter) {
        return maximumRetries <= 0 || retryCounter < maximumRetries;
    }

    public void doDelay(final int retryCounter) throws InterruptedException {
        if (retryDelay > 0 || randomBackOff) {
            long sleepFor;
            sleepFor = exponentialBackOff ? (retryDelay << retryCounter)
                    : (randomBackOff ? new Random().nextInt((int)(maximumRetryDelay > 0 ? maximumRetryDelay : DEFAULT_MAXIMUM_RETRY_DELAY)) : retryDelay);
            if (maximumRetryDelay > 0 && sleepFor > maximumRetryDelay) {
                sleepFor = maximumRetryDelay;
            }
            Thread.sleep(sleepFor);
        }
    }

    public int getMaximumRetries() {
        return maximumRetries;
    }

    public void setMaximumRetries(int maximumRetries) {
        this.maximumRetries = maximumRetries;
    }

    public OptimisticLockRetryPolicy maximumRetries(int maximumRetries) {
        setMaximumRetries(maximumRetries);
        return this;
    }

    public long getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    public OptimisticLockRetryPolicy retryDelay(long retryDelay) {
        setRetryDelay(retryDelay);
        return this;
    }

    public long getMaximumRetryDelay() {
        return maximumRetryDelay;
    }

    public void setMaximumRetryDelay(long maximumRetryDelay) {
        this.maximumRetryDelay = maximumRetryDelay;
    }

    public OptimisticLockRetryPolicy maximumRetryDelay(long maximumRetryDelay) {
        setMaximumRetryDelay(maximumRetryDelay);
        return this;
    }

    public boolean isExponentialBackOff() {
        return exponentialBackOff;
    }

    public void setExponentialBackOff(boolean exponentialBackOff) {
        this.exponentialBackOff = exponentialBackOff;
    }

    public OptimisticLockRetryPolicy exponentialBackOff() {
        setExponentialBackOff(true);
        return this;
    }

    public boolean isRandomBackOff() {
        return randomBackOff;
    }

    public void setRandomBackOff(boolean randomBackOff) {
        this.randomBackOff = randomBackOff;
    }

    public OptimisticLockRetryPolicy randomBackOff() {
        setRandomBackOff(true);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OptimisticLockRetryPolicy[");
        sb.append("maximumRetries=").append(maximumRetries);
        sb.append(", retryDelay=").append(retryDelay);
        sb.append(", maximumRetryDelay=").append(maximumRetryDelay);
        sb.append(", exponentialBackOff=").append(exponentialBackOff);
        sb.append(", randomBackOff=").append(randomBackOff);
        sb.append(']');
        return sb.toString();
    }
}