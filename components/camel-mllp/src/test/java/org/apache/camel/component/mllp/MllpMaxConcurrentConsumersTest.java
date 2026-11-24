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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to maxConcurrentConsumers configuration
 */
public class MllpMaxConcurrentConsumersTest extends CamelTestSupport {

    static final String TEST_MESSAGE
            = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';
    static final String ROUTE_ID = "mllp-max-concurrent-consumers-route";

    @RegisterExtension
    AvailablePortFinder.Port mllpClientPort1 = AvailablePortFinder.find();

    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @RegisterExtension
    public MllpClientResource mllpClient2 = new MllpClientResource();

    @EndpointInject(value = "mock://result")
    MockEndpoint result;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void doPreSetup() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(mllpClientPort1.getPort());

        // both clients connect to the same consumer
        mllpClient2.setMllpHost("localhost");
        mllpClient2.setMllpPort(mllpClientPort1.getPort());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        return context;
    }

    @Test
    public void testConcurrentConsumersLessThanMaxConsumers() throws Exception {

        addTestRoute(2);
        result.expectedMessageCount(1);

        mllpClient.connect();

        String testMessage = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';
        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage, 10000);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testConcurrentConsumersMoreThanMaxConsumers() throws Exception {
        addTestRoute(1);
        result.expectedMessageCount(1);

        mllpClient.connect();
        mllpClient.sendMessageAndWaitForAcknowledgement(TEST_MESSAGE, 10000);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        // the second connection is accepted by the operating system, and then reset by the consumer: no acknowledgement
        mllpClient2.connect();
        assertEquals("", sendAndReceiveAcknowledgement(mllpClient2), "the second connection should be reset");

        // the rejected connection is not kept by the consumer
        MllpTcpServerConsumer consumer = (MllpTcpServerConsumer) context.getRoute(ROUTE_ID).getConsumer();
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getConsumerRunnables().size() == 1);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testConnectionAfterTheOtherOneClosedIsServed() throws Exception {
        addTestRoute(1);
        result.expectedMessageCount(2);

        mllpClient.connect();
        mllpClient.sendMessageAndWaitForAcknowledgement(TEST_MESSAGE, 10000);
        mllpClient.close();

        // once the first connection has ended, the consumer serves a new one
        MllpTcpServerConsumer consumer = (MllpTcpServerConsumer) context.getRoute(ROUTE_ID).getConsumer();
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getConsumerRunnables().isEmpty());
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            mllpClient2.connect();
            try {
                assertTrue(sendAndReceiveAcknowledgement(mllpClient2).contains("MSA|AA"));
            } finally {
                mllpClient2.close();
            }
        });

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    /**
     * The acknowledgement, or an empty string when the consumer closed or reset the connection.
     */
    static String sendAndReceiveAcknowledgement(MllpClientResource client) throws Exception {
        try {
            return client.sendMessageAndWaitForAcknowledgement(TEST_MESSAGE, 10000);
        } catch (MllpJUnitResourceException e) {
            return "";
        }
    }

    void addTestRoute(int maxConcurrentConsumers) throws Exception {
        RouteBuilder builder = new RouteBuilder() {

            @Override
            public void configure() {
                String routeId = ROUTE_ID;

                fromF("mllp://%s:%d?maxConcurrentConsumers=%d&autoAck=true&connectTimeout=100&receiveTimeout=1000",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort(), maxConcurrentConsumers)
                        .routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Test route received message")
                        .to(result);

            }
        };
        context.addRoutes(builder);
        context.start();
    }
}
