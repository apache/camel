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
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.microprofile.metrics.gauge.AtomicIntegerGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_METRIC_PREFIX;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_COMPLETED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_INFLIGHT_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_TOTAL_METRIC_NAME;

public class MicroProfileMetricsExchangeRecorderTest extends MicroProfileMetricsTestSupport {

    private static final Tag[] TAGS = new Tag[] {new Tag("foo", "bar")};
    private MicroProfileMetricsExchangeRecorder recorder;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        recorder = new MicroProfileMetricsExchangeRecorder(metricRegistry, CAMEL_METRIC_PREFIX, TAGS);
    }

    @Test
    public void testMetricsRecorderExchangeComplete() {
        recorder.recordExchangeBegin();
        Exchange exchange = ExchangeBuilder.anExchange(context).build();
        recorder.recordExchangeComplete(exchange);

        Counter exchangesCompleted = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_COMPLETED_METRIC_NAME, TAGS);
        assertEquals(1, exchangesCompleted.getCount());

        Counter exchangesFailed = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_FAILED_METRIC_NAME, TAGS);
        assertEquals(0, exchangesFailed.getCount());

        Counter exchangesTotal = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_TOTAL_METRIC_NAME, TAGS);
        assertEquals(1, exchangesTotal.getCount());

        AtomicIntegerGauge exchangesInflight = getAtomicIntegerGauge(CAMEL_METRIC_PREFIX + EXCHANGES_INFLIGHT_METRIC_NAME, TAGS);
        assertEquals(0, exchangesInflight.getValue().intValue());

        Counter externalRedeliveries = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME, TAGS);
        assertEquals(0, externalRedeliveries.getCount());

        Counter failuresHandled = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_FAILURES_HANDLED_METRIC_NAME, TAGS);
        assertEquals(0, failuresHandled.getCount());
    }

    @Test
    public void testMetricsRecorderExchangeInflight() {
        recorder.recordExchangeBegin();
        AtomicIntegerGauge exchangesInflight = getAtomicIntegerGauge(CAMEL_METRIC_PREFIX + EXCHANGES_INFLIGHT_METRIC_NAME, TAGS);
        assertEquals(1, exchangesInflight.getValue().intValue());

        Exchange exchange = ExchangeBuilder.anExchange(context).build();
        recorder.recordExchangeComplete(exchange);
        assertEquals(0, exchangesInflight.getValue().intValue());
    }

    @Test
    public void testMetricsRecorderFailuresHandled() {
        Exchange exchange = ExchangeBuilder.anExchange(context)
            .withProperty(Exchange.FAILURE_HANDLED, true)
            .build();
        recorder.recordExchangeComplete(exchange);
        Counter failuresHandled = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_FAILURES_HANDLED_METRIC_NAME, TAGS);
        assertEquals(1, failuresHandled.getCount());
    }

    @Test
    public void testMetricsRecorderExchangesFailed() {
        Exchange exchange = ExchangeBuilder.anExchange(context).build();
        exchange.setException(new Exception());
        recorder.recordExchangeComplete(exchange);
        Counter exchangesFailed = getCounter(CAMEL_METRIC_PREFIX + EXCHANGES_FAILED_METRIC_NAME, TAGS);
        assertEquals(1, exchangesFailed.getCount());
    }
}
