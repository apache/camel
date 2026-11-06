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

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The last file of a poll that left files completes on another thread while the next poll runs. The next poll lists the
 * directory completely and leaves a file too, but it keeps the done file again only after it sorted its files. The last
 * file of the first poll completes in between, and must not delete the done file, or the file left by the next poll
 * waits for a new done file.
 */
public class FileConsumerDoneFileNameAsyncLastFileTest extends ContextTestSupport {

    private final AtomicInteger polls = new AtomicInteger();
    private final AtomicBoolean secondPollSorting = new AtomicBoolean();
    private final CountDownLatch bStarted = new CountDownLatch(1);
    private final CountDownLatch releaseB = new CountDownLatch(1);
    private volatile NotifyBuilder bDone;
    private volatile Boolean doneFileAfterB;

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("countPolls", new DefaultPollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                polls.incrementAndGet();
                return true;
            }
        });
        // sorts by name; the sorter is called after the listing and before the done file is kept for the files cut by
        // maxMessagesPerPoll, so in the second poll it lets b.txt, the last file of the first poll, complete there
        Comparator<GenericFile<File>> sorter = (f1, f2) -> {
            if (polls.get() == 2 && secondPollSorting.compareAndSet(false, true)) {
                letBComplete();
            }
            return f1.getFileName().compareTo(f2.getFileName());
        };
        registry.bind("byNameLettingBComplete", sorter);
        return registry;
    }

    private void letBComplete() {
        try {
            if (bStarted.await(20, TimeUnit.SECONDS)) {
                releaseB.countDown();
                if (bDone.matches(20, TimeUnit.SECONDS)) {
                    doneFileAfterB = Files.exists(testFile("async/ready"));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void testLastFileOfPollCompletingWhileNextPollDecidesKeepsDoneFile() throws Exception {
        Files.createDirectories(testDirectory("async"));
        for (String name : List.of("a", "b", "c", "d", "e")) {
            Files.writeString(testFile("async/" + name + ".txt"), name.toUpperCase());
        }
        Files.writeString(testFile("async/ready"), "");
        bDone = new NotifyBuilder(context).whenAnyDoneMatches(header(Exchange.FILE_NAME_ONLY).isEqualTo("b.txt")).create();
        getMockEndpoint("mock:async").expectedBodiesReceivedInAnyOrder("A", "B", "C", "D", "E");

        // the first poll takes a.txt and b.txt and leaves c.txt, d.txt and e.txt; the second takes c.txt and d.txt and
        // leaves e.txt, so the done file must stay until the third poll
        context.getRouteController().startRoute("async");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> doneFileAfterB != null);
        assertEquals(Boolean.TRUE, doneFileAfterB,
                "The done file must not be deleted by b.txt, as the second poll leaves e.txt");
        assertMockEndpointsSatisfied();
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertFalse(Files.exists(testFile("async/ready")), "The done file must be deleted after the last file"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("async?doneFileName=ready&maxMessagesPerPoll=2&eagerMaxMessagesPerPoll=false"
                             + "&sorter=#byNameLettingBComplete&pollStrategy=#countPolls&initialDelay=0&delay=10"))
                        .routeId("async").autoStartup(false)
                        .threads()
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("B".equals(e.getMessage().getBody())) {
                                bStarted.countDown();
                                releaseB.await(20, TimeUnit.SECONDS);
                            }
                        })
                        .to("mock:async");
            }
        };
    }
}
