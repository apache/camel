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
package org.apache.camel.component.file.remote;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A disconnect from an FTP server that stopped answering on an open connection (a hung server, or a connection lost
 * without being closed) does not wait up to soTimeout for the reply to QUIT: a stop with nothing in flight returns
 * within a few seconds, and after a failed poll the forced disconnect does not send QUIT.
 */
public class FtpDisconnectFromSilentServerTest extends CamelTestSupport {

    private static final int SO_TIMEOUT = 15000;
    // nothing is in flight: the stop should take the shutdown timeout at most, with a margin, not soTimeout
    private static final Duration STOP_LIMIT = Duration.ofSeconds(10);

    private FakeFtpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeFtpServer();
        context.getShutdownStrategy().setTimeout(2);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    private String ftpUri(String directory, int soTimeout, String options) {
        return "ftp://camel@127.0.0.1:" + server.port() + "/" + directory + "?password=secret&passiveMode=true&soTimeout="
               + soTimeout + "&" + options;
    }

    @Test
    void stopIsNotHeldByAnIdleConsumerWhoseServerStoppedAnswering() throws Exception {
        server.addFile("/inbox/a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // one poll at start, then idle: nothing is in flight when the server stops answering
                from(ftpUri("inbox", SO_TIMEOUT, "noop=true&initialDelay=0&delay=600000"))
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class)));
            }
        });
        await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() == 1);

        server.silenceOpenConnections();
        Duration took = timedStop();

        assertTrue(took.compareTo(STOP_LIMIT) < 0,
                "CamelContext.stop() took " + took.toMillis() + " ms with soTimeout " + SO_TIMEOUT + " ms");
        // the consumer still says goodbye, it does not wait long for the reply
        assertEquals(List.of("QUIT"), server.commandsOnSilentConnections());
    }

    @Test
    void stopIsNotHeldByAnIdleProducerWhoseServerStoppedAnswering() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:upload").to(ftpUri("outbox", SO_TIMEOUT, "disconnect=false"));
            }
        });
        template.sendBodyAndHeader("direct:upload", "hello", Exchange.FILE_NAME, "a.txt");
        assertTrue(server.files().containsKey("/outbox/a.txt"));

        // the producer keeps its connection (disconnect=false)
        server.silenceOpenConnections();
        Duration took = timedStop();

        assertTrue(took.compareTo(STOP_LIMIT) < 0,
                "CamelContext.stop() took " + took.toMillis() + " ms with soTimeout " + SO_TIMEOUT + " ms");
        assertEquals(List.of("QUIT"), server.commandsOnSilentConnections());
    }

    @Test
    void failedPollDoesNotSendQuitOnTheConnectionThatStoppedAnswering() throws Exception {
        server.addFile("/inbox/a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        // the LIST of the first poll gets no reply: the poll fails after soTimeout, and the poll strategy forces a
        // disconnect so the next poll connects again
        server.stallOn(command -> command.startsWith("LIST"), FakeFtpServer.Stall.BEFORE_REPLY);
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(ftpUri("inbox", 1000, "noop=true&initialDelay=0&delay=100"))
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class)));
            }
        });

        await().atMost(20, TimeUnit.SECONDS).until(() -> received.size() == 1);

        assertEquals(2, server.connectionsOpened());
        // the forced disconnect closed the connection without sending QUIT and waiting soTimeout for the reply
        assertEquals(List.of(), server.commandsOnSilentConnections());
        await().atMost(5, TimeUnit.SECONDS).until(() -> server.silentConnectionsOpen() == 0);
    }

    private Duration timedStop() {
        long start = System.nanoTime();
        context.stop();
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
