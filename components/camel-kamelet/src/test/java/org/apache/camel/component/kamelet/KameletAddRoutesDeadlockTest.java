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
package org.apache.camel.component.kamelet;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategySupport;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

/**
 * Adding a route that uses a Kamelet while the CamelContext runs, at the same time as an exchange creates the route of
 * another Kamelet (toD), must not deadlock.
 * <p/>
 * Adding the routes holds the lock of the model while the new route is created, and creating the route of its Kamelet
 * takes the lock of the Kamelet component. The toD creates the route of its Kamelet holding the lock of the Kamelet
 * component, and adding that route takes the lock of the model.
 */
class KameletAddRoutesDeadlockTest {

    private CamelContext context;
    private boolean hung;

    @AfterEach
    void stopContext() {
        // the threads of a deadlock never release the locks, so the context cannot be stopped
        if (context != null && !hung) {
            context.stop();
        }
    }

    @Test
    void addRoutesWhileToDCreatesTheRouteOfAKamelet() throws Exception {
        CountDownLatch addHoldsModelLock = new CountDownLatch(1);
        CountDownLatch toDHoldsKameletLock = new CountDownLatch(1);
        AtomicReference<Thread> toDThread = new AtomicReference<>();

        context = createContext();
        ModelCamelContext mcc = (ModelCamelContext) context;
        // runs while adding the route "added", holding the lock of the model: wait until the toD holds the lock of
        // the Kamelet component (or waits for a lock)
        mcc.addModelLifecycleStrategy(new ModelLifecycleStrategySupport() {
            @Override
            public void onAddRouteDefinition(RouteDefinition routeDefinition) {
                if ("added".equals(routeDefinition.getRouteId())) {
                    addHoldsModelLock.countDown();
                    awaitOrBlocked(toDHoldsKameletLock, toDThread);
                }
            }
        });
        // runs while the toD creates the route of Kamelet b, holding the lock of the Kamelet component
        mcc.addRouteTemplateDefinitionConverter("b", (in, parameters) -> {
            toDHoldsKameletLock.countDown();
            return RouteTemplateDefinition.Converter.DEFAULT_CONVERTER.apply(in, parameters);
        });
        context.start();

        FutureTask<Void> add = new FutureTask<>(() -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:added").routeId("added").to("kamelet:a");
                }
            });
            return null;
        });
        Thread addThread = new Thread(add, "add-routes");
        addThread.setDaemon(true);
        addThread.start();
        assertThat(addHoldsModelLock.await(10, TimeUnit.SECONDS)).as("the route is being added").isTrue();

        ProducerTemplate template = context.createProducerTemplate();
        FutureTask<Object> toD = new FutureTask<>(() -> template.requestBodyAndHeader("direct:dynamic", "Hello", "x", "1"));
        Thread thread = new Thread(toD, "toD-kamelet");
        thread.setDaemon(true);
        toDThread.set(thread);
        thread.start();

        try {
            add.get(20, TimeUnit.SECONDS);
            assertThat(toD.get(20, TimeUnit.SECONDS)).isEqualTo("b-1-Hello");
        } catch (TimeoutException e) {
            hung = true;
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            long[] ids = threads.findDeadlockedThreads();
            List<ThreadInfo> deadlocked = ids == null
                    ? List.of()
                    : Arrays.stream(threads.getThreadInfo(ids))
                            .filter(i -> i.getThreadId() == addThread.threadId() || i.getThreadId() == thread.threadId())
                            .toList();
            assertThat(deadlocked).as("deadlocked: %s", describe(deadlocked)).isEmpty();
            fail("add-routes or toD-kamelet did not complete");
        }

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("a-Hello");
        template.sendBody("direct:added", "Hello");
        assertThat(template.requestBodyAndHeader("direct:dynamic", "Hello", "x", "2")).isEqualTo("b-2-Hello");
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Control: the same two operations one after the other.
     */
    @Test
    void addRoutesAfterToDCreatedTheRouteOfAKamelet() throws Exception {
        context = createContext();
        context.start();

        ProducerTemplate template = context.createProducerTemplate();
        assertThat(template.requestBodyAndHeader("direct:dynamic", "Hello", "x", "1")).isEqualTo("b-1-Hello");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:added").routeId("added").to("kamelet:a");
            }
        });

        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("a-Hello");
        template.sendBody("direct:added", "Hello");
        MockEndpoint.assertIsSatisfied(context);
    }

    private static CamelContext createContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("a")
                        .from("kamelet:source")
                        .setBody().simple("a-${body}")
                        .to("mock:result");

                routeTemplate("b")
                        .templateParameter("x")
                        .from("kamelet:source")
                        .setBody().simple("b-{{x}}-${body}");

                from("direct:dynamic").routeId("dynamic")
                        .toD("kamelet:b?x=${header.x}");
            }
        });
        return context;
    }

    private static String describe(List<ThreadInfo> infos) {
        return infos.stream()
                .map(i -> i.getThreadName() + " waits for " + i.getLockName() + " held by " + i.getLockOwnerName())
                .collect(Collectors.joining("; "));
    }

    /**
     * Waits until the latch is counted down, or the thread waits for a {@link ReentrantLock} (as the toD does when it
     * takes the lock of the model first). Fails the adding of the route (and so the test) if neither happens.
     */
    private static void awaitOrBlocked(CountDownLatch latch, AtomicReference<Thread> thread) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> latch.getCount() == 0 || waitsForLock(thread.get()));
    }

    private static boolean waitsForLock(Thread thread) {
        Object blocker = thread != null ? LockSupport.getBlocker(thread) : null;
        return blocker != null && blocker.getClass().getName().startsWith(ReentrantLock.class.getName());
    }
}
