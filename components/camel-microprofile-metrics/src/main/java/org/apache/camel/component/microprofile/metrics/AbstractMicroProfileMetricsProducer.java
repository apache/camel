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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_TAGS;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_TYPE;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_METRIC_UNIT;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_PREFIX;

public abstract class AbstractMicroProfileMetricsProducer<T extends Metric> extends DefaultProducer {

    private static final String HEADER_PATTERN = HEADER_PREFIX + "*";

    public AbstractMicroProfileMetricsProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MicroProfileMetricsEndpoint getEndpoint() {
        return (MicroProfileMetricsEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        MicroProfileMetricsEndpoint endpoint = getEndpoint();
        Message in = exchange.getIn();

        String metricName = getStringHeader(in, HEADER_METRIC_NAME, endpoint.getMetricName());
        String metricDescription = getStringHeader(in, HEADER_METRIC_DESCRIPTION, endpoint.getDescription());
        String metricDisplayName = getStringHeader(in, HEADER_METRIC_DISPLAY_NAME, endpoint.getDisplayName());
        String metricUnit = getStringHeader(in, HEADER_METRIC_UNIT, endpoint.getMetricUnit());
        MetricType metricType = exchange.getIn().getHeader(HEADER_METRIC_TYPE,  endpoint.getMetricType(), MetricType.class);

        List<Tag> allTags = new ArrayList<>();
        allTags.addAll(MicroProfileMetricsHelper.getMetricsTag(endpoint.getTags()));
        String headerTags = getStringHeader(in, HEADER_METRIC_TAGS, "");
        allTags.addAll(MicroProfileMetricsHelper.getMetricsTag(headerTags));

        List<Tag> finalTags = allTags.stream()
            .map(tag -> MicroProfileMetricsHelper.parseTag(tag.getTagName() + "=" + tag.getTagValue()))
            .collect(Collectors.toList());
        finalTags.add(MicroProfileMetricsHelper.parseTag(CAMEL_CONTEXT_TAG + "=" + getEndpoint().getCamelContext().getName()));

        MetadataBuilder builder = new MetadataBuilder()
            .withName(metricName)
            .withType(metricType);

        if (metricDescription != null) {
            builder.withDescription(metricDescription);
        }

        if (metricDisplayName != null) {
            builder.withDisplayName(metricDisplayName);
        }

        if (metricUnit != null) {
            builder.withUnit(metricUnit);
        }

        try {
            doProcess(exchange, builder.build(), finalTags);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            clearMetricsHeaders(in);
        }
    }

    protected void doProcess(Exchange exchange, Metadata metadata, List<Tag> tags) {
        doProcess(exchange, getEndpoint(), getOrRegisterMetric(metadata, tags));
    }

    protected T getOrRegisterMetric(Metadata metadata, List<Tag> tags) {
        MetricRegistry metricRegistry = getEndpoint().getMetricRegistry();
        return registerMetric(metadata, tags).apply(metricRegistry);
    }

    protected String getStringHeader(Message in, String header, String defaultValue) {
        String headerValue = in.getHeader(header, String.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    protected Long getLongHeader(Message in, String header, Long defaultValue) {
        return in.getHeader(header, defaultValue, Long.class);
    }

    protected Boolean getBooleanHeader(Message in, String header, Boolean defaultValue) {
        Boolean headerValue = in.getHeader(header, Boolean.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    protected Number getNumericHeader(Message in, String header, Number defaultValue) {
        Number headerValue = in.getHeader(header, Number.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    protected void clearMetricsHeaders(Message in) {
        in.removeHeaders(HEADER_PATTERN);
    }

    protected abstract void doProcess(Exchange exchange, MicroProfileMetricsEndpoint endpoint, T meter);

    protected abstract Function<MetricRegistry, T> registerMetric(Metadata metadata, List<Tag> tags);
}
