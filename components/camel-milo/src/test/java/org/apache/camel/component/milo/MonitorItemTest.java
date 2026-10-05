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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing the monitor functionality for item
 */
public class MonitorItemTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    private static final String MILO_CLIENT_ITEM_C1_1 = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI,
                                                                "myitem1")
                                                        + "&requestedPublishingInterval=2000&samplingInterval=100&queueSize=4"
                                                        + "&allowedSecurityPolicies=None&overrideHost=true";

    // second client endpoint to check for changed default samplingInterval, sharing client for MILO_CLIENT_ITEM_C1_1.
    private static final String MILO_CLIENT_ITEM_C1_2 = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI,
                                                                "myitem1")
                                                        + "&requestedPublishingInterval=2000&queueSize=4"
                                                        + "&allowedSecurityPolicies=None&overrideHost=true";

    // another client endpoint to check for independent requestedPublishingInterval,
    // will create a second client because requestedPublishingInterval is part of toCacheId.
    private static final String MILO_CLIENT_ITEM_C2_1 = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
                                                        + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI,
                                                                "myitem1")
                                                        + "&requestedPublishingInterval=1000&samplingInterval=100&queueSize=3"
                                                        + "&allowedSecurityPolicies=None&overrideHost=true";

    private static final String MOCK_TEST_1 = "mock:test1";

    private static final String MOCK_TEST_2 = "mock:test2";

    private static final String MOCK_TEST_3 = "mock:test3";

    private static final Logger LOG = LoggerFactory.getLogger(MonitorItemTest.class);

    @EndpointInject(MOCK_TEST_1)
    protected MockEndpoint test1Endpoint;

    @EndpointInject(MOCK_TEST_2)
    protected MockEndpoint test2Endpoint;

    @EndpointInject(MOCK_TEST_3)
    protected MockEndpoint test3Endpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);

                from(resolve(MILO_CLIENT_ITEM_C1_1)).to(MOCK_TEST_1);
                from(resolve(MILO_CLIENT_ITEM_C1_2)).to(MOCK_TEST_2);
                from(resolve(MILO_CLIENT_ITEM_C2_1)).to(MOCK_TEST_3);
            }
        };
    }

    @BeforeEach
    public void setup(TestInfo testInfo) {
        final var displayName = testInfo.getDisplayName();
        LOG.info("********************************************************************************");
        LOG.info(displayName);
        LOG.info("********************************************************************************");
    }

    /**
     * Monitor multiple events With explicit parameters for requestedPublishingInterval, samplingInterval, and queueSize
     */
    @Test
    public void testMonitorItem1() throws Exception {
        /*
         * we will wait 2 * 100 milliseconds between server updates since the
         * explicitly set update rate is 100 milliseconds (samplingInterval) in most clients.
         * With 2000ms requestedPublishingInterval and bigger queueSize of 4 we should get all updates (in client 1).
         * The test relies on milo's default discardOldest=true so the newest value ("Done") survives a queue overflow,
         * there is currently no parameter to change this.
         * Extra clients have been added to test independence of parameters set. See description on test endpoints
         *
         */
        final var time = 2 * 100;
        final var timeout = 10 * 1_000; // 10 seconds timeout for assertions

        /**
         * test1Endpoint is related to client C1_1 with: requestedPublishingInterval=2000 samplingInterval=100
         * queueSize=4 It should get the first 3 messages because of samplingInterval less than update rate and
         * queueSize over 3 and an update pause of 2 seconds after they are sent. From the rest 16 messages, sent in
         * about 3 seconds, some should be dropped because of queueSize. They should fall into 2 (or 3 depending on
         * exact timing) publishing periods, so there should be at least 8 and at most 12 extra messages.
         */
        test1Endpoint.reset();
        test1Endpoint.setMinimumExpectedMessageCount(11);    // the first 3, plus at least 8 more from rest (if they fall to 2 periods)
        test1Endpoint.setAssertPeriod(3000);

        /**
         * test2Endpoint is related to client C1_2 with: requestedPublishingInterval=2000 samplingInterval=1000 from
         * default queueSize=4 It should get 1 (or 2 depending on exact timing) of the first 3 messages because of
         * samplingInterval and an update pause of 2 seconds after they are sent. From the rest 16 messages, sent in
         * about 3 seconds, only 3 or 4 should be sampled. 8 and at most 12 extra messages. So minimum is 4, maximum is
         * 6 messages
         */
        test2Endpoint.reset();
        test2Endpoint.setMinimumExpectedMessageCount(4);    // One or 2 of the first 3, plus 3 or 4 of the remaining 16
        test2Endpoint.setAssertPeriod(3000);

        /**
         * test3Endpoint is related to client C2_1 with: requestedPublishingInterval=1000 samplingInterval=100
         * queueSize=3 It should get the first 3 messages because of samplingInterval less than update rate and
         * queueSize over 3 and an update pause of 2 seconds after they are sent. From the rest 16 messages, sent in
         * about 3 seconds, some should be dropped because of queueSize. They should fall into 4 (or 5 depending on
         * exact timing) publishing periods, so there should be at least 9 (3*3+1) and at most 10 (1+3*3+1) extra
         * messages.
         */
        test3Endpoint.reset();
        test3Endpoint.setMinimumExpectedMessageCount(12);    // the first 3, plus at least 3*3 more from rest (if they fall to 3 periods), but no more than 4*3
        test3Endpoint.setAssertPeriod(3000);

        // Allow time for OPC UA client-server connection to establish
        await().pollDelay(1, TimeUnit.SECONDS).untilAsserted(() -> {
            // Connection should be established
        });

        // Debug: Check if server is configured correctly
        LOG.info("Server Port: {}", this.getServerPort());
        LOG.info("Client URI resolved: {}", resolve(MILO_CLIENT_ITEM_C1_1));

        // set server values sent faster than requestedPublishingInterval so they must be put to queue
        this.producer1.sendBody("Foo");
        await().pollDelay(time, TimeUnit.MILLISECONDS).untilAsserted(() -> {
        });
        this.producer1.sendBody("Bar");
        await().pollDelay(time, TimeUnit.MILLISECONDS).untilAsserted(() -> {
        });
        this.producer1.sendBody("Baz");
        await().pollDelay(time, TimeUnit.MILLISECONDS).untilAsserted(() -> {
        });

        // now wait for requestedPublishingInterval so we are sure the 3 values are passed
        await().pollDelay(10 * time, TimeUnit.MILLISECONDS).untilAsserted(() -> {
        });
        for (int i = 1; i <= 15; i++) {
            this.producer1.sendBody("Message " + i);
            await().pollDelay(time, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            });
        }
        this.producer1.sendBody("Done");

        // tests
        testBody(this.test1Endpoint.message(0), assertGoodValue("Foo"));
        testBody(this.test1Endpoint.message(1), assertGoodValue("Bar"));
        testBody(this.test1Endpoint.message(2), assertGoodValue("Baz"));

        // assert
        MockEndpoint.assertIsSatisfied(context, timeout, TimeUnit.MILLISECONDS);

        // The number of messages must be less than the number sent, because from the 15 messages sent at last part,
        // about 10 should be in one requestedPublishingInterval, everything over 4 should be dropped
        int count1 = this.test1Endpoint.getReceivedCounter();
        assertTrue(count1 < 18, "No messages have been dropped, but should because of queueSize");

        // get the last exchange, this must be the last one, if messages are dropped it must be some it between
        List<Exchange> receivedExchanges = this.test1Endpoint.getReceivedExchanges();
        Exchange last = receivedExchanges.get(receivedExchanges.size() - 1);
        assertGoodValue("Done").accept((DataValue) last.getIn().getBody());

        // The second client should get no more than 6 messages
        int count2 = this.test2Endpoint.getReceivedCounter();
        assertTrue(count2 <= 6, "Not enough messages have been dropped, but should because of queueSize");

        // The third client has request
        int count3 = this.test3Endpoint.getReceivedCounter();
        assertTrue(count3 <= 15, "Not enough messages have been dropped, but should because of queueSize");
    }
}
