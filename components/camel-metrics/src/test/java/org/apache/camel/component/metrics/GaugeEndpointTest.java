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

import com.codahale.metrics.MetricRegistry;
import org.apache.camel.Producer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class GaugeEndpointTest {

    private static final String METRICS_NAME = "metrics.name";
    private static final Object VALUE = "subject";

    @Mock
    private MetricRegistry registry;

    private MetricsEndpoint endpoint;

    @Before
    public void setUp() throws Exception {
        endpoint = new MetricsEndpoint(null, null, registry, MetricsType.GAUGE, METRICS_NAME);
    }

    @Test
    public void testGaugeEndpoint() throws Exception {
        assertThat(endpoint.getRegistry(), is(registry));
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
        assertThat(endpoint.getSubject(), is(nullValue()));
    }

    @Test
    public void testCreateProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        assertThat(producer, is(notNullValue()));
        assertThat(producer, is(instanceOf(GaugeProducer.class)));
    }

    @Test
    public void testGetSubject() throws Exception {
        assertThat(endpoint.getSubject(), is(nullValue()));
    }

    @Test
    public void testSetSubject() throws Exception {
        endpoint.setSubject(VALUE);
        assertThat(endpoint.getSubject(), is(VALUE));
    }

}
