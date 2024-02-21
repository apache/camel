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
package org.apache.camel.component.undertow.rest;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestManagementTest extends BaseUndertowTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testRestManagement() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> s = mbeanServer.queryNames(
                new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",type=endpoints,*"), null);
        assertEquals(8, s.size(), "Could not find 8 endpoints: " + s);

        // there should be 3 rest endpoints
        long count = s.stream().filter(p -> p.getCanonicalName().contains("rest")).count();
        assertEquals(3, count, "There should be 3 rest endpoints");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                restConfiguration().component("undertow").host("localhost").port(getPort());

                rest("/say")
                    .get("/hello").to("direct:hello")
                    .get("/bye").consumes("application/json").to("direct:bye")
                    .post("/bye").to("mock:update");

                from("direct:hello")
                        .transform().constant("Hello World");

                from("direct:bye")
                        .transform().constant("Bye World");
            }
        };
    }
}
