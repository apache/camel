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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BatchProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

public class AggregratedJmsRouteTest extends ContextTestSupport {

    private static final transient Log LOG = LogFactory.getLog(AggregratedJmsRouteTest.class);
    private String timeOutEndpointUri = "jms:queue:test.a";
    private String multicastEndpointUri = "jms:queue:multicast";

    /*
     * negative receive wait timeout for jms is blocking so timeout during processing does not hang
     */
    public void testJmsBatchTimeoutExpiryWithAggregrationDelay() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.setSleepForEmptyTest(3 * BatchProcessor.DEFAULT_BATCH_TIMEOUT);
        resultEndpoint.expectedMessageCount(1);
        for (int i = 1; i <= 2; i++) {
            String body = "message:" + i;
            sendExchange(timeOutEndpointUri, body);
        }

        resultEndpoint.assertIsSatisfied();
    }


    public void testJmsMulticastAndAggregration() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:reply", MockEndpoint.class);

        resultEndpoint.expectedMessageCount(2);
        for (int i = 1; i <= 2; i++) {
            String body = "message:" + i;
            sendExchange(multicastEndpointUri, body);
        }

        resultEndpoint.assertIsSatisfied(8000);
    }



    protected void sendExchange(String uri, final Object expectedBody) {
        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);
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
                from(timeOutEndpointUri).to("jms:queue:test.b");
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

                from(multicastEndpointUri).to("jms:queue:point1", "jms:queue:point2", "jms:queue:point3");
                from("jms:queue:point1").process(new MyProcessor()).to("jms:queue:reply");
                from("jms:queue:point2").process(new MyProcessor()).to("jms:queue:reply");
                from("jms:queue:point3").process(new MyProcessor()).to("jms:queue:reply");
                from("jms:queue:reply").aggregator(header("cheese"), new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        Exchange copy = newExchange.copy();
                        LOG.info("try to aggregating the message ");
                        Integer old = (Integer) oldExchange.getProperty("aggregated");
                        if (old == null) {
                            old = 1;
                        }
                        Exchange result = copy;
                        result.setProperty("aggregated", old + 1);
                        return result;
                    }
                }).completedPredicate(header("aggregated").isEqualTo(3))
                .to("mock:reply");
            }
        };
    }
    private class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            LOG.info("get the exchange here " + exchange);
        }

    }
}
