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
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An InOut consumer sends a reply per message it received, as an out hit of its endpoint: the sub-exchanges of a split,
 * multicast or recipient list are no reply, and a route called over direct sends none.
 */
@DisabledOnOs(OS.AIX)
public class ManagedEndpointReplyHitsTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return camelContext;
    }

    @Test
    public void testSplitIsOneReply() {
        assertEquals("done", template.requestBody("seda:split", "a,b,c"));
        assertEquals("done", template.requestBody("seda:split", "d,e,f"));

        assertEquals(2, hits("split", "seda://split", "out"));
    }

    @Test
    public void testMulticastIsOneReply() {
        assertEquals("done", template.requestBody("seda:multicast", "Hello World"));

        assertEquals(1, hits("multicast", "seda://multicast", "out"));
        assertEquals(0, hits("sub", "direct://sub", "out"));
    }

    @Test
    public void testRecipientListIsOneReply() {
        assertEquals("done", template.requestBodyAndHeader("seda:list", "Hello World", "to", "mock:b,direct:sub"));
        assertEquals("done", template.requestBodyAndHeader("seda:list", "Bye World", "to", "mock:b,direct:sub"));

        assertEquals(2, hits("list", "seda://list", "out"));
        assertEquals(0, hits("sub", "direct://sub", "out"));
    }

    @Test
    public void testARouteCalledOverDirectSendsNoReply() {
        for (int i = 0; i < 3; i++) {
            template.requestBody("seda:call", "Hello " + i);
        }

        assertEquals(3, hits("call", "seda://call", "out"));
        assertEquals(3, hits("call", "direct://sub", "out"));
        assertEquals(0, hits("sub", "direct://sub", "out"));
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
                from("seda:split").routeId("split").split(body().tokenize(",")).to("mock:split").end()
                        .transform(constant("done"));
                from("seda:multicast").routeId("multicast").multicast().to("mock:a", "direct:sub").end()
                        .transform(constant("done"));
                from("seda:list").routeId("list").recipientList(header("to")).end().transform(constant("done"));
                from("seda:call").routeId("call").to("direct:sub");
                from("direct:sub").routeId("sub").to("mock:sub");
            }
        };
    }
}
