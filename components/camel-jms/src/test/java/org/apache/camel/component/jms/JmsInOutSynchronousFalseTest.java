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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JmsInOutSynchronousFalseTest extends CamelTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;
    private String url = "activemq:queue:in?synchronous=false";

    @Test
    public void testSynchronous() throws Exception {
        String reply = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", reply);

        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
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
            public void configure() throws Exception {
                from("direct:start")
                        .to("log:before")
                        .process(exchange -> beforeThreadName = Thread.currentThread().getName())
                        .to(url)
                        .process(exchange -> afterThreadName = Thread.currentThread().getName())
                        .to("log:after")
                        .to("mock:result");

                from("activemq:queue:in").process(exchange -> exchange.getMessage().setBody("Bye World"));
            }
        };
    }

}
