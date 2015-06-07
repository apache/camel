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

import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Tests how the correlation between request and reply is done
 */
public class JmsRequestReplyCorrelationTest extends CamelTestSupport {

    private static final String REPLY_BODY = "Bye World";

    /**
     * When the setting useMessageIdAsCorrelationid is false and
     * a correlation id is set on the message then we expect the reply
     * to contain the same correlation id.
     */
    @Test
    public void testRequestReplyCorrelationByGivenCorrelationId() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.send("jms:queue:hello", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("Hello World");
                in.setHeader("JMSCorrelationID", "a");
            }
        });

        result.assertIsSatisfied();

        assertNotNull(out);

        assertEquals(REPLY_BODY, out.getOut().getBody(String.class));
        assertEquals("a", out.getOut().getHeader("JMSCorrelationID"));
    }
    
    /**
     * As the correlationID should be unique when receiving the reply message, 
     * now we just expect to get an exception here.
     */
    @Test
    public void testRequestReplyCorrelationWithDuplicateId() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        NotifyBuilder notify = new NotifyBuilder(context).whenReceived(1).create();

        // just send out the request to fill the correlation id first
        template.asyncSend("jms:queue:helloDelay", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message in = exchange.getIn();
                in.setBody("Hello World");
                in.setHeader("JMSCorrelationID", "b");
            }
        });
        // Added use the notify to make sure the message is processed, so we get the exception later
        notify.matches(1, TimeUnit.SECONDS);
        
        Exchange out = template.send("jms:queue:helloDelay", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("Hello World");
                in.setHeader("JMSCorrelationID", "b");
            }
        });

        result.assertIsSatisfied();

        assertNotNull("We are expecting the exception here!", out.getException());
        assertTrue("Get a wrong exception", out.getException() instanceof IllegalArgumentException);
        
    }


    /**
     * When the setting useMessageIdAsCorrelationid is false and
     * a correlation id is not set on the message then we expect the reply
     * to contain the correlation id dynamically generated on send.
     * Ideally we should also check what happens if the correlation id
     * was not set on send but this is currently not done.
     */
    @Test
    public void testRequestReplyCorrelationWithoutGivenCorrelationId() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.send("jms:queue:hello", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("Hello World");
            }
        });

        result.assertIsSatisfied();

        assertNotNull(out);

        assertEquals(REPLY_BODY, out.getOut().getBody(String.class));
        String correlationId = out.getOut().getHeader("JMSCorrelationID", String.class);
        assertNotNull(correlationId);
        // In ActiveMQ messageIds start with ID: (currently) so the ID should not be generated from AMQ
        assertFalse("CorrelationID should NOT start with ID, was: " + correlationId, correlationId.startsWith("ID:"));
    }

    /**
     * When the setting useMessageIdAsCorrelationid is false and
     * a correlation id is set to empty String ("") the message then we expect the reply
     * to contain the correlation id dynamically generated on send.
     */     
    @Test
    public void testRequestReplyCorrelationWithEmptyString() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.send("jms:queue:hello", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("Hello World");
                in.setHeader("JMSCorrelationID", "");
            }
        });

        assertNotNull(out);
        result.assertIsSatisfied();

        assertEquals(REPLY_BODY, out.getOut().getBody(String.class));
        String correlationId = out.getOut().getHeader("JMSCorrelationID", String.class);
        assertNotNull(correlationId);
        // In ActiveMQ messageIds start with ID: (currently) so the ID should not be generated from AMQ
        assertFalse("CorrelationID should NOT start with ID, was: " + correlationId, correlationId.startsWith("ID:"));
    }

    /**
     * When the setting useMessageIdAsCorrelationid is true for the client and
     * false for the server and a correlation id is not set on the message then
     * we expect the reply to contain the message is from the sent message
     */
    @Test
    public void testRequestReplyCorrelationWithoutGivenCorrelationIdAndUseMessageIdonClient() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.send("jms2:queue:hello", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("Hello World");
            }
        });

        result.assertIsSatisfied();

        assertNotNull(out);

        assertEquals(REPLY_BODY, out.getOut().getBody(String.class));
        String correlationId = (String) out.getOut().getHeader("JMSCorrelationID");

        assertNotNull(correlationId);
        // In ActiveMQ messageIds start with ID: (currently)
        assertTrue("CorrelationID should start with ID, was: " + correlationId, correlationId.startsWith("ID:"));
    }

    /**
     * When the setting useMessageIdAsCorrelationid is true and
     * a correlation id is set on the message then we expect the reply
     * to contain the messageId of the sent message. Here we test only that
     * it is not the correlation id given as the messageId is not know
     * beforehand.
     */
    @Test
    public void testRequestReplyCorrelationByMessageId() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.send("jms2:queue:hello2", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("Hello World");
                in.setHeader("JMSCorrelationID", "a");
            }
        });

        result.assertIsSatisfied();

        assertNotNull(out);

        assertEquals(REPLY_BODY, out.getOut().getBody(String.class));
        assertEquals("a", out.getOut().getHeader("JMSCorrelationID"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent jmsComponent = jmsComponentAutoAcknowledge(connectionFactory);
        jmsComponent.setUseMessageIDAsCorrelationID(false);
        camelContext.addComponent("jms", jmsComponent);

        JmsComponent jmsComponent2 = jmsComponentAutoAcknowledge(connectionFactory);
        jmsComponent2.setUseMessageIDAsCorrelationID(true);
        camelContext.addComponent("jms2", jmsComponent2);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jms:queue:hello").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody(REPLY_BODY);
                        assertNotNull(exchange.getIn().getHeader("JMSReplyTo"));
                    }
                }).to("mock:result");

                from("jms2:queue:hello2").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody(REPLY_BODY);
                        assertNotNull(exchange.getIn().getHeader("JMSReplyTo"));
                    }
                }).to("mock:result");
                
                from("jms:queue:helloDelay").delay().constant(2000).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setBody(REPLY_BODY);
                        assertNotNull(exchange.getIn().getHeader("JMSReplyTo"));
                    }
                }).to("mock:result");
            }
        };
    }

}
