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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.async.MyAsyncComponent;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class AsyncEndpointJmsTXMulticastTest extends CamelSpringTestSupport {
    private static String beforeThreadName;
    private static String afterThreadName;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/JmsTransacted-context.xml");
    }
    
    @Test
    public void testAsyncEndpointOK() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        template.sendBody("activemq:queue:inbox", "Hello Camel");

        assertMockEndpointsSatisfied();

        // we are synchronous due to TX so the we are using same threads during the routing
        assertTrue("Should use same threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("activemq:queue:inbox")
                    .transacted()
                        .to("mock:before")
                        .to("log:before")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                beforeThreadName = Thread.currentThread().getName();
                                assertTrue("Exchange should be transacted", exchange.isTransacted());
                            }
                        })
                        .multicast().to("direct:foo");

                from("direct:foo")
                        // tx should be conveyed to this route as well
                        .to("async:bye:camel")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                                assertTrue("Exchange should be transacted", exchange.isTransacted());
                            }
                        })
                        .to("log:after")
                        .to("mock:after")
                        .to("mock:result");
            }
        };
    }
}