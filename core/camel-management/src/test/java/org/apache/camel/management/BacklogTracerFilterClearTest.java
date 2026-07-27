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

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
class BacklogTracerFilterClearTest extends ManagementTestSupport {

    /**
     * Verify that clearing the trace filter via JMX (setTraceFilter(null)) actually disables filtering so that
     * subsequent messages are traced unconditionally. Before the fix, the old code nulled the filter string but never
     * cleared the predicate, so shouldTrace() kept evaluating a stale predicate.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testClearTraceFilter() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on
                = new ObjectName(
                        "org.apache.camel:context=" + context.getManagementName()
                                 + ",type=tracer,name=BacklogTracer");
        assertTrue(mbeanServer.isRegistered(on));

        // disable removeOnDump so we can count events across phases
        mbeanServer.setAttribute(on, new Attribute("RemoveOnDump", Boolean.FALSE));

        // enable tracing with a filter that requires header "foo"
        mbeanServer.setAttribute(on, new Attribute("Enabled", Boolean.TRUE));
        mbeanServer.setAttribute(on, new Attribute("TraceFilter", "${header.foo} != null"));

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        // send message WITH header — should be traced
        template.sendBodyAndHeader("direct:start", "Matched", "foo", 123);
        assertMockEndpointsSatisfied();

        List<BacklogTracerEventMessage> events
                = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);
        int tracedWithFilter = events.size();
        assertTrue(tracedWithFilter > 0, "Message with header should be traced");

        // send message WITHOUT header — should NOT be traced due to filter
        resetMocks();
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:start", "Not Matched");
        assertMockEndpointsSatisfied();

        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);
        assertEquals(tracedWithFilter, events.size(), "Unmatched message should NOT add trace events");

        // clear the filter
        mbeanServer.setAttribute(on, new Attribute("TraceFilter", null));
        String traceFilter = (String) mbeanServer.getAttribute(on, "TraceFilter");
        assertNull(traceFilter, "TraceFilter should be null after clearing");

        // send a message WITHOUT header — should now be traced since filter is cleared
        resetMocks();
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        template.sendBody("direct:start", "After Clear");
        assertMockEndpointsSatisfied();

        events = (List<BacklogTracerEventMessage>) mbeanServer.invoke(on, "dumpAllTracedMessages", null, null);
        assertFalse(events.size() == tracedWithFilter,
                "After clearing filter, additional trace events should appear (before=" + tracedWithFilter
                                                       + ", after=" + events.size() + ")");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);
                context.setBacklogTracingStandby(true);

                from("direct:start")
                        .to("mock:foo").id("foo")
                        .to("mock:bar").id("bar");
            }
        };
    }

}
