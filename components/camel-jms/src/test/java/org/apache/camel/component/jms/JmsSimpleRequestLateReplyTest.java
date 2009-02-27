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

import java.util.concurrent.CountDownLatch;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A simple requesr / late reply test using InOptionalOut.
 */
public class JmsSimpleRequestLateReplyTest extends ContextTestSupport {

    private static final Log LOG = LogFactory.getLog(JmsSimpleRequestLateReplyTest.class);

    protected String componentName = "activemq";

    private final CountDownLatch latch = new CountDownLatch(1);
    private static String replyDestination;
    private static String cid;

    public void testRequetLateReply() throws Exception {
        // use another thread to send the late reply to simulate that we do it later, not
        // from the origianl route anyway
        Thread sender = new Thread(new SendLateReply());
        sender.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.request("activemq:queue:hello", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // we expect a response so InOut
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("Hello World");
            }
        });

        result.assertIsSatisfied();

        assertNotNull(out);
        // TODO: We should get this late reply to work
        //assertEquals("Late Reply", out.getOut().getBody());
    }

    private class SendLateReply implements Runnable {

        public void run() {
            try {
                LOG.debug("Wating for latch");
                latch.await();

                // wait 1 sec after latch before sending he late replay
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }

            LOG.debug("Sending late reply");
            template.send(componentName + ":" + replyDestination, new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setPattern(ExchangePattern.InOnly);
                    exchange.getIn().setBody("Late reply");
                    exchange.getIn().setHeader("JMSCorrelationID", cid);
                }
            });
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQComponent amq = ActiveMQComponent.activeMQComponent("vm://localhost?broker.persistent=false");
        // as this is a unit test I dont want to wait 20 sec before timeout occurs, so we use 10
        amq.getConfiguration().setRequestTimeout(10000);
        camelContext.addComponent(componentName, amq);

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // set the MEP to InOptionalOut as we might not be able to send a reply
                from(componentName + ":queue:hello").setExchangePattern(ExchangePattern.InOptionalOut).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("Hello World", exchange.getIn().getBody());

                        replyDestination = exchange.getProperty(JmsConstants.JMS_REPLY_DESTINATION, String.class);
                        cid = exchange.getIn().getHeader("JMSCorrelationID", String.class);

                        LOG.debug("ReplyDestination: " + replyDestination);
                        LOG.debug("JMSCorrelationID: " + cid);

                        LOG.debug("Ahh I cannot send a reply. Someone else must do it.");
                        latch.countDown();
                    }
                }).to("mock:result");
            }
        };
    }
}