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
import com.codahale.metrics.Timer;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.component.metrics.MetricsComponent.METRIC_REGISTRY_NAME;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_TIMER_ACTION;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = { TimerRouteTest.TestConfig.class },
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class TimerRouteTest {

    @EndpointInject(uri = "mock:out")
    private MockEndpoint endpoint;

    @Produce(uri = "direct:in-1")
    private ProducerTemplate producer1;

    @Produce(uri = "direct:in-2")
    private ProducerTemplate producer2;

    private MetricRegistry mockRegistry;

    private Timer mockTimer;

    private InOrder inOrder;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:in-1")
                            .to("metrics:timer:A?action=start")
                            .to("mock:out");

                    from("direct:in-2")
                            .to("metrics:timer:A")
                            .to("mock:out");
                }
            };
        }

        @Bean(name = METRIC_REGISTRY_NAME)
        public MetricRegistry getMetricRegistry() {
            return Mockito.mock(MetricRegistry.class);
        }
    }

    @Before
    public void setup() {
        // TODO - 12.05.2014, Lauri - is there any better way to set this up?
        mockRegistry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(METRIC_REGISTRY_NAME, MetricRegistry.class);
        mockTimer = Mockito.mock(Timer.class);
        inOrder = Mockito.inOrder(mockRegistry, mockTimer);
    }

    @After
    public void tearDown() {
        endpoint.reset();
        reset(mockRegistry, mockTimer);
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        when(mockRegistry.timer("B")).thenReturn(mockTimer);

        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer1.sendBodyAndHeader(body, HEADER_METRIC_NAME, "B");
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).timer("B");
        inOrder.verify(mockTimer, times(1)).time();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOverrideExistingAction() throws Exception {
        when(mockRegistry.timer("A")).thenReturn(mockTimer);
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer1.sendBodyAndHeader(body, HEADER_TIMER_ACTION, MetricsTimerAction.stop);
        endpoint.assertIsSatisfied();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOverrideNoAction() throws Exception {
        when(mockRegistry.timer("A")).thenReturn(mockTimer);
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer2.sendBodyAndHeader(body, HEADER_TIMER_ACTION, MetricsTimerAction.start);
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).timer("A");
        inOrder.verify(mockTimer, times(1)).time();
        inOrder.verifyNoMoreInteractions();
    }
}
