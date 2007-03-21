/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jms.JmsExchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision$
 */
public class JmsRouteTest extends TestCase {
    public void testJmsRoute() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        CamelContext container = new DefaultCamelContext<Exchange>();

        // lets configure some componnets
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        container.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        // lets add some routes
        container.setRoutes(new RouteBuilder() {
            public void configure() {
                from("jms:activemq:test.a").to("jms:activemq:test.b");
                from("jms:activemq:test.b").process(new Processor<JmsExchange>() {
                    public void onExchange(JmsExchange exchange) {
                        System.out.println("Received exchange: " + exchange.getIn());
                        latch.countDown();
                    }
                });
            }
        });

        
        container.activateEndpoints();
        
        // now lets fire in a message
        Endpoint<JmsExchange> endpoint = container.resolveEndpoint("jms:activemq:test.a");
        JmsExchange exchange2 = endpoint.createExchange();
        //exchange2.setInBody("Hello there!")
        exchange2.getIn().getHeaders().setHeader("cheese", 123);
        endpoint.onExchange(exchange2);

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not recieve the message!", received);

        container.deactivateEndpoints();
    }
}
