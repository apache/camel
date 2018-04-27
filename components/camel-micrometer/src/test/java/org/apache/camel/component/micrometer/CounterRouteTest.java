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

import io.micrometer.core.instrument.MeterRegistry;
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

import java.util.HashMap;
import java.util.Map;

import static org.apache.camel.component.micrometer.MicrometerComponent.METRICS_REGISTRY;
import static org.apache.camel.component.micrometer.MicrometerConstants.*;
import static org.junit.Assert.assertEquals;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = { CounterRouteTest.TestConfig.class },
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class CounterRouteTest {

    @EndpointInject(uri = "mock:out")
    private MockEndpoint endpoint;

    @Produce(uri = "direct:in-1")
    private ProducerTemplate producer1;

    @Produce(uri = "direct:in-2")
    private ProducerTemplate producer2;

    @Produce(uri = "direct:in-3")
    private ProducerTemplate producer3;

    @Produce(uri = "direct:in-4")
    private ProducerTemplate producer4;

    private MeterRegistry registry;


    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:in-1")
                            .to("micrometer:counter:A?increment=5")
                            .to("mock:out");

                    from("direct:in-2")
                            .to("micrometer:counter:B?decrement=9")
                            .to("mock:out");

                    from("direct:in-3")
                            .setHeader(HEADER_COUNTER_INCREMENT, constant(417L))
                            .to("micrometer:counter:C")
                            .to("mock:out");

                    from("direct:in-4")
                            .setHeader(HEADER_COUNTER_INCREMENT, simple("${body.length}"))
                            .to("micrometer:counter:D")
                            .to("mock:out");
                }
            };
        }

        @Bean(name = METRICS_REGISTRY)
        public MeterRegistry getMetricRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Before
    public void setup() {
        registry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(METRICS_REGISTRY, MeterRegistry.class);
    }

    @After
    public void tearDown() {
        endpoint.reset();
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBodyAndHeader(new Object(), HEADER_METRIC_NAME, "A1");
        assertEquals(5.0D, registry.find("A1").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideIncrement() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBodyAndHeader(new Object(), HEADER_COUNTER_INCREMENT, 14.0D);
        assertEquals(14.0D, registry.find(MicrometerConstants.HEADER_PREFIX + "." + "A").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideDecrement() throws Exception {
        endpoint.expectedMessageCount(1);
        producer2.sendBodyAndHeader(new Object(), HEADER_COUNTER_DECREMENT, 7.0D);
        assertEquals(-7.0D, registry.find(MicrometerConstants.HEADER_PREFIX + "." + "B").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideUsingConstantValue() throws Exception {
        endpoint.expectedMessageCount(1);
        producer3.sendBody(new Object());
        assertEquals(417.0D, registry.find(MicrometerConstants.HEADER_PREFIX + "." + "C").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideUsingScriptEvaluation() throws Exception {
        endpoint.expectedMessageCount(1);
        String message = "Hello from Camel Metrics!";
        producer4.sendBody(message);
        assertEquals(message.length(), registry.find(MicrometerConstants.HEADER_PREFIX + "." + "D").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }
}
