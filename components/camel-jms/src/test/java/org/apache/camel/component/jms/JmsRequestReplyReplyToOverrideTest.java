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

import jakarta.jms.Destination;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
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

public class JmsRequestReplyReplyToOverrideTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final Logger LOG = LoggerFactory.getLogger(JmsRequestReplyReplyToOverrideTest.class);

    private static final String REQUEST_BODY = "Something";
    private static final String EXPECTED_REPLY_BODY = "Re: " + REQUEST_BODY;
    private static final String EXPECTED_REPLY_HEADER = "ActiveMQQueue[JmsRequestReplyReplyToOverrideTest.reply]";
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsRequestReplyReplyToAndReplyToHeader() {
        // must start CamelContext because use route builder is false
        context.start();

        // send request to JmsRequestReplyReplyToOverrideTest, set replyTo to JmsRequestReplyReplyToOverrideTest.reply, but actually expect reply at baz
        Thread sender = new Thread(new Responder());
        sender.start();

        Exchange reply = template.request("jms:queue:JmsRequestReplyReplyToOverrideTest",
                exchange -> exchange.getIn().setBody(REQUEST_BODY));
        assertEquals(EXPECTED_REPLY_BODY, reply.getMessage().getBody());
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ArtemisService service, String componentName) {
        final JmsComponent jmsComponent = super.setupComponent(camelContext, service, componentName);

        jmsComponent.getConfiguration().setReplyTo("baz");
        jmsComponent.getConfiguration().setReplyToOverride("JmsRequestReplyReplyToOverrideTest.reply");

        return jmsComponent;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return null;
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

    private class Responder implements Runnable {

        @Override
        public void run() {
            try {
                LOG.debug("Waiting for request");
                Exchange request = consumer.receive("jms:queue:JmsRequestReplyReplyToOverrideTest", 5000);

                LOG.debug("Got request, sending reply");
                final String body = request.getIn().getBody(String.class);
                final String cid = request.getIn().getHeader("JMSCorrelationID", String.class);
                final Destination replyTo = request.getIn().getHeader("JMSReplyTo", Destination.class);

                assertEquals(EXPECTED_REPLY_HEADER, replyTo.toString());

                // send reply
                template.send("jms:dummy", ExchangePattern.InOnly, exchange -> {

                    Message in = exchange.getIn();
                    in.setBody("Re: " + body);
                    in.setHeader(JmsConstants.JMS_DESTINATION_NAME, "baz");
                    in.setHeader("JMSCorrelationID", cid);
                });
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
