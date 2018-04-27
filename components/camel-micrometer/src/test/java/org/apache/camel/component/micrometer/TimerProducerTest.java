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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.apache.camel.component.micrometer.AbstractMicrometerProducer.HEADER_PATTERN;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_TIMER_ACTION;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TimerProducerTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final String PROPERTY_NAME = "timer" + ":" + METRICS_NAME;

    @Mock
    private MicrometerEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private MeterRegistry registry;

    @Mock
    private MeterRegistry.Config config;

    @Mock
    private Clock clock;

    @Mock
    private Search search;

    @Mock
    private Timer timer;

    @Mock
    private Timer.Sample sample;

    @Mock
    private Message in;

    private TimerProducer producer;

    @Mock
    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        producer = new TimerProducer(endpoint);
        inOrder = Mockito.inOrder(endpoint, exchange, registry, timer, sample, in, search, config, clock);

        when(exchange.getIn()).thenReturn(in);
    }

    @Test
    public void testTimerProducer() throws Exception {
        assertThat(producer, is(notNullValue()));
        assertThat(producer.getEndpoint().equals(endpoint), is(true));
    }

    @Test
    public void testProcessStart() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.start);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.start, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.start);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.start, MicrometerTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        // inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, sample);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessStartWithOverride() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.start);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.start, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.stop);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.start, MicrometerTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(sample, times(1)).stop(timer);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessStop() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.stop);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.stop, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.stop);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.stop, MicrometerTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(sample, times(1)).stop(timer);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessStopWithOverride() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.stop);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.stop, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.start);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.stop, MicrometerTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        // inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, sample);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessNoAction() throws Exception {
        when(endpoint.getAction()).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, (Object) null, MicrometerTimerAction.class);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessNoActionOverride() throws Exception {
        Object action = null;
        when(endpoint.getAction()).thenReturn(null);
        when(in.getHeader(HEADER_TIMER_ACTION, action, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.start);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(endpoint, times(1)).getAction();
        inOrder.verify(in, times(1)).getHeader(HEADER_TIMER_ACTION, action, MicrometerTimerAction.class);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(registry, times(1)).timer(METRICS_NAME);
        // inOrder.verify(timer, times(1)).time();
        inOrder.verify(exchange, times(1)).setProperty(PROPERTY_NAME, sample);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStart() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        when(registry.config()).thenReturn(config);
        when(config.clock()).thenReturn(clock);
        doNothing().when(exchange).setProperty(ArgumentMatchers.eq(METRICS_NAME), ArgumentMatchers.any(Timer.Sample.class));
        producer.handleStart(exchange, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(exchange, times(1)).setProperty(ArgumentMatchers.eq(METRICS_NAME), ArgumentMatchers.any(Timer.Sample.class));
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStartAlreadyRunning() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        producer.handleStart(exchange, registry, METRICS_NAME);
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStop() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(sample, times(1)).stop(timer);
        inOrder.verify(exchange, times(1)).removeProperty(PROPERTY_NAME);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testHandleStopContextNotFound() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Collections.emptyList());
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetPropertyName() throws Exception {
        assertThat(producer.getPropertyName(METRICS_NAME), is("timer" + ":" + METRICS_NAME));
    }

    @Test
    public void testGetTimerContextFromExchange() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        assertThat(producer.getTimerSampleFromExchange(exchange, PROPERTY_NAME), is(sample));
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        //inOrder.verify(exchange, times(1)).getIn();
        //inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetTimerContextFromExchangeNotFound() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        assertThat(producer.getTimerSampleFromExchange(exchange, PROPERTY_NAME), is(nullValue()));
        inOrder.verify(exchange, times(1)).getProperty(PROPERTY_NAME, Timer.Sample.class);
        //inOrder.verify(exchange, times(1)).getIn();
        //inOrder.verify(in, times(1)).removeHeaders(HEADER_PATTERN);
        inOrder.verifyNoMoreInteractions();
    }
}
