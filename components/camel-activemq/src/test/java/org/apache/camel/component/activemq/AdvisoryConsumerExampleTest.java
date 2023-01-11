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
import org.apache.camel.component.activemq.support.ActiveMQTestSupport;
import org.apache.camel.component.jms.JmsMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.activemq.ActiveMQComponent.activeMQComponent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 
 */
public class AdvisoryConsumerExampleTest extends ActiveMQTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AdvisoryConsumerExampleTest.class);

    @Test
    public void testWorks() {
        // lets create a new queue
        assertDoesNotThrow(
                () -> template.sendBody("activemq:NewQueue." + System.currentTimeMillis(), "<hello>world!</hello>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.addComponent("activemq", activeMQComponent(vmUri("?broker.persistent=false")));

                // lets force the creation of a queue up front
                from("activemq:InitialQueue").to("log:Messages");

                from("activemq:topic:ActiveMQ.Advisory.Queue?cacheLevelName=CACHE_CONSUMER").process(new Processor() {
                    public void process(Exchange exchange) {
                        Message in = exchange.getIn();
                        if (in instanceof JmsMessage) {
                            JmsMessage jmsMessage = (JmsMessage) in;
                            jakarta.jms.Message value = jmsMessage.getJmsMessage();
                            if (value instanceof ActiveMQMessage) {
                                ActiveMQMessage activeMQMessage = (ActiveMQMessage) value;
                                DataStructure structure = activeMQMessage.getDataStructure();
                                if (structure instanceof DestinationInfo) {
                                    DestinationInfo destinationInfo = (DestinationInfo) structure;
                                    LOG.info("Received: {}", destinationInfo);
                                }
                            }
                        }
                    }
                });
            }
        };
    }
}
