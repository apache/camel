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
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class ManagedErrorHandlerRedeliveryTest extends ContextTestSupport {

    private static int counter;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    public void testManagedErrorHandlerRedelivery() throws Exception {
        counter = 0;

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumRedeliveries");
        assertEquals(5, max.intValue());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertEquals(3, counter);

        assertMockEndpointsSatisfied();

        // now change to 0 attempts and try again
        counter = 0;
        mock.reset();
        mock.expectedMessageCount(0);
        mbeanServer.setAttribute(on, new Attribute("MaximumRedeliveries", 0));

        try {
            template.sendBody("direct:start", "Bye World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", cause.getMessage());

        }

        assertEquals(1, counter);

        // and should now be 0
        max = (Integer) mbeanServer.getAttribute(on, "MaximumRedeliveries");
        assertEquals(0, max.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(5));

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        counter++;
                        if (counter < 3) {
                            throw new  IllegalArgumentException("Forced");
                        }
                    }
                }).to("mock:result");
            }
        };
    }
}