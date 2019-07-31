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

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.mbean.ManagedThrottlingExceptionRoutePolicyMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.throttling.ThrottlingExceptionHalfOpenHandler;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.Test;

public class ManagedThrottlingExceptionRoutePolicyTest  extends ManagementTestSupport {

    @Test
    public void testRoutes() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }
        
        MBeanServer mbeanServer = getMBeanServer();

        // get the Camel route
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();
        boolean registered = mbeanServer.isRegistered(on);
        assertEquals("Should be registered", true, registered);

        // check the starting endpoint uri
        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        assertEquals("direct://start", uri);

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        // should have ThrottlingExceptionRoutePolicy route policy
        String policy = (String) mbeanServer.getAttribute(on, "RoutePolicyList");
        assertNotNull(policy);
        assertTrue(policy.startsWith("ThrottlingExceptionRoutePolicy"));
        
        // get the RoutePolicy
        String mbeanName = String.format("org.apache.camel:context=camel-1,name=%s,type=services", policy);
        set = mbeanServer.queryNames(new ObjectName(mbeanName), null);
        assertEquals(1, set.size());
        on = set.iterator().next();
        assertTrue(mbeanServer.isRegistered(on));

        // the route has no failures
        String myType = (String) mbeanServer.getAttribute(on, "ServiceType");
        assertEquals("ThrottlingExceptionRoutePolicy", myType);
        
        ManagedThrottlingExceptionRoutePolicyMBean proxy = JMX.newMBeanProxy(mbeanServer, on, ManagedThrottlingExceptionRoutePolicyMBean.class);
        assertNotNull(proxy);
        
        // state should be closed w/ no failures
        String myState = proxy.currentState();
        assertEquals("State closed, failures 0", myState);
        
        // the route has no failures
        Integer val = proxy.getCurrentFailures();
        assertEquals(0, val.intValue());
        
        // the route has no failures
        Long lastFail = proxy.getLastFailure();
        assertEquals(0L, lastFail.longValue());
        
        // the route is closed
        Long openAt = proxy.getOpenAt();
        assertEquals(0L, openAt.longValue());
        
        // the route has a handler
        String handlerClass = proxy.getHalfOpenHandlerName();
        assertEquals("DummyHandler", handlerClass);
        
        // values set during construction of class
        Integer threshold = proxy.getFailureThreshold();
        assertEquals(10, threshold.intValue());

        Long window = proxy.getFailureWindow();
        assertEquals(1000L, window.longValue());

        Long halfOpenAfter = proxy.getHalfOpenAfter();
        assertEquals(5000L, halfOpenAfter.longValue());
        
        // change value
        proxy.setHalfOpenAfter(10000L);
        halfOpenAfter = proxy.getHalfOpenAfter();
        assertEquals(10000L, halfOpenAfter.longValue());
        
        try {
            getMockEndpoint("mock:result").expectedMessageCount(0);
            template.sendBody("direct:start", "Hello World");
            assertMockEndpointsSatisfied();
        } catch (Exception e) {
            // expected
        }
        
        // state should be closed w/ no failures
        myState = proxy.currentState();
        assertTrue(myState.contains("State closed, failures 1, last failure"));
        
        // the route has 1 failure
        val = proxy.getCurrentFailures();
        assertEquals(1, val.intValue());

        Thread.sleep(200);
        
        // the route has 1 failure X mills ago
        lastFail = proxy.getLastFailure();
        assertTrue(lastFail.longValue() > 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        ThrottlingExceptionRoutePolicy policy = new ThrottlingExceptionRoutePolicy(10, 1000, 5000, null);
        policy.setHalfOpenHandler(new DummyHandler());
        
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("testRoute")
                    .routePolicy(policy)
                    .to("log:foo")
                    .process(new BoomProcess())
                    .to("mock:result");
            }
        };
    }

    class BoomProcess implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            // need to sleep a little to cause last failure to be slow
            Thread.sleep(50);
            throw new RuntimeException("boom!");
        }
        
    }
    
    class DummyHandler implements ThrottlingExceptionHalfOpenHandler {

        @Override
        public boolean isReadyToBeClosed() {
            return false;
        }
        
    }
}
