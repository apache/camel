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
import org.apache.camel.BindToRegistry;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_HISTOGRAM_VALUE;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

@CamelSpringTest
public class DistributionSummaryRouteTest extends CamelSpringTestSupport {

    @EndpointInject("mock:out")
    private MockEndpoint endpoint;

    @Produce("direct:in-1")
    private ProducerTemplate producer1;

    @Produce("direct:in-2")
    private ProducerTemplate producer2;

    @BindToRegistry(METRICS_REGISTRY_NAME)
    private MeterRegistry registry = new SimpleMeterRegistry();

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new AnnotationConfigApplicationContext();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
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

    @AfterEach
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
