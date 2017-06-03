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
package org.apache.camel.spring.management;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version 
 */
public class SpringManagedCustomProcessorTest extends SpringTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/management/SpringManagedCustomProcessorTest.xml");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    public void testManageCustomProcessor() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"custom\"");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "hey");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        String foo = (String) mbeanServer.getAttribute(on, "Foo");
        assertEquals("hey", foo);

        // change foo
        mbeanServer.setAttribute(on, new Attribute("Foo", "changed"));

        resetMocks();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "changed");
        template.sendBody("direct:start", "Bye World");
        assertMockEndpointsSatisfied();
    }

    @ManagedResource(description = "My Managed Component")
    public static class MyCustomProcessor implements Processor {
        private String foo = "hey";

        @ManagedAttribute(description = "Foo is the foo")
        public String getFoo() {
            return foo;
        }

        @ManagedAttribute(description = "Foo is the foo")
        public void setFoo(String foo) {
            this.foo = foo;
        }

        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader("foo", getFoo());
        }
    }

}
