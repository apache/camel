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
package org.apache.camel.component.sjms.consumer;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.MyAsyncComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class AsyncConsumerInOutTest extends CamelTestSupport {

    @Test
    public void testAsyncJmsConsumer() throws Exception {
        // Hello World is received first despite its send last
        // the reason is that the first message is processed asynchronously
        // and it takes 2 sec to complete, so in between we have time to
        // process the 2nd message on the queue
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye Camel");

        template.sendBody("sjms:queue:start", "Hello Camel");
        template.sendBody("sjms:queue:start", "Hello World");

        assertMockEndpointsSatisfied();
    }
    
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        camelContext.addComponent("async", new MyAsyncComponent());

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                "vm://broker?broker.persistent=false&broker.useJmx=false");
        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable async in only mode on the consumer
                from("sjms:queue:start?synchronous=false")
                        .choice()
                            .when(body().contains("Camel"))
                            .to("async:camel?delay=2000")
                            .inOut("sjms:queue:in.out.test?namedReplyTo=response.queue&synchronous=false")
                            .to("mock:result")
                        .otherwise()
                            .to("log:other")
                            .to("mock:result");

                from("sjms:queue:in.out.test?exchangePattern=InOut&synchronous=false")
                    .to("log:camel")
                    .transform(constant("Bye Camel"));
            }
        };
    }
}
