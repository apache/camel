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
package org.apache.camel.component.jms;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.jms.Destination;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A simple request / late reply test.
 */
public class JmsSimpleRequestLateReplyTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(JmsSimpleRequestLateReplyTest.class);
    private static Destination replyDestination;
    private static String cid;
    protected final String expectedBody = "Late Reply";
    protected JmsComponent activeMQComponent;
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private final CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testRequestLateReplyUsingCustomDestinationHeaderForReply() throws Exception {
        doTest(new SendLateReply());
    }

    protected void doTest(Runnable runnable) throws InterruptedException {
        // use another thread to send the late reply to simulate that we do it later, not
        // from the original route anyway
        new Thread(runnable).start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        Object body = template.requestBody(getQueueEndpointName(), "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(expectedBody, body);
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

    private class SendLateReply implements Runnable {

        @Override
        public void run() {
            try {
                LOG.info("Waiting for latch");
                latch.await(30, TimeUnit.SECONDS);

                // wait 1 sec after latch before sending he late replay
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }

            LOG.info("Sending late reply");
            // use some dummy queue as we override this with the property: JmsConstants.JMS_DESTINATION
            Map<String, Object> headers = new HashMap<>();
            headers.put(JmsConstants.JMS_DESTINATION, replyDestination);
            headers.put("JMSCorrelationID", cid);
            template.sendBodyAndHeaders("activemq:dummy", expectedBody, headers);
        }
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, service, componentName);

        // as this is a unit test I dont want to wait 20 sec before timeout occurs, so we use 10
        component.getConfiguration().setRequestTimeout(10000);

        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getQueueEndpointName())
                        .process(exchange -> {
                            // set the MEP to InOnly as we are not able to send a reply right now but will do it later
                            // from that other thread
                            exchange.setPattern(ExchangePattern.InOnly);

                            Message in = exchange.getIn();
                            assertEquals("Hello World", in.getBody());

                            replyDestination = in.getHeader("JMSReplyTo", Destination.class);
                            cid = in.getHeader("JMSCorrelationID", String.class);

                            LOG.info("ReplyDestination: {}", replyDestination);
                            LOG.info("JMSCorrelationID: {}", cid);

                            LOG.info("Ahh I cannot send a reply. Someone else must do it.");
                            // signal to the other thread to send back the reply message
                            latch.countDown();
                        })
                        .to("mock:result");
            }
        };
    }

    protected static String getQueueEndpointName() {
        // need to use a fixed queue for reply as a temp queue may be deleted
        return "activemq:queue:JmsSimpleRequestLateReplyTest?replyTo=JmsSimpleRequestLateReplyTest.reply";
    }
}
