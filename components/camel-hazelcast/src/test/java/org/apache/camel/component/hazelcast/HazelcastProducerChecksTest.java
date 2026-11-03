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
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.hazelcast.collection.IList;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ISet;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producers against a Hazelcast member: a missing operation, header or body fails with a message that names what is
 * missing, getAll gives a copy of the list or set, and removeIf works on a Hazelcast queue.
 */
class HazelcastProducerChecksTest extends CamelTestSupport {

    private static HazelcastInstance member;

    @BeforeAll
    static void startMember() {
        Config config = new Config();
        config.setClusterName("producer-checks-" + UUID.randomUUID());
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void stopMember() {
        if (member != null) {
            member.shutdown();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HazelcastCamelTestHelper.registerHazelcastComponents(context, member);
        return context;
    }

    @ParameterizedTest
    @ValueSource(strings = { "map", "multimap", "replicatedmap", "list", "set", "ringbuffer", "pncounter" })
    void noOperationNamesTheHeaderAndTheOption(String type) {
        IllegalArgumentException e = failure(() -> template.sendBody("hazelcast-" + type + ":no-operation", "a"));
        assertTrue(e.getMessage().contains(HazelcastConstants.OPERATION), e.getMessage());
        assertTrue(e.getMessage().contains("defaultOperation"), e.getMessage());
    }

    @Test
    void listGetWithoutIndexNamesTheHeader() {
        member.getList("get-without-index").add("a");

        IllegalArgumentException e = failure(() -> template.sendBodyAndHeader("hazelcast-list:get-without-index", null,
                HazelcastConstants.OPERATION, HazelcastOperation.GET));
        assertTrue(e.getMessage().contains(HazelcastConstants.OBJECT_POS), e.getMessage());
    }

    @Test
    void listGetAllGivesACopy() {
        IList<Object> list = member.getList("get-all");
        list.add("a");

        List<?> body = template.requestBodyAndHeader("hazelcast-list:get-all", null, HazelcastConstants.OPERATION,
                HazelcastOperation.GET_ALL, List.class);
        assertFalse(body instanceof IList, "the body is the distributed list itself");

        // a change of the distributed list does not show in the body, and the reverse
        list.add("b");
        assertEquals(List.of("a"), body);
        ((List<Object>) body).add("c");
        assertEquals(List.of("a", "b"), new ArrayList<>(list));
    }

    @Test
    void setGetAllGivesACopy() {
        ISet<Object> set = member.getSet("get-all");
        set.add("a");

        Set<?> body = template.requestBodyAndHeader("hazelcast-set:get-all", null, HazelcastConstants.OPERATION,
                HazelcastOperation.GET_ALL, Set.class);
        assertFalse(body instanceof ISet, "the body is the distributed set itself");

        // a change of the distributed set does not show in the body, and the reverse
        set.add("b");
        assertEquals(Set.of("a"), body);
        ((Set<Object>) body).add("c");
        assertEquals(Set.of("a", "b"), Set.copyOf(set));
    }

    @Test
    void queueRemoveIfRemovesTheMatchingItems() {
        IQueue<Object> queue = member.getQueue("remove-if");
        queue.addAll(List.of("a", "bb", "ccc", "dd", "bb"));

        Predicate<Object> twoLetters = item -> item.toString().length() == 2;
        Boolean removed = template.requestBodyAndHeader("hazelcast-queue:remove-if", twoLetters,
                HazelcastConstants.OPERATION, HazelcastOperation.REMOVE_IF, Boolean.class);
        assertTrue(removed);
        assertEquals(List.of("a", "ccc"), new ArrayList<>(queue));

        // nothing matches any more
        removed = template.requestBodyAndHeader("hazelcast-queue:remove-if", twoLetters,
                HazelcastConstants.OPERATION, HazelcastOperation.REMOVE_IF, Boolean.class);
        assertFalse(removed);
        assertEquals(List.of("a", "ccc"), new ArrayList<>(queue));
    }

    @Test
    void queueRemoveIfWithoutPredicateSaysSo() {
        IQueue<Object> queue = member.getQueue("remove-if-without-predicate");
        queue.add("a");

        IllegalArgumentException e = failure(() -> template.sendBodyAndHeader("hazelcast-queue:remove-if-without-predicate",
                null, HazelcastConstants.OPERATION, HazelcastOperation.REMOVE_IF));
        assertTrue(e.getMessage().contains("Predicate"), e.getMessage());
        assertEquals(1, queue.size());
    }

    @Test
    void queueDrainToWithoutCollectionNamesTheHeader() {
        IQueue<Object> queue = member.getQueue("drain-to-without-collection");
        queue.add("a");

        IllegalArgumentException e = failure(() -> template.sendBodyAndHeader("hazelcast-queue:drain-to-without-collection",
                null, HazelcastConstants.OPERATION, HazelcastOperation.DRAIN_TO));
        assertTrue(e.getMessage().contains(HazelcastConstants.DRAIN_TO_COLLECTION), e.getMessage());
        // no item was taken from the queue
        assertEquals(1, queue.size());
    }

    @Test
    void pnCounterGetAndAddWithoutBodySaysSo() {
        IllegalArgumentException e = failure(() -> template.sendBodyAndHeader("hazelcast-pncounter:get-and-add", null,
                HazelcastConstants.OPERATION, HazelcastOperation.GET_AND_ADD));
        assertTrue(e.getMessage().contains("numeric body"), e.getMessage());
        assertEquals(0, member.getPNCounter("get-and-add").get());
    }

    @Test
    void sedaBodyNeitherSerializableNorBytesSaysSo() {
        IllegalArgumentException e = failure(() -> template.sendBody("hazelcast-seda:not-serializable", new NotSerializable()));
        assertTrue(e.getMessage().contains("Serializable or convertible to bytes"), e.getMessage());
        assertTrue(e.getMessage().contains(NotSerializable.class.getName()), e.getMessage());
        assertEquals(0, member.getQueue("not-serializable").size());
    }

    @Test
    void sedaWithoutBodySaysSo() {
        IllegalArgumentException e = failure(() -> template.sendBody("hazelcast-seda:no-body", null));
        assertTrue(e.getMessage().contains("no body"), e.getMessage());
        assertEquals(0, member.getQueue("no-body").size());
    }

    private static IllegalArgumentException failure(Runnable send) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, send::run);
        return assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    /**
     * A body that is not Serializable and that no type converter turns into bytes.
     */
    private static final class NotSerializable {
    }
}
