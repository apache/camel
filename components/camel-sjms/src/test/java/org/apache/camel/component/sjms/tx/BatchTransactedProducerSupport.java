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
package org.apache.camel.component.sjms.tx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.BatchMessage;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;

public abstract class BatchTransactedProducerSupport extends CamelTestSupport {

    public abstract String getBrokerUri();

    protected void runTest(String destinationName, int consumerRouteCount, int messageCount, int totalAttempts)
        throws Exception {
        
        // The CountDownLatch is used to make our final assertions await
        // unit all the messages have been processed. It is also
        // set to time out on the await. Our values are multiplied
        // by the number of routes we have.
        CountDownLatch latch = new CountDownLatch(totalAttempts + (messageCount * consumerRouteCount));
        addRoute(destinationName, consumerRouteCount, latch);
        
        // We should see the BatchMessage once in the prebatch and once in the
        // redelivery. Then we should see 30 messages arrive in the postbatch.
        getMockEndpoint("mock:test.producer").expectedMessageCount(totalAttempts);
        for (int i = 1; i <= consumerRouteCount; i++) {
            getMockEndpoint("mock:test.consumer." + i).expectedMessageCount(messageCount);
        }

        List<BatchMessage<String>> messages = new ArrayList<BatchMessage<String>>();
        for (int i = 1; i <= messageCount; i++) {
            String body = "Hello World " + i;
            BatchMessage<String> message = new BatchMessage<String>(body, null);
            messages.add(message);
        }
        
        // First we send the batch to capture the failure.
        try {
            log.info("Send Messages");
            template.sendBody("direct:start", messages);
        } catch (Exception e) {
            log.info("Send Again");
            template.sendBody("direct:start", messages);
        }

        // Await on our countdown for 10 seconds at most
        // then move on
        latch.await(10, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
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
    
    protected void addRoute(final String destinationName, final int consumerRouteCount, final CountDownLatch latch) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                
                from("direct:start")
                    .id("producer.route")
                    .log("Producer Route Body: ${body}")
                    .to("mock:test.producer")
                    .to(destinationName + "?transacted=true")
                    // This Processor will force an exception to occur on the exchange
                    .process(new Processor() {
                        private final AtomicInteger counter = new AtomicInteger(0);
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            
                            // Only throw the exception the first time around.
                            // Otherwise allow it to proceed.
                            if (counter.getAndIncrement() == 0) {
                                log.info("BatchMessage received without redelivery. Rolling back.");
                                exchange.setException(new Exception());
                            }
                            // Countdown the latch
                            latch.countDown();
                        }
                    });

                for (int i = 1; i <= consumerRouteCount; i++) {
                    from(destinationName)
                        .id("consumer.route." + i)
                        .log("Consumer Route " + i + " Body: ${body}")
                        .to("mock:test.consumer." + i)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                
                                // Countdown the latch
                                latch.countDown();
                            }
                        });   
                }
            }
        });
    }
}
