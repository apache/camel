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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.codahale.metrics.MetricRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MetricsComponentTest {

    @Mock
    private CamelContext camelContext;

    @Mock
    private Registry camelRegistry;

    @Mock
    private MetricRegistry metricRegistry;

    private InOrder inOrder;

    private MetricsComponent component;

    @Before
    public void setUp() throws Exception {
        component = new MetricsComponent();
        inOrder = Mockito.inOrder(camelContext, camelRegistry, metricRegistry);
    }

    @Test
    public void testCreateEndpoint() throws Exception {
        component.setCamelContext(camelContext);
        when(camelContext.getRegistry()).thenReturn(camelRegistry);
        when(camelRegistry.lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class)).thenReturn(metricRegistry);
        Map<String, Object> params = new HashMap<String, Object>();
        Long value = System.currentTimeMillis();
        params.put("mark", value);
        Endpoint result = component.createEndpoint("metrics:meter:long.meter", "meter:long.meter", params);
        assertThat(result, is(notNullValue()));
        assertThat(result, is(instanceOf(MetricsEndpoint.class)));
        MetricsEndpoint me = (MetricsEndpoint) result;
        assertThat(me.getMark(), is(value));
        assertThat(me.getMetricsName(), is("long.meter"));
        assertThat(me.getRegistry(), is(metricRegistry));
        inOrder.verify(camelContext, times(1)).getRegistry();
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class);
        inOrder.verify(camelContext, times(1)).getTypeConverter();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCreateEndpoints() throws Exception {
        component.setCamelContext(camelContext);
        when(camelContext.getRegistry()).thenReturn(camelRegistry);
        when(camelRegistry.lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class)).thenReturn(metricRegistry);
        Map<String, Object> params = new HashMap<String, Object>();
        Long value = System.currentTimeMillis();
        params.put("mark", value);
        Endpoint result = component.createEndpoint("metrics:meter:long.meter", "meter:long.meter", params);
        assertThat(result, is(notNullValue()));
        assertThat(result, is(instanceOf(MetricsEndpoint.class)));
        MetricsEndpoint me = (MetricsEndpoint) result;
        assertThat(me.getMark(), is(value));
        assertThat(me.getMetricsName(), is("long.meter"));
        assertThat(me.getRegistry(), is(metricRegistry));

        params = new HashMap<String, Object>();
        params.put("increment", value + 1);
        params.put("decrement", value - 1);

        result = component.createEndpoint("metrics:counter:long.counter", "counter:long.counter", params);
        assertThat(result, is(notNullValue()));
        assertThat(result, is(instanceOf(MetricsEndpoint.class)));
        MetricsEndpoint ce = (MetricsEndpoint) result;
        assertThat(ce.getIncrement(), is(value + 1));
        assertThat(ce.getDecrement(), is(value - 1));
        assertThat(ce.getMetricsName(), is("long.counter"));
        assertThat(ce.getRegistry(), is(metricRegistry));

        inOrder.verify(camelContext, times(1)).getRegistry();
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class);
        inOrder.verify(camelContext, times(2)).getTypeConverter();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetMetricsName() throws Exception {
        assertThat(component.getMetricsName("meter:metric-a"), is("metric-a"));
        assertThat(component.getMetricsName("meter:metric-a:sub-b"), is("metric-a:sub-b"));
        assertThat(component.getMetricsName("metric-a"), is("metric-a"));
        assertThat(component.getMetricsName("//metric-a"), is("//metric-a"));
        assertThat(component.getMetricsName("meter://metric-a"), is("//metric-a"));
    }

    @Test
    public void testCreateNewEndpointForCounter() throws Exception {
        Endpoint endpoint = new MetricsEndpoint(null, null, metricRegistry, MetricsType.COUNTER, "a name");
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MetricsEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForMeter() throws Exception {
        Endpoint endpoint = new MetricsEndpoint(null, null, metricRegistry, MetricsType.METER, "a name");
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MetricsEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForGauge() throws Exception {
        MetricsEndpoint endpoint = new MetricsEndpoint(null, null, metricRegistry, MetricsType.GAUGE, "a name");
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MetricsEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForHistogram() throws Exception {
        Endpoint endpoint = new MetricsEndpoint(null, null, metricRegistry, MetricsType.HISTOGRAM, "a name");
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MetricsEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForTimer() throws Exception {
        Endpoint endpoint = new MetricsEndpoint(null, null, metricRegistry, MetricsType.TIMER, "a name");
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MetricsEndpoint.class)));
    }

    @Test
    public void testGetMetricsType() throws Exception {
        for (MetricsType type : EnumSet.allOf(MetricsType.class)) {
            assertThat(component.getMetricsType(type.toString() + ":metrics-name"), is(type));
        }
    }

    @Test
    public void testGetMetricsTypeNotSet() throws Exception {
        assertThat(component.getMetricsType("no-metrics-type"), is(MetricsComponent.DEFAULT_METRICS_TYPE));
    }

    @Test(expected = RuntimeCamelException.class)
    public void testGetMetricsTypeNotFound() throws Exception {
        component.getMetricsType("unknown-metrics:metrics-name");
    }

    @Test
    public void testGetOrCreateMetricRegistryFoundInCamelRegistry() throws Exception {
        when(camelRegistry.lookupByNameAndType("name", MetricRegistry.class)).thenReturn(metricRegistry);
        MetricRegistry result = component.getOrCreateMetricRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MetricRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateMetricRegistryFoundInCamelRegistryByType() throws Exception {
        when(camelRegistry.lookupByNameAndType("name", MetricRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(MetricRegistry.class)).thenReturn(Collections.singleton(metricRegistry));
        MetricRegistry result = component.getOrCreateMetricRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MetricRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(MetricRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateMetricRegistryNotFoundInCamelRegistry() throws Exception {
        when(camelRegistry.lookupByNameAndType("name", MetricRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(MetricRegistry.class)).thenReturn(Collections.<MetricRegistry>emptySet());
        MetricRegistry result = component.getOrCreateMetricRegistry(camelRegistry, "name");
        assertThat(result, is(notNullValue()));
        assertThat(result, is(not(metricRegistry)));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MetricRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(MetricRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetMetricRegistryFromCamelRegistry() throws Exception {
        when(camelRegistry.lookupByNameAndType("name", MetricRegistry.class)).thenReturn(metricRegistry);
        MetricRegistry result = component.getMetricRegistryFromCamelRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MetricRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCreateMetricRegistry() throws Exception {
        MetricRegistry registry = component.createMetricRegistry();
        assertThat(registry, is(notNullValue()));
    }
}
