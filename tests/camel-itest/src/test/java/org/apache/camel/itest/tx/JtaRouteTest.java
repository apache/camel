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
package org.apache.camel.itest.tx;

import java.util.Arrays;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.transaction.RequiresNewJtaTransactionPolicy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JtaRouteTest extends CamelTestSupport {
    @EndpointInject("mock:splitted")
    private MockEndpoint splitted;

    @EndpointInject("mock:test")
    private MockEndpoint test;

    @EndpointInject("mock:a")
    private MockEndpoint a;

    @EndpointInject("mock:b")
    private MockEndpoint b;

    @EndpointInject("mock:c")
    private MockEndpoint c;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("direct:split_test")
    private ProducerTemplate split;

    @EndpointInject("direct:multicast_test")
    private ProducerTemplate multicast;

    @EndpointInject("direct:recipient_test")
    private ProducerTemplate recipient;

    @EndpointInject("direct:enrich_test")
    private ProducerTemplate enrich;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("PROPAGATION_REQUIRES_NEW", new RequiresNewJtaTransactionPolicy());

                from("direct:split_test")
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .to("direct:split");

                from("direct:split")
                        .split(body()).delimiter("_").to("mock:splitted").end()
                        .log("after splitter log which you will never see...");

                from("direct:multicast_test").routeId("r.route1")
                        .log(LoggingLevel.DEBUG, "Entering route: ${routeId}")
                        .transacted()
                        .to("direct:multicast")
                        .log("will never get here");

                from("direct:multicast").routeId("r.route2")
                        .log(LoggingLevel.DEBUG, "Entering route: ${routeId}")
                        .multicast()
                        .to("log:r.test", "direct:route3", "mock:test")
                        .end();

                from("direct:route3").routeId("r.route3")
                        .process(e -> Assertions.assertTrue(e.isTransacted()))
                        .log(LoggingLevel.DEBUG, "Entering route: ${routeId}");

                from("direct:recipient_test")
                        .transacted()
                        .to("direct:recipient");

                from("direct:recipient")
                        .recipientList(constant("mock:a", "mock:b", "mock:c"));

                from("direct:enrich_test")
                        .transacted()
                        .to("direct:enrich");

                from("direct:enrich")
                        .enrich("direct:content", new AggregationStrategy() {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                if (newExchange == null) {
                                    return oldExchange;
                                }
                                Object oldBody = oldExchange.getIn().getBody();
                                Object newBody = newExchange.getIn().getBody();
                                oldExchange.getIn().setBody(oldBody + " " + newBody);
                                return oldExchange;
                            }
                        })
                        .to("mock:result");

                from("direct:content").transform().constant("Enrich");

            }
        };
    }

    @Test
    void testTransactedSplit() throws Exception {
        splitted.setExpectedMessageCount(2);
        splitted.expectedBodiesReceived("requires", "new");
        splitted.whenAnyExchangeReceived(e -> Assertions.assertTrue(e.isTransacted()));

        split.sendBody("requires_new");
        splitted.assertIsSatisfied();
    }

    @Test
    public void testTransactedMultiCast() throws Exception {
        test.setExpectedMessageCount(1);
        test.expectedBodiesReceived("multicast");
        splitted.whenAnyExchangeReceived(e -> Assertions.assertTrue(e.isTransacted()));

        multicast.sendBody("multicast");
        test.assertIsSatisfied();
    }

    @Test
    public void testTransactedRecipient() throws Exception {
        Arrays.asList(a, b, c).forEach(m -> {
            m.setExpectedMessageCount(1);
            m.expectedBodiesReceived("recipient");
            m.whenAnyExchangeReceived(e -> Assertions.assertTrue(e.isTransacted()));
        });

        recipient.sendBody("recipient");

        for (MockEndpoint m : Arrays.asList(a, b, c)) {
            m.assertIsSatisfied();
        }
    }

    @Test
    public void testTransactedEnrich() throws Exception {
        result.setExpectedMessageCount(1);
        result.expectedBodiesReceived("Message Enrich");
        result.whenAnyExchangeReceived(e -> Assertions.assertTrue(e.isTransacted()));

        enrich.sendBody("Message");
        result.assertIsSatisfied();
    }
}
