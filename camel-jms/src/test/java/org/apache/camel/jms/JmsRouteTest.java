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
package org.apache.camel.jms;

import junit.framework.TestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContainer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.jms.JmsComponent.jmsComponentClientAcknowledge;

import javax.jms.ConnectionFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$
 */
public class JmsRouteTest extends TestCase {
    public void testJmsRoute() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        CamelContainer container = new CamelContainer();

        // lets configure some componnets
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        container.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        // lets add some routes
        container.routes(new RouteBuilder() {
            public void configure() {
                from("jms:activemq:test.a").to("jms:activemq:test.b");
                from("jms:activemq:test.b").process(new Processor<JmsExchange>() {
                    public void onExchange(JmsExchange exchange) {
                        System.out.println("Received exchange: " + exchange.getRequest());
                        latch.countDown();
                    }
                });
            }
        });

        // now lets fire in a message
        Endpoint<JmsExchange> endpoint = container.endpoint("jms:activemq:test.a");
        JmsExchange exchange2 = endpoint.createExchange();
        //exchange2.setInBody("Hello there!")
        exchange2.setHeader("cheese", 123);
        endpoint.send(exchange2);

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not recieve the message!", received);

        // TODO
        //container.stop();
    }
}
