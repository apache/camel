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
package org.apache.camel.component.xmpp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xmpp.XmppMessage;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

import org.jivesoftware.smack.packet.Message;

/**
 * @version $Revision$
 */
public class XmppRouteChatTest extends CamelTestSupport {
    protected MockEndpoint consumerEndpoint;
    protected MockEndpoint producerEndpoint;
    protected String body1 = "the first message";
    protected String body2 = "the second message";


    @Test
    public void testXmppChat() {
        producerEndpoint = (MockEndpoint)context.getEndpoint("mock:fromProducer");
        consumerEndpoint = (MockEndpoint)context.getEndpoint("mock:fromConsumer");

        //will send chat messages to the consumer
        template.sendBody("direct:toProducer", body1);
        template.sendBody("direct:toProducer", body2);
        consumerEndpoint.expectedBodiesReceived(body1, body2);
        
        template.sendBody("direct:toConsumer", body1);
        template.sendBody("direct:toConsumer", body2);
        consumerEndpoint.expectedBodiesReceived(body1, body2);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                from("direct:toConsumer")
                    .to(getConsumerUri());

                from("direct:toProducer")
                    .to(getProducerUri());

                from(getConsumerUri())
                    .to("mock:fromConsumer");

                from(getProducerUri())
                    .to("mock:fromProducer");

                from("direct:getString")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            XmppMessage xmppMessage = (XmppMessage)exchange.getIn();
                            Message message = xmppMessage.getXmppMessage();
                            exchange.getIn().setBody(message.getBody());
                        }
                    })
                    .to("direct:getStringResult");

            }
        };
    }

    protected String getProducerUri() {
        return "xmpp://camel_producer@jabber.org:5222/camel_consumer@jabber.org?user=camel_producer&password=secret&serviceName=jabber.org";
    }
    
    protected String getConsumerUri() {
        return "xmpp://camel_consumer@jabber.org:5222/camel_producer@jabber.org?user=camel_consumer&password=secret&serviceName=jabber.org";
    }

}
