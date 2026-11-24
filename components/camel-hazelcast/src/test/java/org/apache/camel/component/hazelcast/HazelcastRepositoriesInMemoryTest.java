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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Address;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapStoreAdapter;
import org.apache.camel.processor.aggregate.hazelcast.HazelcastAggregationRepository;
import org.apache.camel.processor.aggregate.hazelcast.ReplicatedHazelcastAggregationRepository;
import org.apache.camel.processor.idempotent.hazelcast.HazelcastIdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Hazelcast repositories tell whether they keep their data only in memory, from the cluster as it is when asked.
 */
class HazelcastRepositoriesInMemoryTest {

    private final String clusterName = "in-memory-" + UUID.randomUUID();
    private final List<HazelcastInstance> instances = new ArrayList<>();
    private final List<ServiceSupport> repositories = new ArrayList<>();

    @AfterEach
    void stop() {
        repositories.forEach(ServiceSupport::stop);
        // the client first, then the members
        for (int i = instances.size() - 1; i >= 0; i--) {
            instances.get(i).shutdown();
        }
    }

    @Test
    void memberAloneIsInMemory() {
        HazelcastInstance member = newMember(memberConfig());

        assertTrue(idempotent(member).isInMemory());
        assertTrue(keyValue(member).isInMemory());
        assertTrue(aggregation(member).isInMemory());
        assertTrue(replicatedAggregation(member).isInMemory());
    }

    @Test
    void backupOnAnotherMemberIsNotInMemory() {
        HazelcastInstance member = newMember(memberConfig());
        HazelcastIdempotentRepository idempotent = idempotent(member);
        HazelcastKeyValueRepository keyValue = keyValue(member);
        HazelcastAggregationRepository aggregation = aggregation(member);
        ReplicatedHazelcastAggregationRepository replicated = replicatedAggregation(member);
        assertTrue(idempotent.isInMemory());

        // decided when asked: a second member joins, and the default backup count of 1 keeps a copy on it
        newMember(memberConfig());
        await().atMost(30, TimeUnit.SECONDS).until(() -> member.getCluster().getMembers().size() == 2);

        assertFalse(idempotent.isInMemory());
        assertFalse(keyValue.isInMemory());
        assertFalse(aggregation.isInMemory());
        assertFalse(replicated.isInMemory());
    }

    @Test
    void noBackupWithOtherMembersIsInMemory() {
        Config config = memberConfig();
        config.addMapConfig(new MapConfig("ids").setBackupCount(0).setAsyncBackupCount(0));
        HazelcastInstance member = newMember(config);
        newMember(memberConfig().addMapConfig(new MapConfig("ids").setBackupCount(0).setAsyncBackupCount(0)));
        await().atMost(30, TimeUnit.SECONDS).until(() -> member.getCluster().getMembers().size() == 2);

        assertTrue(idempotent(member).isInMemory());
    }

    @Test
    void mapStoreIsNotInMemory() {
        Config config = memberConfig();
        MapConfig mapConfig = new MapConfig("ids");
        mapConfig.getMapStoreConfig().setEnabled(true).setImplementation(new MapStoreAdapter<String, Boolean>());
        config.addMapConfig(mapConfig);
        HazelcastInstance member = newMember(config);

        assertFalse(idempotent(member).isInMemory());
        // another map of the same member has no map store
        assertTrue(keyValue(member).isInMemory());
    }

    @Test
    void clientIsNotInMemory() {
        HazelcastInstance member = newMember(memberConfig());
        HazelcastInstance client = newClient(member);

        assertFalse(idempotent(client).isInMemory());
        assertFalse(keyValue(client).isInMemory());
        assertFalse(aggregation(client).isInMemory());
    }

    @Test
    void notStartedCannotTell() {
        HazelcastInstance member = newMember(memberConfig());

        assertFalse(new HazelcastIdempotentRepository(member, "ids").isInMemory());
        assertFalse(new HazelcastKeyValueRepository(member, "entries").isInMemory());
        assertFalse(new HazelcastAggregationRepository("aggregates", member).isInMemory());
        assertFalse(new ReplicatedHazelcastAggregationRepository("replicated", member).isInMemory());
    }

    private HazelcastIdempotentRepository idempotent(HazelcastInstance instance) {
        return started(new HazelcastIdempotentRepository(instance, "ids"));
    }

    private HazelcastKeyValueRepository keyValue(HazelcastInstance instance) {
        return started(new HazelcastKeyValueRepository(instance, "entries"));
    }

    private HazelcastAggregationRepository aggregation(HazelcastInstance instance) {
        return started(new HazelcastAggregationRepository("aggregates", instance));
    }

    private ReplicatedHazelcastAggregationRepository replicatedAggregation(HazelcastInstance instance) {
        return started(new ReplicatedHazelcastAggregationRepository("replicated", instance));
    }

    private <T extends ServiceSupport> T started(T repository) {
        repository.start();
        repositories.add(repository);
        return repository;
    }

    private HazelcastInstance newMember(Config config) {
        HazelcastInstance member = Hazelcast.newHazelcastInstance(config);
        instances.add(member);
        return member;
    }

    private HazelcastInstance newClient(HazelcastInstance member) {
        Address address = member.getCluster().getLocalMember().getAddress();
        ClientConfig config = new ClientConfig();
        config.setClusterName(clusterName);
        config.getNetworkConfig().addAddress(address.getHost() + ":" + address.getPort());
        config.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(30000);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(config);
        instances.add(client);
        return client;
    }

    private Config memberConfig() {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.setProperty("hazelcast.local.localAddress", "127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        // the members of a test find each other on the loopback address, the cluster name keeps other tests out
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        return config;
    }
}
