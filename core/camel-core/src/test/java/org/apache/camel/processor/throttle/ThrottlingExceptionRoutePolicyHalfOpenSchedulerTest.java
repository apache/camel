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
package org.apache.camel.processor.throttle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half open check of {@link ThrottlingExceptionRoutePolicy} runs on one thread of a scheduler that the policy
 * creates through the ExecutorServiceManager, and that thread ends when the last route of the policy is removed.
 */
class ThrottlingExceptionRoutePolicyHalfOpenSchedulerTest extends ContextTestSupport {

    private final LatchPolicy policy = new LatchPolicy(100, 2);
    private final LatchPolicy removePolicy = new LatchPolicy(60000, 1);
    private final LatchPolicy sharedPolicy = new LatchPolicy(1000, 1);

    @Test
    void testHalfOpenChecksRunOnOneThreadOfThePolicy() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("checked").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:checked", "fail"));
        assertEquals("opened", policy.getStateAsString());
        await().atMost(10, TimeUnit.SECONDS).until(() -> policy.checkThreads.size() == 1);
        assertEquals("half opened", policy.getStateAsString());
        assertFalse(consumer.isSuspended());

        // a failure in the half open state opens the circuit again, and the next check runs on the same thread
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:checked", "fail"));
        assertTrue(policy.halfOpened.await(10, TimeUnit.SECONDS));
        assertEquals("half opened", policy.getStateAsString());

        Thread thread = policy.checkThreads.get(0);
        assertSame(thread, policy.checkThreads.get(1));
        assertTrue(isSchedulerThread(thread), thread.getName());
    }

    @Test
    void testRemovingTheRouteEndsTheThreadOfTheHalfOpenCheck() throws Exception {
        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:removed", "fail"));
        assertEquals("opened", removePolicy.getStateAsString());
        assertEquals(1, schedulerThreads().size(), "the half open check is scheduled");

        context.getRouteController().stopRoute("removed");
        assertTrue(context.removeRoute("removed"));

        await().atMost(10, TimeUnit.SECONDS).until(() -> schedulerThreads().isEmpty());
        assertEquals(1, removePolicy.halfOpened.getCount(), "the check scheduled before the removal must not run");
    }

    @Test
    void testRemovingOneOfTwoRoutesOfAPolicyKeepsItsHalfOpenCheck() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("shared2").getConsumer();

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:shared2", "fail"));
        assertEquals("opened", sharedPolicy.getStateAsString());
        assertTrue(consumer.isSuspended());

        context.getRouteController().stopRoute("shared1");
        assertTrue(context.removeRoute("shared1"));
        assertEquals(1, sharedPolicy.halfOpened.getCount(), "the check is still pending when the other route is removed");

        // the route that is left still gets its half open check
        assertTrue(sharedPolicy.halfOpened.await(10, TimeUnit.SECONDS));
        assertFalse(consumer.isSuspended());

        context.getRouteController().stopRoute("shared2");
        assertTrue(context.removeRoute("shared2"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> schedulerThreads().isEmpty());
    }

    private boolean isSchedulerThread(Thread thread) {
        // the thread name pattern of the ExecutorServiceManager, with the name of this CamelContext
        String name = thread.getName();
        return name.startsWith("Camel (" + context.getName() + ") thread #")
                && name.endsWith(" - ThrottlingExceptionRoutePolicy");
    }

    private List<Thread> schedulerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).filter(this::isSchedulerThread)
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:checked").routeId("checked").routePolicy(policy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                from("direct:removed").routeId("removed").routePolicy(removePolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));

                from("direct:shared1").routeId("shared1").routePolicy(sharedPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));
                from("direct:shared2").routeId("shared2").routePolicy(sharedPolicy)
                        .filter(body().isEqualTo("fail")).throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    /**
     * The policy, with the threads of its half open checks and a latch counted down when the circuit is half opened.
     */
    private static final class LatchPolicy extends ThrottlingExceptionRoutePolicy {
        private final List<Thread> checkThreads = new CopyOnWriteArrayList<>();
        private final CountDownLatch halfOpened;

        LatchPolicy(long halfOpenAfter, int halfOpenings) {
            super(1, 60000, halfOpenAfter, List.of());
            this.halfOpened = new CountDownLatch(halfOpenings);
        }

        @Override
        protected void halfOpenCircuit(Route route) {
            super.halfOpenCircuit(route);
            checkThreads.add(Thread.currentThread());
            halfOpened.countDown();
        }
    }
}
