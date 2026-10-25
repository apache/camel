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
package org.apache.camel.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StartupListener;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A component that is created on first use, after CamelContext has started or while it starts, is started by the thread
 * that created it. Another thread that gets the same component meanwhile must not create endpoints from it before it
 * has started.
 */
class ComponentStartedBeforeUseTest extends ContextTestSupport {

    private final CountDownLatch starting = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch created = new CountDownLatch(2);
    private final SlowComponent component = new SlowComponent();

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        release.countDown();
        super.tearDown();
    }

    @Test
    void testOtherThreadWaitsForTheComponentToStart() throws Exception {
        context.getRegistry().bind("slow", component);
        resolveFromTwoThreads(context);
    }

    @Test
    void testOtherThreadWaitsForTheComponentToStartWhileCamelContextStarts() throws Exception {
        // the consumers of the routes that have started can send while CamelContext is still starting
        CamelContext starting = new DefaultCamelContext();
        starting.getRegistry().bind("slow", component);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        starting.addStartupListener(new StartupListener() {
            @Override
            public void onCamelContextStarting(CamelContext camelContext, boolean alreadyStarted) {
                try {
                    assertTrue(camelContext.isStarting());
                    resolveFromTwoThreads(camelContext);
                } catch (Throwable e) {
                    failure.set(e);
                }
            }

            @Override
            public void onCamelContextStarted(CamelContext camelContext, boolean alreadyStarted) {
                // noop
            }
        });
        try {
            starting.start();
            assertNull(failure.get(), () -> String.valueOf(failure.get()));
        } finally {
            starting.stop();
        }
    }

    @Test
    void testStoppedComponentIsNotStartedAgain() {
        // a control (as on main): only a component that has not started yet is started, not one that has been stopped
        // (such as by removeComponent while another thread still uses it)
        release.countDown();
        context.getRegistry().bind("slow", component);
        assertNotNull(context.getEndpoint("slow:a"));
        assertTrue(component.isStarted());

        component.stop();
        assertSame(component, context.getComponent("slow"));
        assertTrue(component.isStopped());
    }

    private void resolveFromTwoThreads(CamelContext camelContext) throws Exception {
        AtomicReference<Throwable> first = new AtomicReference<>();
        Thread creator = new Thread(() -> getEndpoint(camelContext, "slow:a", first), "creator");
        creator.start();
        // the creator thread has created the component and is starting it
        assertTrue(starting.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> second = new AtomicReference<>();
        Thread other = new Thread(() -> getEndpoint(camelContext, "slow:b", second), "other");
        other.start();
        // the other thread either created its endpoint already, or waits for the component to be started
        await().atMost(5, TimeUnit.SECONDS).until(() -> created.getCount() == 1 || isWaiting(other));

        release.countDown();
        creator.join(5000);
        other.join(5000);
        assertFalse(creator.isAlive());
        assertFalse(other.isAlive());

        assertNull(first.get(), () -> String.valueOf(first.get()));
        assertNull(second.get(), () -> String.valueOf(second.get()));
        assertEquals(ServiceStatus.Started, component.statusAtCreate.get("a"));
        assertEquals(ServiceStatus.Started, component.statusAtCreate.get("b"));
    }

    private static boolean isWaiting(Thread thread) {
        Thread.State state = thread.getState();
        return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING || state == Thread.State.BLOCKED;
    }

    private static void getEndpoint(CamelContext camelContext, String uri, AtomicReference<Throwable> failure) {
        try {
            assertNotNull(camelContext.getEndpoint(uri));
        } catch (Throwable e) {
            failure.set(e);
        }
    }

    /**
     * A component that creates the client its endpoints use when it starts (as for example vertx-http creates its Vertx
     * instance).
     */
    private final class SlowComponent extends DefaultComponent {
        private final Map<String, ServiceStatus> statusAtCreate = new ConcurrentHashMap<>();
        private volatile Object client;

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            starting.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            client = new Object();
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            statusAtCreate.put(remaining, getStatus());
            created.countDown();
            // the endpoint needs the client of the component
            String name = remaining + "@" + client.hashCode();
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException(name);
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    throw new UnsupportedOperationException(name);
                }
            };
        }
    }
}
