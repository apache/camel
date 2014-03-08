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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsInOutExclusiveTopicTest extends CamelTestSupport {

    @Test
    public void testJmsInOutExclusiveTopicTest() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String out = template.requestBody("direct:start", "Camel", String.class);
        assertEquals("Bye Camel", out);

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .to("activemq:topic:news?replyToType=Exclusive&replyTo=queue:back")
                    .to("mock:result");

                from("activemq:topic:news?disableReplyTo=true")
                        .transform(body().prepend("Bye "))
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                String replyTo = exchange.getIn().getHeader("JMSReplyTo", String.class);
                                String cid = exchange.getIn().getHeader("JMSCorrelationID", String.class);

                                log.info("ReplyTo: {}", replyTo);
                                log.info("CorrelationID: {}", cid);
                                if (replyTo != null && cid != null) {
                                    // wait a bit before sending back
                                    Thread.sleep(1000);
                                    log.info("Sending back reply message on {}", replyTo);
                                    template.sendBodyAndHeader("activemq:" + replyTo, exchange.getIn().getBody(), "JMSCorrelationID", cid);
                                }
                            }
                        });
            }
        };
    }

}
