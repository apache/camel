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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <code>SamplingThrottler</code> is a special kind of throttler. It also
 * limits the number of exchanges sent to a downstream endpoint. It differs from
 * a normal throttler in that it will not queue exchanges above the threshold
 * for a given period. Instead these exchanges will be stopped, precluding them
 * from being processed at all by downstream consumers.
 * <p/>
 * This kind of throttling can be useful for taking a sample from
 * an exchange stream, rough consolidation of noisy and bursty exchange traffic
 * or where queuing of throttled exchanges is undesirable.
 *
 * @version 
 */
public class SamplingThrottler extends DelegateAsyncProcessor implements Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(SamplingThrottler.class);
    private String id;
    private long messageFrequency;
    private long currentMessageCount;
    private long samplePeriod;
    private long periodInMillis;
    private TimeUnit units;
    private long timeOfLastExchange;
    private StopProcessor stopper = new StopProcessor();
    private final Object calculationLock = new Object();
    private SampleStats sampled = new SampleStats();

    public SamplingThrottler(Processor processor, long messageFrequency) {
        super(processor);

        if (messageFrequency <= 0) {
            throw new IllegalArgumentException("A positive value is required for the sampling message frequency");
        }
        this.messageFrequency = messageFrequency;
    }

    public SamplingThrottler(Processor processor, long samplePeriod, TimeUnit units) {
        super(processor);

        if (samplePeriod <= 0) {
            throw new IllegalArgumentException("A positive value is required for the sampling period");
        }
        if (units == null) {
            throw new IllegalArgumentException("A invalid null value was supplied for the units of the sampling period");
        }
        this.samplePeriod = samplePeriod;
        this.units = units;
        this.periodInMillis = units.toMillis(samplePeriod);
    }

    @Override
    public String toString() {
        if (messageFrequency > 0) {
            return "SamplingThrottler[1 exchange per: " + messageFrequency + " messages received -> " + getProcessor() + "]";
        } else {
            return "SamplingThrottler[1 exchange per: " + samplePeriod + " " + units.toString().toLowerCase(Locale.ENGLISH) + " -> " + getProcessor() + "]";
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceLabel() {
        if (messageFrequency > 0) {
            return "samplingThrottler[1 exchange per: " + messageFrequency + " messages received]";
        } else {
            return "samplingThrottler[1 exchange per: " + samplePeriod + " " + units.toString().toLowerCase(Locale.ENGLISH) + "]";
        }
    }

    public long getMessageFrequency() {
        return messageFrequency;
    }

    public long getSamplePeriod() {
        return samplePeriod;
    }

    public TimeUnit getUnits() {
        return units;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        boolean doSend = false;

        synchronized (calculationLock) {
            
            if (messageFrequency > 0) {
                currentMessageCount++;
                if (currentMessageCount % messageFrequency == 0) {
                    doSend = true;
                }
            } else {
                long now = System.currentTimeMillis();
                if (now >= timeOfLastExchange + periodInMillis) {
                    doSend = true;
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(sampled.sample());
                    }
                    timeOfLastExchange = now;
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(sampled.drop());
                    }
                }
            }
        }

        if (doSend) {
            // continue routing
            return processor.process(exchange, callback);
        } else {
            // okay to invoke this synchronously as the stopper
            // will just set a property
            try {
                stopper.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
        }

        // we are done synchronously
        callback.done(true);
        return true;
    }

    private static class SampleStats {
        private long droppedThisPeriod;
        private long totalDropped;
        private long totalSampled;
        private long totalThisPeriod;

        String drop() {
            droppedThisPeriod++;
            totalThisPeriod++;
            totalDropped++;
            return getDroppedLog();
        }

        String sample() {
            totalThisPeriod = 1; // a new period, reset to 1
            totalSampled++;
            droppedThisPeriod = 0;
            return getSampledLog();
        }

        String getSampledLog() {
            return String.format("Sampled %d of %d total exchanges", totalSampled, totalSampled + totalDropped);
        }

        String getDroppedLog() {
            return String.format("Dropped %d of %d exchanges in this period, totalling %d dropped of %d exchanges overall.",
                droppedThisPeriod, totalThisPeriod, totalDropped, totalSampled + totalDropped);
        }
    }

}
