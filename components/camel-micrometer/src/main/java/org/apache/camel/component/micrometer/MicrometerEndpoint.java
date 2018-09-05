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
package org.apache.camel.component.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * To collect various metrics directly from Camel routes using the Micrometer library.
 */
@UriEndpoint(firstVersion = "2.22.0", scheme = "micrometer", title = "Micrometer", syntax = "micrometer:metricsType:metricsName", producerOnly = true, label = "monitoring")
public class MicrometerEndpoint extends DefaultEndpoint {

    protected MeterRegistry registry;

    @UriPath(description = "Type of metrics")
    @Metadata(required = "true")
    protected final Meter.Type metricsType;
    @UriPath(description = "Name of metrics")
    @Metadata(required = "true")
    protected final String metricsName;
    @UriPath(description = "Tags of metrics")
    protected final Iterable<Tag> tags;
    @UriParam(description = "Action expression when using timer type")
    private String action;
    @UriParam(description = "Value expression when using histogram type")
    private String value;
    @UriParam(description = "Increment value expression when using counter type")
    private String increment;
    @UriParam(description = "Decrement value expression when using counter type")
    private String decrement;

    public MicrometerEndpoint(String uri, Component component, MeterRegistry registry, Meter.Type metricsType, String metricsName, Iterable<Tag> tags) {
        super(uri, component);
        this.registry = registry;
        this.metricsType = metricsType;
        this.metricsName = metricsName;
        this.tags = tags;
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new RuntimeCamelException("Cannot consume from " + getClass().getSimpleName() + ": " + getEndpointUri());
    }

    @Override
    public Producer createProducer() {
        if (metricsType == Meter.Type.COUNTER) {
            return new CounterProducer(this);
        } else if (metricsType == Meter.Type.DISTRIBUTION_SUMMARY) {
            return new DistributionSummaryProducer(this);
        } else if (metricsType == Meter.Type.TIMER) {
            return new TimerProducer(this);
        } else {
            throw new IllegalArgumentException("Metrics type " + metricsType + " is not supported");
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public String getMetricsName() {
        return metricsName;
    }

    public Iterable<Tag> getTags() {
        return tags;
    }

    public Meter.Type getMetricsType() {
        return metricsType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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

    void setRegistry(MeterRegistry registry) {
        this.registry = registry;
    }
}
