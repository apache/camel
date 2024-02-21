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
package org.apache.camel.component.etcd3.policy;

import io.etcd.jetcd.ByteSequence;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd3.support.Etcd3TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Etcd3RoutePolicyIT extends Etcd3TestSupport {

    private Etcd3RoutePolicy policyAsLeader;
    private Etcd3RoutePolicy policyAsNotLeader;

    @AfterEach
    public void cleanUp() throws Exception {
        if (policyAsLeader != null) {
            policyAsLeader.doStop();
        }
        if (policyAsNotLeader != null) {
            policyAsNotLeader.doStop();
        }
    }

    @Test
    void testAsLeader() throws Exception {
        await().atMost(10, SECONDS).untilAsserted(() -> assertTrue(policyAsLeader.isLeader()));
        MockEndpoint mock = getMockEndpoint("mock:leader");
        mock.expectedBodiesReceived("ABC");
        template.sendBody("direct:leader", "ABC");
        MockEndpoint.assertIsSatisfied(context);
        getClient().getLeaseClient().revoke(policyAsLeader.getLeaseId());
        getClient().getKVClient().put(
                ByteSequence.from(policyAsLeader.getServicePath().getBytes()),
                ByteSequence.from("not-leader".getBytes()))
                .get();
        await().atMost(10, SECONDS).untilAsserted(() -> assertFalse(policyAsLeader.isLeader()));
    }

    @Test
    void testAsNotLeader() throws Exception {
        await().atMost(10, SECONDS).untilAsserted(() -> assertFalse(policyAsNotLeader.isLeader()));
        getClient().getKVClient().delete(
                ByteSequence.from(policyAsNotLeader.getServicePath().getBytes())).get();
        await().atMost(10, SECONDS).untilAsserted(() -> assertTrue(policyAsNotLeader.isLeader()));
        MockEndpoint mock = getMockEndpoint("mock:not-leader");
        mock.expectedBodiesReceived("DEF");
        template.sendBody("direct:not-leader", "DEF");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                policyAsLeader = new Etcd3RoutePolicy(getClient());
                policyAsLeader.setServicePath("/Etcd3RoutePolicyIT/leader");
                policyAsLeader.setServiceName("leader");
                policyAsLeader.setTtl(5);
                from("direct:leader")
                        .routePolicy(policyAsLeader)
                        .id("as-leader")
                        .to("mock:leader");
                policyAsNotLeader = new Etcd3RoutePolicy(getClient());
                policyAsNotLeader.setServicePath("/Etcd3RoutePolicyIT/not-leader");
                policyAsNotLeader.setServiceName("not-leader");
                policyAsNotLeader.setTtl(5);
                getClient().getKVClient().put(
                        ByteSequence.from(policyAsNotLeader.getServicePath().getBytes()),
                        ByteSequence.from("leader".getBytes()))
                        .get();
                from("direct:not-leader")
                        .routePolicy(policyAsNotLeader)
                        .id("as-not-leader")
                        .to("mock:not-leader");

            }
        };
    }
}
