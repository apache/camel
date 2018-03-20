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

import java.util.SortedMap;
import java.util.TreeMap;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.metrics.GaugeProducer.CamelMetricsGauge;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.component.metrics.MetricsComponent.METRIC_REGISTRY_NAME;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_GAUGE_SUBJECT;
import static org.apache.camel.component.metrics.MetricsConstants.HEADER_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = { GaugeRouteTest.TestConfig.class },
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class GaugeRouteTest {

    private static SortedMap<String, Gauge> mockGauges = new TreeMap<>();

    @EndpointInject(uri = "mock:out")
    private MockEndpoint endpoint;

    @Produce(uri = "direct:in-1")
    private ProducerTemplate producer1;

    @Produce(uri = "direct:in-2")
    private ProducerTemplate producer2;

    private MetricRegistry mockRegistry;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:in-1")
                            .to("metrics:gauge:A?subject=#mySubject")
                            .to("mock:out");

                    from("direct:in-2")
                            .setHeader(HEADER_METRIC_NAME, constant("B"))
                            .setHeader(HEADER_GAUGE_SUBJECT, constant("my overriding subject"))
                            .to("metrics:gauge:A?subject=#mySubject")
                            .to("mock:out");

                }
            };
        }

        @Bean(name = METRIC_REGISTRY_NAME)
        public MetricRegistry getMetricRegistry() {
            MetricRegistry registry = Mockito.mock(MetricRegistry.class);
            when(registry.getGauges()).thenReturn(mockGauges);
            when(registry.register(anyString(), any())).then(
                    new Answer<CamelMetricsGauge>() {
                        @Override
                        public CamelMetricsGauge answer(InvocationOnMock invocation) throws Throwable {
                            mockGauges.put(invocation.getArgument(0), invocation.getArgument(1));
                            return invocation.getArgument(1);
                        }
                    });
            return registry;
        }

        @Bean(name = "mySubject")
        public String getSubject() {
            return "my subject";
        }
    }

    @Before
    public void setup() {
        // TODO - 12.05.2014, Lauri - is there any better way to set this up?
        mockRegistry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(METRIC_REGISTRY_NAME, MetricRegistry.class);
    }

    @After
    public void tearDown() {
        endpoint.reset();
        mockGauges.clear();
    }

    @Test
    public void testDefault() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBody(new Object());
        endpoint.assertIsSatisfied();
        verify(mockRegistry, times(1)).register(eq("A"), argThat(new ArgumentMatcher<CamelMetricsGauge>() {
            @Override
            public boolean matches(CamelMetricsGauge argument) {
                return "my subject".equals(argument.getValue());
            }
        }));
    }

    @Test
    public void testOverride() throws Exception {
        verify(mockRegistry, times(1)).register(eq("A"), argThat(new ArgumentMatcher<CamelMetricsGauge>() {
            @Override
            public boolean matches(CamelMetricsGauge argument) {
                return "my subject".equals(argument.getValue());
            }
        }));
        endpoint.expectedMessageCount(1);
        producer2.sendBody(new Object());
        endpoint.assertIsSatisfied();
        verify(mockRegistry, times(1)).register(eq("B"), argThat(new ArgumentMatcher<CamelMetricsGauge>() {
            @Override
            public boolean matches(CamelMetricsGauge argument) {
                return "my overriding subject".equals(argument.getValue());
            }
        }));
    }

}
