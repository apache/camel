/**
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
import javax.management.AttributeValueExp;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StringValueExp;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedResetIncludeRoutesTest extends ManagementTestSupport {

    public void testReset() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        QueryExp queryExp = Query.match(new AttributeValueExp("RouteId"), new StringValueExp("first"));
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), queryExp);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        // send in 5 messages
        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");
        template.sendBody("direct:start", "D");
        template.sendBody("direct:start", "E");

        // and 1 for the 2nd route
        template.sendBody("direct:baz", "F");

        assertMockEndpointsSatisfied();

        // should be 5 on the route
        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(5, completed.longValue());

        // and on the processors as well
        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), queryExp);
        assertEquals(3, set.size());
        for (ObjectName name : set) {
            completed = (Long) mbeanServer.getAttribute(name, "ExchangesCompleted");
            assertEquals(5, completed.longValue());
        }

        // reset which should reset all routes also
        ObjectName ctx = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");
        mbeanServer.invoke(ctx, "reset", new Object[]{true}, new String[]{"boolean"});

        // should be 0 on the route
        completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(0, completed.longValue());

        // and on the processors as well
        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), queryExp);
        assertEquals(3, set.size());
        for (ObjectName name : set) {
            completed = (Long) mbeanServer.getAttribute(name, "ExchangesCompleted");
            assertEquals(0, completed.longValue());
        }

        // test that the 2nd route is also reset
        queryExp = Query.match(new AttributeValueExp("RouteId"), new StringValueExp("second"));
        set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), queryExp);
        assertEquals(1, set.size());
        on = set.iterator().next();

        completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(0, completed.longValue());

        // and on the processors as well
        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), queryExp);
        assertEquals(1, set.size());
        for (ObjectName name : set) {
            completed = (Long) mbeanServer.getAttribute(name, "ExchangesCompleted");
            assertEquals(0, completed.longValue());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("first")
                    .to("log:foo").id("foo")
                    .to("log:bar").id("bar")
                    .to("mock:result").id("mock");

                from("direct:baz").routeId("second")
                    .to("mock:baz").id("baz");
            }
        };
    }

}