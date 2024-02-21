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
package org.apache.camel.component.sjms.producer;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.MyAsyncComponent;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AsyncQueueProducerTest extends CamelTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;
    private static String sedaThreadName;
    private static String route = "";

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    @Test
    public void testAsyncJmsProducerEndpoint() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        template.sendBody("direct:start.AsyncQueueProducerTest", "Hello Camel");
        // we should run before the async processor that sets B
        route += "A";

        MockEndpoint.assertIsSatisfied(context);

        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
        assertFalse(beforeThreadName.equalsIgnoreCase(sedaThreadName), "Should use different threads");
        assertFalse(afterThreadName.equalsIgnoreCase(sedaThreadName), "Should use different threads");

        assertEquals("AB", route);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                service.serviceAddress());
        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start.AsyncQueueProducerTest")
                        .to("mock:before")
                        .to("log:before")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                beforeThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("async:bye:camel")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("sjms:queue:foo.AsyncQueueProducerTest");

                from("sjms:queue:foo.AsyncQueueProducerTest?asyncConsumer=true")
                        .to("mock:after")
                        .to("log:after")
                        .delay(1000)
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                route += "B";
                                sedaThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
