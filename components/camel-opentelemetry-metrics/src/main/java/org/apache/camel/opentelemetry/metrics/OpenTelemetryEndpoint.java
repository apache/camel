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
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.17.0", scheme = "opentelemetry-metrics", title = "OpenTelemetry Metrics",
             remote = false, syntax = "opentelemetry-metrics:metricType:metricName", producerOnly = true,
             category = { Category.MONITORING },
             headersClass = OpenTelemetryConstants.class)
public class OpenTelemetryEndpoint extends DefaultEndpoint {

    protected Meter meter;

    @UriPath(description = "Type of metrics", enums = "counter,summary,timer")
    @Metadata(required = true)
    protected final InstrumentType metricType;
    @UriPath(description = "Name of metric")
    @Metadata(required = true)
    protected final String metricName;
    @UriParam(description = "Description of metrics")
    protected String metricsDescription;
    @UriParam(description = "metric attributes", multiValue = true, prefix = "attributes.")
    protected Map<String, String> attributes;
    @UriParam(description = "Action expression when using timer type", enums = "start,stop")
    private String action;
    @UriParam(description = "The time unit when using the timer type", defaultValue = "MILLISECONDS")
    private TimeUnit unit = TimeUnit.MILLISECONDS;
    @UriParam(description = "Value expression when using histogram type")
    private String value;
    @UriParam(description = "Increment value expression when using counter type")
    private String increment;
    @UriParam(description = "Decrement value expression when using counter type")
    private String decrement;

    public OpenTelemetryEndpoint(String uri, Component component, Meter meter, InstrumentType metricType,
                                 String metricName) {
        super(uri, component);
        this.meter = meter;
        this.metricType = metricType;
        this.metricName = metricName;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public Producer createProducer() {
        if (metricType == InstrumentType.COUNTER) {
            return new CounterProducer(this);
        } else if (metricType == InstrumentType.DISTRIBUTION_SUMMARY) {
            return new DistributionSummaryProducer(this);
        } else if (metricType == InstrumentType.TIMER) {
            return new TimerProducer(this);
        } else {
            throw new IllegalArgumentException("Metrics type " + metricType + " is not supported");
        }
    }

    Attributes createAttributes() {
        if (attributes != null && !attributes.isEmpty()) {
            AttributesBuilder ab = Attributes.builder();
            attributes.forEach(ab::put);
            return ab.build();
        }
        return Attributes.empty();
    }

    public Meter getMeter() {
        return meter;
    }

    public String getMetricName() {
        return metricName;
    }

    public InstrumentType getMetricType() {
        return metricType;
    }

    public String getMetricsDescription() {
        return metricsDescription;
    }

    public void setMetricsDescription(String metricsDescription) {
        this.metricsDescription = metricsDescription;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public void setUnit(TimeUnit timeUnit) {
        this.unit = timeUnit;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getIncrement() {
        return increment;
    }

    public void setIncrement(String increment) {
        this.increment = increment;
    }

    public String getDecrement() {
        return decrement;
    }

    public void setDecrement(String decrement) {
        this.decrement = decrement;
    }
}
