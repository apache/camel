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
package org.apache.camel.itest.jms;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class JmsConsumerShutdownTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "activemq:start")
    protected ProducerTemplate activemq;

    @Produce(uri = "seda:start")
    protected ProducerTemplate seda;

    @EndpointInject(uri = "mock:end")
    protected MockEndpoint end;

    @EndpointInject(uri = "mock:exception")
    protected MockEndpoint exception;

    @Test
    @DirtiesContext
    public void testJmsConsumerShutdownWithMessageInFlight() throws InterruptedException {
        end.expectedMessageCount(0);
        end.setResultWaitTime(2000);

        // direct:dir route always fails
        exception.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new Exception("Kaboom!");
            }
        });

        activemq.sendBody("activemq:start", "Hello");

        end.assertIsSatisfied();
    }

    // Just for the sake of comparison test the SedaConsumer as well
    @Test
    @DirtiesContext
    public void testSedaConsumerShutdownWithMessageInFlight() throws InterruptedException {
        end.expectedMessageCount(0);
        end.setResultWaitTime(2000);

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
        public void configure() throws Exception {
            from("activemq:start")
                    .to("direct:dir")
                    .to("mock:end");

            from("seda:start")
                    .to("direct:dir")
                    .to("mock:end");

            from("direct:dir")
                    .onException(Exception.class)
                        .redeliveryDelay(1000)
                        .maximumRedeliveries(-1) // forever
                    .end()
                    .to("mock:exception");

        }
    }

}
