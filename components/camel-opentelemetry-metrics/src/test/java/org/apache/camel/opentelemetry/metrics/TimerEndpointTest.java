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

package org.apache.camel.opentelemetry.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TimerEndpointTest {

    private static final String METRICS_NAME = "metrics.name";

    @Mock
    private Meter meter;

    private OpenTelemetryEndpoint endpoint;

    private InOrder inOrder;

    @BeforeEach
    public void setUp() {
        endpoint = new OpenTelemetryEndpoint(null, null, meter, InstrumentType.TIMER, METRICS_NAME);
        inOrder = Mockito.inOrder(meter);
    }

    @AfterEach
    public void tearDown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTimerEndpoint() {
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint.getMeter(), is(meter));
        assertThat(endpoint.getMetricName(), is(METRICS_NAME));
    }

    @Test
    public void testCreateProducer() {
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
        endpoint.setAction(OpenTelemetryTimerAction.START.name());
        assertThat(OpenTelemetryTimerAction.valueOf(endpoint.getAction()), is(OpenTelemetryTimerAction.START));
    }
}
