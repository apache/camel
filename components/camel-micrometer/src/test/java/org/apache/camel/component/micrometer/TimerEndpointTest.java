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
package org.apache.camel.component.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.Producer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TimerEndpointTest {

    private static final String METRICS_NAME = "metrics.name";

    @Mock
    private MeterRegistry registry;

    private MicrometerEndpoint endpoint;

    private InOrder inOrder;

    @Before
    public void setUp() {
        endpoint = new MicrometerEndpoint(null, null, registry, Meter.Type.TIMER, METRICS_NAME, Tags.empty());
        inOrder = Mockito.inOrder(registry);
    }

    @After
    public void tearDown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTimerEndpoint() {
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint.getRegistry(), is(registry));
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
    }

    @Test
    public void testCreateProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(notNullValue()));
        assertThat(producer, is(instanceOf(TimerProducer.class)));
    }

    @Test
    public void testGetAction() {
        assertThat(endpoint.getAction(), is(nullValue()));
    }

    @Test
    public void testSetAction() {
        assertThat(endpoint.getAction(), is(nullValue()));
        endpoint.setAction(MicrometerTimerAction.start.name());
        assertThat(MicrometerTimerAction.valueOf(endpoint.getAction()), is(MicrometerTimerAction.start));
    }

}
