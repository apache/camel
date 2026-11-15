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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the FTP server stops answering on the connection of the consumer in the middle of a batch, only the file being
 * transferred fails: the consumer closes the connection, which cannot be used any more, and leaves the rest of the
 * batch to the next poll, which connects again. Before, every remaining file of the batch sent a command on the dead
 * connection and waited soTimeout for its reply.
 */
public class FtpConsumerBatchAfterReadTimeoutTest extends CamelTestSupport {

    private static final int FILES = 10;

    private final RecordingExceptionHandler failures = new RecordingExceptionHandler();
    private FakeFtpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeFtpServer();
        context.getRegistry().bind("failures", failures);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    @ParameterizedTest
    @EnumSource(FakeFtpServer.Stall.class)
    void readTimeoutFailsOnlyTheFileBeingTransferred(FakeFtpServer.Stall stall) throws Exception {
        for (int i = 1; i <= FILES; i++) {
            server.addFile(String.format("/inbox/f-%02d.txt", i), ("content of file " + i).getBytes(StandardCharsets.UTF_8));
        }
        // the connection stops answering at the 3rd file: before the reply to RETR (the client times out on the
        // control connection), or in the middle of the transfer (the client times out on the data connection)
        server.stallOn(command -> command.equals("RETR f-03.txt"), stall);

        Map<String, String> delivered = new ConcurrentHashMap<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&noop=true&sortBy=file:name&initialDelay=0&delay=100&soTimeout=1000&timeout=1000"
                     + "&exceptionHandler=#failures")
                        .process(e -> delivered.put(e.getMessage().getHeader(Exchange.FILE_NAME, String.class),
                                e.getMessage().getBody(String.class)));
            }
        });

        await().atMost(30, TimeUnit.SECONDS).until(() -> delivered.size() == FILES);

        for (int i = 1; i <= FILES; i++) {
            assertEquals("content of file " + i, delivered.get(String.format("f-%02d.txt", i)));
        }
        assertEquals(List.of("f-03.txt"), failures.files, "the files that failed");
        // no file of the batch waited soTimeout for a reply on the connection that stopped answering
        assertEquals(List.of(), server.commandsOnSilentConnections(), "the commands sent after the stall");
        await().atMost(5, TimeUnit.SECONDS).until(() -> server.silentConnectionsOpen() == 0);
    }

    @Test
    void connectionThatStoppedAnsweringIsClosedBeforeConnectingAgain() throws Exception {
        server.addFile("/inbox/a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        List<String> received = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&noop=true&initialDelay=0&delay=1000&soTimeout=1000")
                        .process(e -> received.add(e.getMessage().getHeader(Exchange.FILE_NAME, String.class)));
            }
        });
        await().atMost(10, TimeUnit.SECONDS).until(() -> received.size() == 1);

        // the connection stops answering between two polls: the NOOP of the next poll times out, and the consumer
        // connects again
        server.silenceOpenConnections();
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.connectionsOpened() == 2);

        assertTrue(server.commandsOnSilentConnections().contains("NOOP"));
        // the connection that stopped answering is closed, not left open behind the new one
        await().atMost(5, TimeUnit.SECONDS).until(() -> server.silentConnectionsOpen() == 0);
    }

    /** Records the name of the files whose processing failed. */
    static final class RecordingExceptionHandler implements ExceptionHandler {

        final List<String> files = new CopyOnWriteArrayList<>();

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            if (exchange != null) {
                files.add(exchange.getMessage().getHeader(Exchange.FILE_NAME_ONLY, String.class));
            }
        }
    }
}
