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
package org.apache.camel.component.resilience4j;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The circuit breaker runs a copy of the exchange with its own unit of work: the copy is no second input of the route.
 */
public class ResilienceInputHitsTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    @Test
    public void testOneInputPerMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("in stock", "in stock", "in stock");
        for (int i = 0; i < 3; i++) {
            template.sendBody("seda:check", "CAMEL-MUG");
        }
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(3, hits("check", "seda://check", "in"));
        assertEquals(3, hits("supplier", "direct://supplier", "in"));
    }

    private long hits(String routeId, String uri, String direction) {
        return context.getRuntimeEndpointRegistry().getEndpointStatistics().stream()
                .filter(s -> routeId.equals(s.getRouteId()) && uri.equals(s.getUri()) && direction.equals(s.getDirection()))
                .mapToLong(RuntimeEndpointRegistry.Statistic::getHits).sum();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:check").routeId("check")
                        .circuitBreaker()
                            .to("direct:supplier")
                        .onFallback()
                            .transform().constant("not called")
                        .end()
                        .to("mock:result");

                from("direct:supplier").routeId("supplier").transform().constant("in stock");
            }
        };
    }
}
