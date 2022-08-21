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
package org.apache.camel.component.jms.issues;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test using a fixed replyTo specified on the JMS endpoint
 */
public class JmsJMSReplyToConsumerEndpointUsingInOutTest extends AbstractJMSTest {

    @Test
    public void testCustomJMSReplyToInOut() {
        template.sendBody("activemq:queue:JmsJMSReplyToConsumerEndpointUsingInOutTest", "What is your name?");

        String reply
                = consumer.receiveBody("activemq:queue:JmsJMSReplyToConsumerEndpointUsingInOutTest.reply", 5000, String.class);
        assertEquals("My name is Camel", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsJMSReplyToConsumerEndpointUsingInOutTest?replyTo=queue:JmsJMSReplyToConsumerEndpointUsingInOutTest.reply")
                        .to("log:hello")
                        .transform(constant("My name is Camel"));
            }
        };
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

}
