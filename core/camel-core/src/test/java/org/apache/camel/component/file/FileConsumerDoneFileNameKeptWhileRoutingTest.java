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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A done file with a fixed name that a poll kept, as it left files for the next poll, must stay while a file of that
 * poll is still routing on another thread: when the consumer is stopped meanwhile, and when the next poll does not list
 * the directory of that file because the file is still in progress.
 */
class FileConsumerDoneFileNameKeptWhileRoutingTest extends ContextTestSupport {

    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicReference<String> first = new AtomicReference<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void stopWhileTheLastFileOfThePollRoutesKeepsTheDoneFile() throws Exception {
        Files.writeString(testFile("a.txt"), "A");
        Files.writeString(testFile("b.txt"), "B");
        Files.writeString(testFile("ready"), "");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?doneFileName=ready&maxMessagesPerPoll=1&initialDelay=0&delay=600000"))
                        .routeId("files").to("seda:work");
                // the first file waits on the seda route until the test releases it
                from("seda:work").routeId("work").process(e -> {
                    String name = e.getMessage().getHeader(Exchange.FILE_NAME_ONLY, String.class);
                    processed.add(name);
                    if (first.compareAndSet(null, name)) {
                        assertTrue(release.await(20, TimeUnit.SECONDS));
                    }
                });
            }
        });
        context.start();

        // the first poll takes one file, and keeps the done file for the other
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> first.get() != null);
        String taken = first.get();
        String left = "a.txt".equals(taken) ? "b.txt" : "a.txt";

        // the file route has no exchange in flight (the file was handed over to seda), so it stops at once
        context.getRouteController().stopRoute("files", 10, TimeUnit.SECONDS);
        release.countDown();
        // the file is moved once its done file was handled
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> !Files.exists(testFile(taken)));
        assertTrue(Files.exists(testFile("ready")),
                "The last file of the poll must not delete the done file kept for " + left + " after the consumer stopped");

        context.getRouteController().startRoute("files");
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(List.of(taken, left), processed);
            assertTrue(!Files.exists(testFile(left)) && !Files.exists(testFile("ready")),
                    "The file left and then the done file must be consumed after the restart");
        });
    }

    @Test
    void doneFileOfAFileStillRoutingIsNotGoneForTheNextPoll() throws Exception {
        Files.createDirectories(testDirectory("d1"));
        Files.createDirectories(testDirectory("d2"));
        Files.writeString(testFile("d1/a.txt"), "A");
        Files.writeString(testFile("d1/ready"), "");
        Files.writeString(testFile("d2/b.txt"), "B");
        Files.writeString(testFile("d2/ready"), "");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?doneFileName=ready&recursive=true&maxMessagesPerPoll=1&initialDelay=0&delay=10"))
                        .routeId("files")
                        .process(new FailFirstFileLater());
            }
        });
        context.start();

        // the first poll takes one file, and keeps both done files as it leaves the other; that file routes on another
        // thread, so the next poll lists only the other directory, and consumes its file
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> processed.size() == 2);
        String routing = first.get();
        String routingDir = routing.substring(0, 2);
        String other = processed.get(1);
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> !Files.exists(testFile(other)));
        assertTrue(Files.exists(testFile(routingDir + "/ready")),
                "The done file of " + routing + ", which is still routing, must not be deleted as gone");

        // the file fails, keeps its done file, and is consumed again
        release.countDown();
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(2, processed.stream().filter(routing::equals).count());
            assertTrue(!Files.exists(testFile(routing)) && !Files.exists(testFile(routingDir + "/ready")),
                    "The failed file and then its done file must be consumed");
        });
    }

    /**
     * The first file continues on another thread, and fails once the test releases it; the others complete at once.
     */
    private class FailFirstFileLater extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            String name = exchange.getMessage().getHeader(Exchange.FILE_NAME, String.class).replace('\\', '/');
            processed.add(name);
            if (!first.compareAndSet(null, name)) {
                callback.done(true);
                return true;
            }
            Thread thread = new Thread(() -> {
                try {
                    if (release.await(20, TimeUnit.SECONDS)) {
                        exchange.setException(new IllegalStateException("Forced failure of " + name));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exchange.setException(e);
                }
                callback.done(false);
            });
            thread.start();
            return false;
        }
    }
}
