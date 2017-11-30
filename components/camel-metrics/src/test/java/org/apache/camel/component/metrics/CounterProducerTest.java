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

import com.codahale.metrics.Counter;
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

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_COUNTER_INCREMENT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CounterProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long INCREMENT = 100000L;
    private static final Long DECREMENT = 91929199L;

    @Mock
    private MetricsEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Counter counter;

    @Mock
    private Message in;

    private CounterProducer producer;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new CounterProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, exchange, registry, counter, in);
        when(registry.counter(METRICS_NAME)).thenReturn(counter);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testCounterProducer() throws Exception {
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessWithIncrementOnly() throws Exception {
        Object action = null;
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(null);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class)).thenReturn(INCREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, action, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithDecrementOnly() throws Exception {
        Object action = null;
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, action, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).dec(DECREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDoProcessWithIncrementAndDecrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class)).thenReturn(INCREMENT);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessWithOutIncrementAndDecrement() throws Exception {
        Object action = null;
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, action, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, action, Long.class);
        inOrder.verify(counter, times(1)).inc();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverridingIncrement() throws Exception {
        when(endpoint.getIncrement()).thenReturn(INCREMENT);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class)).thenReturn(INCREMENT + 1);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, INCREMENT, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).inc(INCREMENT + 1);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessOverridingDecrement() throws Exception {
        Object action = null;
        when(endpoint.getIncrement()).thenReturn(null);
        when(endpoint.getDecrement()).thenReturn(DECREMENT);
        when(in.getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class)).thenReturn(DECREMENT - 1);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(registry, times(1)).counter(METRICS_NAME);
        inOrder.verify(endpoint, times(1)).getIncrement();
        inOrder.verify(endpoint, times(1)).getDecrement();
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_INCREMENT, action, Long.class);
        inOrder.verify(in, times(1)).getHeader(HEADER_COUNTER_DECREMENT, DECREMENT, Long.class);
        inOrder.verify(counter, times(1)).dec(DECREMENT - 1);
        inOrder.verifyNoMoreInteractions();
    }
}
