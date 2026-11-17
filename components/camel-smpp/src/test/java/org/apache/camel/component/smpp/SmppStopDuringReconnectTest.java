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
package org.apache.camel.component.smpp;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jsmpp.session.BindRequest;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPServerSessionListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;

/**
 * A consumer or producer that is stopped while it waits to reconnect to an SMSC that is down must not leave the thread
 * that reported the lost session waiting for the reconnect task for ever.
 */
class SmppStopDuringReconnectTest extends CamelTestSupport {

    @RegisterExtension
    static AvailablePortFinder.Port port = AvailablePortFinder.find();

    private final List<SMPPServerSession> serverSessions = new CopyOnWriteArrayList<>();
    private SMPPServerSessionListener smsc;
    private Thread acceptor;

    @BeforeEach
    void startSmsc() throws IOException {
        smsc = new SMPPServerSessionListener(port.getPort());
        acceptor = new Thread(this::acceptBinds, "smsc-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopSmsc() throws IOException {
        smsc.close();
        serverSessions.forEach(SMPPServerSession::close);
    }

    private void acceptBinds() {
        try {
            while (true) {
                SMPPServerSession session = smsc.accept();
                BindRequest bind = session.waitForBind(5000);
                bind.accept("smsc");
                serverSessions.add(session);
            }
        } catch (Exception e) {
            // the SMSC is closed
        }
    }

    @Test
    void stopOfConsumerReleasesTheThreadWaitingToReconnect() throws Exception {
        stopDuringReconnect(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri()).routeId("smpp").to("mock:received");
            }
        }, SmppConsumer.class);
    }

    @Test
    void stopOfProducerReleasesTheThreadWaitingToReconnect() throws Exception {
        stopDuringReconnect(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send").routeId("smpp").to(uri());
            }
        }, SmppProducer.class);
    }

    private static String uri() {
        // the stop comes while the reconnect waits for its next attempt (5 s by default)
        return "smpp://smppclient@localhost:" + port.getPort()
               + "?password=password&initialReconnectDelay=60000&reconnectDelay=60000";
    }

    private void stopDuringReconnect(RoutesBuilder route, Class<?> type) throws Exception {
        context.addRoutes(route);
        await().atMost(10, TimeUnit.SECONDS).until(() -> serverSessions.size() == 1);

        // the SMSC goes down: it closes the session and no longer accepts connections
        smsc.close();
        serverSessions.get(0).close();
        await().atMost(10, TimeUnit.SECONDS).until(() -> threadsWaitingToReconnect(type) == 1);

        context.getRouteController().stopRoute("smpp");

        await().atMost(10, TimeUnit.SECONDS).until(() -> threadsWaitingToReconnect(type) == 0);
    }

    private static long threadsWaitingToReconnect(Class<?> type) {
        return Thread.getAllStackTraces().values().stream()
                .filter(stack -> List.of(stack).stream()
                        .anyMatch(frame -> frame.getClassName().equals(type.getName())
                                && frame.getMethodName().equals("reconnect")))
                .count();
    }
}
