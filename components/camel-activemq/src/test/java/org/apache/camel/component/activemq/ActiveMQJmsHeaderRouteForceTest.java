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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsMessage;

/**
 * 
 */
public class ActiveMQJmsHeaderRouteForceTest extends ActiveMQJmsHeaderRouteTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // do not map jms message as we want to tamper with the JMS
                // message directly, and not use the Camel API for that
                from("activemq:test.a?mapJmsMessage=false").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // lets set the custom JMS headers using the JMS API
                        JmsMessage jmsMessage = assertIsInstanceOf(JmsMessage.class, exchange.getIn());

                        jmsMessage.getJmsMessage().setJMSReplyTo(replyQueue);
                        jmsMessage.getJmsMessage().setJMSCorrelationID(correlationID);
                        jmsMessage.getJmsMessage().setJMSType(messageType);
                    }
                    // force sending the incoming JMS Message, as we want to
                    // tamper with the JMS API directly
                    // instead of using the Camel API for setting JMS headers.
                }).to("activemq:test.b?preserveMessageQos=true&forceSendOriginalMessage=true");

                from("activemq:test.b").to("mock:result");
            }
        };
    }
}
