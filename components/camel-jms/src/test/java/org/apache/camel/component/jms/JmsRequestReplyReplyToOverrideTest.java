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

import javax.jms.Destination;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsRequestReplyReplyToOverrideTest extends AbstractJMSTest {

    private static final Logger LOG = LoggerFactory.getLogger(JmsRequestReplyReplyToOverrideTest.class);

    private static final String REQUEST_BODY = "Something";
    private static final String EXPECTED_REPLY_BODY = "Re: " + REQUEST_BODY;
    private static final String EXPECTED_REPLY_HEADER = "queue://JmsRequestReplyReplyToOverrideTest.reply";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

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
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent jmsComponent = super.setupComponent(camelContext, service, componentName);

        jmsComponent.getConfiguration().setReplyTo("baz");
        jmsComponent.getConfiguration().setReplyToOverride("JmsRequestReplyReplyToOverrideTest.reply");

        return jmsComponent;
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
