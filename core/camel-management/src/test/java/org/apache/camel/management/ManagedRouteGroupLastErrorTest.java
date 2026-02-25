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
import java.util.concurrent.RejectedExecutionException;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.api.management.mbean.RouteError;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedRouteGroupLastErrorTest extends ManagementTestSupport {

    @Test
    public void testLastError() throws Exception {
        // fire a message to get it running
        getMockEndpoint("mock:result").expectedMessageCount(2);
        template.sendBody("direct:first", "Hello World");
        template.sendBody("direct:second", "Bye World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(3, set.size());
        var it = set.iterator();
        ObjectName on = it.next();
        boolean registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");

        mbeanServer.invoke(on, "stopAndFail", null, null);
        on = it.next();

        mbeanServer.invoke(on, "stopAndFail", null, null);
        // leave the 3rd route okay

        set = mbeanServer.queryNames(new ObjectName("*:type=routegroups,*"), null);
        on = set.iterator().next();
        registered = mbeanServer.isRegistered(on);
        assertTrue(registered, "Should be registered");

        String group = (String) mbeanServer.getAttribute(on, "RouteGroup");
        assertEquals("myGroup", group);

        org.apache.camel.api.management.mbean.RouteError re = (RouteError) mbeanServer.getAttribute(on, "LastError");

        Assertions.assertNotNull(re);
        Assertions.assertInstanceOf(RejectedExecutionException.class, re.getException());
        Assertions.assertEquals(RouteError.Phase.STOP, re.getPhase());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:first").routeId("first").group("myGroup").to("log:foo").to("mock:result");
                from("direct:second").routeId("second").group("myGroup").to("log:foo").to("mock:result");
                from("direct:third").routeId("third").group("myGroup").to("log:foo").to("mock:result");
            }
        };
    }

}
