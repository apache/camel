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

import org.apache.camel.Exchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedMessageHistoryTest extends ManagementTestSupport {

    @Test
    public void testStepOnly() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        Exchange out = template.request("direct:start", e -> {
            e.getMessage().setBody("Hello World");
        });

        assertMockEndpointsSatisfied();

        // only the step eips are in the history
        List<MessageHistory> history = out.getProperty(Exchange.MESSAGE_HISTORY, List.class);
        assertNotNull(history);
        assertEquals(3, history.size());
        assertEquals("step", history.get(0).getNode().getShortName());
        assertEquals("a", history.get(0).getNode().getId());
        assertEquals("step", history.get(1).getNode().getShortName());
        assertEquals("b", history.get(1).getNode().getId());
        assertEquals("step", history.get(2).getNode().getShortName());
        assertEquals("bar", history.get(2).getNode().getId());

        // check mbeans
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");
        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String name = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", name);
        Boolean mh = (Boolean) mbeanServer.getAttribute(on, "MessageHistory");
        assertEquals(Boolean.TRUE, mh);

        on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=services,name=DefaultMessageHistoryFactory");

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        Boolean en = (Boolean) mbeanServer.getAttribute(on, "Enabled");
        assertEquals(Boolean.TRUE, en);
        Boolean cm = (Boolean) mbeanServer.getAttribute(on, "CopyMessage");
        assertEquals(Boolean.FALSE, cm);
        String np = (String) mbeanServer.getAttribute(on, "NodePattern");
        assertEquals("step", np);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setMessageHistory(true);
                context.getMessageHistoryFactory().setNodePattern("step");

                from("direct:start")
                    .step("a")
                        .to("log:foo")
                        .to("mock:a")
                    .end()
                    .step("b")
                        .to("direct:bar")
                        .to("mock:b")
                    .end();

                from("direct:bar")
                    .step("bar")
                        .to("log:bar")
                        .to("mock:bar")
                    .end();
            }
        };
    }


}
