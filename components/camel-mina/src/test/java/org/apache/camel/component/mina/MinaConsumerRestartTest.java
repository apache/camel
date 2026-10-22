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
package org.apache.camel.component.mina;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoService;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A route stop/start creates a new MinaConsumer, and the stopped one is never shut down: its acceptor (selectors, I/O
 * threads) and worker pool must be released when it stops.
 */
class MinaConsumerRestartTest extends BaseMinaTest {

    private final int udpPort = getNextPort();
    private final int vmPort = getNextPort();

    @Test
    void routeRestartReleasesTheAcceptorOfTheStoppedTcpConsumer() throws Exception {
        for (IoAcceptor acceptor : restart("tcp", 3)) {
            assertReleased(acceptor);
        }
        // the consumer of the restarted route works
        sendAndExpect("mina:tcp://localhost:" + getPort() + "?sync=false&textline=true");
    }

    @Test
    void routeRestartReleasesTheAcceptorOfTheStoppedUdpConsumer() throws Exception {
        addUdpRoute();
        for (IoAcceptor acceptor : restart("udp", 3)) {
            assertReleased(acceptor);
        }
        sendAndExpect("mina:udp://localhost:" + udpPort + "?sync=false");
    }

    @Test
    void routeRestartReleasesTheAcceptorOfTheStoppedVmConsumer() throws Exception {
        for (IoAcceptor acceptor : restart("vm", 3)) {
            assertTrue(acceptor.isDisposed(), "the acceptor of a stopped consumer must be disposed");
        }
        sendAndExpect("mina:vm://localhost:" + vmPort + "?sync=false&textline=true");
    }

    @Test
    void theSameConsumerCanBeStartedAgain() throws Exception {
        // a consumer that is stopped and started again (not replaced) creates a new acceptor
        MinaConsumer consumer = (MinaConsumer) context.getRoute("tcp").getConsumer();
        IoAcceptor first = consumer.getAcceptor();

        consumer.stop();
        consumer.start();

        assertTrue(first.isDisposed());
        assertNotSame(first, consumer.getAcceptor());
        assertFalse(consumer.getAcceptor().isDisposed());
        sendAndExpect("mina:tcp://localhost:" + getPort() + "?sync=false&textline=true");
    }

    @Test
    void theSameClientModeConsumerCanBeStartedAgain() throws Exception {
        // a consumer in client mode (connected to the tcp route) that is stopped and started again creates a new
        // connector and connects again; before, the start failed as the stopped connector was disposed
        context.getRouteController().startRoute("client");
        MinaConsumer consumer = (MinaConsumer) context.getRoute("client").getConsumer();
        IoConnector first = consumer.getConnector();

        consumer.stop();
        consumer.start();

        assertReleased(first);
        assertNotSame(first, consumer.getConnector());
        assertFalse(consumer.getConnector().isDisposed());
        assertEquals(1, consumer.getConnector().getManagedSessionCount(), "the restarted consumer is connected");
    }

    @Test
    void onlyACamelContextStopWaitsForTheIoThreads() throws Exception {
        // the address is already unbound, so a route stop does not wait for the I/O threads of the acceptor (a datagram
        // acceptor can take seconds to end); a CamelContext stop waits for them, as doShutdown did before
        addUdpRoute();
        IoAcceptor routeStopped = watchAcceptor("udp");
        context.getRouteController().stopRoute("udp");
        verify(routeStopped).dispose(false);
        verify(routeStopped, never()).dispose(true);

        context.getRouteController().startRoute("udp");
        IoAcceptor contextStopped = watchAcceptor("udp");
        context.stop();
        verify(contextStopped, never()).dispose(false);
        // doShutdown disposes again, which does nothing then
        verify(contextStopped, atLeastOnce()).dispose(true);
    }

    private void addUdpRoute() throws Exception {
        // only in the udp tests: a datagram acceptor takes seconds to end, and the CamelContext stop waits for it
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("mina:udp://localhost:%d?sync=false", udpPort).routeId("udp").to("mock:result");
            }
        });
    }

    private IoAcceptor watchAcceptor(String routeId) {
        // the acceptor of the consumer, behind a mock that passes every call on to it
        MinaConsumer consumer = (MinaConsumer) context.getRoute(routeId).getConsumer();
        IoAcceptor acceptor = mock(IoAcceptor.class, delegatesTo(consumer.getAcceptor()));
        consumer.setAcceptor(acceptor);
        return acceptor;
    }

    private static void assertReleased(IoService service) {
        assertTrue(service.isDisposed(), "the acceptor of a stopped consumer must be disposed");
        ExecutorFilter workers = (ExecutorFilter) service.getFilterChain().get("threadPool");
        assertTrue(((ExecutorService) workers.getExecutor()).isShutdown(),
                "the worker pool of a stopped consumer must be shut down");
    }

    private List<IoAcceptor> restart(String routeId, int times) throws Exception {
        List<IoAcceptor> stopped = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            stopped.add(((MinaConsumer) context.getRoute(routeId).getConsumer()).getAcceptor());
            context.getRouteController().stopRoute(routeId);
            context.getRouteController().startRoute(routeId);
        }
        assertNotSame(stopped.get(times - 1), ((MinaConsumer) context.getRoute(routeId).getConsumer()).getAcceptor(),
                "a route restart creates a new consumer");
        return stopped;
    }

    private void sendAndExpect(String uri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        template.sendBody(uri, "hello");
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("mina:tcp://localhost:%d?sync=false&textline=true", getPort()).routeId("tcp").to("mock:result");
                fromF("mina:vm://localhost:%d?sync=false&textline=true", vmPort).routeId("vm").to("mock:result");
                fromF("mina:tcp://localhost:%d?sync=false&textline=true&clientMode=true", getPort()).routeId("client")
                        .autoStartup(false).to("mock:client");
            }
        };
    }
}
