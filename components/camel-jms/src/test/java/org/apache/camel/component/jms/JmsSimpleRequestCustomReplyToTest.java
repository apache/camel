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

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * A simple request/reply using custom reply to header.
 */
public class JmsSimpleRequestCustomReplyToTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JmsSimpleRequestCustomReplyToTest.class);
    private static String myReplyTo;
    protected String componentName = "activemq";
    private CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testRequetCustomReplyTo() throws Exception {
        // use another thread to send the late reply to simulate that we do it later, not
        // from the original route anyway
        Thread sender = new Thread(new SendLateReply());
        sender.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.request("activemq:queue:hello", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOnly);
                exchange.getIn().setHeader("MyReplyQeueue", "foo");
                exchange.getIn().setBody("Hello World");
            }
        });

        result.assertIsSatisfied();
        assertNotNull(out);
        assertFalse(out.hasOut());

        // get the reply from the special reply queue
        Endpoint end = context.getEndpoint(componentName + ":" + myReplyTo);
        final Consumer consumer = end.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                assertEquals("Late reply", exchange.getIn().getBody());
                latch.countDown();

            }
        });
        // reset latch
        latch = new CountDownLatch(1);
        consumer.start();

        latch.await();
        consumer.stop();
    }

    private class SendLateReply implements Runnable {

        public void run() {
            try {
                LOG.debug("Waiting for latch");
                latch.await();

                // wait 1 sec after latch before sending he late replay
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }

            LOG.debug("Sending late reply");
            template.send(componentName + ":" + myReplyTo, new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.setPattern(ExchangePattern.InOnly);
                    exchange.getIn().setBody("Late reply");
                }
            });
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        JmsComponent jms = camelContext.getComponent("activemq", JmsComponent.class);
        // as this is a unit test I dont want to wait 20 sec before timeout occurs, so we use 10
        jms.getConfiguration().setRequestTimeout(10000);

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(componentName + ":queue:hello").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("Hello World", exchange.getIn().getBody());

                        myReplyTo = exchange.getIn().getHeader("MyReplyQeueue", String.class);
                        LOG.debug("ReplyTo: " + myReplyTo);

                        LOG.debug("Ahh I cannot send a reply. Someone else must do it.");
                        latch.countDown();
                    }
                }).to("mock:result");
            }
        };
    }
}
