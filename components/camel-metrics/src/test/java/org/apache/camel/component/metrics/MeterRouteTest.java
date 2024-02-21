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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import static org.apache.camel.component.metrics.MetricsComponent.METRIC_REGISTRY_NAME;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_METER_MARK;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_METRIC_NAME;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@CamelSpringTest
public class MeterRouteTest extends CamelSpringTestSupport {

    @EndpointInject("mock:out")
    private MockEndpoint endpoint;

    @Produce("direct:in-1")
    private ProducerTemplate producer1;

    @Produce("direct:in-2")
    private ProducerTemplate producer2;

    private MetricRegistry mockRegistry;

    private Meter mockMeter;

    private InOrder inOrder;

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new AnnotationConfigApplicationContext();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        mockRegistry = Mockito.mock(MetricRegistry.class);
        context.getRegistry().bind(METRIC_REGISTRY_NAME, mockRegistry);

        mockMeter = Mockito.mock(Meter.class);
        inOrder = Mockito.inOrder(mockRegistry, mockMeter);
        when(mockRegistry.meter("A")).thenReturn(mockMeter);

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in-1")
                        .to("metrics:meter:A?mark=3179")
                        .to("mock:out");

                from("direct:in-2")
                        .to("metrics:meter:A")
                        .to("mock:out");
            }
        };
    }

    @AfterEach
    public void tearDown() {
        endpoint.reset();
        reset(mockRegistry, mockMeter);
    }

    @Test
    public void testValueSetInUri() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer1.sendBody(body);
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).meter("A");
        inOrder.verify(mockMeter, times(1)).mark(3179L);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testValueNoSetInUri() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer2.sendBody(body);
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).meter("A");
        inOrder.verify(mockMeter, times(1)).mark();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        when(mockRegistry.meter("B")).thenReturn(mockMeter);
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer1.sendBodyAndHeader(body, HEADER_METRIC_NAME, "B");
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).meter("B");
        inOrder.verify(mockMeter, times(1)).mark(3179L);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOverrideValueWithHeader() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer1.sendBodyAndHeader(body, HEADER_METER_MARK, 9926L);
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).meter("A");
        inOrder.verify(mockMeter, times(1)).mark(9926L);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testValueNoSetInUriOverrideWithHeader() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer2.sendBodyAndHeader(body, HEADER_METER_MARK, 7707370L);
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).meter("A");
        inOrder.verify(mockMeter, times(1)).mark(7707370L);
        inOrder.verifyNoMoreInteractions();
    }
}
