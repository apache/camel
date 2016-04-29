/**
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
package org.apache.camel.component.netty4;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test the effect of redelivery in association with netty component.
 */
public class NettyRedeliveryTest extends CamelTestSupport {

    /**
     * Body of sufficient size such that it doesn't fit into the TCP buffer and has to be read.
     */
    private static final byte[] LARGE_BUFFER_BODY = new byte[1000000];

    /**
     * Failure will occur with 2 redeliveries however is increasingly more likely the more it retries.
     */
    private static final int REDELIVERY_COUNT = 100;

    private ExecutorService listener = Executors.newSingleThreadExecutor();

    @EndpointInject(uri = "mock:exception")
    private MockEndpoint exception;

    @EndpointInject(uri = "mock:downstream")
    private MockEndpoint downstream;

    private Deque<Callable<?>> tasks = new LinkedBlockingDeque<Callable<?>>();
    private int port;
    private boolean alive = true;

    @Override
    protected void doPreSetup() throws Exception {
        // Create a server to attempt to connect to
        port = createServerSocket(0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                        .maximumRedeliveries(REDELIVERY_COUNT)
                        .retryAttemptedLogLevel(LoggingLevel.INFO)
                        .retriesExhaustedLogLevel(LoggingLevel.ERROR)
                        // lets have a little delay so we do async redelivery
                        .redeliveryDelay(10)
                        .to("mock:exception")
                        .handled(true);

                from("direct:start")
                        .routeId("start")
                        .to("netty4:tcp://localhost:" + port)
                        .to("log:downstream")
                        .to("mock:downstream");
            }
        };
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        alive = false;
        listener.shutdown();
    }

    @Test
    public void testExceptionHandler() throws Exception {
        /*
         * We should have 0 for this as it should never be successful however it is usual that this actually returns 1.
         *
         * This is because two or more threads run concurrently and will setException(null) which is checked during
         * redelivery to ascertain whether the delivery was successful, this leads to multiple downstream invocations being
         * possible.
         */
        downstream.setExpectedMessageCount(0);
        downstream.setAssertPeriod(1000);

        exception.setExpectedMessageCount(1);

        sendBody("direct:start", LARGE_BUFFER_BODY);

        exception.assertIsSatisfied();

        // given 100 retries usually yields somewhere around -95
        // assertEquals(0, context.getInflightRepository().size("start"));

        // Verify the number of tasks submitted - sometimes both callbacks add a task
        assertEquals(REDELIVERY_COUNT, tasks.size());

        // Verify the downstream completed messages - othertimes one callback gets treated as done
        downstream.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // Override the error handler executor service such that we can track the tasks created
        CamelContext context = new DefaultCamelContext(createRegistry()) {
            @Override
            public ScheduledExecutorService getErrorHandlerExecutorService() {
                return getScheduledExecutorService();
            }
        };
        return context;
    }

    private ScheduledExecutorService getScheduledExecutorService() {
        final ScheduledExecutorService delegate = Executors.newScheduledThreadPool(10);
        return newProxy(ScheduledExecutorService.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("submit".equals(method.getName()) || "schedule".equals(method.getName())) {
                    tasks.add((Callable<?>) args[0]);
                }
                return method.invoke(delegate, args);
            }
        });
    }

    private int createServerSocket(int port) throws IOException {
        final ServerSocket listen = new ServerSocket(port);
        listen.setSoTimeout(100);
        listener.execute(new Runnable() {

            private ExecutorService pool = Executors.newCachedThreadPool();

            @Override
            public void run() {
                try {
                    while (alive) {
                        try {
                            pool.execute(new ClosingClientRunnable(listen.accept()));
                        } catch (SocketTimeoutException ignored) {
                            // Allow the server socket to terminate in a timely fashion
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        listen.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        });
        return listen.getLocalPort();
    }

    private static <T> T newProxy(Class<T> interfaceType, InvocationHandler handler) {
        Object object = Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[]{interfaceType}, handler);
        return interfaceType.cast(object);
    }

    /**
     * Handler for client connection.
     */
    private class ClosingClientRunnable implements Runnable {
        private final Socket socket;

        ClosingClientRunnable(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(10);
                socket.close();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

}
