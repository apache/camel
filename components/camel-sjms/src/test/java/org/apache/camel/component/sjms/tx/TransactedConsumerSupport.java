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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;

public abstract class TransactedConsumerSupport extends CamelTestSupport {
    
    public abstract String getBrokerUri();

    protected void runTest(String destinationName, int routeCount, int messageCount, int totalRedeliverdFalse, int totalRedeliveredTrue, int batchCount, int consumerCount, int maxAttemptsCount)
        throws Exception {
        // The CountDownLatch is used to make our final assertions await
        // unit all the messages have been processed. It is also
        // set to time out on the await. Our values are multiplied
        // by the number of routes we have.
        CountDownLatch latch = new CountDownLatch((totalRedeliverdFalse * routeCount) + (totalRedeliveredTrue * routeCount));

        for (int i = 1; i <= routeCount; i++) {
            // We add a route here so we can pass our latch into it.
            addRoute(destinationName, i, batchCount, consumerCount, maxAttemptsCount, latch);
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
            template.sendBody("direct:start", message);
            log.trace("Sending message: {}", message);
        }

        // Await on our countdown for 30 seconds at most then move on
        latch.await(30, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(getBrokerUri());
        // use low redelivery delay to speed
        connectionFactory.getRedeliveryPolicy().setInitialRedeliveryDelay(100);
        connectionFactory.getRedeliveryPolicy().setRedeliveryDelay(100);
        connectionFactory.getRedeliveryPolicy().setUseCollisionAvoidance(false);
        connectionFactory.getRedeliveryPolicy().setUseExponentialBackOff(false);

        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }
    
    protected void addRoute(final String destinationName, final int routeNumber, final int batchCount, final int consumerCount, 
                            final int maxAttemptsCount, final CountDownLatch latch) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                
                if (context.getRoute("direct.route") == null) {
                    from("direct:start")
                        .id("direct.route")
                        .to(destinationName);
                }

                // Our test consumer route
                from(destinationName + "?transacted=true&transactionBatchCount=" + batchCount + "&consumerCount=" + consumerCount)
                    .id("consumer.route." + routeNumber)
                    // first consume all the messages that are not redelivered
                    .choice().when(header("JMSRedelivered").isEqualTo("false"))
                        // The rollback processor
                        .log("Route " + routeNumber + " 1st attempt Body: ${body} | Redeliverd: ${header.JMSRedelivered}")
                        .to("mock:test.before." + routeNumber)
                        .process(new Processor() {
                            private final AtomicInteger counter = new AtomicInteger(0);

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                if (counter.incrementAndGet() == maxAttemptsCount) {
                                    log.info("{} Messages have been processed. Failing the exchange to force a rollback of the transaction.", maxAttemptsCount);
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
                            public void process(Exchange exchange) throws Exception {
                                
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
