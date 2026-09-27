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
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledOnOs(OS.AIX)
public class BacklogTracerAggregateStandbyTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // tracing is in standby (not enabled) and message history is not enabled
        context.setBacklogTracingStandby(true);
        context.setMessageHistory(false);
        return context;
    }

    @Test
    public void testAggregateNotTracedInStandby() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C");

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");

        assertMockEndpointsSatisfied();

        BacklogTracer tracer = context.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        assertNotNull(tracer);
        assertFalse(tracer.isEnabled());
        assertEquals(0, tracer.getTraceCounter());
        assertEquals(0, tracer.dumpAllTracedMessages().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .aggregate(constant(true)).completionSize(3).aggregationStrategy(AggregationStrategies.string(","))
                            .id("aggregate")
                            .to("mock:result").id("result")
                        .end();
            }
        };
    }

}
