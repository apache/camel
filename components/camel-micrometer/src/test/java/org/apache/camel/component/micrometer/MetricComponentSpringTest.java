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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@CamelSpringTest
public class MetricComponentSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:out")
    private MockEndpoint endpoint;

    @Produce("direct:in")
    private ProducerTemplate producer;

    @BindToRegistry(METRICS_REGISTRY_NAME)
    private MeterRegistry registry = Mockito.mock(MeterRegistry.class);

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new AnnotationConfigApplicationContext();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in")
                        .to("micrometer:counter:A?increment=512")
                        .to("mock:out");
            }
        };
    }

    @Test
    public void testMetricsRegistryFromCamelRegistry() throws Exception {
        MeterRegistry mockRegistry = endpoint.getCamelContext().getRegistry()
                .lookupByNameAndType(MicrometerConstants.METRICS_REGISTRY_NAME, MeterRegistry.class);
        Counter mockCounter = Mockito.mock(Counter.class);
        InOrder inOrder = Mockito.inOrder(mockRegistry, mockCounter);
        when(mockRegistry.counter(eq("A"), anyIterable())).thenReturn(mockCounter);
        endpoint.expectedMessageCount(1);
        producer.sendBody(new Object());
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).counter(eq("A"), anyIterable());
        inOrder.verify(mockCounter, times(1)).increment(512D);
        inOrder.verifyNoMoreInteractions();
    }
}
