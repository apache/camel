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
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision$
 */
public class ManagedUnregisterProducerTest extends ContextTestSupport {

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        DefaultManagementNamingStrategy naming = (DefaultManagementNamingStrategy) context.getManagementStrategy().getManagementNamingStrategy();
        naming.setHostName("localhost");
        naming.setDomainName("org.apache.camel");
        return context;
    }

    @SuppressWarnings("unchecked")
    public void testUnregisterProducer() throws Exception {
        // send a message so the managed producer is started
        // do this "manually" to avoid camel manageging the direct:start producer as well
        // this makes the unit test easier as we only have 1 managed producer = mock:result
        Endpoint endpoint = context.getEndpoint("direct:start");
        Producer producer = endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");

        producer.start();
        producer.process(exchange);
        producer.stop();

        MBeanServer mbeanServer = context.getManagementStrategy().getManagementAgent().getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=producers,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        assertEquals("mock://result", uri);

        // TODO: populating route id on producers is not implemented yet
//        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
//        assertEquals("route1", routeId);

        context.stop();

        assertFalse("Should no longer be registered", mbeanServer.isRegistered(on));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }

}