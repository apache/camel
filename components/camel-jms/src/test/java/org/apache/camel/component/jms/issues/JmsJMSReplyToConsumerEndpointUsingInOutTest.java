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
package org.apache.camel.component.jms.issues;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.activemq.camel.component.ActiveMQComponent.activeMQComponent;

/**
 * Unit test using a fixed replyTo specified on the JMS endpoint
 *
 * @version $Revision$
 */
public class JmsJMSReplyToConsumerEndpointUsingInOutTest extends CamelTestSupport {
    private static final String MQURI = "vm://localhost?broker.persistent=false&broker.useJmx=false";
    private ActiveMQComponent amq;

    @Test
    public void testCustomJMSReplyToInOut() throws Exception {
        template.sendBody("activemq:queue:hello", "What is your name?");

        String reply = consumer.receiveBody("activemq:queue:namedReplyQueue", 5000, String.class);
        assertEquals("My name is Camel", reply);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:hello?replyTo=queue:namedReplyQueue")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("My name is Camel");
                        }
                    });
            }
        };
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        amq = activeMQComponent(MQURI);
        camelContext.addComponent("activemq", amq);
        return camelContext;
    }

}