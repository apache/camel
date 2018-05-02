/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import io.micrometer.core.instrument.search.Search;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

public abstract class AbstractMicrometerProducer<T extends Meter> extends DefaultProducer {

    public static final String HEADER_PATTERN = MicrometerConstants.HEADER_PREFIX + "*";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractMicrometerProducer.class);

    private String prefix = MicrometerConstants.HEADER_PREFIX;

    public AbstractMicrometerProducer(MicrometerEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MicrometerEndpoint getEndpoint() {
        return (MicrometerEndpoint) super.getEndpoint();
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        String defaultMetricsName = getEndpoint().getMetricsName();
        String finalMetricsName = getMetricsName(in, defaultMetricsName);
        Iterable<Tag> defaultTags = getEndpoint().getTags();
        Iterable<Tag> finalTags = getTags(in, defaultTags);
        try {
            doProcess(exchange, finalMetricsName, finalTags);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            clearMetricsHeaders(in);
        }
    }

    protected abstract Function<MeterRegistry, T> registrar(String name, Iterable<Tag> tags);

    protected void doProcess(Exchange exchange, String name, Iterable<Tag> tags) {
        T meter = getMeter(name, tags);
        try {
            doProcess(exchange, getEndpoint(), meter);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            clearMetricsHeaders(exchange.getIn());
        }
    }

    protected T getMeter(String name, Iterable<Tag> tags) {
        MeterRegistry registry = getEndpoint().getRegistry();
        return registrar(name, tags).apply(registry);
    }

    protected abstract void doProcess(Exchange exchange, MicrometerEndpoint endpoint, T meter);

    public String getMetricsName(Message in, String defaultValue) {
        return getStringHeader(in, MicrometerConstants.HEADER_METRIC_NAME, defaultValue);
    }

    public Iterable<Tag> getTags(Message in, Iterable<Tag> defaultTags) {
        return getTagHeader(in, MicrometerConstants.HEADER_METRIC_TAGS, defaultTags);
    }

    public String getStringHeader(Message in, String header, String defaultValue) {
        String headerValue = in.getHeader(header, String.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    public Long getLongHeader(Message in, String header, Long defaultValue) {
        return in.getHeader(header, defaultValue, Long.class);
    }

    public Double getDoubleHeader(Message in, String header, Double defaultValue) {
        return in.getHeader(header, defaultValue, Double.class);
    }

    public Iterable<Tag> getTagHeader(Message in, String header, Iterable<Tag> defaultTags) {
        return in.getHeader(header, defaultTags, Iterable.class);
    }

    protected boolean clearMetricsHeaders(Message in) {
        return in.removeHeaders(HEADER_PATTERN);
    }
}
