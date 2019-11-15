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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Using exclusive fixed replyTo queues should be faster as there is no need for
 * JMSMessage selectors.
 */
public class JmsRequestReplyExclusiveReplyToTest extends CamelTestSupport {

    @Test
    public void testJmsRequestReplyExclusiveFixedReplyTo() throws Exception {
        StopWatch watch = new StopWatch();

        assertEquals("Hello A", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Exclusive", "A"));
        assertEquals("Hello B", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Exclusive", "B"));
        assertEquals("Hello C", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Exclusive", "C"));
        assertEquals("Hello D", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Exclusive", "D"));
        assertEquals("Hello E", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Exclusive", "E"));

        long delta = watch.taken();
        assertTrue("Should be faster than about 4 seconds, was: " + delta, delta < 4200);
    }

    @Test
    public void testInvalidConfiguration() throws Exception {
        try {
            template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Temporary", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(FailedToCreateProducerException.class, e.getCause());
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("ReplyToType Temporary is not supported when replyTo bar is also configured.", e.getCause().getCause().getMessage());
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo")
                    .transform(body().prepend("Hello "));
            }
        };
    }
}
