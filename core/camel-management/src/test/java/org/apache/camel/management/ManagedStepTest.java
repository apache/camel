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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedSendProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedStepMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedStepTest extends ManagementTestSupport {

    @Test
    public void testManageStep() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=steps,name=\"foo\"");

        // should be on route1
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("route1", routeId);

        // should be step foo
        String stepId = (String) mbeanServer.getAttribute(on, "StepId");
        assertEquals("foo", stepId);

        String camelId = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", camelId);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        ManagedCamelContext mcc = context.getExtension(ManagedCamelContext.class);
        ManagedStepMBean step = mcc.getManagedStep("foo");

        assertEquals("foo", step.getProcessorId());
        assertEquals(1, step.getExchangesCompleted());

        String xml = mcc.getManagedCamelContext().dumpStepStatsAsXml(false);
        assertNotNull(xml);
        assertTrue(xml.contains("<stepStat id=\"foo\""));

        xml = mcc.getManagedCamelContext().dumpStepStatsAsXml(true);
        assertNotNull(xml);
        assertTrue(xml.contains("<stepStat id=\"foo\""));

        // the processors within the step should point-back to the parent step
        ManagedSendProcessorMBean mp = mcc.getManagedProcessor("abc", ManagedSendProcessorMBean.class);
        assertNotNull(mp);
        assertEquals("route1", mp.getRouteId());
        assertEquals("foo", mp.getStepId());
        assertEquals("log://foo", mp.getDestination());

        mp = mcc.getManagedProcessor("def", ManagedSendProcessorMBean.class);
        assertNotNull(mp);
        assertEquals("route1", mp.getRouteId());
        assertEquals("foo", mp.getStepId());
        assertEquals("mock://foo", mp.getDestination());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("route1")
                    .step("foo")
                        .to("log:foo").id("abc")
                        .to("mock:foo").id("def")
                    .end()
                    .to("mock:result");
            }
        };
    }

}
