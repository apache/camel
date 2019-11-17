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

import com.codahale.metrics.Meter;
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

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_METER_MARK;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MeterProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long MARK = 9919120L;

    @Mock
    private MetricsEndpoint endpoint;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Meter meter;

    @Mock
    private Exchange exchange;

    @Mock
    private Message in;

    private MeterProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new MeterProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, registry, meter, exchange, in);
        when(registry.meter(METRICS_NAME)).thenReturn(meter);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testMeterProducer() throws Exception {
        assertThat(producer, is(notNullValue()));
        assertThat(producer.getEndpoint(), is(equalTo(endpoint)));
    }

    @Test
    public void testProcessMarkSet() throws Exception {
        when(endpoint.getMark()).thenReturn(MARK);
        when(in.getHeader(HEADER_METER_MARK, MARK, Long.class)).thenReturn(MARK);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, MARK, Long.class);
        inOrder.verify(meter, times(1)).mark(MARK);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMarkSetOverrideByHeaderValue() throws Exception {
        when(endpoint.getMark()).thenReturn(MARK);
        when(in.getHeader(HEADER_METER_MARK, MARK, Long.class)).thenReturn(MARK + 101);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, MARK, Long.class);
        inOrder.verify(meter, times(1)).mark(MARK + 101);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMarkNotSet() throws Exception {
        Object action = null;
        when(endpoint.getMark()).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(registry, times(1)).meter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getMark();
        inOrder.verify(in, times(1)).getHeader(HEADER_METER_MARK, action, Long.class);
        inOrder.verify(meter, times(1)).mark();
        inOrder.verifyNoMoreInteractions();
    }

}
