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
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public abstract class AbstractMetricsProducer extends DefaultProducer {

    public static final String HEADER_PATTERN = MetricsConstants.HEADER_PREFIX + "*";

    protected AbstractMetricsProducer(MetricsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MetricsEndpoint getEndpoint() {
        return (MetricsEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        String defaultMetricsName = getEndpoint().getMetricsName();
        String finalMetricsName = getMetricsName(in, defaultMetricsName);
        MetricRegistry registry = getEndpoint().getRegistry();

        try {
            doProcess(exchange, getEndpoint(), registry, finalMetricsName);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            clearMetricsHeaders(in);
        }
    }

    protected abstract void doProcess(Exchange exchange, MetricsEndpoint endpoint, MetricRegistry registry, String metricsName)
            throws Exception;

    public String getMetricsName(Message in, String defaultValue) {
        return getStringHeader(in, MetricsConstants.HEADER_METRIC_NAME, defaultValue);
    }

    public String getStringHeader(Message in, String header, String defaultValue) {
        String headerValue = in.getHeader(header, String.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    public Long getLongHeader(Message in, String header, Long defaultValue) {
        return in.getHeader(header, defaultValue, Long.class);
    }

    protected boolean clearMetricsHeaders(Message in) {
        return in.removeHeaders(HEADER_PATTERN);
    }
}
