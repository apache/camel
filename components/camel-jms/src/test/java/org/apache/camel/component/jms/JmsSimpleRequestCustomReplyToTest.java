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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A simple request/reply using custom reply to header.
 */
public class JmsSimpleRequestCustomReplyToTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(JmsSimpleRequestCustomReplyToTest.class);
    private static String myReplyTo;
    protected final String componentName = "activemq";
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testRequetCustomReplyTo() throws Exception {
        // use another thread to send the late reply to simulate that we do it later, not
        // from the original route anyway
        Thread sender = new Thread(new SendLateReply());
        sender.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.request("activemq:queue:JmsSimpleRequestCustomReplyToTest", exchange -> {
            exchange.setPattern(ExchangePattern.InOnly);
            exchange.getIn().setHeader("MyReplyQeueue", "JmsSimpleRequestCustomReplyToTest.reply");
            exchange.getIn().setBody("Hello World");
        });

        result.assertIsSatisfied();
        assertNotNull(out);
        /*
          The getMessage returns the In message if the Out one is not present. Therefore, we check if
          the body of the returned message equals to the In one and infer that the out one was null.
         */
        assertEquals("Hello World", out.getMessage().getBody(), "There shouldn't be an out message");

        // get the reply from the special reply queue
        Endpoint end = context.getEndpoint(componentName + ":" + myReplyTo);
        final Consumer consumer = end.createConsumer(exchange -> {
            assertEquals("Late reply", exchange.getIn().getBody());
            latch.countDown();

        });
        // reset latch
        latch = new CountDownLatch(1);
        consumer.start();

        latch.await();
        consumer.stop();
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
                LOG.debug("Waiting for latch");
                latch.await();

                // wait 1 sec after latch before sending he late replay
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }

            LOG.debug("Sending late reply");
            template.send(componentName + ":" + myReplyTo, exchange -> {
                exchange.setPattern(ExchangePattern.InOnly);
                exchange.getIn().setBody("Late reply");
            });
        }
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, service, componentName);

        // as this is a unit test I don't want to wait 20 sec before timeout occurs, so we use 10
        component.getConfiguration().setRequestTimeout(10000);
        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(componentName + ":queue:JmsSimpleRequestCustomReplyToTest").process(exchange -> {
                    assertEquals("Hello World", exchange.getIn().getBody());

                    myReplyTo = exchange.getIn().getHeader("MyReplyQeueue", String.class);
                    LOG.debug("ReplyTo: {}", myReplyTo);

                    LOG.debug("Ahh I cannot send a reply. Someone else must do it.");
                    latch.countDown();
                }).to("mock:result");
            }
        };
    }
}
