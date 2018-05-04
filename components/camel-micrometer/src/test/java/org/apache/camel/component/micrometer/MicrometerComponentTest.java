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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
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
public class MicrometerComponentTest {

    @Mock
    private CamelContext camelContext;

    @Mock
    private TypeConverter typeConverter;

    @Mock
    private Registry camelRegistry;

    @Mock
    private MeterRegistry metricRegistry;

    private InOrder inOrder;

    private MicrometerComponent component;

    @Before
    public void setUp() {
        component = new MicrometerComponent();
        inOrder = Mockito.inOrder(camelContext, camelRegistry, metricRegistry, typeConverter);
    }

    @Test
    public void testCreateEndpoint() throws Exception {
        component.setCamelContext(camelContext);
        when(camelContext.getRegistry()).thenReturn(camelRegistry);
        when(camelContext.getTypeConverter()).thenReturn(typeConverter);
        when(typeConverter.convertTo(String.class, "key=value")).thenReturn("key=value");
        when(camelRegistry.lookupByNameAndType(MicrometerComponent.METRICS_REGISTRY_NAME, MeterRegistry.class)).thenReturn(metricRegistry);

        Map<String, Object> params = new HashMap<>();
        params.put("tags", "key=value");
        Endpoint result = component.createEndpoint("micrometer:counter:counter", "counter:counter", params);
        assertThat(result, is(notNullValue()));
        assertThat(result, is(instanceOf(MicrometerEndpoint.class)));
        MicrometerEndpoint me = (MicrometerEndpoint) result;
        assertThat(me.getMetricsName(), is(MicrometerConstants.HEADER_PREFIX + "." + "counter"));
        assertThat(me.getRegistry(), is(metricRegistry));
        inOrder.verify(camelContext, times(1)).getRegistry();
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType(MicrometerComponent.METRICS_REGISTRY_NAME, MeterRegistry.class);
        inOrder.verify(camelContext, times(1)).getTypeConverter();
        inOrder.verify(typeConverter, times(1)).convertTo(String.class, "key=value");
        inOrder.verify(camelContext, times(1)).getTypeConverter();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCreateNewEndpointForCounter() {
        Endpoint endpoint = new MicrometerEndpoint(null, null, metricRegistry, MetricsType.COUNTER, "a name", Collections.emptyList());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForGauge() {
        MicrometerEndpoint endpoint = new MicrometerEndpoint(null, null, metricRegistry, MetricsType.GAUGE, "a name", Collections.emptyList());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForHistogram() {
        Endpoint endpoint = new MicrometerEndpoint(null, null, metricRegistry, MetricsType.DISTRIBUTION_SUMMARY, "a name", Collections.emptyList());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForTimer() {
        Endpoint endpoint = new MicrometerEndpoint(null, null, metricRegistry, MetricsType.TIMER, "a name", Collections.emptyList());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testGetMetricsType() {
        for (MetricsType type : EnumSet.allOf(MetricsType.class)) {
            assertThat(component.getMetricsType(type.toString() + ":metrics-name"), is(type));
        }
    }

    @Test
    public void testGetMetricsTypeNotSet() {
        assertThat(component.getMetricsType("no-metrics-type"), is(MicrometerComponent.DEFAULT_METER_TYPE));
    }

    @Test(expected = RuntimeCamelException.class)
    public void testGetMetricsTypeNotFound() {
        component.getMetricsType("unknown-metrics:metrics-name");
    }

    @Test
    public void testGetOrCreateMetricRegistryFoundInCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(metricRegistry);
        MeterRegistry result = component.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateMetricRegistryFoundInCamelRegistryByType() {
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(MeterRegistry.class)).thenReturn(Collections.singleton(metricRegistry));
        MeterRegistry result = component.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateMetricRegistryNotFoundInCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(MeterRegistry.class)).thenReturn(Collections.emptySet());
        MeterRegistry result = component.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(notNullValue()));
        assertThat(result, is(not(metricRegistry)));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetMetricRegistryFromCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(metricRegistry);
        MeterRegistry result = component.getMeterRegistryFromCamelRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCreateMetricRegistry() {
        MeterRegistry registry = component.createMeterRegistry();
        assertThat(registry, is(notNullValue()));
    }
}
