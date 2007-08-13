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
package org.apache.camel.processor;

import java.io.Serializable;
import java.util.Random;

// Code taken from the ActiveMQ codebase

/**
 * The policy used to decide how many times to redeliver and the time between
 * the redeliveries before being sent to a <a
 * href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter
 * Channel</a>
 * 
 * @version $Revision$
 */
public class RedeliveryPolicy implements Cloneable, Serializable {
    protected static transient Random randomNumberGenerator;
    protected int maximumRedeliveries = 6;
    protected long initialRedeliveryDelay = 1000L;
    protected double backOffMultiplier = 2;
    protected boolean useExponentialBackOff;
    // +/-15% for a 30% spread -cgs
    protected double collisionAvoidanceFactor = 0.15d;
    protected boolean useCollisionAvoidance;

    public RedeliveryPolicy() {
    }

    @Override
    public String toString() {
        return "RedeliveryPolicy[maximumRedeliveries=" + maximumRedeliveries + "]";
    }

    public RedeliveryPolicy copy() {
        try {
            return (RedeliveryPolicy)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Could not clone: " + e, e);
        }
    }

    /**
     * Returns true if the policy decides that the message exchange should be
     * redelivered
     */
    public boolean shouldRedeliver(int redeliveryCounter) {
        return redeliveryCounter < getMaximumRedeliveries();
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Sets the maximum number of times a message exchange will be redelivered
     */
    public RedeliveryPolicy maximumRedeliveries(int maximumRedeliveries) {
        setMaximumRedeliveries(maximumRedeliveries);
        return this;
    }

    /**
     * Sets the initial redelivery delay in milliseconds on the first redelivery
     */
    public RedeliveryPolicy initialRedeliveryDelay(long initialRedeliveryDelay) {
        setInitialRedeliveryDelay(initialRedeliveryDelay);
        return this;
    }

    /**
     * Enables collision avoidence which adds some randomization to the backoff
     * timings to reduce contention probability
     */
    public RedeliveryPolicy useCollisionAvoidance() {
        setUseCollisionAvoidance(true);
        return this;
    }

    /**
     * Enables exponential backof using the {@link #getBackOffMultiplier()} to
     * increase the time between retries
     */
    public RedeliveryPolicy useExponentialBackOff() {
        setUseExponentialBackOff(true);
        return this;
    }

    /**
     * Enables exponential backoff and sets the multiplier used to increase the
     * delay between redeliveries
     */
    public RedeliveryPolicy backOffMultiplier(double multiplier) {
        useExponentialBackOff();
        setBackOffMultiplier(multiplier);
        return this;
    }

    /**
     * Enables collision avoidence and sets the percentage used
     */
    public RedeliveryPolicy collisionAvoidancePercent(double collisionAvoidancePercent) {
        useCollisionAvoidance();
        setCollisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------
    public double getBackOffMultiplier() {
        return backOffMultiplier;
    }

    /**
     * Sets the multiplier used to increase the delay between redeliveries if
     * {@link #setUseExponentialBackOff(boolean)} is enabled
     */
    public void setBackOffMultiplier(double backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
    }

    public short getCollisionAvoidancePercent() {
        return (short)Math.round(collisionAvoidanceFactor * 100);
    }

    /**
     * Sets the percentage used for collision avoidence if enabled via
     * {@link #setUseCollisionAvoidance(boolean)}
     */
    public void setCollisionAvoidancePercent(double collisionAvoidancePercent) {
        this.collisionAvoidanceFactor = collisionAvoidancePercent * 0.01d;
    }

    public double getCollisionAvoidanceFactor() {
        return collisionAvoidanceFactor;
    }

    /**
     * Sets the factor used for collision avoidence if enabled via
     * {@link #setUseCollisionAvoidance(boolean)}
     */
    public void setCollisionAvoidanceFactor(double collisionAvoidanceFactor) {
        this.collisionAvoidanceFactor = collisionAvoidanceFactor;
    }

    public long getInitialRedeliveryDelay() {
        return initialRedeliveryDelay;
    }

    /**
     * Sets the initial redelivery delay in milliseconds on the first redelivery
     */
    public void setInitialRedeliveryDelay(long initialRedeliveryDelay) {
        this.initialRedeliveryDelay = initialRedeliveryDelay;
    }

    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    /**
     * Sets the maximum number of times a message exchange will be redelivered
     */
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public long getRedeliveryDelay(long previousDelay) {
        long redeliveryDelay;

        if (previousDelay == 0) {
            redeliveryDelay = initialRedeliveryDelay;
        } else if (useExponentialBackOff && backOffMultiplier > 1) {
            redeliveryDelay = Math.round(backOffMultiplier * previousDelay);
        } else {
            redeliveryDelay = previousDelay;
        }

        if (useCollisionAvoidance) {

            /*
             * First random determines +/-, second random determines how far to
             * go in that direction. -cgs
             */
            Random random = getRandomNumberGenerator();
            double variance = (random.nextBoolean() ? collisionAvoidanceFactor : -collisionAvoidanceFactor)
                              * random.nextDouble();
            redeliveryDelay += redeliveryDelay * variance;
        }

        return redeliveryDelay;
    }

    public boolean isUseCollisionAvoidance() {
        return useCollisionAvoidance;
    }

    /**
     * Enables/disables collision avoidence which adds some randomization to the
     * backoff timings to reduce contention probability
     */
    public void setUseCollisionAvoidance(boolean useCollisionAvoidance) {
        this.useCollisionAvoidance = useCollisionAvoidance;
    }

    public boolean isUseExponentialBackOff() {
        return useExponentialBackOff;
    }

    /**
     * Enables/disables exponential backof using the
     * {@link #getBackOffMultiplier()} to increase the time between retries
     */
    public void setUseExponentialBackOff(boolean useExponentialBackOff) {
        this.useExponentialBackOff = useExponentialBackOff;
    }

    protected static synchronized Random getRandomNumberGenerator() {
        if (randomNumberGenerator == null) {
            randomNumberGenerator = new Random();
        }
        return randomNumberGenerator;
    }
}
