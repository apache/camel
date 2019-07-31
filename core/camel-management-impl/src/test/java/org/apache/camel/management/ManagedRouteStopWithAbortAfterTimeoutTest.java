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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ManagedRouteStopWithAbortAfterTimeoutTest extends ManagementTestSupport {

    @Test
    public void testStopRouteWithAbortAfterTimeoutTrue() throws Exception {
        // JMX tests dont work well on AIX or windows CI servers (hangs them)
        if (isPlatform("aix") || isPlatform("windows")) {
            return;
        }

        MockEndpoint mockEP = getMockEndpoint("mock:result");
        mockEP.setExpectedMessageCount(10);
        
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        // confirm that route has started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("route should be started", ServiceStatus.Started.name(), state);

        //send some message through the route
        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:start", "message-" + i);
        }
        
        // stop route with a 1s timeout and abortAfterTimeout=true (should abort after 1s)
        Long timeout = new Long(1);
        Boolean abortAfterTimeout = Boolean.TRUE;
        Object[] params = {timeout, abortAfterTimeout};
        String[] sig = {"java.lang.Long", "java.lang.Boolean"};
        Boolean stopRouteResponse = (Boolean) mbeanServer.invoke(on, "stop", params, sig);

        // confirm that route is still running
        state = (String) mbeanServer.getAttribute(on, "State");
        assertFalse("stopRoute response should be False", stopRouteResponse);
        assertEquals("route should still be started", ServiceStatus.Started.name(), state);
        
        //send some more messages through the route
        for (int i = 5; i < 10; i++) {
            template.sendBody("seda:start", "message-" + i);
        }

        mockEP.assertIsSatisfied();
    }

    @Test
    public void testStopRouteWithAbortAfterTimeoutFalse() throws Exception {
        // JMX tests dont work well on AIX or windows CI servers (hangs them)
        if (isPlatform("aix") || isPlatform("windows")) {
            return;
        }

        MockEndpoint mockEP = getMockEndpoint("mock:result");
        
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        // confirm that route has started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("route should be started", ServiceStatus.Started.name(), state);

        //send some message through the route
        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:start", "message-" + i);
        }
        
        // stop route with a 1s timeout and abortAfterTimeout=false (normal timeout behavior)
        Long timeout = new Long(1);
        Boolean abortAfterTimeout = Boolean.FALSE;
        Object[] params = {timeout, abortAfterTimeout};
        String[] sig = {"java.lang.Long", "java.lang.Boolean"};
        Boolean stopRouteResponse = (Boolean) mbeanServer.invoke(on, "stop", params, sig);

        // confirm that route is stopped
        state = (String) mbeanServer.getAttribute(on, "State");
        assertTrue("stopRoute response should be True", stopRouteResponse);
        assertEquals("route should be stopped", ServiceStatus.Stopped.name(), state);
        
        // send some more messages through the route
        for (int i = 5; i < 10; i++) {
            template.sendBody("seda:start", "message-" + i);
        }
        
        Thread.sleep(1000);
        
        assertTrue("Should not have received more than 5 messages", mockEP.getExchanges().size() <= 5);
    }

    static ObjectName getRouteObjectName(MBeanServer mbeanServer) throws Exception {
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());
        return set.iterator().next();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // shutdown this test faster
                context.getShutdownStrategy().setTimeout(3);

                from("seda:start").delay(100).to("mock:result");
            }
        };
    }

}
