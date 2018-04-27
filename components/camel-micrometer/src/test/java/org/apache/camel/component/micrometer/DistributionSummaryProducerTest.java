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
package org.apache.camel.component.micrometer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.apache.camel.component.micrometer.AbstractMicrometerProducer.HEADER_PATTERN;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_HISTOGRAM_VALUE;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DistributionSummaryProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Double VALUE = new Long(System.currentTimeMillis()).doubleValue();

    @Mock
    private MicrometerEndpoint endpoint;

    @Mock
    private MeterRegistry registry;

    @Mock
    private Search search;

    @Mock
    private DistributionSummary histogram;

    @Mock
    private Exchange exchange;

    @Mock
    private Message in;

    private DistributionSummaryProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        endpoint = mock(MicrometerEndpoint.class);
        producer = new DistributionSummaryProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, registry, histogram, search, exchange, in);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(registry.find(METRICS_NAME)).thenReturn(search);
        when(search.summary()).thenReturn(histogram);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testHistogramProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessValueSet() throws Exception {
        when(endpoint.getValue()).thenReturn(VALUE);
        when(in.getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Double.class)).thenReturn(VALUE);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(registry, times(1)).find(METRICS_NAME);
        inOrder.verify(search, times(1)).summary();
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Double.class);
        inOrder.verify(histogram, times(1)).record(VALUE);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessValueNotSet() throws Exception {
        Object action = null;
        when(endpoint.getValue()).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(registry, times(1)).find(METRICS_NAME);
        inOrder.verify(search, times(1)).summary();
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, action, Double.class);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverrideValue() throws Exception {
        when(endpoint.getValue()).thenReturn(VALUE);
        when(in.getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Double.class)).thenReturn(VALUE + 3.0d);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(registry, times(1)).find(METRICS_NAME);
        inOrder.verify(search, times(1)).summary();
        inOrder.verify(endpoint, times(1)).getValue();
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).getHeader(HEADER_HISTOGRAM_VALUE, VALUE, Double.class);
        inOrder.verify(histogram, times(1)).record(VALUE + 3);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

}
