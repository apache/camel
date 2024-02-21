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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TransactedConsumerSupport extends CamelTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @RegisterExtension
    protected ArtemisService service = ArtemisServiceFactory.createVMService();

    public abstract String getBrokerUri();

    protected void runTest(
            String destinationName, int routeCount, int messageCount, int totalRedeliverdFalse, int totalRedeliveredTrue,
            int concurrentConsumers, int maxAttemptsCount)
            throws Exception {
        // The CountDownLatch is used to make our final assertions await
        // unit all the messages have been processed. It is also
        // set to time out on the await. Our values are multiplied
        // by the number of routes we have.
        CountDownLatch latch = new CountDownLatch((totalRedeliverdFalse * routeCount));

        for (int i = 1; i <= routeCount; i++) {
            // We add a route here so we can pass our latch into it.
            addRoute(destinationName, i, concurrentConsumers, maxAttemptsCount, latch);
            // Create mock endpoints for the before and after
            getMockEndpoint("mock:test.before." + i).expectedMessageCount(totalRedeliverdFalse);
            getMockEndpoint("mock:test.after." + i).expectedMessageCount(totalRedeliveredTrue);
        }

        // We should never see a message here or something is
        // wrong with the JMS provider.
        getMockEndpoint("mock:test.after").expectedMessageCount(0);

        // Send only 10 messages
        for (int i = 1; i <= messageCount; i++) {
            String message = "Hello World " + i;
            template.sendBody("direct:start?block=false", message);
            log.trace("Sending message: {}", message);
        }

        // Await on our countdown for 20 seconds at most then move on
        log.info("Waiting for latch to count down from: {}", latch.getCount());
        boolean zero = latch.await(20, TimeUnit.SECONDS);
        log.info("Latch wait done: {} with count: {}", zero, latch.getCount());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(getBrokerUri());

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }

    protected void addRoute(
            final String destinationName, final int routeNumber, final int concurrentConsumers,
            final int maxAttemptsCount, final CountDownLatch latch)
            throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                if (context.getRoute("direct.route") == null) {
                    from("direct:start")
                            .id("direct.route")
                            .to(destinationName);
                }

                // Our test consumer route
                from(destinationName + "?transacted=true&concurrentConsumers="
                     + concurrentConsumers)
                        .id("consumer.route." + routeNumber)
                        // first consume all the messages that are not redelivered
                        .choice().when(header("JMSRedelivered").isEqualTo("false"))
                        // The rollback processor
                        .log("Route " + routeNumber + " 1st attempt Body: ${body} | Redeliverd: ${header.JMSRedelivered}")
                        .to("mock:test.before." + routeNumber)
                        .process(new Processor() {
                            private final AtomicInteger counter = new AtomicInteger();

                            @Override
                            public void process(Exchange exchange) {
                                if (counter.incrementAndGet() == maxAttemptsCount) {
                                    log.info(
                                            "{} Messages have been processed. Failing the exchange to force a rollback of the transaction.",
                                            maxAttemptsCount);
                                    throw new IllegalArgumentException("Forced rollback");
                                }

                                // Countdown the latch
                                latch.countDown();
                            }
                        })
                        // Now process again any messages that were redelivered
                        .when(header("JMSRedelivered").isEqualTo("true"))
                        .log("Route " + routeNumber + " 2nd attempt Body: ${body} | Redeliverd: ${header.JMSRedelivered}")
                        .to("mock:test.after." + routeNumber)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {

                                // Countdown the latch
                                latch.countDown();
                            }
                        })
                        .otherwise()
                        .to("mock:test.after");
            }
        });
    }
}
