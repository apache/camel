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

import java.util.Collections;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MicrometerComponentTest {

    @Mock
    private CamelContext camelContext;

    @Mock
    private TypeConverter typeConverter;

    @Mock
    private Registry camelRegistry;

    @Mock
    private MeterRegistry metricRegistry;

    @Mock
    private CompositeMeterRegistry compositeMeterRegistry;

    private InOrder inOrder;

    private MicrometerComponent component;

    @BeforeEach
    public void setUp() {
        component = new MicrometerComponent();
        inOrder = Mockito.inOrder(camelContext, camelRegistry, metricRegistry, typeConverter);
    }

    @Test
    public void testCreateNewEndpointForCounter() {
        Endpoint endpoint = new MicrometerEndpoint(null, null, metricRegistry, Meter.Type.COUNTER, "a name", Tags.empty());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForHistogram() {
        Endpoint endpoint
                = new MicrometerEndpoint(null, null, metricRegistry, Meter.Type.DISTRIBUTION_SUMMARY, "a name", Tags.empty());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testCreateNewEndpointForTimer() {
        Endpoint endpoint = new MicrometerEndpoint(null, null, metricRegistry, Meter.Type.TIMER, "a name", Tags.empty());
        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, is(instanceOf(MicrometerEndpoint.class)));
    }

    @Test
    public void testGetMetricsType() {
        Meter.Type[] supportedTypes = { Meter.Type.COUNTER, Meter.Type.DISTRIBUTION_SUMMARY, Meter.Type.TIMER };
        for (Meter.Type type : supportedTypes) {
            assertThat(component.getMetricsType(MicrometerUtils.getName(type) + ":metrics-name"), is(type));
        }
    }

    @Test
    public void testGetMetricsTypeNotSet() {
        assertThat(component.getMetricsType("no-metrics-type"), is(MicrometerComponent.DEFAULT_METER_TYPE));
    }

    @Test
    public void testGetMetricsTypeNotFound() {
        assertThrows(RuntimeCamelException.class,
                () -> component.getMetricsType("unknown-metrics:metrics-name"));
    }

    @Test
    public void testGetOrCreateMetricRegistryFoundInCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", CompositeMeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(CompositeMeterRegistry.class)).thenReturn(null);
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(metricRegistry);
        MeterRegistry result = MicrometerUtils.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateCompositeMetricRegistryFoundInCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", CompositeMeterRegistry.class))
                .thenReturn(compositeMeterRegistry);
        MeterRegistry result = MicrometerUtils.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(compositeMeterRegistry));
        inOrder.verify(camelRegistry, times(1))
                .lookupByNameAndType("name", CompositeMeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateMetricRegistryFoundInCamelRegistryByType() {
        when(camelRegistry.lookupByNameAndType("name", CompositeMeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(CompositeMeterRegistry.class)).thenReturn(Collections.singleton(null));
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(MeterRegistry.class)).thenReturn(Collections.singleton(metricRegistry));
        MeterRegistry result = MicrometerUtils.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetOrCreateCompositeMetricRegistryFoundInCamelRegistryByType() {
        when(camelRegistry.lookupByNameAndType("name", CompositeMeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(CompositeMeterRegistry.class))
                .thenReturn(Collections.singleton(compositeMeterRegistry));
        MeterRegistry result = MicrometerUtils.getOrCreateMeterRegistry(camelRegistry, "name");
        assertThat(result, is(compositeMeterRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(CompositeMeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetMetricRegistryFromCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", CompositeMeterRegistry.class)).thenReturn(null);
        when(camelRegistry.findByType(CompositeMeterRegistry.class)).thenReturn(null);
        when(camelRegistry.lookupByNameAndType("name", MeterRegistry.class)).thenReturn(metricRegistry);
        MeterRegistry result = MicrometerUtils.getMeterRegistryFromCamelRegistry(camelRegistry, "name");
        assertThat(result, is(metricRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).findByType(CompositeMeterRegistry.class);
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", MeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetCompositeMetricRegistryFromCamelRegistry() {
        when(camelRegistry.lookupByNameAndType("name", CompositeMeterRegistry.class))
                .thenReturn(compositeMeterRegistry);
        MeterRegistry result = MicrometerUtils.getMeterRegistryFromCamelRegistry(camelRegistry, "name");
        assertThat(result, is(compositeMeterRegistry));
        inOrder.verify(camelRegistry, times(1)).lookupByNameAndType("name", CompositeMeterRegistry.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCreateMetricRegistry() {
        MeterRegistry registry = MicrometerUtils.createMeterRegistry();
        assertThat(registry, isA(SimpleMeterRegistry.class));
    }
}
