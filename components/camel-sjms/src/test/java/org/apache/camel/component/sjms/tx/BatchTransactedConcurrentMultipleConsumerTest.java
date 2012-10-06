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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Verify the ability to batch transactions to the consumer.
 */
public class BatchTransactedConcurrentMultipleConsumerTest extends CamelTestSupport {

    private static final int BATCH_COUNT = 10;
    private static final int CONSUMER_COUNT = 2;
    private static final int MAX_ATTEMPTS_COUNT = 10;
    private static final int MESSAGE_COUNT = 20;
    private static final int TOTAL_REDELIVERED_FALSE = 19;
    private static final int TOTAL_REDELIVERED_TRUE = 5;
    private static final String BROKER_URI = "vm://btcmcTestBroker?broker.persistent=false&broker.useJmx=true";

    /**
     * Verify that the batch transactions behave correctly when we combine it
     * with concurrent consumers.
     * 
     * @throws Exception
     */
    @Test
    public void testEndpointConfiguredBatchTransaction() throws Exception {

        getMockEndpoint("mock:test.redelivered.false").expectedMessageCount(TOTAL_REDELIVERED_FALSE);
        getMockEndpoint("mock:test.redelivered.true").expectedMessageCount(TOTAL_REDELIVERED_TRUE);

        // We should never see a message appear in this endpoint or we
        // have problem with our JMS provider
        getMockEndpoint("mock:test.after").expectedMessageCount(0);

        // Send messages
        for (int i = 1; i <= MESSAGE_COUNT; i++) {
            String message = "Hello World " + i;
            template.sendBody("direct:start", message);
            log.trace("Sending message: {}", message);
        }
        Thread.sleep(3000);
        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URI);
        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // Having a producer route helps with debugging and logging
                from("direct:start").to("sjms:queue:transacted.consumer.test");

                // Our test consumer route
                from("sjms:queue:transacted.consumer.test?transacted=true&transactionBatchCount=" + BATCH_COUNT + "&consumerCount=" + CONSUMER_COUNT)
                    // first consume all the messages that are not redelivered
                    .choice().when(header("JMSRedelivered").isEqualTo("false"))
                        .process(new Processor() {
                            private final AtomicInteger counter = new AtomicInteger(0);
    
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                if (counter.incrementAndGet() == MAX_ATTEMPTS_COUNT) {
                                    log.info("{} Messages have been processed. Failing the exchange to force a rollback of the transaction.", MAX_ATTEMPTS_COUNT);
                                    exchange.getOut().setFault(true);
                                }
                            }
                        })
                        .log("1st attempt Body: ${body} | Redeliverd: ${header.JMSRedelivered}")
                        .to("mock:test.redelivered.false")
                    // Now process again any messages that were redelivered
                    .when(header("JMSRedelivered").isEqualTo("true"))
                        .log("2nd attempt Body: ${body} | Redeliverd: ${header.JMSRedelivered}")
                        .to("mock:test.redelivered.true")
                    .otherwise()
                        .to("mock:test.after");
            }
        };
    }
}
