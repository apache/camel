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

package org.apache.camel.component.rocketmq;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rocketmq.infra.EmbeddedRocketMQServer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RocketMQRouteTest extends CamelTestSupport {

    public static final String EXPECTED_MESSAGE = "hello, RocketMQ.";

    private static final int NAMESRV_PORT = 59876;

    private static final String NAMESRV_ADDR = "127.0.0.1:" + NAMESRV_PORT;

    private static final String START_ENDPOINT_URI = "rocketmq:START_TOPIC?producerGroup=p1&consumerGroup=c1&sendTag=startTag";

    private static final String RESULT_ENDPOINT_URI = "mock:result";

    private static NamesrvController namesrvController;

    private static BrokerController brokerController;

    private MockEndpoint resultEndpoint;

    @BeforeAll
    static void beforeAll() throws Exception {
        namesrvController = EmbeddedRocketMQServer.createAndStartNamesrv(NAMESRV_PORT);
        brokerController = EmbeddedRocketMQServer.createAndStartBroker(NAMESRV_ADDR);
        EmbeddedRocketMQServer.createTopic(NAMESRV_ADDR, "DefaultCluster", "START_TOPIC");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        resultEndpoint = (MockEndpoint) context.getEndpoint(RESULT_ENDPOINT_URI);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        RocketMQComponent rocketMQComponent = new RocketMQComponent();
        rocketMQComponent.setNamesrvAddr(NAMESRV_ADDR);
        camelContext.addComponent("rocketmq", rocketMQComponent);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(START_ENDPOINT_URI).to(RESULT_ENDPOINT_URI);
            }
        };
    }

    @Test
    public void testSimpleRoute() throws Exception {
        resultEndpoint.expectedBodiesReceived(EXPECTED_MESSAGE);
        resultEndpoint.message(0).header(RocketMQConstants.TOPIC).isEqualTo("START_TOPIC");
        resultEndpoint.message(0).header(RocketMQConstants.TAG).isEqualTo("startTag");

        template.sendBody(START_ENDPOINT_URI, EXPECTED_MESSAGE);

        resultEndpoint.assertIsSatisfied();
    }

    @AfterAll
    public static void afterAll() {
        brokerController.shutdown();
        namesrvController.shutdown();
    }
}
