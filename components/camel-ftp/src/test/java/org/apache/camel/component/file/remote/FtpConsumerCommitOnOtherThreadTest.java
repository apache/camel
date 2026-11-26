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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the route hands the exchange over to another thread (here a seda consumer), the delete of a consumed file runs
 * on that thread, while the ftp consumer goes on with the next file of the poll, or the next poll, on the same
 * connection. The commands of the two must not mix on the one control connection: the delete waits until the consumer
 * is done with the download, or with the walk through the directories.
 */
class FtpConsumerCommitOnOtherThreadTest extends CamelTestSupport {

    private final RecordingFtpClient client = new RecordingFtpClient();
    private FakeFtpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeFtpServer();
        context.getRegistry().bind("client", client);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    @Test
    void deleteOnOtherThreadWaitsForTheDownload() throws Exception {
        for (int i = 1; i <= 3; i++) {
            server.addFile("/inbox/f-" + i + ".txt", ("content of file " + i).getBytes(StandardCharsets.UTF_8));
        }
        // the delete of the first file comes while the consumer downloads the second
        client.window = "RETR f-2.txt";
        MockEndpoint mock = getMockEndpoint("mock:work");
        mock.expectedBodiesReceived("content of file 1", "content of file 2", "content of file 3");

        addRoutes();

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.files().isEmpty());
        assertEquals(List.of(), client.commandsInWindow, "the commands another thread sent during the download");
    }

    @Test
    void deleteOnOtherThreadWaitsForTheWalkOfTheNextPoll() throws Exception {
        server.addFile("/inbox/f-1.txt", "content of file 1".getBytes(StandardCharsets.UTF_8));
        // the delete of the file comes while the next poll lists the directory it changed to (stepwise): the delete
        // changes to the directory of the file from there, and fails, so the file would be consumed again
        client.window = "LIST 2";
        MockEndpoint mock = getMockEndpoint("mock:work");
        mock.expectedBodiesReceived("content of file 1");
        // and not consumed again
        mock.setAssertPeriod(1000);

        addRoutes();

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.files().isEmpty());
        assertEquals(List.of(), client.commandsInWindow, "the commands another thread sent during the listing");
    }

    private void addRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ftp://camel@127.0.0.1:" + server.port() + "/inbox?password=secret&passiveMode=true"
                     + "&delete=true&sortBy=file:name&initialDelay=0&delay=100&ftpClient=#client")
                        .to("seda:work");

                // a file is done (and deleted) once the consumer is in the window
                from("seda:work")
                        .process(e -> client.windowStarted.await(5, TimeUnit.SECONDS))
                        .convertBodyTo(String.class).to("mock:work");
            }
        });
    }

    /**
     * Records the commands that another thread sends while the consumer is in a window: the download of a file, or the
     * listing of a poll. The consumer waits up to 2 seconds in the window for such a command, so the delete that the
     * seda consumer runs once the window has begun comes while the consumer is in the window, when nothing keeps it
     * out.
     */
    private static final class RecordingFtpClient extends FTPClient {

        private final List<String> commandsInWindow = new CopyOnWriteArrayList<>();
        private final CountDownLatch windowStarted = new CountDownLatch(1);
        private final CountDownLatch otherThreadCommand = new CountDownLatch(1);
        private final AtomicInteger listings = new AtomicInteger();
        private volatile String window;
        private volatile Thread inWindow;

        @Override
        public boolean retrieveFile(String remote, OutputStream local) throws IOException {
            if (("RETR " + remote).equals(window)) {
                return inWindow(() -> super.retrieveFile(remote, local));
            }
            return super.retrieveFile(remote, local);
        }

        @Override
        public FTPFile[] listFiles() throws IOException {
            if (("LIST " + listings.incrementAndGet()).equals(window)) {
                return inWindow(super::listFiles);
            }
            return super.listFiles();
        }

        private <T> T inWindow(IOCall<T> call) throws IOException {
            inWindow = Thread.currentThread();
            windowStarted.countDown();
            try {
                otherThreadCommand.await(2, TimeUnit.SECONDS);
                return call.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } finally {
                inWindow = null;
            }
        }

        @Override
        public int sendCommand(String command, String args) throws IOException {
            Thread t = inWindow;
            if (t != null && t != Thread.currentThread()) {
                commandsInWindow.add(args != null ? command + " " + args : command);
                otherThreadCommand.countDown();
            }
            return super.sendCommand(command, args);
        }
    }

    @FunctionalInterface
    private interface IOCall<T> {
        T call() throws IOException;
    }
}
