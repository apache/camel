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
import org.apache.camel.component.metrics.GaugeProducer.CamelMetricsGauge;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_GAUGE_SUBJECT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GaugeProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final String METRICS_NAME_HEADER = "metrics.name.header";
    private static final Object VALUE = "my subject";
    private static final Object VALUE_HEADER = "my subject header";

    @Mock
    private MetricsEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Message in;

    private GaugeProducer producer;

    @Before
    public void setUp() throws Exception {
        when(endpoint.getRegistry()).thenReturn(registry);
        when(endpoint.getSubject()).thenReturn(VALUE);
        when(endpoint.getMetricsName()).thenReturn(METRICS_NAME);
        when(exchange.getIn()).thenReturn(in);
        producer = new GaugeProducer(endpoint);
    }

    @Test
    public void testGaugeProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testDefault() throws Exception {
        verify(registry, times(1)).register(eq(METRICS_NAME), argThat(new ArgumentMatcher<CamelMetricsGauge>() {
            @Override
            public boolean matches(CamelMetricsGauge argument) {
                return VALUE.equals(argument.getValue());
            }
        }));
    }

    @Test
    public void testProcessWithHeaderValues() throws Exception {
        when(in.getHeader(HEADER_GAUGE_SUBJECT, Object.class)).thenReturn(VALUE_HEADER);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME_HEADER);
        verify(in, times(1)).getHeader(HEADER_GAUGE_SUBJECT, Object.class);
        verify(registry, times(1)).register(eq(METRICS_NAME), argThat(new ArgumentMatcher<CamelMetricsGauge>() {
            @Override
            public boolean matches(CamelMetricsGauge argument) {
                return VALUE.equals(argument.getValue());
            }
        }));
        verify(registry, times(1)).register(eq(METRICS_NAME_HEADER), argThat(new ArgumentMatcher<CamelMetricsGauge>() {
            @Override
            public boolean matches(CamelMetricsGauge argument) {
                return VALUE_HEADER.equals(argument.getValue());
            }
        }));
    }

}
