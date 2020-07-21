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
package org.apache.camel.component.micrometer;

import java.util.function.Function;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_TIMER_ACTION;

public class TimerProducer extends AbstractMicrometerProducer<Timer> {

    private static final Logger LOG = LoggerFactory.getLogger(TimerProducer.class);

    public TimerProducer(MicrometerEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected Function<MeterRegistry, Timer> registrar(String name, Iterable<Tag> tags) {
        return meterRegistry -> meterRegistry.timer(name, tags);
    }

    @Override
    protected void doProcess(Exchange exchange, MicrometerEndpoint endpoint, Timer timer) {
        String propertyName = getPropertyName(timer.getId().getName());
        Timer.Sample sample = getTimerSampleFromExchange(exchange, propertyName);
        sample.stop(timer);
        exchange.removeProperty(propertyName);
    }

    @Override
    protected void doProcess(Exchange exchange, String metricsName, Iterable<Tag> tags) {
        MeterRegistry registry = getEndpoint().getRegistry();
        Message in = exchange.getIn();
        MicrometerTimerAction action = simple(exchange, getEndpoint().getAction(), MicrometerTimerAction.class);
        MicrometerTimerAction finalAction = in.getHeader(HEADER_TIMER_ACTION, action, MicrometerTimerAction.class);
        if (finalAction == MicrometerTimerAction.start) {
            handleStart(exchange, registry, metricsName);
        } else if (finalAction == MicrometerTimerAction.stop) {
            handleStop(exchange, metricsName, tags);
        } else {
            LOG.warn("No action provided for timer \"{}\"", metricsName);
        }
    }

    private void handleStop(Exchange exchange, String metricsName, Iterable<Tag> tags) {
        if (getTimerSampleFromExchange(exchange, getPropertyName(metricsName)) != null) {
            doProcess(exchange, getEndpoint(), getOrRegisterMeter(metricsName, tags));
        }
    }

    void handleStart(Exchange exchange, MeterRegistry registry, String metricsName) {
        String propertyName = getPropertyName(metricsName);
        Timer.Sample sample = getTimerSampleFromExchange(exchange, propertyName);
        if (sample == null) {
            sample = Timer.start(registry);
            exchange.setProperty(propertyName, sample);
        } else {
            LOG.warn("Timer \"{}\" already running", metricsName);
        }
    }

    String getPropertyName(String metricsName) {
        return "timer:" + metricsName;
    }

    Timer.Sample getTimerSampleFromExchange(Exchange exchange, String propertyName) {
        return exchange.getProperty(propertyName, Timer.Sample.class);
    }
}
