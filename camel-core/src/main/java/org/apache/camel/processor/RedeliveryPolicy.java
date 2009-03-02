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

import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// Code taken from the ActiveMQ codebase

/**
 * The policy used to decide how many times to redeliver and the time between
 * the redeliveries before being sent to a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
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
 * <p/>
 * This policy can be configured either by one of the following two settings:
 * <ul>
 *   <li>using convnetional options, using all the options defined above</li>
 *   <li>using delay pattern to declare intervals for delays</li>
 * </ul>
 * <p/>
 * <b>Note:</b> If using delay patterns then the following options is not used (delay, backOffMultiplier, useExponentialBackOff, useCollisionAvoidance)
 * <p/>
 * <b>Using delay pattern</b>:
 * <br/>The delay pattern syntax is: <tt>limit:delay;limit 2:delay 2;limit 3:delay 3;...;limit N:delay N</tt>.
 * <p/>
 * How it works is best illustrate with an example with this pattern: <tt>delayPattern=5:1000;10:5000:20:20000</tt>
 * <br/>The delays will be for attempt in range 0..4 = 0 millis, 5..9 = 1000 millis, 10..19 = 5000 millis, >= 20 = 20000 millis.
 * <p/>
 * If you want to set a starting delay, then use 0 as the first limit, eg: <tt>0:1000;5:5000</tt> will use 1 sec delay
 * until attempt number 5 where it will use 5 seconds going forward.
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
    protected String delayPattern;

    public RedeliveryPolicy() {
    }

    @Override
    public String toString() {
        return "RedeliveryPolicy[maximumRedeliveries=" + maximumRedeliveries
            + ", delay=" + delay
            + ", maximumRedeliveryDelay=" + maximumRedeliveryDelay
            + ", retriesExhaustedLogLevel=" + retriesExhaustedLogLevel
            + ", retryAttemptedLogLevel=" + retryAttemptedLogLevel
            + ", useExponentialBackOff="  + useExponentialBackOff
            + ", backOffMultiplier=" + backOffMultiplier
            + ", useCollisionAvoidance=" + useCollisionAvoidance
            + ", collisionAvoidanceFactor=" + collisionAvoidanceFactor
            + ", delayPattern=" + delayPattern + "]";
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
     * redelivered.
     *
     * @param exchange  the current exchange
     * @param redeliveryCounter  the current retry counter
     * @param retryUntil  an optional predicate to determine if we should redeliver or not
     * @return true to redeliver, false to stop
     */
    public boolean shouldRedeliver(Exchange exchange, int redeliveryCounter, Predicate retryUntil) {
        // predicate is always used if provided
        if (retryUntil != null) {
            return retryUntil.matches(exchange);
        }

        if (getMaximumRedeliveries() < 0) {
            // retry forever if negative value
            return true;
        }
        // redeliver until we hit the max
        return redeliveryCounter <= getMaximumRedeliveries();
    }


    /**
     * Calculates the new redelivery delay based on the last one then sleeps for the necessary amount of time
     *
     * @param redeliveryDelay  previous redelivery delay
     * @param redeliveryCounter  number of previous redelivery attempts
     * @return the calculate delay
     * @throws InterruptedException is thrown if the sleep is interruped likely because of shutdown
     */
    public long sleep(long redeliveryDelay, int redeliveryCounter) throws InterruptedException {
        redeliveryDelay = calculateRedeliveryDelay(redeliveryDelay, redeliveryCounter);

        if (redeliveryDelay > 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sleeping for: " + redeliveryDelay + " millis until attempting redelivery");
            }
            Thread.sleep(redeliveryDelay);
        }
        return redeliveryDelay;
    }

    protected long calculateRedeliveryDelay(long previousDelay, int redeliveryCounter) {
        if (ObjectHelper.isNotEmpty(delayPattern)) {
            // calculate delay using the pattern
            return calculateRedeliverDelayUsingPattern(delayPattern, redeliveryCounter);
        }

        // calculate the delay using the conventional parameters
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

    /**
     * Calculates the delay using the delay pattern
     */
    protected static long calculateRedeliverDelayUsingPattern(String delayPattern, int redeliveryCounter) {
        String[] groups = delayPattern.split(";");
        // find the group where ther redelivery counter matches
        long answer = 0;
        for (String group : groups) {
            long delay = Long.valueOf(ObjectHelper.after(group, ":"));
            int count = Integer.valueOf(ObjectHelper.before(group, ":"));
            if (count > redeliveryCounter) {
                break;
            } else {
                answer = delay;
            }
        }

        return answer;
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
    
    /**
     * Sets the delay pattern with delay intervals.
     */
    public RedeliveryPolicy delayPattern(String delayPattern) {
        setDelayPattern(delayPattern);
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

    public String getDelayPattern() {
        return delayPattern;
    }

    /**
     * Sets an optional delay pattern to use insted of fixed delay.
     */
    public void setDelayPattern(String delayPattern) {
        this.delayPattern = delayPattern;
    }
}
