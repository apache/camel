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
package org.apache.camel.opentelemetry.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_COUNTER_INCREMENT;

public class CounterProducer extends AbstractOpenTelemetryProducer<LongUpDownCounter> {

    private final Map<String, LongUpDownCounter> counters = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public CounterProducer(OpenTelemetryEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected LongUpDownCounter getInstrument(String name, String description) {
        LongUpDownCounter counter = counters.get(name);
        if (counter == null) {
            lock.lock();
            try {
                counter = counters.get(name);
                if (counter == null) {
                    Meter meter = getEndpoint().getMeter();
                    LongUpDownCounterBuilder builder = meter.upDownCounterBuilder(name);
                    if (description != null) {
                        builder.setDescription(description);
                    }
                    counter = builder.build();
                    counters.put(name, counter);
                }
            } finally {
                lock.unlock();
            }
        }
        return counter;
    }

    @Override
    protected void doProcess(
            Exchange exchange, String metricsName, LongUpDownCounter counter, Attributes attributes) {
        Message in = exchange.getIn();
        Long increment = simple(exchange, getEndpoint().getIncrement(), Long.class);
        Long decrement = simple(exchange, getEndpoint().getDecrement(), Long.class);
        Long finalIncrement = getLongHeader(in, HEADER_COUNTER_INCREMENT, increment);
        Long finalDecrement = getLongHeader(in, HEADER_COUNTER_DECREMENT, decrement);

        if (finalIncrement != null) {
            counter.add(finalIncrement, attributes);
        } else if (finalDecrement != null) {
            counter.add(-finalDecrement, attributes);
        } else if (getEndpoint().getIncrement() != null || getEndpoint().getDecrement() != null) {
            // an increment or decrement is configured, but it did not give a number (such as a Map or null body)
            throw new IllegalArgumentException(
                    describeOptions(getEndpoint().getIncrement(), getEndpoint().getDecrement()) + " of counter "
                                               + metricsName + " did not evaluate to a number");
        } else {
            counter.add(1L, attributes);
        }
    }

    private static String describeOptions(String increment, String decrement) {
        // name only the options that are configured
        if (increment != null && decrement != null) {
            return "The increment (" + increment + ") and decrement (" + decrement + ")";
        } else if (increment != null) {
            return "The increment (" + increment + ")";
        } else {
            return "The decrement (" + decrement + ")";
        }
    }
}
