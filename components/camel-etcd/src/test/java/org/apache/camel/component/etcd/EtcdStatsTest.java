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
package org.apache.camel.component.etcd;

import mousio.etcd4j.responses.EtcdLeaderStatsResponse;
import mousio.etcd4j.responses.EtcdSelfStatsResponse;
import mousio.etcd4j.responses.EtcdStoreStatsResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd.support.EtcdTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class EtcdStatsTest extends EtcdTestSupport {

    @Test
    public void testStats() throws Exception {
        testStatsConsumer("mock:stats-leader-consumer", EtcdConstants.ETCD_LEADER_STATS_PATH, EtcdLeaderStatsResponse.class);
        testStatsConsumer("mock:stats-self-consumer", EtcdConstants.ETCD_SELF_STATS_PATH, EtcdSelfStatsResponse.class);
        testStatsConsumer("mock:stats-store-consumer", EtcdConstants.ETCD_STORE_STATS_PATH, EtcdStoreStatsResponse.class);

        testStatsProducer("direct:stats-leader", "mock:stats-leader-producer", EtcdConstants.ETCD_LEADER_STATS_PATH, EtcdLeaderStatsResponse.class);
        testStatsProducer("direct:stats-self", "mock:stats-self-producer", EtcdConstants.ETCD_SELF_STATS_PATH, EtcdSelfStatsResponse.class);
        testStatsProducer("direct:stats-store", "mock:stats-store-producer", EtcdConstants.ETCD_STORE_STATS_PATH, EtcdStoreStatsResponse.class);
    }

    protected void testStatsConsumer(String mockEnpoint, String expectedPath, final Class<?> expectedType) throws Exception {
        MockEndpoint mock = getMockEndpoint(mockEnpoint);
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EtcdConstants.ETCD_NAMESPACE, "stats");
        mock.expectedHeaderReceived(EtcdConstants.ETCD_PATH, expectedPath);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody().getClass() == expectedType;
            }
        });

        assertMockEndpointsSatisfied();
    }

    protected void testStatsProducer(String producerEnpoint, String mockEnpoint, String expectedPath, final Class<?> expectedType) throws Exception {
        sendBody(producerEnpoint, "");

        testStatsConsumer(mockEnpoint, expectedPath, expectedType);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // CONSUMER
                from("etcd-stats:leader?delay=50&initialDelay=0")
                    .to("mock:stats-leader-consumer");
                from("etcd-stats:self?delay=50&initialDelay=0")
                    .to("mock:stats-self-consumer");
                from("etcd-stats:store?delay=50&initialDelay=0")
                    .to("mock:stats-store-consumer");

                // PRODUCER
                from("direct:stats-leader")
                    .to("etcd-stats:leader")
                        .to("mock:stats-leader-producer");
                from("direct:stats-self")
                    .to("etcd-stats:self")
                        .to("mock:stats-self-producer");
                from("direct:stats-store")
                    .to("etcd-stats:store")
                        .to("mock:stats-store-producer");
            }
        };
    }
}
