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
package org.apache.camel.component.milo;

import java.util.Arrays;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadValuesClientTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";
    private static final String DIRECT_START_2 = "direct:start2";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    private static final String MILO_CLIENT_ITEM_C1_1
            = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?allowedSecurityPolicies=None&overrideHost=true";

    private static final String MOCK_TEST_1 = "mock:test1";

    private static final Logger LOG = LoggerFactory.getLogger(ReadValuesClientTest.class);

    @EndpointInject(MOCK_TEST_1)
    protected MockEndpoint test1Endpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Produce(DIRECT_START_2)
    protected ProducerTemplate producer2;

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);

                from(DIRECT_START_2)
                        .setHeader("CamelMiloNodeIds", constant(Arrays.asList("nsu=urn:org:apache:camel;s=myitem1")))
                        .enrich(resolve(MILO_CLIENT_ITEM_C1_1), new AggregationStrategy() {

                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                return newExchange;
                            }
                        }).to(MOCK_TEST_1);
            }
        };
    }

    @Test
    void testReadValuesSuccessful() throws Exception {
        this.test1Endpoint.expectedMinimumMessageCount(1);

        producer1.sendBody("Foo");
        producer2.sendBody("Bar");

        this.test1Endpoint.await();

        testBody(this.test1Endpoint.message(0), assertGoodValue("Foo"));
    }
}
