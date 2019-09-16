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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

@UriEndpoint(firstVersion = "3.0.0", scheme = "microprofile-metrics", title = "MicroProfile Metrics", syntax = "microprofile-metrics:metricType:metricName", producerOnly = true, label = "monitoring")
public class MicroProfileMetricsEndpoint extends DefaultEndpoint {

    protected final MetricRegistry metricRegistry;

    @UriPath(description = "Metric type")
    @Metadata(required = true, enums = "concurrent gauge,counter,histogram,meter,timer")
    private final MetricType metricType;
    @UriPath(description = "Metric name")
    @Metadata(required = true)
    private final String metricName;
    @UriParam(description = "Comma delimited list of tags associated with the metric in the format tagName=tagValue")
    private String tags;
    @UriParam(description = "Action to use when using the timer type")
    private String action;
    @UriParam(description = "Mark value to set when using the meter type")
    private Long mark;
    @UriParam(description = "Value to set when using the histogram type")
    private Long value;
    @UriParam(description = "Increment value when using the counter type")
    private Long counterIncrement;
    @UriParam(description = "Increment metric value when using the concurrent gauge type")
    private Boolean gaugeIncrement = false;
    @UriParam(description = "Decrement metric value when using concurrent gauge type")
    private Boolean gaugeDecrement = false;
    @UriParam(description = "Decrement metric value when using concurrent gauge type")
    private Number gaugeValue;
    @UriParam(description = "Metric description")
    private String description;
    @UriParam(description = "Metric display name")
    private String displayName;
    @UriParam(description = "Metric unit. See org.eclipse.microprofile.metrics.MetricUnits")
    private String metricUnit;

    public MicroProfileMetricsEndpoint(String uri, Component component, MetricRegistry metricRegistry, MetricType metricType, String metricsName) {
        super(uri, component);
        this.metricRegistry = metricRegistry;
        this.metricType = metricType;
        this.metricName = metricsName;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (metricType.equals(MetricType.COUNTER)) {
            return new MicroProfileMetricsCounterProducer(this);
        } else if (metricType.equals(MetricType.CONCURRENT_GAUGE)) {
            return new MicroProfileMetricsConcurrentGaugeProducer(this);
        } else if (metricType.equals(MetricType.GAUGE)) {
            return new MicroProfileMetricsGaugeProducer(this);
        } else if (metricType.equals(MetricType.HISTOGRAM)) {
            return new MicroProfileMetricsHistogramProducer(this);
        } else if (metricType.equals(MetricType.METERED)) {
            return new MicroProfileMetricsMeteredProducer(this);
        } else if (metricType.equals(MetricType.TIMER)) {
            return new MicroProfileMetricsTimerProducer(this);
        } else {
            throw new IllegalStateException("Unknown metric type " + metricType);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The microprofile-metrics endpoint does not support consumers");
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getAction() {
        return action;
    }

    /**
     * The action to use when using the Timer metric type
     */
    public void setAction(String action) {
        this.action = action;
    }

    public Long getMark() {
        return mark;
    }

    /**
     * The mark value to set when using the Meter metric type
     */
    public void setMark(Long mark) {
        this.mark = mark;
    }

    public Long getValue() {
        return value;
    }

    /**
     * The value to set when using the Histogram metric type
     */
    public void setValue(Long value) {
        this.value = value;
    }

    public Long getCounterIncrement() {
        return counterIncrement;
    }

    /**
     * The amount to increment to use when using the Counter metric type
     */
    public void setCounterIncrement(Long counterIncrement) {
        this.counterIncrement = counterIncrement;
    }

    public Boolean getGaugeIncrement() {
        return gaugeIncrement;
    }

    /**
     * Increments a gauge value when using the ConcurrentGauge metric type
     */
    public void setGaugeIncrement(Boolean gaugeIncrement) {
        this.gaugeIncrement = gaugeIncrement;
    }

    public Boolean getGaugeDecrement() {
        return gaugeDecrement;
    }

    /**
     * Decrements a gauge value when using the ConcurrentGauge metric type
     */
    public void setGaugeDecrement(Boolean gaugeDecrement) {
        this.gaugeDecrement = gaugeDecrement;
    }

    public Number getGaugeValue() {
        return gaugeValue;
    }

    /**
     * Sets the gauge value when using the Gauge metric type
     */
    public void setGaugeValue(Number gaugeValue) {
        this.gaugeValue = gaugeValue;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sets a description within the metric metadata
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets a display name within the metric metadata
     */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMetricUnit() {
        return metricUnit;
    }

    /**
     * Sets a metric unit within the metric metadata
     */
    public void setMetricUnit(String metricUnit) {
        this.metricUnit = metricUnit;
    }
}
