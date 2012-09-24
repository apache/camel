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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.BatchMessage;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test used to verify the batch transaction capability of the SJMS Component
 * for a Queue Producer.
 */
public class BatchTransactedQueueProducerTest extends CamelTestSupport {

    @Produce
    protected ProducerTemplate template;

    /**
     * Verify that after processing a {@link BatchMessage} twice with 30
     * messages in for a total of 60 delivery attempts that we only see 30
     * messages end up at the final consumer. This is due to an exception being
     * thrown during the processing of the first 30 messages which causes a
     * redelivery.
     * 
     * @throws Exception
     */
    @Test
    public void testEndpointConfiguredBatchTransaction() throws Exception {
        // We should see the BatchMessage once in the prebatch and once in the
        // redelivery. Then we should see 30 messages arrive in the postbatch.
        getMockEndpoint("mock:test.prebatch").expectedMessageCount(1);
        getMockEndpoint("mock:test.redelivery").expectedMessageCount(1);
        getMockEndpoint("mock:test.postbatch").expectedMessageCount(30);

        List<BatchMessage<String>> messages = new ArrayList<BatchMessage<String>>();
        for (int i = 1; i <= 30; i++) {
            String body = "Hello World " + i;
            BatchMessage<String> message = new BatchMessage<String>(body, null);
            messages.add(message);
        }
        template.sendBody("direct:start", messages);

        assertMockEndpointsSatisfied();

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=true");
        SjmsComponent sjms = new SjmsComponent();
        sjms.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", sjms);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                    .handled(true)
                    .setHeader("redeliveryAttempt")
                        .constant("1")
                    .log("Redelivery attempt 1")
                    .to("mock:test.redelivery")
                    .to("direct:start");
                
                from("direct:start")
                    .to("log:test-before?showAll=true")
                    .to("sjms:queue:batch.queue?transacted=true")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // This will force an exception to occur on the exchange
                            // which will invoke our onException handler to
                            // redeliver our batch message
                            
                            // Get the redelivery header 
                            String redeliveryAttempt = exchange.getIn().getHeader("redeliveryAttempt", String.class);
                            
                            // Verify that it isn't empty
                            // if it is do nothing and force the Exception
                            if(redeliveryAttempt != null && redeliveryAttempt.equals("1")) {
                                // do nothing and allow it to proceed
                            } else {
                                log.info("BatchMessage received without redelivery. Rolling back.");
                                exchange.setException(new Exception());
                            }
                        }
                    })
                    .to("mock:test.prebatch");

                from("sjms:queue:batch.queue")
                    .to("log:test-after?showAll=true")
                    .to("mock:test.postbatch");
            }
        };
    }
}
