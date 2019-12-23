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
package org.apache.camel.component.activemq;

import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.DataStructure;
import org.apache.activemq.command.DestinationInfo;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * 
 */
public class AdvisoryConsumerExample extends CamelTestSupport {

    @Test
    public void testWorks() throws Exception {
        // lets create a new queue
        template.sendBody("activemq:NewQueue." + System.currentTimeMillis(), "<hello>world!</hello>");

        Thread.sleep(10000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets force the creation of a queue up front
                from("activemq:InitialQueue").to("log:Messages");

                from("activemq:topic:ActiveMQ.Advisory.Queue?cacheLevelName=CACHE_CONSUMER").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        if (in instanceof JmsMessage) {
                            JmsMessage jmsMessage = (JmsMessage)in;
                            javax.jms.Message value = jmsMessage.getJmsMessage();
                            if (value instanceof ActiveMQMessage) {
                                ActiveMQMessage activeMQMessage = (ActiveMQMessage)value;
                                DataStructure structure = activeMQMessage.getDataStructure();
                                if (structure instanceof DestinationInfo) {
                                    DestinationInfo destinationInfo = (DestinationInfo)structure;
                                    System.out.println("Received: " + destinationInfo);
                                }
                            }
                        }
                    }
                });
            }
        };
    }
}
