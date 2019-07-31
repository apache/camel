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

import java.util.Map;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Headers;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedCustomBeanTest extends ManagementTestSupport {

    @Test
    public void testManageCustomBean() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"custom\"");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "hey");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();

        String foo = (String) mbeanServer.getAttribute(on, "Foo");
        assertEquals("hey", foo);

        // change foo
        mbeanServer.setAttribute(on, new Attribute("Foo", "changed"));

        resetMocks();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "changed");
        template.sendBody("direct:start", "Camel");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .bean(new MyCustomBean()).id("custom")
                    .to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    @ManagedResource(description = "My Managed Bean")
    public static class MyCustomBean {
        private String foo = "hey";

        @ManagedAttribute
        public String getFoo() {
            return foo;
        }

        @ManagedAttribute
        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String doSomething(String body, @Headers Map<Object, Object> headers) throws Exception {
            headers.put("foo", foo);
            return "Hello " + body;
        }
    }
    // END SNIPPET: e1

}
