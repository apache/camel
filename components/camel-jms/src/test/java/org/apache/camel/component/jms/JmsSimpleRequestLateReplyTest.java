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
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * A simple requesr / late reply test using InOptionalOut.
 */
public class JmsSimpleRequestLateReplyTest extends CamelTestSupport {

    private static final Log LOG = LogFactory.getLog(JmsSimpleRequestLateReplyTest.class);
    private static Destination replyDestination;
    private static String cid;
    private static int count;
    protected String expectedBody = "Late Reply";
    protected JmsComponent activeMQComponent;
    private final CountDownLatch latch = new CountDownLatch(1);
    
    @Before
    public void setUp() throws Exception {
        count++;
        super.setUp();
    }

    @Test
    public void testRequestLateReplyUsingCustomDestinationHeaderForReply() throws Exception {
        doTest(new SendLateReply());
    }
    
    @Test
    public void testRequestLateReplyUsingDestinationEndpointForReply() throws Exception {
        doTest(new SendLateReplyUsingTemporaryEndpoint());
    }

    protected void doTest(Runnable runnable) throws InterruptedException {
        // use another thread to send the late reply to simulate that we do it later, not
        // from the original route anyway
        Thread sender = new Thread(runnable);
        sender.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.request(getQueueEndpointName(), new Processor() {
            public void process(Exchange exchange) throws Exception {
                // we expect a response so InOut
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("Hello World");
            }
        });

        result.assertIsSatisfied();

        assertNotNull(out);
        assertEquals(expectedBody, out.getOut().getBody());
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
            // use some dummy queue as we override this with the property: JmsConstants.JMS_DESTINATION
            template.send("activemq:dummy", new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setPattern(ExchangePattern.InOnly);

                    Message in = exchange.getIn();
                    in.setBody(expectedBody);
                    in.setHeader(JmsConstants.JMS_DESTINATION, replyDestination);
                    in.setHeader("JMSCorrelationID", cid);
                }
            });
        }
    }

    private class SendLateReplyUsingTemporaryEndpoint implements Runnable {

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

            try {
                JmsEndpoint endpoint = JmsEndpoint.newInstance(replyDestination, activeMQComponent);

                template.send(endpoint, new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.setPattern(ExchangePattern.InOnly);

                        Message in = exchange.getIn();
                        in.setBody(expectedBody);
                        in.setHeader("JMSCorrelationID", cid);
                    }
                });

            } catch (JMSException e) {
                LOG.error("Failed to create the endpoint for " + replyDestination);
            }
        }
    }


    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        activeMQComponent = camelContext.getComponent("activemq", JmsComponent.class);
        // as this is a unit test I dont want to wait 20 sec before timeout occurs, so we use 10
        activeMQComponent.getConfiguration().setRequestTimeout(10000);

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // set the MEP to InOptionalOut as we might not be able to send a reply
                from(getQueueEndpointName()).setExchangePattern(ExchangePattern.InOptionalOut).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        assertEquals("Hello World", in.getBody());

                        replyDestination = in.getHeader("JMSReplyTo", Destination.class);
                        cid = in.getHeader("JMSCorrelationID", String.class);

                        LOG.debug("ReplyDestination: " + replyDestination);
                        LOG.debug("JMSCorrelationID: " + cid);

                        LOG.debug("Ahh I cannot send a reply. Someone else must do it.");
                        latch.countDown();
                    }
                }).to("mock:result");
            }
        };
    }


    protected static String getQueueEndpointName() {
        // lets use a different queue name for each test
        return "activemq:queue:hello.queue" + count;
    }

}
