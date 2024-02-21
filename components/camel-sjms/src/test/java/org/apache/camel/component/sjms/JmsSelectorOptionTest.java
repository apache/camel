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
package org.apache.camel.component.sjms;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsSelectorOptionTest extends JmsTestSupport {

    @Test
    public void testJmsMessageWithSelector() throws Exception {
        MockEndpoint endpointA = getMockEndpoint("mock:a");
        MockEndpoint endpointB = getMockEndpoint("mock:b");
        MockEndpoint endpointC = getMockEndpoint("mock:c");

        endpointA.expectedBodiesReceivedInAnyOrder("A blue car!", "A blue car, again!");
        endpointA.expectedHeaderReceived("color", "blue");
        endpointB.expectedHeaderReceived("color", "red");
        endpointB.expectedBodiesReceived("A red car!");

        endpointC.expectedBodiesReceived("Message1", "Message2");
        endpointC.expectedMessageCount(2);

        template.sendBodyAndHeader("sjms:queue:hello.JmsSelectorOptionTest", "A blue car!", "color", "blue");
        template.sendBodyAndHeader("sjms:queue:hello.JmsSelectorOptionTest", "A red car!", "color", "red");
        template.sendBodyAndHeader("sjms:queue:hello.JmsSelectorOptionTest", "A blue car, again!", "color", "blue");
        template.sendBodyAndHeader("sjms:queue:hello.JmsSelectorOptionTest", "Message1", "SIZE_NUMBER", 1505);
        template.sendBodyAndHeader("sjms:queue:hello.JmsSelectorOptionTest", "Message3", "SIZE_NUMBER", 1300);
        template.sendBodyAndHeader("sjms:queue:hello.JmsSelectorOptionTest", "Message2", "SIZE_NUMBER", 1600);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConsumerTemplate() {
        template.sendBodyAndHeader("sjms:queue:consumer.JmsSelectorOptionTest", "Message1", "SIZE_NUMBER", 1505);
        template.sendBodyAndHeader("sjms:queue:consumer.JmsSelectorOptionTest", "Message3", "SIZE_NUMBER", 1300);
        template.sendBodyAndHeader("sjms:queue:consumer.JmsSelectorOptionTest", "Message2", "SIZE_NUMBER", 1600);

        Exchange ex = consumer.receive("sjms:queue:consumer.JmsSelectorOptionTest?messageSelector=SIZE_NUMBER<1500", 5000L);
        Message message = ex.getIn();
        int size = message.getHeader("SIZE_NUMBER", int.class);
        assertEquals(1300, size, "The message header SIZE_NUMBER should be less than 1500");
        assertEquals("Message3", message.getBody(), "The message body is wrong");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:queue:hello.JmsSelectorOptionTest?messageSelector=color='blue'").to("mock:a");
                from("sjms:queue:hello.JmsSelectorOptionTest?messageSelector=color='red'").to("mock:b");
                from("sjms:queue:hello.JmsSelectorOptionTest?messageSelector=SIZE_NUMBER>1500").to("mock:c");
            }
        };
    }

}
