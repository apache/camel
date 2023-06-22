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
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(MockitoExtension.class)
public class MicrometerEndpointTest {

    private static final String METRICS_NAME = "metrics.name";

    @Mock
    private MeterRegistry registry;

    @Mock
    private Processor processor;

    @Mock
    private Exchange exchange;

    @Mock
    private Message in;

    private MicrometerEndpoint endpoint;

    private InOrder inOrder;

    @BeforeEach
    public void setUp() {
        endpoint = new MicrometerEndpoint(null, null, registry, Meter.Type.COUNTER, METRICS_NAME, Tags.empty()) {
            @Override
            public Producer createProducer() {
                return null;
            }

            @Override
            protected String createEndpointUri() {
                return "not real endpoint";
            }
        };
        inOrder = Mockito.inOrder(registry, processor, exchange, in);
    }

    @AfterEach
    public void tearDown() {
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testAbstractMetricsEndpoint() {
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
        assertThat(endpoint.getRegistry(), is(registry));
    }

    @Test
    public void testIsSingleton() {
        assertThat(endpoint.isSingleton(), is(true));
    }

    @Test
    public void testGetRegistry() {
        assertThat(endpoint.getRegistry(), is(registry));
    }

    @Test
    public void testGetMetricsName() {
        assertThat(endpoint.getMetricsName(), is(METRICS_NAME));
    }
}
