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
package org.apache.camel.management;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The input hits of a route are the messages its consumer received: the sub-exchanges of a split, multicast or
 * recipient list are no input of the route, and a route that consumes from direct has an input hit per message it gets.
 */
@DisabledOnOs(OS.AIX)
public class ManagedEndpointInputHitsTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return camelContext;
    }

    @Test
    public void testSplitIsOneInput() throws Exception {
        getMockEndpoint("mock:split").expectedMessageCount(6);
        template.sendBody("seda:split", "a,b,c");
        template.sendBody("seda:split", "d,e,f");
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(2, hits("split", "seda://split", "in"));
    }

    @Test
    public void testMulticastIsOneInputAndAnInputOfTheRouteItCallsOverDirect() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:sub").expectedMessageCount(1);
        template.sendBody("seda:multicast", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, hits("multicast", "seda://multicast", "in"));
        assertEquals(1, hits("multicast", "direct://sub", "out"));
        assertEquals(1, hits("sub", "direct://sub", "in"));
    }

    @Test
    public void testRecipientListIsOneInputAndAnInputOfTheRouteItCallsOverDirect() throws Exception {
        getMockEndpoint("mock:b").expectedMessageCount(2);
        getMockEndpoint("mock:sub").expectedMessageCount(2);
        template.sendBodyAndHeader("seda:list", "Hello World", "to", "mock:b,direct:sub");
        template.sendBodyAndHeader("seda:list", "Bye World", "to", "mock:b,direct:sub");
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(2, hits("list", "seda://list", "in"));
        assertEquals(2, hits("sub", "direct://sub", "in"));
    }

    @Test
    public void testDirectIsAnInputOfTheRouteThatConsumesIt() throws Exception {
        getMockEndpoint("mock:sub").expectedMessageCount(3);
        for (int i = 0; i < 3; i++) {
            template.sendBody("seda:call", "Hello " + i);
        }
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(3, hits("call", "seda://call", "in"));
        assertEquals(3, hits("call", "direct://sub", "out"));
        assertEquals(3, hits("sub", "direct://sub", "in"));
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
                from("seda:split").routeId("split").split(body().tokenize(",")).to("mock:split");
                from("seda:multicast").routeId("multicast").multicast().to("mock:a", "direct:sub");
                from("seda:list").routeId("list").recipientList(header("to"));
                from("seda:call").routeId("call").to("direct:sub");
                from("direct:sub").routeId("sub").to("mock:sub");
            }
        };
    }
}
