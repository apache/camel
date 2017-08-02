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
package org.apache.camel.util.backoff;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.ObjectHelper;

public final class BackOff {
    public static final long NEVER = -1L;
    public static final Duration MAX_DURATION = Duration.ofMillis(Long.MAX_VALUE);
    public static final Duration DEFAULT_DELAY = Duration.ofSeconds(2);
    public static final double DEFAULT_MULTIPLIER = 1f;

    private Duration delay;
    private Duration maxDelay;
    private Duration maxElapsedTime;
    private Long maxAttempts;
    private Double multiplier;

    public BackOff() {
        this(DEFAULT_DELAY, MAX_DURATION, MAX_DURATION, Long.MAX_VALUE, DEFAULT_MULTIPLIER);
    }

    public BackOff(Duration delay, Duration maxDelay, Duration maxElapsedTime, Long maxAttempts, Double multiplier) {
        this.delay = ObjectHelper.supplyIfEmpty(delay, () -> DEFAULT_DELAY);
        this.maxDelay = ObjectHelper.supplyIfEmpty(maxDelay, () -> MAX_DURATION);
        this.maxElapsedTime = ObjectHelper.supplyIfEmpty(maxElapsedTime, () -> MAX_DURATION);
        this.maxAttempts = ObjectHelper.supplyIfEmpty(maxAttempts, () -> Long.MAX_VALUE);
        this.multiplier = ObjectHelper.supplyIfEmpty(multiplier, () -> DEFAULT_MULTIPLIER);
    }

    // *************************************
    // Properties
    // *************************************

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
    }

    public Duration getMaxElapsedTime() {
        return maxElapsedTime;
    }

    public void setMaxElapsedTime(Duration maxElapsedTime) {
        this.maxElapsedTime = maxElapsedTime;
    }

    public Long getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Long maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public String toString() {
        return "BackOff{"
            + "delay=" + delay
            + ", maxDelay=" + maxDelay
            + ", maxElapsedTime=" + maxElapsedTime
            + ", maxAttempts=" + maxAttempts
            + ", multiplier=" + multiplier
            + '}';
    }

    // *****************************************
    // Builder
    // *****************************************

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Duration delay = BackOff.DEFAULT_DELAY;
        private Duration maxDelay = BackOff.MAX_DURATION;
        private Duration maxElapsedTime = BackOff.MAX_DURATION;
        private Long maxAttempts = Long.MAX_VALUE;
        private Double multiplier = BackOff.DEFAULT_MULTIPLIER;

        public Builder read(BackOff template) {
            delay = template.delay;
            maxDelay = template.maxDelay;
            maxElapsedTime = template.maxElapsedTime;
            maxAttempts = template.maxAttempts;
            multiplier = template.multiplier;

            return this;
        }

        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        public Builder delay(long delay, TimeUnit unit) {
            return delay(Duration.ofMillis(unit.toMillis(delay)));
        }

        public Builder delay(long delay) {
            return delay(Duration.ofMillis(delay));
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder maxDelay(long maxDelay, TimeUnit unit) {
            return maxDelay(Duration.ofMillis(unit.toMillis(maxDelay)));
        }

        public Builder maxDelay(long maxDelay) {
            return maxDelay(Duration.ofMillis(maxDelay));
        }

        public Builder maxElapsedTime(Duration maxElapsedTime) {
            this.maxElapsedTime = maxElapsedTime;
            return this;
        }

        public Builder maxElapsedTime(long maxElapsedTime, TimeUnit unit) {
            return maxElapsedTime(Duration.ofMillis(unit.toMillis(maxElapsedTime)));
        }

        public Builder maxElapsedTime(long maxElapsedTime) {
            return maxElapsedTime(Duration.ofMillis(maxElapsedTime));
        }

        public Builder maxAttempts(Long attempts) {
            this.maxAttempts = attempts;
            return this;
        }

        public Builder multiplier(Double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public BackOff build() {
            return new BackOff(delay, maxDelay, maxElapsedTime, maxAttempts, multiplier);
        }
    }
}
