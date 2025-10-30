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
package org.apache.camel.opentelemetry2.component;

import java.util.HashMap;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.opentelemetry2.OpenTelemetryConstants.CAMEL_CONTEXT_ATTRIBUTE;
import static org.apache.camel.opentelemetry2.OpenTelemetryConstants.HEADER_METRIC_ATTRIBUTES;
import static org.apache.camel.opentelemetry2.OpenTelemetryConstants.HEADER_METRIC_DESCRIPTION;
import static org.apache.camel.opentelemetry2.OpenTelemetryConstants.HEADER_METRIC_NAME;
import static org.apache.camel.opentelemetry2.OpenTelemetryConstants.HEADER_PREFIX;

abstract public class AbstractOpenTelemetryProducer<T> extends DefaultProducer {

    private static final String HEADER_PATTERN = HEADER_PREFIX + "*";
    private Attributes attributes;

    protected AbstractOpenTelemetryProducer(OpenTelemetryEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        this.attributes = getEndpoint().createAttributes();
    }

    @Override
    public OpenTelemetryEndpoint getEndpoint() {
        return (OpenTelemetryEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) {
        Message in = exchange.getIn();
        String defaultMetricsName = simple(exchange, getEndpoint().getMetricName(), String.class);
        String finalMetricsName = getStringHeader(in, HEADER_METRIC_NAME, defaultMetricsName);
        String defaultMetricsDescription = simple(exchange, getEndpoint().getMetricsDescription(), String.class);
        String finalMetricsDescription = getStringHeader(in, HEADER_METRIC_DESCRIPTION, defaultMetricsDescription);

        Map<AttributeKey<?>, Object> map = new HashMap<>(attributes.asMap());
        map.putAll(getAttributesHeader(in, HEADER_METRIC_ATTRIBUTES, Attributes.empty()).asMap());

        AttributesBuilder ab = Attributes.builder();
        for (Map.Entry<AttributeKey<?>, Object> entry : map.entrySet()) {
            ab.put(simple(exchange, entry.getKey().toString(), String.class),
                    simple(exchange, entry.getValue().toString(), String.class));
        }
        ab.put(CAMEL_CONTEXT_ATTRIBUTE, getEndpoint().getCamelContext().getName());

        try {
            doProcess(exchange, finalMetricsName, finalMetricsDescription, ab.build());
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            clearMetricsHeaders(in);
        }
    }

    protected void doProcess(Exchange exchange, String metricsName, String description, Attributes attributes) {
        doProcess(exchange, metricsName, getInstrument(metricsName, description), attributes);
    }

    protected abstract T getInstrument(String name, String description);

    protected abstract void doProcess(
            Exchange exchange, String metricsName, T meter, Attributes attributes);

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

    protected Long getLongHeader(Message in, String header, Long defaultValue) {
        return in.getHeader(header, defaultValue, Long.class);
    }

    protected Attributes getAttributesHeader(Message in, String header, Attributes defaultAttributes) {
        return in.getHeader(header, defaultAttributes, Attributes.class);
    }

    protected boolean clearMetricsHeaders(Message in) {
        return in.removeHeaders(HEADER_PATTERN);
    }
}
