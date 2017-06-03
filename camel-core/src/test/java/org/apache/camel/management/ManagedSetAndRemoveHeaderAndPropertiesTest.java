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

import java.util.Iterator;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version
 */
public class ManagedSetAndRemoveHeaderAndPropertiesTest extends ManagementTestSupport {

    public void testSetAndRemove() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // fire a message to get it running
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(8, set.size());
        Iterator<ObjectName> it = set.iterator();

        boolean found = false;
        boolean found2 = false;
        boolean found3 = false;
        boolean found4 = false;
        boolean found5 = false;
        boolean found6 = false;
        for (int i = 0; i < 8; i++) {
            ObjectName on = it.next();

            boolean registered = mbeanServer.isRegistered(on);
            assertEquals("Should be registered", true, registered);

            // should be one with name setFoo
            String id = (String) mbeanServer.getAttribute(on, "ProcessorId");
            log.info("id = {}", id);

            found |= "setFoo".equals(id);
            found2 |= "setBeer".equals(id);
            found3 |= "unsetFoo".equals(id);
            found4 |= "unsetFoos".equals(id);
            found5 |= "unsetBeer".equals(id);
            found6 |= "unsetBeers".equals(id);
        }

        assertTrue("Should find setHeader mbean", found);
        assertTrue("Should find setProperty mbean", found2);
        assertTrue("Should find removeHeader mbean", found3);
        assertTrue("Should find removeHeaders mbean", found4);
        assertTrue("Should find removeProperty mbean", found5);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .setHeader("foo", constant("bar")).id("setFoo")
                    .setProperty("beer", constant("yes")).id("setBeer")
                    .removeHeader("foo").id("unsetFoo")
                    .removeHeaders("foo").id("unsetFoos")
                    .removeProperty("beer").id("unsetBeer")
                    .removeProperties("beer").id("unsetBeers")
                    .to("log:foo").id("logFoo")
                    .to("mock:result").id("mockResult");
            }
        };
    }

}