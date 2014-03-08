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
 * @version
 */
public class JmsRequestReplySharedReplyToTest extends CamelTestSupport {

    @Test
    public void testJmsRequestReplySharedReplyTo() throws Exception {
        StopWatch watch = new StopWatch();

        // shared is more slower than exclusive, due it need to use a JMS Message Selector
        // and has a receiveTimeout of 1 sec per default, so it react slower to new messages

        assertEquals("Hello A", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Shared", "A"));
        assertEquals("Hello B", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Shared", "B"));
        assertEquals("Hello C", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Shared", "C"));
        assertEquals("Hello D", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Shared", "D"));
        assertEquals("Hello E", template.requestBody("activemq:queue:foo?replyTo=bar&replyToType=Shared", "E"));

        long delta = watch.stop();
        assertTrue("Should be slower than about 2 seconds, was: " + delta, delta > 2000);
    }

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