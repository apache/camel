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
package org.apache.camel.component.hazelcast;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The map consumer against a Hazelcast member: an entry whose time to live ends gives an expired event.
 */
class HazelcastMapConsumerExpiredTest extends CamelTestSupport {

    private HazelcastInstance member;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        Config config = new Config();
        config.setClusterName("map-expired-" + UUID.randomUUID());
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        // look for expired entries every second rather than every five
        config.setProperty("hazelcast.internal.map.expiration.task.period.seconds", "1");
        member = Hazelcast.newHazelcastInstance(config);

        CamelContext context = super.createCamelContext();
        HazelcastCamelTestHelper.registerHazelcastComponents(context, member);
        return context;
    }

    @AfterEach
    void stopMember() {
        // stop the route before the member, so the consumer removes its listener from a running member
        context.stop();
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void entryWhoseTimeToLiveEndsGivesAnExpiredEvent() throws Exception {
        MockEndpoint added = getMockEndpoint("mock:added");
        added.expectedMessageCount(1);
        added.expectedHeaderReceived(HazelcastConstants.OBJECT_ID, "4711");
        MockEndpoint expired = getMockEndpoint("mock:expired");
        expired.expectedMessageCount(1);
        expired.expectedHeaderReceived(HazelcastConstants.OBJECT_ID, "4711");
        expired.expectedHeaderReceived(HazelcastConstants.LISTENER_TYPE, HazelcastConstants.CACHE_LISTENER);

        member.getMap("expiring").put("4711", "my-foo", 1, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-map:expiring")
                        .choice()
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
                        .to("mock:added")
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.EXPIRED))
                        .to("mock:expired")
                        .otherwise()
                        .to("mock:other");
            }
        };
    }
}
