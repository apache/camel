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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class AggregratedJmsRouteTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AggregratedJmsRouteTest.class);
    private String timeOutEndpointUri = "jms:queue:test.a";
    private String multicastEndpointUri = "jms:queue:multicast";

    /*
     * negative receive wait timeout for jms is blocking so timeout during processing does not hang
     */
    @Test
    public void testJmsBatchTimeoutExpiryWithAggregrationDelay() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.setSleepForEmptyTest(3000);
        resultEndpoint.expectedMessageCount(1);
        for (int i = 1; i <= 2; i++) {
            String body = "message:" + i;
            sendExchange(timeOutEndpointUri, body);
        }

        resultEndpoint.assertIsSatisfied();
    }

    @Test
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

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(timeOutEndpointUri).to("jms:queue:test.b");

                from("jms:queue:test.b").aggregate(header("cheese"), new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            LOG.error("aggregration delay sleep inturrepted", e);
                            fail("aggregration delay sleep inturrepted");
                        }
                        return newExchange;
                    }
                }).completionTimeout(2000L).to("mock:result");

                from(multicastEndpointUri).to("jms:queue:point1", "jms:queue:point2", "jms:queue:point3");
                from("jms:queue:point1").process(new MyProcessor()).to("jms:queue:reply");
                from("jms:queue:point2").process(new MyProcessor()).to("jms:queue:reply");
                from("jms:queue:point3").process(new MyProcessor()).to("jms:queue:reply");
                from("jms:queue:reply").aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionSize(3)
                    .to("mock:reply");
            }
        };
    }
    private static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            LOG.info("get the exchange here " + exchange);
        }

    }
}
