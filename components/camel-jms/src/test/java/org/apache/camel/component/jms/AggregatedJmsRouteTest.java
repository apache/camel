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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

public class AggregatedJmsRouteTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(AggregatedJmsRouteTest.class);
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private final String timeOutEndpointUri = "jms:queue:AggregatedJmsRouteTestQueueA";
    private final String multicastEndpointUri = "jms:queue:multicast";

    /*
     * negative receive wait timeout for jms is blocking so timeout during processing does not hang
     */
    @Test
    public void testJmsBatchTimeoutExpiryWithAggregationDelay() throws Exception {
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

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(timeOutEndpointUri).to("jms:queue:AggregatedJmsRouteTestQueueB");

                from("jms:queue:AggregatedJmsRouteTestQueueB").aggregate(header("cheese"), (oldExchange, newExchange) -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        LOG.error("aggregation delay sleep interrupted", e);
                        fail("aggregation delay sleep interrupted");
                    }
                    return newExchange;
                }).completionTimeout(2000L).to("mock:result");

                from(multicastEndpointUri).to("jms:queue:AggregatedJmsRouteTestQueuePoint1",
                        "jms:queue:AggregatedJmsRouteTestQueuePoint2", "jms:queue:AggregatedJmsRouteTestQueuePoint3");
                from("jms:queue:AggregatedJmsRouteTestQueuePoint1").process(new MyProcessor())
                        .to("jms:queue:AggregatedJmsRouteTestQueueReply");
                from("jms:queue:AggregatedJmsRouteTestQueuePoint2").process(new MyProcessor())
                        .to("jms:queue:AggregatedJmsRouteTestQueueReply");
                from("jms:queue:AggregatedJmsRouteTestQueuePoint3").process(new MyProcessor())
                        .to("jms:queue:AggregatedJmsRouteTestQueueReply");
                from("jms:queue:AggregatedJmsRouteTestQueueReply")
                        .aggregate(header("cheese"), new UseLatestAggregationStrategy()).completionSize(3)
                        .to("mock:reply");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

    private static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {
            LOG.info("get the exchange here {}", exchange);
        }

    }
}
