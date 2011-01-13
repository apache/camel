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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class ManagedUnregisterProducerTest extends ManagementTestSupport {

    public void testUnregisterProducer() throws Exception {
        // send a message so the managed producer is started
        // do this "manually" to avoid camel managing the direct:start producer as well
        // this makes the unit test easier as we only have 1 managed producer = mock:result
        Endpoint endpoint = context.getEndpoint("direct:start");
        Producer producer = endpoint.createProducer();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");

        producer.start();
        producer.process(exchange);
        producer.stop();

        // TODO: producers are not managed due they can lead to memory leak CAMEL-2484

//        MBeanServer mbeanServer = getMBeanServer();

//        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=producers,*"), null);
//        assertEquals(0, set.size());

//        ObjectName on = set.iterator().next();

//        assertTrue("Should be registered", mbeanServer.isRegistered(on));
//        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
//        assertEquals("mock://result", uri);

        // TODO: populating route id on producers is not implemented yet
//        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
//        assertEquals("route1", routeId);

//        Boolean singleton = (Boolean) mbeanServer.getAttribute(on, "Singleton");
//        assertEquals(Boolean.TRUE, singleton);

        context.stop();

//        assertFalse("Should no longer be registered", mbeanServer.isRegistered(on));
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