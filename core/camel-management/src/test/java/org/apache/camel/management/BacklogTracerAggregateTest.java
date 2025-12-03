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

import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledOnOs(OS.AIX)
public class BacklogTracerAggregateTest extends ManagementTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBacklogTracerAggregate() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=tracer,name=BacklogTracer");
        assertNotNull(on);
        mbeanServer.isRegistered(on);

        Boolean enabled = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, enabled, "Should be enabled");

        getMockEndpoint("mock:input").expectedMessageCount(3);
        getMockEndpoint("mock:output").expectedMessageCount(3);
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C");

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");

        assertMockEndpointsSatisfied();

        List<BacklogTracerEventMessage> events
                = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);

        assertNotNull(events);
        assertEquals(19, events.size());

        // this is the 1st incoming message
        BacklogTracerEventMessage event = events.get(0);
        Assertions.assertTrue(event.isFirst());
        Assertions.assertFalse(event.isLast());
        Assertions.assertEquals("direct://start", event.getEndpointUri());
        JsonObject jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=A}", jo.getMap("message").get("body").toString());
        event = events.get(4);
        Assertions.assertFalse(event.isFirst());
        Assertions.assertTrue(event.isLast());
        Assertions.assertEquals("direct://start", event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=A}", jo.getMap("message").get("body").toString());

        // this is the 2nd incoming message
        event = events.get(5);
        Assertions.assertTrue(event.isFirst());
        Assertions.assertFalse(event.isLast());
        Assertions.assertEquals("direct://start", event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=B}", jo.getMap("message").get("body").toString());
        event = events.get(9);
        Assertions.assertFalse(event.isFirst());
        Assertions.assertTrue(event.isLast());
        Assertions.assertEquals("direct://start", event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=B}", jo.getMap("message").get("body").toString());

        // this is the 3rd incoming message
        event = events.get(10);
        Assertions.assertTrue(event.isFirst());
        Assertions.assertFalse(event.isLast());
        Assertions.assertEquals("direct://start", event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=C}", jo.getMap("message").get("body").toString());
        event = events.get(14);
        Assertions.assertFalse(event.isFirst());
        Assertions.assertTrue(event.isLast());
        Assertions.assertEquals("direct://start", event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=C}", jo.getMap("message").get("body").toString());

        // this is the outgoing aggregation result
        event = events.get(15);
        Assertions.assertTrue(event.isFirst());
        Assertions.assertFalse(event.isLast());
        Assertions.assertNull(event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=A,B,C}", jo.getMap("message").get("body").toString());
        event = events.get(18);
        Assertions.assertFalse(event.isFirst());
        Assertions.assertTrue(event.isLast());
        Assertions.assertNull(event.getEndpointUri());
        jo = (JsonObject) event.asJSon();
        Assertions.assertEquals("{type=java.lang.String, value=A,B,C}", jo.getMap("message").get("body").toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setBacklogTracing(true);

                from("direct:start").routeId("myRoute")
                        .to("mock:input").id("input")
                        .aggregate(constant(true)).completionSize(3).aggregationStrategy(AggregationStrategies.string(",")).id("aggregate")
                            .log("aggregated ${body}").id("log")
                            .to("mock:result").id("result")
                        .end()
                        .to("mock:output").id("output");
            }
        };
    }

}
