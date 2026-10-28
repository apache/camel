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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.milo.NodeIds.nodeValue;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the executor that a client connection handles the outcome of its requests on.
 */
public class MiloClientExecutorTest extends AbstractMiloServerTest {

    private static final String MILO_CLIENT_ITEM
            = "milo-client:opc.tcp://foo:bar@localhost:@@port@@?node="
              + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1") + "&overrideHost=true";

    // no server listens on this port
    @RegisterExtension
    AvailablePortFinder.Port unreachablePort = AvailablePortFinder.find();

    @EndpointInject("mock:item")
    protected MockEndpoint item;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("milo-server:myitem1").to("mock:item");
                from("direct:write").routeId("write").to(resolve(MILO_CLIENT_ITEM));
                from("milo-client:opc.tcp://localhost:" + unreachablePort.getPort() + "?node="
                     + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1") + "&overrideHost=true")
                        .routeId("unreachable").autoStartup(false).to("mock:unreachable");
            }
        };
    }

    /**
     * An awaited write completes while every thread of the shared scheduled executor of Milo is busy: neither the
     * request nor the handling of its outcome needs a thread of that executor, which has a single thread on a machine
     * with one processor.
     */
    @Test
    void awaitedWriteCompletesWhileTheSharedScheduledExecutorOfMiloIsBusy() throws Exception {
        item.expectedMessageCount(2);
        testBody(item.message(0), assertGoodValue("Foo1"));
        testBody(item.message(1), assertGoodValue("Foo2"));

        // connects
        write("Foo1");

        final ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) Stack.sharedScheduledExecutor();
        final CountDownLatch busy = new CountDownLatch(scheduler.getCorePoolSize());
        final CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < scheduler.getCorePoolSize(); i++) {
            scheduler.execute(() -> {
                busy.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        try {
            assertTrue(busy.await(30, TimeUnit.SECONDS), "All the threads of the executor should be busy");

            final CompletableFuture<Object> written = template.asyncRequestBodyAndHeader(
                    "direct:write", new Variant("Foo2"), MiloConstants.HEADER_AWAIT, true);
            written.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * The pool that handles the outcome of the requests of a connection is shut down when the last endpoint releases
     * the connection.
     */
    @Test
    void executorIsShutDownWithTheConnection() throws Exception {
        write("Foo1");
        assertFalse(executorThreads().isEmpty(), "The write should have been handled on the executor");

        // the route is the only user of the connection
        context.getRouteController().stopRoute("write");

        await().atMost(30, TimeUnit.SECONDS).until(() -> executorThreads().isEmpty());
    }

    /**
     * A connection that cannot connect waits for its next reconnect on a thread of its own. The thread ends when the
     * last endpoint releases the connection, without waiting until that reconnect is due.
     */
    @Test
    void reconnectThreadEndsWithTheConnection() throws Exception {
        context.getRouteController().startRoute("unreachable");
        await().atMost(30, TimeUnit.SECONDS).until(() -> !threads(" - MiloClientReconnect").isEmpty());

        // the route is the only user of the connection
        context.getRouteController().stopRoute("unreachable");

        // the reconnect is due 10 seconds after the failed connect
        await().atMost(5, TimeUnit.SECONDS).until(() -> threads(" - MiloClientReconnect").isEmpty());
    }

    private void write(final String value) {
        template.sendBodyAndHeader("direct:write", new Variant(value), MiloConstants.HEADER_AWAIT, true);
    }

    private List<Thread> executorThreads() {
        return threads(" - MiloClient");
    }

    private List<Thread> threads(final String suffix) {
        final String prefix = "Camel (" + context.getName() + ") thread #";
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith(prefix) && thread.getName().endsWith(suffix))
                .toList();
    }
}
