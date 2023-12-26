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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Collect various metrics directly from Camel routes using the DropWizard metrics library.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "metrics", title = "Metrics", syntax = "metrics:metricsType:metricsName",
             remote = false, producerOnly = true, category = { Category.MONITORING }, headersClass = MetricsConstants.class)
public class MetricsEndpoint extends DefaultEndpoint {

    protected final MetricRegistry registry;

    @UriPath(description = "Type of metrics")
    @Metadata(required = true)
    protected final MetricsType metricsType;
    @UriPath(description = "Name of metrics")
    @Metadata(required = true)
    protected final String metricsName;
    @UriParam(description = "Action when using timer type")
    private MetricsTimerAction action;
    @UriParam(description = "Mark when using meter type")
    private Long mark;
    @UriParam(description = "Value value when using histogram type")
    private Long value;
    @UriParam(description = "Increment value when using counter type")
    private Long increment;
    @UriParam(description = "Decrement value when using counter type")
    private Long decrement;
    @UriParam(description = "Subject value when using gauge type")
    private Object subject;

    public MetricsEndpoint(String uri, Component component, MetricRegistry registry, MetricsType metricsType,
                           String metricsName) {
        super(uri, component);
        this.registry = registry;
        this.metricsType = metricsType;
        this.metricsName = metricsName;
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
