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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Using exclusive fixed replyTo queues should be faster as there is no need for
 * JMSMessage selectors.
 *
 * @version 
 */
public class JmsRequestReplyExclusiveReplyToComponentTest extends CamelTestSupport {

    @Test
    public void testJmsRequestReplyExclusiveFixedReplyTo() throws Exception {
        StopWatch watch = new StopWatch();

        assertEquals("Hello A", template.requestBody("activemq:queue:foo?replyTo=bar", "A"));
        assertEquals("Hello B", template.requestBody("activemq:queue:foo?replyTo=bar", "B"));
        assertEquals("Hello C", template.requestBody("activemq:queue:foo?replyTo=bar", "C"));
        assertEquals("Hello D", template.requestBody("activemq:queue:foo?replyTo=bar", "D"));
        assertEquals("Hello E", template.requestBody("activemq:queue:foo?replyTo=bar", "E"));

        long delta = watch.stop();
        assertTrue("Should be faster than about 4 seconds, was: " + delta, delta < 4200);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        // mark the reply to type as exclusive on the component
        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        jms.setReplyToType(ReplyToType.Exclusive);
        camelContext.addComponent("activemq", jms);
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