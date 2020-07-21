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

import org.apache.camel.Exchange;
import org.apache.camel.component.microprofile.metrics.gauge.AtomicIntegerGauge;
import org.apache.camel.support.ExchangeHelper;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_COMPLETED_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_COMPLETED_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_COMPLETED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILED_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILED_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_INFLIGHT_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_INFLIGHT_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_INFLIGHT_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_TOTAL_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_TOTAL_DISPLAY_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_TOTAL_METRIC_NAME;

public class MicroProfileMetricsExchangeRecorder {

    private Counter exchangesCompleted;
    private Counter exchangesFailed;
    private Counter exchangesTotal;
    private AtomicIntegerGauge exchangesInflight;
    private Counter externalRedeliveries;
    private Counter failuresHandled;

    public MicroProfileMetricsExchangeRecorder(MetricRegistry metricRegistry, String metricName, Tag... tags) {
        configureMetrics(metricRegistry, metricName, tags);
    }

    public void recordExchangeBegin() {
        exchangesInflight.increment();
    }

    public void recordExchangeComplete(Exchange exchange) {
        exchangesTotal.inc();
        exchangesInflight.decrement();

        if (!exchange.isFailed()) {
            exchangesCompleted.inc();

            if (ExchangeHelper.isFailureHandled(exchange)) {
                failuresHandled.inc();
            }

            if (exchange.isExternalRedelivered()) {
                externalRedeliveries.inc();
            }
        } else {
            exchangesFailed.inc();
        }
    }

    protected void configureMetrics(MetricRegistry metricRegistry, String metricName, Tag... tags) {
        Metadata exchangesCompletedMetadata = new MetadataBuilder()
            .withName(metricName + EXCHANGES_COMPLETED_METRIC_NAME)
            .withDisplayName(EXCHANGES_COMPLETED_DISPLAY_NAME)
            .withDescription(EXCHANGES_COMPLETED_DESCRIPTION)
            .withType(MetricType.COUNTER)
            .build();
        this.exchangesCompleted = metricRegistry.counter(exchangesCompletedMetadata, tags);

        Metadata exchangesFailedMetadata = new MetadataBuilder()
            .withName(metricName + EXCHANGES_FAILED_METRIC_NAME)
            .withDisplayName(EXCHANGES_FAILED_DISPLAY_NAME)
            .withDescription(EXCHANGES_FAILED_DESCRIPTION)
            .withType(MetricType.COUNTER)
            .build();
        this.exchangesFailed = metricRegistry.counter(exchangesFailedMetadata, tags);

        Metadata exchangesTotalMetadata = new MetadataBuilder()
            .withName(metricName + EXCHANGES_TOTAL_METRIC_NAME)
            .withDisplayName(EXCHANGES_TOTAL_DISPLAY_NAME)
            .withDescription(EXCHANGES_TOTAL_DESCRIPTION)
            .withType(MetricType.COUNTER)
            .build();
        this.exchangesTotal = metricRegistry.counter(exchangesTotalMetadata, tags);

        Metadata exchangesInflightMetadata = new MetadataBuilder()
            .withName(metricName + EXCHANGES_INFLIGHT_METRIC_NAME)
            .withDisplayName(EXCHANGES_INFLIGHT_DISPLAY_NAME)
            .withDescription(EXCHANGES_INFLIGHT_DESCRIPTION)
            .withType(MetricType.GAUGE)
            .build();
        this.exchangesInflight = metricRegistry.register(exchangesInflightMetadata, new AtomicIntegerGauge(), tags);

        Metadata externalRedeliveriesMetadata = new MetadataBuilder()
            .withName(metricName + EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME)
            .withDisplayName(EXCHANGES_EXTERNAL_REDELIVERIES_DISPLAY_NAME)
            .withDescription(EXCHANGES_EXTERNAL_REDELIVERIES_DESCRIPTION)
            .withType(MetricType.COUNTER)
            .build();
        this.externalRedeliveries = metricRegistry.counter(externalRedeliveriesMetadata, tags);

        Metadata failuresHandledMetadata = new MetadataBuilder()
            .withName(metricName + EXCHANGES_FAILURES_HANDLED_METRIC_NAME)
            .withDisplayName(EXCHANGES_FAILURES_HANDLED_DISPLAY_NAME)
            .withDescription(EXCHANGES_FAILURES_HANDLED_DESCRIPTION)
            .withType(MetricType.COUNTER)
            .build();
        this.failuresHandled = metricRegistry.counter(failuresHandledMetadata, tags);
    }
}
