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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Exchange;

import static org.apache.camel.opentelemetry2.OpenTelemetryConstants.HEADER_HISTOGRAM_VALUE;

public class DistributionSummaryProducer extends AbstractOpenTelemetryProducer<LongHistogram> {

    private final Map<String, LongHistogram> distributionSummaries = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public DistributionSummaryProducer(OpenTelemetryEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected LongHistogram getInstrument(String name, String description) {
        LongHistogram summary = distributionSummaries.get(name);
        if (summary == null) {
            synchronized (lock) {
                summary = distributionSummaries.get(name);
                if (summary == null) {
                    Meter meter = getEndpoint().getMeter();
                    LongHistogramBuilder builder = meter.histogramBuilder(name).ofLongs();
                    if (description != null) {
                        builder.setDescription(description);
                    }
                    summary = builder.build();
                    distributionSummaries.put(name, summary);
                }
            }
        }
        return summary;
    }

    @Override
    protected void doProcess(
            Exchange exchange, String metricsName, LongHistogram summary,
            Attributes attributes) {
        Long value = simple(exchange, getEndpoint().getValue(), Long.class);
        Long finalValue = getLongHeader(exchange.getIn(), HEADER_HISTOGRAM_VALUE, value);

        if (finalValue != null) {
            summary.record(finalValue, attributes);
        }
    }
}
