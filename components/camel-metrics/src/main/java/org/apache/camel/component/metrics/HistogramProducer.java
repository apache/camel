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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_HISTOGRAM_VALUE;

public class HistogramProducer extends AbstractMetricsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(HistogramProducer.class);

    public HistogramProducer(MetricsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, MetricsEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
        Message in = exchange.getIn();
        Histogram histogram = registry.histogram(metricsName);
        Long value = endpoint.getValue();
        Long finalValue = getLongHeader(in, HEADER_HISTOGRAM_VALUE, value);
        if (finalValue != null) {
            histogram.update(finalValue);
        } else {
            LOG.warn("Cannot update histogram \"{}\" with null value", metricsName);
        }
    }
}
