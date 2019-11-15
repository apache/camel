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
package org.apache.camel.component.microprofile.metrics;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.Timer.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_TIMER_ACTION;

public class MicroProfileMetricsTimerProducer extends AbstractMicroProfileMetricsProducer<Timer> {

    private static final Logger LOG = LoggerFactory.getLogger(MicroProfileMetricsTimerProducer.class);

    public MicroProfileMetricsTimerProducer(MicroProfileMetricsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, MicroProfileMetricsEndpoint endpoint, Timer timer) {
        String propertyName = getPropertyName(endpoint.getMetricName());
        Context context = getTimerContextFromExchange(exchange, propertyName);
        if (context != null) {
            context.stop();
        } else {
            LOG.warn("Timer context for metric '{}' was not found", propertyName);
        }
        exchange.removeProperty(propertyName);
    }

    @Override
    protected void doProcess(Exchange exchange, Metadata metadata, List<Tag> tags) {
        String actionString = getEndpoint().getAction().toUpperCase(Locale.US);
        TimerAction action = exchange.getIn().getHeader(HEADER_TIMER_ACTION, actionString, TimerAction.class);
        if (action == TimerAction.START) {
            handleStart(exchange, metadata, tags);
        } else {
            handleStop(exchange, metadata, tags);
        }
    }

    @Override
    protected Function<MetricRegistry, Timer> registerMetric(Metadata metadata, List<Tag> tags) {
        return metricRegistry -> metricRegistry.timer(metadata, tags.toArray(new Tag[0]));
    }

    private void handleStart(Exchange exchange, Metadata metadata, List<Tag> tags) {
        String propertyName = getPropertyName(metadata.getName());
        Context context = getTimerContextFromExchange(exchange, propertyName);
        if (context == null) {
            Timer timer = getOrRegisterMetric(metadata, tags);
            exchange.setProperty(propertyName, timer.time());
        } else {
            LOG.warn("Timer '{}' is already running", metadata.getName());
        }
    }

    private void handleStop(Exchange exchange, Metadata metadata, List<Tag> tags) {
        if (getTimerContextFromExchange(exchange, getPropertyName(metadata.getName())) != null) {
            doProcess(exchange, getEndpoint(), getOrRegisterMetric(metadata, tags));
        }
    }

    private String getPropertyName(String metricName) {
        return "timer:" + metricName;
    }

    private Context getTimerContextFromExchange(Exchange exchange, String propertyName) {
        return exchange.getProperty(propertyName, Context.class);
    }
}
