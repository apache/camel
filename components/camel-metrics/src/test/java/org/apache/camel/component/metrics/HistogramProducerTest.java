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
package org.apache.camel.component.metrics;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_HISTOGRAM_VALUE;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistogramProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long VALUE = System.currentTimeMillis();

    @Mock
    private MetricsEndpoint endpoint;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Histogram histogram;

    @Mock
    private Exchange exchange;

    @Mock
    private Message in;

    private HistogramProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new HistogramProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, registry, histogram, exchange, in);
        when(registry.histogram(METRICS_NAME)).thenReturn(histogram);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testHistogramProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessValueSet() throws Exception {
        when(endpoint.getValue()).thenReturn(VALUE);
        when(in.getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Long.class)).thenReturn(VALUE);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Long.class);
        inOrder.verify(histogram, times(1)).update(VALUE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessValueNotSet() throws Exception {
        Object action = null;
        when(endpoint.getValue()).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, action, Long.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverrideValue() throws Exception {
        when(endpoint.getValue()).thenReturn(VALUE);
        when(in.getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Long.class)).thenReturn(VALUE + 3);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).histogram(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Long.class);
        inOrder.verify(histogram, times(1)).update(VALUE + 3);
        inOrder.verifyNoMoreInteractions();
    }

}
