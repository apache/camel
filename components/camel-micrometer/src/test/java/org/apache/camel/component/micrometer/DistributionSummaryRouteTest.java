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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_HISTOGRAM_VALUE;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.junit.Assert.assertEquals;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = {DistributionSummaryRouteTest.TestConfig.class},
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class DistributionSummaryRouteTest {

    @EndpointInject("mock:out")
    private MockEndpoint endpoint;

    @Produce("direct:in-1")
    private ProducerTemplate producer1;

    @Produce("direct:in-2")
    private ProducerTemplate producer2;

    private MeterRegistry registry;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() {
                    from("direct:in-1")
                            .to("micrometer:summary:A?value=332491")
                            .to("mock:out");
                    from("direct:in-2")
                            .to("micrometer:summary:${body}?value=${header.nextValue}")
                            .to("mock:out");
                }
            };
        }

        @Bean(name = METRICS_REGISTRY_NAME)
        public MeterRegistry getMetricRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Before
    public void setup() {
        registry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(METRICS_REGISTRY_NAME, MeterRegistry.class);
    }

    @After
    public void tearDown() {
        endpoint.reset();
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBodyAndHeader(new Object(), HEADER_METRIC_NAME, "B");
        assertEquals(1L, registry.find("B").summary().count());
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideValue() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBodyAndHeader(new Object(), HEADER_HISTOGRAM_VALUE, 181D);
        DistributionSummary summary = registry.find("A").summary();
        assertEquals(1L, summary.count());
        HistogramSnapshot snapshot = summary.takeSnapshot();
        assertEquals(181.0D, snapshot.total(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testScriptEvaluationValue() throws Exception {
        endpoint.expectedMessageCount(1);
        producer2.sendBodyAndHeader("C", "nextValue", "181.0");
        DistributionSummary summary = registry.find("C").summary();
        assertEquals(1L, summary.count());
        HistogramSnapshot snapshot = summary.takeSnapshot();
        assertEquals(181.0D, snapshot.total(), 0.01D);
        endpoint.assertIsSatisfied();
    }
}
