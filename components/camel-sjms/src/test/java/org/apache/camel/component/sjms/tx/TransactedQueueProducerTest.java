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
package org.apache.camel.component.sjms.tx;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.fail;

public class TransactedQueueProducerTest extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    @Produce
    protected ProducerTemplate template;

    @Test
    public void testRoute() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World 2");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Hello World 2");

        try {
            template.sendBodyAndHeader("direct:start", "Hello World 1", "isfailed", true);
            fail("Should fail");
        } catch (Exception e) {
            // expected
        }
        template.sendBodyAndHeader("direct:start", "Hello World 2", "isfailed", false);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        ActiveMQConnectionFactory connectionFactory
                = new ActiveMQConnectionFactory(service.serviceAddress());
        CamelContext camelContext = super.createCamelContext();
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
                from("direct:start")
                        .to("sjms:queue:test.queue.TransactedQueueProducerTest.queue?transacted=true")
                        .to("sjms:queue:test.queue2.TransactedQueueProducerTest.queue2?transacted=true")
                        .process(
                                new Processor() {
                                    @Override
                                    public void process(Exchange exchange) throws Exception {
                                        if (exchange.getIn().getHeader("isfailed", Boolean.class)) {
                                            log.info("We failed. Should roll back.");
                                            throw new RollbackExchangeException(exchange);
                                        } else {
                                            log.info("We passed.  Should commit.");
                                        }
                                    }
                                });

                from("sjms:queue:test.queue.TransactedQueueProducerTest.queue?transacted=true")
                        .to("mock:result");

                from("sjms:queue:test.queue2.TransactedQueueProducerTest.queue2?transacted=true")
                        .to("mock:result2");
            }
        };
    }
}
