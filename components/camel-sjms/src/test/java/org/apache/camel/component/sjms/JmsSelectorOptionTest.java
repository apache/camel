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
import org.junit.Test;

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

        template.sendBodyAndHeader("sjms:queue:hello", "A blue car!", "color", "blue");
        template.sendBodyAndHeader("sjms:queue:hello", "A red car!", "color", "red");
        template.sendBodyAndHeader("sjms:queue:hello", "A blue car, again!", "color", "blue");
        template.sendBodyAndHeader("sjms:queue:hello", "Message1", "SIZE_NUMBER", 1505);
        template.sendBodyAndHeader("sjms:queue:hello", "Message3", "SIZE_NUMBER", 1300);
        template.sendBodyAndHeader("sjms:queue:hello", "Message2", "SIZE_NUMBER", 1600);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumerTemplate() throws Exception {
        template.sendBodyAndHeader("sjms:queue:consumer", "Message1", "SIZE_NUMBER", 1505);
        template.sendBodyAndHeader("sjms:queue:consumer", "Message3", "SIZE_NUMBER", 1300);
        template.sendBodyAndHeader("sjms:queue:consumer", "Message2", "SIZE_NUMBER", 1600);

        // process every exchange which is ready. If no exchange is left break
        // the loop
        while (true) {
            Exchange ex = consumer.receiveNoWait("sjms:queue:consumer?messageSelector=SIZE_NUMBER<1500");
            if (ex != null) {
                Message message = ex.getIn();
                int size = message.getHeader("SIZE_NUMBER", int.class);
                assertTrue("The message header SIZE_NUMBER should be less than 1500", size < 1500);
                assertEquals("The message body is wrong", "Message3", message.getBody());
            } else {
                break;
            }
        }

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("sjms:queue:hello?messageSelector=color='blue'").to("mock:a");
                from("sjms:queue:hello?messageSelector=color='red'").to("mock:b");
                from("sjms:queue:hello?messageSelector=SIZE_NUMBER>1500").to("mock:c");
            }
        };
    }

}
