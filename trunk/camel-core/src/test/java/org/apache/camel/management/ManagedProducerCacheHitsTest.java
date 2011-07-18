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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedProducerCacheHitsTest extends ManagementTestSupport {

    public void testManageProducerCache() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:a");
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:b");
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:c");
        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<ObjectName>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("ProducerCache")) {
                on = name;
                break;
            }
        }

        assertNotNull("Should have found ProducerCache", on);

        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumCacheSize");
        assertEquals(1000, max.intValue());

        Integer current = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(3, current.intValue());

        // since we only send 1 message to each of the 3, we will have 0 hits and 3 misses
        Long hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(0, hits.longValue());
        Long misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(3, misses.longValue());


        // now send a message to a and b so we have 2 hits
        // -----------------------------------------------
        resetMocks();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", "mock:a");
        template.sendBodyAndHeader("direct:start", "Bye World", "foo", "mock:b");
        assertMockEndpointsSatisfied();

        // since we only send 1 message to each of the 3, we will have 0 hits and 3 misses
        hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(2, hits.longValue());
        misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(3, misses.longValue());


        // and send to mock:d to have another miss
        // ---------------------------------------
        resetMocks();

        getMockEndpoint("mock:d").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hi World", "foo", "mock:d");
        assertMockEndpointsSatisfied();

        // sending to d should be a miss as this is the first time
        hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(2, hits.longValue());
        misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(4, misses.longValue());


        // reset statistics
        // ----------------
        mbeanServer.invoke(on, "resetStatistics", null, null);
        hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(0, hits.longValue());
        misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(0, misses.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").recipientList().header("foo");
            }
        };
    }

}