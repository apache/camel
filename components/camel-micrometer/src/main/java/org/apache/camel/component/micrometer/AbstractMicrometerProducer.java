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

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;


public abstract class AbstractMicrometerProducer<T extends Meter> extends DefaultProducer {

    public static final String HEADER_PATTERN = MicrometerConstants.HEADER_PREFIX + "*";

    public AbstractMicrometerProducer(MicrometerEndpoint endpoint) {
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
        String finalMetricsName = getMetricsName(in, defaultMetricsName);
        Iterable<Tag> defaultTags = getEndpoint().getTags();
        Iterable<Tag> finalTags = Tags.concat(defaultTags, getTags(in)).stream()
                .map(tag -> Tag.of(
                        simple(exchange, tag.getKey(), String.class),
                        simple(exchange, tag.getValue(), String.class)))
                .reduce(Tags.empty(), Tags::and, Tags::and)
                .and(Tags.of(CAMEL_CONTEXT_TAG, getEndpoint().getCamelContext().getName()));
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
        doProcess(exchange, getEndpoint(), getOrRegisterMeter(name, tags));
    }

    protected T getOrRegisterMeter(String name, Iterable<Tag> tags) {
        MeterRegistry registry = getEndpoint().getRegistry();
        return registrar(name, tags).apply(registry);
    }

    protected abstract void doProcess(Exchange exchange, MicrometerEndpoint endpoint, T meter);

    protected <T> T simple(Exchange exchange, String expression, Class<T> clazz) {
        if (expression != null) {
            Expression simple = SimpleLanguage.simple(expression);
            if (simple != null) {
                return simple.evaluate(exchange, clazz);
            }
        }
        return getEndpoint().getCamelContext().getTypeConverter().convertTo(clazz, expression);
    }

    public String getMetricsName(Message in, String defaultValue) {
        return getStringHeader(in, MicrometerConstants.HEADER_METRIC_NAME, defaultValue);
    }

    public Iterable<Tag> getTags(Message in) {
        return getTagHeader(in, MicrometerConstants.HEADER_METRIC_TAGS, Tags.empty());
    }

    public String getStringHeader(Message in, String header, String defaultValue) {
        String headerValue = in.getHeader(header, String.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    public Double getDoubleHeader(Message in, String header, Double defaultValue) {
        return in.getHeader(header, defaultValue, Double.class);
    }

    public Iterable<Tag> getTagHeader(Message in, String header, Iterable<Tag> defaultTags) {
        return (Iterable<Tag>) in.getHeader(header, defaultTags, Iterable.class);
    }

    protected boolean clearMetricsHeaders(Message in) {
        return in.removeHeaders(HEADER_PATTERN);
    }
}
