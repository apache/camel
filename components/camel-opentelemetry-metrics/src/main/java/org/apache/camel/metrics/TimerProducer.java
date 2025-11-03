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
package org.apache.camel.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static org.apache.camel.metrics.OpenTelemetryConstants.HEADER_TIMER_ACTION;

public class TimerProducer extends AbstractOpenTelemetryProducer<LongHistogram> {

    private final Map<String, LongHistogram> timers = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public TimerProducer(OpenTelemetryEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected LongHistogram getInstrument(String name, String description) {
        LongHistogram timer = timers.get(name);
        if (timer == null) {
            synchronized (lock) {
                timer = timers.get(name);
                if (timer == null) {
                    Meter meter = getEndpoint().getMeter();
                    LongHistogramBuilder builder = meter.histogramBuilder(name).ofLongs();
                    if (description != null) {
                        builder.setDescription(description);
                    }
                    builder.setUnit(getEndpoint().getUnit().name().toLowerCase());
                    timer = builder.build();
                    timers.put(name, timer);
                }
            }
        }
        return timer;
    }

    @Override
    protected void doProcess(Exchange exchange, String metricsName, String metricsDescription, Attributes attributes) {
        Message in = exchange.getIn();
        OpenTelemetryTimerAction action = simple(exchange, getEndpoint().getAction(), OpenTelemetryTimerAction.class);
        OpenTelemetryTimerAction finalAction = in.getHeader(HEADER_TIMER_ACTION, action, OpenTelemetryTimerAction.class);

        if (finalAction == OpenTelemetryTimerAction.START) {
            handleStart(exchange, metricsName);
        } else if (finalAction == OpenTelemetryTimerAction.STOP) {
            handleStop(exchange, metricsName, metricsDescription, attributes);
        }
    }

    @Override
    protected void doProcess(
            Exchange exchange, String metricsName, LongHistogram timer, Attributes attributes) {

        String propertyName = getPropertyName(metricsName);
        TaskTimer timedTask = getTimerFromExchange(exchange, propertyName);
        timer.record(timedTask.duration(getEndpoint().getUnit()), attributes);
        exchange.removeProperty(propertyName);
    }

    private void handleStart(Exchange exchange, String metricsName) {
        String propertyName = getPropertyName(metricsName);
        TaskTimer timedTask = getTimerFromExchange(exchange, propertyName);
        if (timedTask == null) {
            exchange.setProperty(propertyName, new TaskTimer());
        }
    }

    private void handleStop(Exchange exchange, String metricsName, String metricsDescription, Attributes attributes) {
        TaskTimer timer = getTimerFromExchange(exchange, getPropertyName(metricsName));
        if (timer != null) {
            doProcess(exchange, metricsName, getInstrument(metricsName, metricsDescription), attributes);
        }
    }

    private String getPropertyName(String metricsName) {
        return "timer:" + metricsName;
    }

    private TaskTimer getTimerFromExchange(Exchange exchange, String propertyName) {
        return exchange.getProperty(propertyName, TaskTimer.class);
    }
}
