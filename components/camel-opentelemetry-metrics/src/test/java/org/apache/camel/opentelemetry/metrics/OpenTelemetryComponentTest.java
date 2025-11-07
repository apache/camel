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

import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class OpenTelemetryComponentTest {

    @Mock
    private Meter meter;

    private OpenTelemetryComponent component;

    @BeforeEach
    public void setUp() {
        component = new OpenTelemetryComponent();
    }

    @Test
    public void testCreateNewEndpointForCounter() {
        Endpoint endpoint = new OpenTelemetryEndpoint(null, null, meter, InstrumentType.COUNTER, "a name");
        assertNotNull(endpoint);
        assertInstanceOf(OpenTelemetryEndpoint.class, endpoint);
    }

    @Test
    public void testCreateNewEndpointForHistogram() {
        Endpoint endpoint
                = new OpenTelemetryEndpoint(null, null, meter, InstrumentType.DISTRIBUTION_SUMMARY, "a name");
        assertNotNull(endpoint);
        assertInstanceOf(OpenTelemetryEndpoint.class, endpoint);
    }

    @Test
    public void testCreateNewEndpointForTimer() {
        Endpoint endpoint = new OpenTelemetryEndpoint(null, null, meter, InstrumentType.TIMER, "a name");
        assertNotNull(endpoint);
        assertInstanceOf(OpenTelemetryEndpoint.class, endpoint);
    }

    @Test
    public void testGetMetricsType() {
        InstrumentType[] supportedTypes = { InstrumentType.COUNTER, InstrumentType.DISTRIBUTION_SUMMARY, InstrumentType.TIMER };
        for (InstrumentType type : supportedTypes) {
            assertEquals(type, component.getMetricsType(type.getName() + ":metrics-name"));
        }
    }

    @Test
    public void testGetMetricsTypeNotSet() {
        assertEquals(OpenTelemetryComponent.DEFAULT_INSTRUMENT_TYPE, component.getMetricsType("no-metrics-type"));
    }

    @Test
    public void testGetMetricsTypeNotFound() {
        assertThrows(RuntimeCamelException.class,
                () -> component.getMetricsType("unknown-metrics:metrics-name"));
    }
}
