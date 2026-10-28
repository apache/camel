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
package org.apache.camel.component.cxf.jaxws;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suspended CXF consumer (suspended route, graceful shutdown, route policies) must not process new requests, while
 * the requests in flight complete.
 */
class CxfConsumerSuspendTest extends CamelTestSupport {

    private static final String ADDRESS = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfConsumerSuspendTest/test";

    private final AtomicInteger processed = new AtomicInteger();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean block;
    // the bus of the test clients
    private Bus clientBus;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf://" + ADDRESS + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService")
                        .routeId("cxf")
                        .process(exchange -> {
                            if (processed.incrementAndGet() == 1 && block) {
                                entered.countDown();
                                release.await(20, TimeUnit.SECONDS);
                            }
                            String text = (String) exchange.getIn().getBody(List.class).get(0);
                            exchange.getMessage().setBody("echo " + text);
                        });
            }
        };
    }

    @Test
    void testSuspendedRouteRejectsRequests() throws Exception {
        HelloService client = createClient();
        assertEquals("echo a", client.echo("a"));

        context.getRouteController().suspendRoute("cxf");
        Exception e = assertThrows(Exception.class, () -> client.echo("b"), "A suspended route must not accept a request");
        assertServiceUnavailable(e);
        assertEquals(1, processed.get(), "A suspended route must not process a request");

        context.getRouteController().resumeRoute("cxf");
        assertEquals("echo c", client.echo("c"));
        assertEquals(2, processed.get());
    }

    @Test
    void testRejectedRequestIsNotLoggedAsWarning() throws Exception {
        HelloService client = createClient();
        assertEquals("echo a", client.echo("a"));
        context.getRouteController().suspendRoute("cxf");

        // CXF logs an unexpected fault at WARN level with its stack trace: a rejected request is expected while the
        // consumer is suspended (e.g. every request during a graceful shutdown under load)
        // (the client, on this thread, logs the 503 fault it receives)
        String clientThread = Thread.currentThread().getName();
        List<LogEvent> warnings = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("CxfConsumerSuspendTest", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.WARN) && event.getLoggerName().startsWith("org.apache.cxf")
                        && !clientThread.equals(event.getThreadName())) {
                    warnings.add(event.toImmutable());
                }
            }
        };
        appender.start();
        Logger cxfLogger = (Logger) LogManager.getLogger("org.apache.cxf");
        cxfLogger.addAppender(appender);
        try {
            Exception e = assertThrows(Exception.class, () -> client.echo("b"));
            assertServiceUnavailable(e);
        } finally {
            cxfLogger.removeAppender(appender);
            appender.stop();
        }
        assertTrue(warnings.isEmpty(), () -> "A rejected request must not be logged as a warning by the CXF server: "
                                             + warnings.stream()
                                                     .map(w -> w.getThreadName() + " " + w.getLoggerName() + ": "
                                                               + w.getMessage().getFormattedMessage())
                                                     .toList());
    }

    @Test
    void testGracefulStopCompletesInflightAndRejectsNewRequests() throws Exception {
        block = true;
        HelloService client = createClient();
        CompletableFuture<String> inflight = CompletableFuture.supplyAsync(() -> {
            try {
                return client.echo("a");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(entered.await(20, TimeUnit.SECONDS));

        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("cxf", 20, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ServiceSupport consumer = (ServiceSupport) context.getRoute("cxf").getConsumer();
        await().atMost(20, TimeUnit.SECONDS).until(consumer::isSuspendingOrSuspended);

        HelloService other = createClient();
        Exception e = assertThrows(Exception.class, () -> other.echo("b"),
                "A request received while stopping must be rejected");
        assertServiceUnavailable(e);
        assertEquals(1, processed.get(), "A request received while stopping must not be processed");

        release.countDown();
        assertEquals("echo a", inflight.get(20, TimeUnit.SECONDS));
        stop.get(30, TimeUnit.SECONDS);
    }

    private static void assertServiceUnavailable(Exception e) {
        HTTPException http = ObjectHelper.getException(HTTPException.class, e);
        assertNotNull(http, "Expected an HTTP 503 response, got: " + e);
        assertEquals(503, http.getResponseCode());
    }

    private HelloService createClient() {
        if (clientBus == null) {
            clientBus = BusFactory.newInstance().createBus();
        }
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ADDRESS);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(clientBus);
        return (HelloService) proxyFactory.create();
    }

    @AfterEach
    void shutdownClientBus() {
        if (clientBus != null) {
            clientBus.shutdown(true);
            clientBus = null;
        }
    }
}
