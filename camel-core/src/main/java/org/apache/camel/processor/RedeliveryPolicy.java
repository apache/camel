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

import org.apache.camel.model.LoggingLevel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// Code taken from the ActiveMQ codebase

/**
 * The policy used to decide how many times to redeliver and the time between
 * the redeliveries before being sent to a <a
 * href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter
 * Channel</a>
 * <p>
 * The default values are:
 * <ul>
 *   <li>maximumRedeliveries = 5</li>
 *   <li>delay = 1000L (the initial delay)</li>
 *   <li>maximumRedeliveryDelay = 60 * 1000L</li>
 *   <li>backOffMultiplier = 2</li>
 *   <li>useExponentialBackOff = false</li>
 *   <li>collisionAvoidanceFactor = 0.15d</li>
 *   <li>useCollisionAvoidance = false</li>
 *   <li>retriesExhaustedLogLevel = LoggingLevel.ERROR</li>
 *   <li>retryAttemptedLogLevel = LoggingLevel.ERROR</li>
 * </ul>
 * <p/>
 * Setting the maximumRedeliveries to a negative value such as -1 will then always redeliver (unlimited).
 * Setting the maximumRedeliveries to 0 will disable redelivery.
 *
 * @version $Revision$
 */
public class RedeliveryPolicy extends DelayPolicy {
    protected static transient Random randomNumberGenerator;
    private static final transient Log LOG = LogFactory.getLog(RedeliveryPolicy.class);

    protected int maximumRedeliveries = 5;
    protected long maximumRedeliveryDelay = 60 * 1000L;
    protected double backOffMultiplier = 2;
    protected boolean useExponentialBackOff;
    // +/-15% for a 30% spread -cgs
    protected double collisionAvoidanceFactor = 0.15d;
    protected boolean useCollisionAvoidance;
    protected LoggingLevel retriesExhaustedLogLevel = LoggingLevel.ERROR;
    protected LoggingLevel retryAttemptedLogLevel = LoggingLevel.ERROR;

    public RedeliveryPolicy() {
    }

    @Override
    public String toString() {
        return "RedeliveryPolicy[maximumRedeliveries=" + maximumRedeliveries
            + ", initialRedeliveryDelay=" + delay
            + ", maximumRedeliveryDelay=" + maximumRedeliveryDelay
            + ", retriesExhaustedLogLevel=" + retriesExhaustedLogLevel
            + ", retryAttemptedLogLevel=" + retryAttemptedLogLevel
            + ", useExponentialBackOff="  + useExponentialBackOff
            + ", backOffMultiplier=" + backOffMultiplier
            + ", useCollisionAvoidance=" + useCollisionAvoidance
            + ", collisionAvoidanceFactor=" + collisionAvoidanceFactor + "]";
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
        if (getMaximumRedeliveries() < 0) {
            return true;
        }
        // redeliver until we hit the max
        return redeliveryCounter <= getMaximumRedeliveries();
    }


