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
import com.codahale.metrics.Timer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.apache.camel.component.metrics.MetricsConstants.HEADER_TIMER_ACTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TimerProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final String PROPERTY_NAME = "timer" + ":" + METRICS_NAME;

    @Mock
    private MetricsEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private MetricRegistry registry;

    @Mock
    private Timer timer;

    @Mock
    private Timer.Context context;

    @Mock
    private Message in;

    private TimerProducer producer;

    @Mock
    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new TimerProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, exchange, registry, timer, context, in);
        when(registry.timer(METRICS_NAME)).thenReturn(timer);
        when(timer.time()).thenReturn(context);
        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testTimerProducer() throws Exception {
        assertThat(producer, is(notNullValue()));
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessStart() throws Exception {
        when(endpoint.getAction()).thenReturn(MetricsTimerAction.start);
        when(in.getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.start, MetricsTimerAction.class)).thenReturn(MetricsTimerAction.start);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.start, MetricsTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessStartWithOverride() throws Exception {
        when(endpoint.getAction()).thenReturn(MetricsTimerAction.start);
        when(in.getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.start, MetricsTimerAction.class)).thenReturn(MetricsTimerAction.stop);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(context);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.start, MetricsTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(context, times(1)).stop();
        inOrder.verify(exchange, times(1)).removeProperty(PROPERTY_NAME);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessStop() throws Exception {
        when(endpoint.getAction()).thenReturn(MetricsTimerAction.stop);
        when(in.getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.stop, MetricsTimerAction.class)).thenReturn(MetricsTimerAction.stop);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(context);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.stop, MetricsTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(context, times(1)).stop();
        inOrder.verify(exchange, times(1)).removeProperty(PROPERTY_NAME);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessStopWithOverride() throws Exception {
        when(endpoint.getAction()).thenReturn(MetricsTimerAction.stop);
        when(in.getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.stop, MetricsTimerAction.class)).thenReturn(MetricsTimerAction.start);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MetricsTimerAction.stop, MetricsTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessNoAction() throws Exception {
        when(endpoint.getAction()).thenReturn(null);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, (Object) null, MetricsTimerAction.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessNoActionOverride() throws Exception {
        Object action = null;
        when(endpoint.getAction()).thenReturn(null);
        when(in.getHeader(HEADER_TIMER_ACTION, action, MetricsTimerAction.class)).thenReturn(MetricsTimerAction.start);
        producer.doProcess(exchange, endpoint, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, action, MetricsTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStart() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(null);
        producer.handleStart(exchange, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStartAlreadyRunning() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(context);
        producer.handleStart(exchange, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStop() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(context);
        producer.handleStop(exchange, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verify(context, times(1)).stop();
        inOrder.verify(exchange, times(1)).removeProperty(PROPERTY_NAME);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStopContextNotFound() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(null);
        producer.handleStop(exchange, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetPropertyName() throws Exception {
        assertThat(producer.getPropertyName(METRICS_NAME), is("timer" + ":" + METRICS_NAME));
    }

    @Test
    public void testGetTimerContextFromExchange() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(context);
        assertThat(producer.getTimerContextFromExchange(exchange, PROPERTY_NAME), is(context));
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetTimerContextFromExchangeNotFound() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Context.class)).thenReturn(null);
        assertThat(producer.getTimerContextFromExchange(exchange, PROPERTY_NAME), is(nullValue()));
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Context.class);
        inOrder.verifyNoMoreInteractions();
    }
}
