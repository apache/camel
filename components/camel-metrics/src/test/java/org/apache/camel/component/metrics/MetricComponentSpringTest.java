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

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = { MetricComponentSpringTest.TestConfig.class },
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class MetricComponentSpringTest {

    @EndpointInject(uri = "mock:out")
    private MockEndpoint endpoint;

    @Produce(uri = "direct:in")
    private ProducerTemplate producer;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:in")
                            .to("metrics:counter:A?increment=512")
                            .to("mock:out");
                }
            };
        }

        @Bean(name = MetricsComponent.METRIC_REGISTRY_NAME)
        public MetricRegistry getMetricRegistry() {
            return Mockito.mock(MetricRegistry.class);
        }
    }

    @Test
    public void testMetricsRegistryFromCamelRegistry() throws Exception {
        // TODO - 12.05.2014, Lauri - is there any better way to set this up?
        MetricRegistry mockRegistry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class);
        Counter mockCounter = Mockito.mock(Counter.class);
        InOrder inOrder = Mockito.inOrder(mockRegistry, mockCounter);
        when(mockRegistry.counter("A")).thenReturn(mockCounter);

        endpoint.expectedMessageCount(1);
        producer.sendBody(new Object());
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).counter("A");
        inOrder.verify(mockCounter, times(1)).inc(512L);
        inOrder.verifyNoMoreInteractions();
    }
}
