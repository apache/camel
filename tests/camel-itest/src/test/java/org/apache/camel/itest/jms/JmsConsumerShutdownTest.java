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
package org.apache.camel.itest.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@CamelSpringTest
@ContextConfiguration
public class JmsConsumerShutdownTest {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    @Autowired
    CamelContext camelContext;

    @Produce("jms:start")
    protected ProducerTemplate activemq;

    @Produce("seda:start")
    protected ProducerTemplate seda;

    @EndpointInject("mock:end")
    protected MockEndpoint end;

    @EndpointInject("mock:exception")
    protected MockEndpoint exception;

    @Test
    @DirtiesContext
    void testJmsConsumerShutdownWithMessageInFlight() throws InterruptedException {
        camelContext.getShutdownStrategy().setTimeout(3);

        end.expectedMessageCount(0);
        end.setResultWaitTime(1000);

        // direct:dir route always fails
        exception.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new Exception("Kaboom!");
            }
        });

        activemq.sendBody("jms:start", "Hello");

        end.assertIsSatisfied();
    }

    // Just for the sake of comparison test the SedaConsumer as well
    @Test
    @DirtiesContext
    void testSedaConsumerShutdownWithMessageInFlight() throws InterruptedException {
        camelContext.getShutdownStrategy().setTimeout(3);

        end.expectedMessageCount(0);
        end.setResultWaitTime(1000);

        // direct:dir route always fails
        exception.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new Exception("Kaboom!");
            }
        });

        seda.sendBody("seda:start", "Hello");

        end.assertIsSatisfied();
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() {
            from("jms:start")
                    .to("direct:dir")
                    .to("mock:end");

            from("seda:start")
                    .to("direct:dir")
                    .to("mock:end");

            from("direct:dir")
                    .onException(Exception.class)
                    .redeliveryDelay(500)
                    .maximumRedeliveries(-1) // forever
                    .end()
                    .to("mock:exception");

        }
    }

}
