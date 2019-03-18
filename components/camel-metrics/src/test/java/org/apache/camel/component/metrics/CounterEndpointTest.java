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
public class CounterEndpointTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Long VALUE = System.currentTimeMillis();

    @Mock
    private MetricRegistry registry;

    private MetricsEndpoint endpoint;

    private InOrder inOrder;

    @Before
    public void setUp() throws Exception {
        endpoint = new MetricsEndpoint(null, null, registry, MetricsType.COUNTER, METRICS_NAME);
        inOrder = Mockito.inOrder(registry);
    }

    @After
    public void tearDown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCounterEndpoint() throws Exception {
        assertThat(endpoint.getRegistry(), is(registry));
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
        assertThat(endpoint.getIncrement(), is(nullValue()));
        assertThat(endpoint.getDecrement(), is(nullValue()));
    }

    @Test
    public void testCreateProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(notNullValue()));
        assertThat(producer, is(instanceOf(CounterProducer.class)));
    }

    @Test
    public void testGetIncrement() throws Exception {
        assertThat(endpoint.getIncrement(), is(nullValue()));
    }

    @Test
    public void testSetIncrement() throws Exception {
        assertThat(endpoint.getIncrement(), is(nullValue()));
        endpoint.setIncrement(VALUE);
        assertThat(endpoint.getIncrement(), is(VALUE));
    }

    @Test
    public void testGetDecrement() throws Exception {
        assertThat(endpoint.getDecrement(), is(nullValue()));
    }

    @Test
    public void testSetDecrement() throws Exception {
        assertThat(endpoint.getDecrement(), is(nullValue()));
        endpoint.setDecrement(VALUE);
        assertThat(endpoint.getDecrement(), is(VALUE));
    }

}
