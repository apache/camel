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
package org.apache.camel.component.jms.tx;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.component.jms.async.MyAsyncComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class AsyncEndpointJmsTXWireTapTest extends AbstractSpringJMSTestSupport {
    private static String beforeThreadName;
    private static String afterThreadName;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/JmsTransacted-context.xml");
    }

    @Test
    public void testAsyncEndpointOK() throws Exception {
        getMockEndpoint("mock:tap").expectedBodiesReceived("Hi Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        template.sendBody("activemq:queue:inbox", "Hello Camel");

        MockEndpoint.assertIsSatisfied(context);

        // the tapped exchange is not transacted
        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());

                from("activemq:queue:inbox")
                        .transacted()
                        .process(exchange -> assertTrue(exchange.isTransacted(), "Exchange should be transacted"))
                        .to("async:bye:camel")
                        .wireTap("direct:tap")
                        .to("mock:result");

                from("direct:tap")
                        .process(exchange -> {
                            beforeThreadName = Thread.currentThread().getName();
                            assertFalse(exchange.isTransacted(), "Exchange should NOT be transacted");
                        })
                        .to("async:hi:camel")
                        .process(exchange -> afterThreadName = Thread.currentThread().getName())
                        .to("mock:tap");
            }
        };
    }
}
