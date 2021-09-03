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

import java.util.List;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.microprofile.metrics.gauge.SimpleGauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_GAUGE_VALUE;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper.findMetric;

public class MicroProfileMetricsGaugeProducer extends AbstractMicroProfileMetricsProducer<SimpleGauge> {

    public MicroProfileMetricsGaugeProducer(MicroProfileMetricsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, MicroProfileMetricsEndpoint endpoint, SimpleGauge gauge) {
        Message in = exchange.getIn();
        Number finalGaugeValue = getNumericHeader(in, HEADER_GAUGE_VALUE, endpoint.getGaugeValue());

        if (finalGaugeValue != null) {
            gauge.setValue(finalGaugeValue);
        }
    }

    @Override
    protected Function<MetricRegistry, SimpleGauge> registerMetric(Metadata metadata, List<Tag> tags) {
        return metricRegistry -> {
            Tag[] tagArray = tags.toArray(new Tag[0]);
            SimpleGauge existing = findMetric(metricRegistry, metadata.getName(), SimpleGauge.class, tags);
            if (existing == null) {
                return metricRegistry.register(metadata, new SimpleGauge(), tagArray);
            }
            return existing;
        };
    }
}