    /**
     * Calculates the new redelivery delay based on the last one then sleeps for the necessary amount of time
     */
    public long sleep(long redeliveryDelay) {
        redeliveryDelay = getRedeliveryDelay(redeliveryDelay);

        if (redeliveryDelay > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sleeping for: " + redeliveryDelay + " millis until attempting redelivery");
            }
            try {
                Thread.sleep(redeliveryDelay);
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Thread interrupted: " + e, e);
                }
            }
        }
        return redeliveryDelay;
    }


    public long getRedeliveryDelay(long previousDelay) {
        long redeliveryDelay;

        if (previousDelay == 0) {
            redeliveryDelay = delay;
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

        if (maximumRedeliveryDelay > 0 && redeliveryDelay > maximumRedeliveryDelay) {
            redeliveryDelay = maximumRedeliveryDelay;
        }

        return redeliveryDelay;
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
     *
     * @deprecated use delay. Will be removed in Camel 2.0.
     */
    public RedeliveryPolicy initialRedeliveryDelay(long initialRedeliveryDelay) {
        setDelay(initialRedeliveryDelay);
        return this;
    }

    /**
     * Enables collision avoidance which adds some randomization to the backoff
     * timings to reduce contention probability
     */
    public RedeliveryPolicy useCollisionAvoidance() {
        setUseCollisionAvoidance(true);
        return this;
    }

    /**
     * Enables exponential backoff using the {@link #getBackOffMultiplier()} to
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
     * Enables collision avoidance and sets the percentage used
     */
    public RedeliveryPolicy collisionAvoidancePercent(double collisionAvoidancePercent) {
        useCollisionAvoidance();
        setCollisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    /**
     * Sets the maximum redelivery delay if using exponential back off.
     * Use -1 if you wish to have no maximum
     */
    public RedeliveryPolicy maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        setMaximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    /**
     * Sets the logging level to use for log messages when retries have been exhausted.
     */
    public RedeliveryPolicy retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }    

    /**
     * Sets the logging level to use for log messages when retries are attempted.
     */    
    public RedeliveryPolicy retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        setRetryAttemptedLogLevel(retryAttemptedLogLevel);
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
     * Sets the percentage used for collision avoidance if enabled via
     * {@link #setUseCollisionAvoidance(boolean)}
     */
    public void setCollisionAvoidancePercent(double collisionAvoidancePercent) {
        this.collisionAvoidanceFactor = collisionAvoidancePercent * 0.01d;
    }

    public double getCollisionAvoidanceFactor() {
        return collisionAvoidanceFactor;
    }

    /**
     * Sets the factor used for collision avoidance if enabled via
     * {@link #setUseCollisionAvoidance(boolean)}
     */
    public void setCollisionAvoidanceFactor(double collisionAvoidanceFactor) {
        this.collisionAvoidanceFactor = collisionAvoidanceFactor;
    }

    /**
     * @deprecated  use delay instead. Will be removed in Camel 2.0.
     */
    public long getInitialRedeliveryDelay() {
        return getDelay();
    }

    /**
     * Sets the initial redelivery delay in milliseconds on the first redelivery
     *
     * @deprecated use delay instead. Will be removed in Camel 2.0.
     */
    public void setInitialRedeliveryDelay(long initialRedeliveryDelay) {
        setDelay(initialRedeliveryDelay);
    }

    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    /**
     * Sets the maximum number of times a message exchange will be redelivered.
     * Setting a negative value will retry forever.
     */
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public long getMaximumRedeliveryDelay() {
        return maximumRedeliveryDelay;
    }

    /**
     * Sets the maximum redelivery delay if using exponential back off.
     * Use -1 if you wish to have no maximum
     */
    public void setMaximumRedeliveryDelay(long maximumRedeliveryDelay) {
        this.maximumRedeliveryDelay = maximumRedeliveryDelay;
    }

    public boolean isUseCollisionAvoidance() {
        return useCollisionAvoidance;
    }

    /**
     * Enables/disables collision avoidance which adds some randomization to the
     * backoff timings to reduce contention probability
     */
    public void setUseCollisionAvoidance(boolean useCollisionAvoidance) {
        this.useCollisionAvoidance = useCollisionAvoidance;
    }

    public boolean isUseExponentialBackOff() {
        return useExponentialBackOff;
    }

    /**
     * Enables/disables exponential backoff using the
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

    /**
     * Sets the logging level to use for log messages when retries have been exhausted.
     */    
    public void setRetriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        this.retriesExhaustedLogLevel = retriesExhaustedLogLevel;        
    }
    
    public LoggingLevel getRetriesExhaustedLogLevel() {
        return retriesExhaustedLogLevel;
    }

    /**
     * Sets the logging level to use for log messages when retries are attempted.
     */    
    public void setRetryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        this.retryAttemptedLogLevel = retryAttemptedLogLevel;
    }

    public LoggingLevel getRetryAttemptedLogLevel() {
        return retryAttemptedLogLevel;
    }
}
