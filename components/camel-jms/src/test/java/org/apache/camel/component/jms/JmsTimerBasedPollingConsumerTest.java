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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version $Revision$
 */
public class JmsTimerBasedPollingConsumerTest extends CamelTestSupport {

    @Test
    public void testJmsTimerBasedPollingConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectsAscending(header("number"));
        mock.expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("activemq:queue.inbox", "World");
        }

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false");
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                MyCoolBean cool = new MyCoolBean();
                cool.setProducer(template);
                cool.setConsumer(consumer);

                from("timer://foo?period=5000").bean(cool, "someBusinessLogic");

                from("activemq:queue.foo").to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    public static class MyCoolBean {

        private int count;
        private ConsumerTemplate consumer;
        private ProducerTemplate producer;

        public void setConsumer(ConsumerTemplate consumer) {
            this.consumer = consumer;
        }

        public void setProducer(ProducerTemplate producer) {
            this.producer = producer;
        }

        public void someBusinessLogic() {
            // loop to empty queue
            while (true) {
                // receive the message from the queue, wait at most 3 sec
                String msg = consumer.receiveBody("activemq:queue.inbox", 3000, String.class);
                if (msg == null) {
                    // no more messages in queue
                    break;
                }

                // do something with body
                msg = "Hello " + msg;

                // send it to the next queue
                producer.sendBodyAndHeader("activemq:queue.foo", msg, "number", count++);
            }
        }
    }
    // END SNIPPET: e2

}
