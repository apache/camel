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

import org.apache.camel.Producer;
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

public class MicroProfileMetricsEndpointTest {

    @Test
    public void testCreateCounterProducer() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.COUNTER);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsCounterProducer.class)));
    }

    @Test
    public void testCreateConcurrentGaugeProducer() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.CONCURRENT_GAUGE);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsConcurrentGaugeProducer.class)));
    }

    @Test
    public void testCreateGaugeProducer() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.GAUGE);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsGaugeProducer.class)));
    }

    @Test
    public void testCreateHistogramProducer() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.HISTOGRAM);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsHistogramProducer.class)));
    }

    @Test
    public void testCreateMeteredProducer() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.METERED);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsMeteredProducer.class)));
    }

    @Test
    public void testCreateTimerProducer() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.TIMER);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsTimerProducer.class)));
    }

    @Test(expected = IllegalStateException.class)
    public void testInvalidMetricType() throws Exception {
        MicroProfileMetricsEndpoint endpoint = createEndpoint(MetricType.INVALID);
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(instanceOf(MicroProfileMetricsCounterProducer.class)));
    }

    private MicroProfileMetricsEndpoint createEndpoint(MetricType metricType) {
        return new MicroProfileMetricsEndpoint("microprofile-metrics:foo:bar", null, null, metricType, null);
    }
}
