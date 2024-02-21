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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_DESCRIPTION;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_TAGS;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_PREFIX;

public abstract class AbstractMicrometerProducer<T extends Meter> extends DefaultProducer {

    private static final String HEADER_PATTERN = HEADER_PREFIX + "*";

    protected AbstractMicrometerProducer(MicrometerEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MicrometerEndpoint getEndpoint() {
        return (MicrometerEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) {
        Message in = exchange.getIn();
        String defaultMetricsName = simple(exchange, getEndpoint().getMetricsName(), String.class);
        String finalMetricsName = getStringHeader(in, HEADER_METRIC_NAME, defaultMetricsName);
        String defaultMetricsDescription = simple(exchange, getEndpoint().getMetricsDescription(), String.class);
        String finalMetricsDescription = getStringHeader(in, HEADER_METRIC_DESCRIPTION, defaultMetricsDescription);
        Iterable<Tag> defaultTags = getEndpoint().getTags();
        Iterable<Tag> headerTags = getTagHeader(in, HEADER_METRIC_TAGS, Tags.empty());
        Iterable<Tag> finalTags = Tags.concat(defaultTags, headerTags).stream()
                .map(tag -> Tag.of(
                        simple(exchange, tag.getKey(), String.class),
                        simple(exchange, tag.getValue(), String.class)))
                .reduce(Tags.empty(), Tags::and, Tags::and)
                .and(Tags.of(
                        CAMEL_CONTEXT_TAG, getEndpoint().getCamelContext().getName()));
        try {
            doProcess(exchange, finalMetricsName, finalMetricsDescription, finalTags);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            clearMetricsHeaders(in);
        }
    }

    protected abstract Function<MeterRegistry, T> registrar(String name, String description, Iterable<Tag> tags);

    protected void doProcess(Exchange exchange, String name, String description, Iterable<Tag> tags) {
        doProcess(exchange, getEndpoint(), getOrRegisterMeter(name, description, tags));
    }

    protected T getOrRegisterMeter(String name, String description, Iterable<Tag> tags) {
        MeterRegistry registry = getEndpoint().getRegistry();
        return registrar(name, description, tags).apply(registry);
    }

    protected abstract void doProcess(Exchange exchange, MicrometerEndpoint endpoint, T meter);

    protected <C> C simple(Exchange exchange, String expression, Class<C> clazz) {
        if (expression != null) {
            Language language = exchange.getContext().resolveLanguage("simple");
            Expression simple = language.createExpression(expression);
            if (simple != null) {
                return simple.evaluate(exchange, clazz);
            }
        }
        return getEndpoint().getCamelContext().getTypeConverter().convertTo(clazz, expression);
    }

    protected String getStringHeader(Message in, String header, String defaultValue) {
        String headerValue = in.getHeader(header, String.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    protected Double getDoubleHeader(Message in, String header, Double defaultValue) {
        return in.getHeader(header, defaultValue, Double.class);
    }

    @SuppressWarnings("unchecked")
    protected Iterable<Tag> getTagHeader(Message in, String header, Iterable<Tag> defaultTags) {
        return in.getHeader(header, defaultTags, Iterable.class);
    }

    protected boolean clearMetricsHeaders(Message in) {
        return in.removeHeaders(HEADER_PATTERN);
    }
}
