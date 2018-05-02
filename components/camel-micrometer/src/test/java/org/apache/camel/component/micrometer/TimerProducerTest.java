/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer;

import io.micrometer.core.instrument.*;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_TIMER_ACTION;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

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
    private Timer timer;

    @Mock
    private Timer.Sample sample;

    @Mock
    private Message in;

    private TimerProducer producer;

    @Before
    public void setUp() throws Exception {
        producer = new TimerProducer(endpoint);
        when(endpoint.getRegistry()).thenReturn(registry);
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
        when(registry.config()).thenReturn(config);
        when(config.clock()).thenReturn(clock);
        producer.doProcess(exchange, METRICS_NAME, Tags.empty());
    }

    @Test
    public void testProcessStopWithOverride() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.stop);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.stop, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.start);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        when(registry.config()).thenReturn(config);
        when(config.clock()).thenReturn(clock);
        producer.doProcess(exchange, METRICS_NAME, Tags.empty());
    }


    @Test
    public void testProcessNoActionOverride() throws Exception {
        Object action = null;
        when(endpoint.getAction()).thenReturn(null);
        when(in.getHeader(HEADER_TIMER_ACTION, action, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.start);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        when(registry.config()).thenReturn(config);
        when(config.clock()).thenReturn(clock);
        producer.doProcess(exchange, METRICS_NAME, Tags.empty());
    }

    @Test
    public void testProcessStartWithOverride() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.start);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.start, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.stop);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(registry.timer(METRICS_NAME, Tags.empty())).thenReturn(timer);
        when(timer.getId()).thenReturn(new Meter.Id(METRICS_NAME, Collections.emptyList(), null, null, Meter.Type.TIMER));
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        when(sample.stop(timer)).thenReturn(0L);
        when(exchange.removeProperty(PROPERTY_NAME)).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Tags.empty());
    }

    @Test
    public void testProcessStop() throws Exception {
        when(endpoint.getAction()).thenReturn(MicrometerTimerAction.stop);
        when(in.getHeader(HEADER_TIMER_ACTION, MicrometerTimerAction.stop, MicrometerTimerAction.class)).thenReturn(MicrometerTimerAction.stop);
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        when(endpoint.getRegistry()).thenReturn(registry);
        when(registry.timer(METRICS_NAME, Tags.empty())).thenReturn(timer);
        when(timer.getId()).thenReturn(new Meter.Id(METRICS_NAME, Collections.emptyList(), null, null, Meter.Type.TIMER));
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        when(sample.stop(timer)).thenReturn(0L);
        when(exchange.removeProperty(PROPERTY_NAME)).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Tags.empty());
    }

    @Test
    public void testProcessNoAction() throws Exception {
        when(endpoint.getAction()).thenReturn(null);
        producer.doProcess(exchange, METRICS_NAME, Tags.empty());
    }


    @Test
    public void testGetPropertyName() throws Exception {
        assertThat(producer.getPropertyName(METRICS_NAME), is("timer" + ":" + METRICS_NAME));
    }

    @Test
    public void testGetTimerContextFromExchange() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(sample);
        assertThat(producer.getTimerSampleFromExchange(exchange, PROPERTY_NAME), is(sample));
    }

    @Test
    public void testGetTimerContextFromExchangeNotFound() throws Exception {
        when(exchange.getProperty(PROPERTY_NAME, Timer.Sample.class)).thenReturn(null);
        assertThat(producer.getTimerSampleFromExchange(exchange, PROPERTY_NAME), is(nullValue()));
    }
}
