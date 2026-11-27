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
package org.apache.camel.component.metrics;

import com.codahale.metrics.MetricRegistry;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Update counters, gauges, histograms, meters and timers of a Dropwizard Metrics registry from Camel routes.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "metrics", title = "Metrics", syntax = "metrics:metricsType:metricsName",
             remote = false, internal = true, producerOnly = true, category = { Category.MONITORING },
             headersClass = MetricsConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: the values come from the options or the CamelMetrics headers,"
                                                + " such as CamelMetricsCounterIncrement or CamelMetricsGaugeSubject"),
           producerOut = @BodyType(value = Carrier.UNCHANGED, description = "Unchanged; the CamelMetrics headers are removed"))
public class MetricsEndpoint extends DefaultEndpoint {

    protected final MetricRegistry registry;

    @UriPath(description = "The type of the metric. If the URI gives only a name, as in metrics:myName, the type is meter.")
    @Metadata(required = true)
    protected final MetricsType metricsType;
    @UriPath(description = "The name of the metric in the MetricRegistry. A CamelMetricsName header that is not empty"
                           + " replaces this option.")
    @Metadata(required = true)
    protected final String metricsName;
    @UriParam(description = "For the timer type: start starts a timer and stores it on the exchange, and stop stops the timer"
                            + " that start stored on the same exchange and records the elapsed time. If no action is set,"
                            + " the producer logs a warning and does nothing. The CamelMetricsTimerAction header replaces"
                            + " this option.")
    private MetricsTimerAction action;
    @UriParam(description = "For the meter type: the number of events to mark on the meter. If not set, the meter marks one"
                            + " event. The CamelMetricsMeterMark header replaces this option.")
    private Long mark;
    @UriParam(description = "For the histogram type: the value to add to the histogram. If not set, the producer logs a"
                            + " warning and does not update the histogram. The CamelMetricsHistogramValue header replaces"
                            + " this option.")
    private Long value;
    @UriParam(description = "For the counter type: the amount to add to the counter. It wins over decrement, and if neither is"
                            + " set the counter is incremented by one. The CamelMetricsCounterIncrement header replaces this"
                            + " option.")
    private Long increment;
    @UriParam(description = "For the counter type: the amount to subtract from the counter, used only when no increment is set"
                            + " by the option or the CamelMetricsCounterIncrement header. The CamelMetricsCounterDecrement"
                            + " header replaces this option.")
    private Long decrement;
    @UriParam(description = "For the gauge type: the object that the gauge returns as its value, for example a bean reference."
                            + " The producer registers the gauge with it when the producer starts, and the"
                            + " CamelMetricsGaugeSubject header replaces the value of the gauge.")
    private Object subject;

    public MetricsEndpoint(String uri, Component component, MetricRegistry registry, MetricsType metricsType,
                           String metricsName) {
        super(uri, component);
        this.registry = registry;
        this.metricsType = metricsType;
        this.metricsName = metricsName;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume from " + getClass().getSimpleName() + ": " + getEndpointUri());
    }

    @Override
    public Producer createProducer() throws Exception {
        if (metricsType == MetricsType.COUNTER) {
            return new CounterProducer(this);
        } else if (metricsType == MetricsType.HISTOGRAM) {
            return new HistogramProducer(this);
        } else if (metricsType == MetricsType.METER) {
            return new MeterProducer(this);
        } else if (metricsType == MetricsType.TIMER) {
            return new TimerProducer(this);
        } else if (metricsType == MetricsType.GAUGE) {
            return new GaugeProducer(this);
        } else {
            throw new IllegalArgumentException("Metrics type " + metricsType + " is not supported");
        }
    }

    public MetricRegistry getRegistry() {
        return registry;
    }

    public String getMetricsName() {
        return metricsName;
    }

    public MetricsType getMetricsType() {
        return metricsType;
    }

    public MetricsTimerAction getAction() {
        return action;
    }

    public void setAction(MetricsTimerAction action) {
        this.action = action;
    }

    public Long getMark() {
        return mark;
    }

    public void setMark(Long mark) {
        this.mark = mark;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public Long getIncrement() {
        return increment;
    }

    public void setIncrement(Long increment) {
        this.increment = increment;
    }

    public Long getDecrement() {
        return decrement;
    }

    public void setDecrement(Long decrement) {
        this.decrement = decrement;
    }

    public Object getSubject() {
        return subject;
    }

    public void setSubject(Object subject) {
        this.subject = subject;
    }
}
