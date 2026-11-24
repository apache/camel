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
package org.apache.camel.processor.aggregate.hazelcast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link ReplicatedHazelcastAggregationRepository} cannot do optimistic locking (a ReplicatedMap has no atomic
 * compare-and-set), so it refuses to start with {@code optimistic=true}. The last test is a control (it passes without
 * the change too): in its thread-safe mode, with one aggregator per correlation key as the documentation says, no
 * message is lost when several threads aggregate the same key.
 */
class ReplicatedHazelcastAggregationRepositoryOptimisticTest extends HazelcastAggregationRepositoryCamelTestSupport {

    @Test
    void optimisticRepositoryFailsToStart() {
        ReplicatedHazelcastAggregationRepository repository
                = new ReplicatedHazelcastAggregationRepository("replicatedOptimisticStart", true, getFirstInstance());
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, repository::start);
            assertEquals(ReplicatedHazelcastAggregationRepository.OPTIMISTIC_NOT_SUPPORTED, e.getMessage());
        } finally {
            repository.stop();
        }
    }

    @Test
    void optimisticRepositoryWithoutInstanceFailsToStart() {
        ReplicatedHazelcastAggregationRepository repository
                = new ReplicatedHazelcastAggregationRepository("replicatedOptimisticLocal", true);
        try {
            assertThrows(IllegalArgumentException.class, repository::start);
            // the repository has no Hazelcast instance of its own left running
            assertNull(repository.getHazelcastInstance());
        } finally {
            repository.stop();
        }
    }

    @Test
    void routeWithOptimisticRepositoryFailsToStart() throws Exception {
        ReplicatedHazelcastAggregationRepository repository
                = new ReplicatedHazelcastAggregationRepository("replicatedOptimisticRoute", true, getFirstInstance());
        CamelContext camel = new DefaultCamelContext();
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in")
                        .aggregate(header("id"), new SumOfIntsAggregationStrategy())
                        .aggregationRepository(repository)
                        .optimisticLocking()
                        .completionSize(2)
                        .to("mock:result");
            }
        });
        try {
            Exception e = assertThrows(Exception.class, camel::start);
            assertTrue(hasCause(e, ReplicatedHazelcastAggregationRepository.OPTIMISTIC_NOT_SUPPORTED),
                    "expected the cause to say optimistic locking is not supported, but was: " + e);
        } finally {
            camel.stop();
        }
    }

    @Test
    void optimisticLockingRouteWithThreadSafeRepositoryFailsWithAMessage() throws Exception {
        ReplicatedHazelcastAggregationRepository repository
                = new ReplicatedHazelcastAggregationRepository("replicatedThreadSafeRoute", false, getFirstInstance());
        CamelContext camel = new DefaultCamelContext();
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in")
                        .aggregate(header("id"), new SumOfIntsAggregationStrategy())
                        .aggregationRepository(repository)
                        .optimisticLocking()
                        .completionSize(2)
                        .to("mock:result");
            }
        });
        camel.start();
        try {
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> camel.createProducerTemplate().sendBodyAndHeader("direct:in", 1, "id", "g"));
            UnsupportedOperationException cause = assertInstanceOf(UnsupportedOperationException.class, e.getCause());
            assertEquals(ReplicatedHazelcastAggregationRepository.OPTIMISTIC_LOCKING_NOT_SUPPORTED, cause.getMessage());
            assertEquals(0, camel.getEndpoint("mock:result", MockEndpoint.class).getReceivedCounter());
        } finally {
            camel.stop();
        }
    }

    @Test
    void threadSafeRepositoryWithOneAggregatorLosesNoMessage() throws Exception {
        int threads = 4;
        int perThread = 50;
        ReplicatedHazelcastAggregationRepository repository
                = new ReplicatedHazelcastAggregationRepository("replicatedThreadSafeOneAggregator", getFirstInstance());
        CamelContext camel = new DefaultCamelContext();
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in")
                        .aggregate(header("id"), new SumOfIntsAggregationStrategy())
                        .aggregationRepository(repository)
                        .completionSize(threads * perThread)
                        .to("mock:result");
            }
        });
        camel.start();
        try {
            MockEndpoint mock = camel.getEndpoint("mock:result", MockEndpoint.class);
            // exactly one aggregate, with every message of every thread
            mock.expectedBodiesReceived(threads * perThread);
            mock.setAssertPeriod(500);

            CyclicBarrier barrier = new CyclicBarrier(threads);
            AtomicReference<Exception> failure = new AtomicReference<>();
            List<Thread> senders = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Thread sender = new Thread(() -> send(camel, barrier, perThread, failure));
                senders.add(sender);
                sender.start();
            }
            for (Thread sender : senders) {
                sender.join(TimeUnit.SECONDS.toMillis(30));
                assertFalse(sender.isAlive());
            }
            assertNull(failure.get());

            mock.assertIsSatisfied();
            assertTrue(repository.getKeys().isEmpty());
        } finally {
            camel.stop();
        }
    }

    private static void send(CamelContext camel, CyclicBarrier barrier, int count, AtomicReference<Exception> failure) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            for (int i = 0; i < count; i++) {
                camel.createProducerTemplate().sendBodyAndHeader("direct:in", 1, "id", "g");
            }
        } catch (Exception e) {
            failure.compareAndSet(null, e);
        }
    }

    private static boolean hasCause(Throwable t, String message) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IllegalArgumentException && message.equals(c.getMessage())) {
                return true;
            }
        }
        return false;
    }
}
