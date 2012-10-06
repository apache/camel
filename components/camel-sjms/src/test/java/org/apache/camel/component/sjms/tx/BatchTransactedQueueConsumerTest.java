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
 *
 */
public class BatchTransactedQueueConsumerTest extends CamelTestSupport {

    /**
     * Verify that after only sending 10 messages that 10 are delivered to the
     * processor and upon the 10th message throwing an Exception which causes
     * the messages deliveries to be rolled back. The messages should then be
     * redelivered with the JMSRedelivered flag set to true for a total of 10
     * delivered messages.
     * 
     * @throws Exception
     */
    @Test
    public void testEndpointConfiguredBatchTransaction() throws Exception {
        // We should get two sets of 10 messages. 10 before the rollback and 10
        // after the rollback.
        getMockEndpoint("mock:test.before").expectedMessageCount(10);
        getMockEndpoint("mock:test.after").expectedMessageCount(10);

        // Send only 10 messages
        for (int i = 1; i <= 10; i++) {
            template.sendBody("direct:start", "Hello World " + i);
        }

        getMockEndpoint("mock:test.before").assertIsSatisfied();
        getMockEndpoint("mock:test.after").assertIsSatisfied();

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=true");
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
                from("direct:start")
                    .to("sjms:queue:transacted.consumer.test");

                // Our test consumer route
                from("sjms:queue:transacted.consumer.test?transacted=true&transactionBatchCount=10")
                    // first consume all the messages that are not redelivered
                    .choice()
                        .when(header("JMSRedelivered").isEqualTo("false"))
                            .to("log:before_log?showAll=true")
                            .to("mock:test.before")
                            // This is where we will cause the rollback after 10 messages have been sent.
                            .process(new Processor() {
                                    @Override
                                    public void process(Exchange exchange) throws Exception {
                                        // Get the body
                                        String body = exchange.getIn().getBody(String.class);
                                        
                                        // If the message ends with 10, throw the exception
                                        if (body.endsWith("4") || body.endsWith("6")) {
                                            log.info("10th message received.  Rolling back.");
                                            exchange.getOut().setFault(true);
                                            exchange.getOut().setBody("10th message received.  Rolling back.");
                                        }
                                    }
                                })
                        .otherwise()
                            .to("log:after_log?showAll=true")
                            .to("mock:test.after");
            }
        };
    }
}
