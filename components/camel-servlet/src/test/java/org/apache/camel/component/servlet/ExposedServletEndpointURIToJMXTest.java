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
package org.apache.camel.component.servlet;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ExposedServletEndpointURIToJMXTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void exposedEndpointURIShouldContainContextAndOptions() throws Exception {
        checkServletEndpointURI("\"servlet:/test1\\?matchOnUriPrefix=true\"");
        checkServletEndpointURI("\"servlet:/test2\\?servletName=test2\"");
        checkServletEndpointURI("\"servlet:/test3\\?matchOnUriPrefix=true&servletName=test3\"");
    }

    private void checkServletEndpointURI(String servletEndpointURI) throws Exception {
        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();
        ObjectName name = new ObjectName("org.apache.camel:context=camel-1,type=endpoints,name=" + servletEndpointURI);
        Set<ObjectName> objectNamesSet = mbeanServer.queryNames(name, null);
        assertEquals("Expect one MBean for the servlet endpoint", 1, objectNamesSet.size());

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("servlet:test1?matchOnUriPrefix=true").to("mock:jmx");
                from("servlet:test2?servletName=test2").to("mock:jmx");
                from("servlet:test3?matchOnUriPrefix=true&servletName=test3").to("mock:jmx");
            }

        };
    }

}
