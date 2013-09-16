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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.ConsumerCache;

/**
 * @version
 */
public class ManagedConsumerCacheHitsTest extends ManagementTestSupport {

    public void testManageConsumerCache() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // always register services in JMX so we can enlist our consumer template/cache
        context.getManagementStrategy().getManagementAgent().setRegisterAlways(true);

        ConsumerCache cache = new ConsumerCache(this, context);
        context.addService(cache);

        template.sendBody("seda:a", "Hello World");

        Exchange out = cache.getConsumer(context.getEndpoint("seda:a")).receive(3000);
        assertNotNull("Should got an exchange", out);
        assertEquals("Hello World", out.getIn().getBody());

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<ObjectName>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("ConsumerCache")) {
                on = name;
                break;
            }
        }

        assertNotNull("Should have found ConsumerCache", on);

        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumCacheSize");
        assertEquals(1000, max.intValue());

        Integer current = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(1, current.intValue());

        // since we only send 1 message we should have 0 hits and 1 miss
        Long hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(0, hits.longValue());
        Long misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(1, misses.longValue());


        // send more
        // ---------

        template.sendBody("seda:b", "Hello World");
        template.sendBody("seda:c", "Hello World");
        out = cache.getConsumer(context.getEndpoint("seda:b")).receive(3000);
        assertNotNull(out);
        out = cache.getConsumer(context.getEndpoint("seda:c")).receive(3000);
        assertNotNull(out);

        // we have only consumed from 3 different endpoints so all is misses
        hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(0, hits.longValue());
        misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(3, misses.longValue());


        // send to same endpoints
        // ----------------------

        template.sendBody("seda:a", "Bye World");
        template.sendBody("seda:b", "Bye World");
        out = cache.getConsumer(context.getEndpoint("seda:a")).receive(3000);
        assertNotNull(out);
        out = cache.getConsumer(context.getEndpoint("seda:b")).receive(3000);
        assertNotNull(out);

        // we should have hits now
        hits = (Long) mbeanServer.getAttribute(on, "Hits");
        assertEquals(2, hits.longValue());
        misses = (Long) mbeanServer.getAttribute(on, "Misses");
        assertEquals(3, misses.longValue());


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
                // note: this route is not used in the test
                from("direct:start").to("mock:result");
            }
        };
    }

}