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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedSetAndRemoveHeaderAndPropertiesTest extends ManagementTestSupport {

    @Test
    public void testSetAndRemove() throws Exception {
        // fire a message to get it running
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(9, set.size());

        boolean found = false;
        boolean found2 = false;
        boolean found3 = false;
        boolean found4 = false;
        boolean found5 = false;
        boolean found6 = false;
        boolean found7 = false;

        for (ObjectName on : set) {
            boolean registered = mbeanServer.isRegistered(on);
            assertEquals(true, registered, "Should be registered");

            // should be one with name setFoo
            String id = (String) mbeanServer.getAttribute(on, "ProcessorId");
            log.info("id = {}", id);

            found |= "setFoo".equals(id);
            found2 |= "setBeer".equals(id);
            found3 |= "setCheese".equals(id);
            found4 |= "unsetFoo".equals(id);
            found5 |= "unsetFoos".equals(id);
            found6 |= "unsetBeer".equals(id);
            found7 |= "unsetBeers".equals(id);
        }

        assertTrue(found, "Should find setHeader mbean");
        assertTrue(found2, "Should find setProperty mbean");
        assertTrue(found3, "Should find setVariable mbean");
        assertTrue(found4, "Should find removeHeader mbean");
        assertTrue(found5, "Should find removeHeaders mbean");
        assertTrue(found6, "Should find removeProperty mbean");
        assertTrue(found7, "Should find removeProperty mbean");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                        .setHeader("foo", constant("bar")).id("setFoo")
                        .setProperty("beer", constant("yes")).id("setBeer")
                        .setVariable("cheese", constant("gauda")).id("setCheese")
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
