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
import javax.jms.Destination;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsRequestReplyReplyToOverrideTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsRequestReplyReplyToOverrideTest.class);

    private static final String REQUEST_BODY = "Something";
    private static final String EXPECTED_REPLY_BODY = "Re: " + REQUEST_BODY;
    private static final String EXPECTED_REPLY_HEADER = "queue://bar";
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testJmsRequestReplyReplyToAndReplyToHeader() throws Exception {
        // must start CamelContext because use route builder is false
        context.start();

        // send request to foo, set replyTo to bar, but actually expect reply at baz
        Thread sender = new Thread(new Responder());
        sender.start();

        Exchange reply = template.request("jms:queue:foo", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(REQUEST_BODY);
            }
        });
        assertEquals(EXPECTED_REPLY_BODY, reply.getOut().getBody());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent jmsComponent = jmsComponentAutoAcknowledge(connectionFactory);
        jmsComponent.getConfiguration().setReplyTo("baz");
        jmsComponent.getConfiguration().setReplyToOverride("bar");
        camelContext.addComponent("jms", jmsComponent);
        return camelContext;
    }

    private class Responder implements Runnable {

        public void run() {
            try {
                LOG.debug("Waiting for request");
                Exchange request = consumer.receive("jms:queue:foo", 5000);

                LOG.debug("Got request, sending reply");
                final String body = request.getIn().getBody(String.class);
                final String cid = request.getIn().getHeader("JMSCorrelationID", String.class);
                final Destination replyTo = request.getIn().getHeader("JMSReplyTo", Destination.class);
                
                assertEquals(EXPECTED_REPLY_HEADER, replyTo.toString());
                
                // send reply
                template.send("jms:dummy", ExchangePattern.InOnly, new Processor() {
                    public void process(Exchange exchange) throws Exception {

                        Message in = exchange.getIn();
                        in.setBody("Re: " + body);
                        in.setHeader(JmsConstants.JMS_DESTINATION_NAME, "baz");
                        in.setHeader("JMSCorrelationID", cid);
                    }
                });
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
