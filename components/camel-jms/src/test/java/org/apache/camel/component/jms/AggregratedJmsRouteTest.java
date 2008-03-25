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
package org.apache.camel.component.jms;


import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BatchProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

public class AggregratedJmsRouteTest extends ContextTestSupport {

    private static final transient Log LOG = LogFactory.getLog(AggregratedJmsRouteTest.class);
    private String startEndpointUri = "jms:queue:test.a";

    /*
     * negative recieve wait timeout for jms is blocking so timeout during processing does not hang
     */
    public void testJmsBatchTimeoutExpiryWithAggregrationDelay() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.setSleepForEmptyTest(3 * BatchProcessor.DEFAULT_BATCH_TIMEOUT);
        resultEndpoint.expectedMessageCount(1);
        for (int i = 1; i <= 2; i++) {
            String body = "message:" + i;
            sendExchange(body);
        }

        resultEndpoint.assertIsSatisfied();
    }

    protected void sendExchange(final Object expectedBody) {
        template.sendBodyAndHeader(startEndpointUri, expectedBody, "cheese", 123);
    }


    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("jms", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(startEndpointUri).to("jms:queue:test.b");
                from("jms:queue:test.b").aggregator(header("cheese"), new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        try {
                            Thread.sleep(2 * BatchProcessor.DEFAULT_BATCH_TIMEOUT);
                        } catch (InterruptedException e) {
                            LOG.error("aggregration delay sleep inturrepted", e);
                            fail("aggregration delay sleep inturrepted");
                        }
                        return newExchange;
                    }
                }).to("mock:result");
            }
        };
    }
}
