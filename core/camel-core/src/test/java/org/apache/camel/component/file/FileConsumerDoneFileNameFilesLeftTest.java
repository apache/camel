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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.strategy.MarkerFileExclusiveReadLockStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A done file with a fixed name must not be deleted while the poll left files of its directory for the next one (beyond
 * maxMessagesPerPoll, or not started), or they wait for a new done file. While it is kept, the next polls consume the
 * files of the directory as they do whenever the done file is there, including a file written meanwhile. A file that
 * fails is handled as before: the done file is deleted after the last file of the poll, unless that file failed.
 * <p/>
 * A test that expects a file not to be consumed checks it once the done file is gone: from then on the file cannot be
 * consumed without a new done file, and the routes are synchronous, so any file consumed before is already received.
 */
public class FileConsumerDoneFileNameFilesLeftTest extends ContextTestSupport {

    private final AtomicBoolean failB = new AtomicBoolean(true);
    private final AtomicBoolean failLastB = new AtomicBoolean(true);
    private final AtomicBoolean writeLate = new AtomicBoolean(true);
    private final AtomicBoolean lockB = new AtomicBoolean(true);
    private final AtomicBoolean produceLate = new AtomicBoolean(true);
    private final AtomicInteger badAttempts = new AtomicInteger();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        // the read lock of b.txt is not acquired the first time, so its processing is not started
        registry.bind("lockBOnce", new MarkerFileExclusiveReadLockStrategy() {
            @Override
            public boolean acquireExclusiveReadLock(
                    GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
                    throws Exception {
                if ("b.txt".equals(file.getFileNameOnly()) && lockB.getAndSet(false)) {
                    return false;
                }
                return super.acquireExclusiveReadLock(operations, file, exchange);
            }
        });
        return registry;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // a file component whose download of a.txt fails, after the preMove of the begin strategy
        context.addComponent("failingfile", new FileComponent() {
            @Override
            protected GenericFileEndpoint<File> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters)
                    throws Exception {
                FileEndpoint endpoint = new FileEndpoint(uri, this) {
                    @Override
                    protected FileConsumer newFileConsumer(Processor processor, GenericFileOperations<File> operations) {
                        return super.newFileConsumer(processor, new FileOperations(this) {
                            @Override
                            public boolean retrieveFile(String name, Exchange exchange, long size) {
                                if (name.endsWith("a.txt")) {
                                    throw new GenericFileOperationFailedException("Cannot download " + name);
                                }
                                return super.retrieveFile(name, exchange, size);
                            }
                        });
                    }
                };
                File file = new File(remaining);
                endpoint.setFile(file);
                GenericFileConfiguration config = new GenericFileConfiguration();
                config.setDirectory(file.getPath());
                endpoint.setConfiguration(config);
                return endpoint;
            }
        });
        return context;
    }

    @Test
    public void testFilesLeftByMaxMessagesPerPoll() throws Exception {
        writeFilesAndDoneFile("limit");
        getMockEndpoint("mock:limit").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("limit");

        assertMockEndpointsSatisfied();
        awaitDoneFileDeleted("limit/ready");
    }

    @Test
    public void testFilesLeftByMaxMessagesPerPollNotEager() throws Exception {
        writeFilesAndDoneFile("noteager");
        getMockEndpoint("mock:noteager").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("noteager");

        assertMockEndpointsSatisfied();
        awaitDoneFileDeleted("noteager/ready");
    }

    @Test
    public void testFileNotStartedIsConsumedByNextPoll() throws Exception {
        writeFilesAndDoneFile("notstarted");
        getMockEndpoint("mock:notstarted").expectedBodiesReceivedInAnyOrder("A", "B", "C");

        context.getRouteController().startRoute("notstarted");

        assertMockEndpointsSatisfied();
        awaitDoneFileDeleted("notstarted/ready");
    }

    @Test
    public void testDoneFileDeletedWhenExactlyMaxMessagesPerPollFiles() throws Exception {
        template.sendBodyAndHeader(fileUri("exact"), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri("exact"), "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri("exact"), "", Exchange.FILE_NAME, "ready");
        getMockEndpoint("mock:exact").expectedBodiesReceivedInAnyOrder("A", "B");

        context.getRouteController().startRoute("exact");

        assertMockEndpointsSatisfied();
        awaitDoneFileDeleted("exact/ready");
    }

    @Test
    public void testFileWrittenWhileFilesAreLeftIsConsumed() throws Exception {
        // x.txt is written while a.txt is routed: b.txt and c.txt are left for later polls, and the done file kept for
        // them is there, so x.txt is consumed as well (as when a poll finds a file written after the done file)
        writeFilesAndDoneFile("gate");
        MockEndpoint mock = getMockEndpoint("mock:gate");
        mock.expectedBodiesReceivedInAnyOrder("A", "B", "C", "X");

        context.getRouteController().startRoute("gate");

        mock.assertIsSatisfied();
        awaitDoneFileDeleted("gate/ready");
        assertFileNotExists(testFile("gate/x.txt"));
    }

    @Test
    public void testFileWrittenByCamelProducerWhileFilesAreLeftIsConsumed() throws Exception {
        // a Camel producer with doneFileName writes x.txt and rewrites the done file while a.txt is routed: x.txt must
        // be consumed, not stranded once the done file is deleted after the files left
        writeFilesAndDoneFile("producer");
        MockEndpoint mock = getMockEndpoint("mock:producer");
        mock.expectedBodiesReceivedInAnyOrder("A", "B", "C", "X");

        context.getRouteController().startRoute("producer");

        mock.assertIsSatisfied();
        awaitDoneFileDeleted("producer/ready");
        assertFileNotExists(testFile("producer/x.txt"));
    }

    @Test
    public void testConsumerTemplateReceivesEveryFileOfTheDoneFile() throws Exception {
        // the polling consumer takes one file per receive (maxMessagesPerPoll=1): the done file is kept until the last
        // file is received (before, the first receive deleted it and the other files waited for a new done file)
        writeFilesAndDoneFile("receive");
        String uri = fileUri("receive?doneFileName=ready");

        Set<String> bodies = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            bodies.add(consumer.receiveBody(uri, 5000, String.class));
        }

        assertEquals(Set.of("A", "B", "C"), bodies);
        awaitDoneFileDeleted("receive/ready");
        assertNull(consumer.receiveBodyNoWait(uri, String.class));
    }

    @Test
    public void testDownloadFailingAfterPreMoveKeepsTheDoneFileOfTheDirectory() throws Exception {
        // the download of a.txt fails after it was pre moved to work/a.txt; the done file kept for it must be the one of
        // the directory polled, not work/ready, which belongs to whatever writes to the work directory
        writeFilesAndDoneFile("premove");
        template.sendBodyAndHeader(fileUri("premove/work"), "", Exchange.FILE_NAME, "ready");
        MockEndpoint mock = getMockEndpoint("mock:premove");
        mock.expectedBodiesReceivedInAnyOrder("B", "C");

        context.getRouteController().startRoute("premove");

        mock.assertIsSatisfied();
        // a.txt is no longer in the directory, so the next poll deletes the done file kept for it
        awaitDoneFileDeleted("premove/ready");

        // a later batch is consumed, so the polls after the failed download have run
        mock.reset();
        mock.expectedBodiesReceived("D");
        template.sendBodyAndHeader(fileUri("premove"), "D", Exchange.FILE_NAME, "d.txt");
        template.sendBodyAndHeader(fileUri("premove"), "", Exchange.FILE_NAME, "ready");
        mock.assertIsSatisfied();
        awaitDoneFileDeleted("premove/ready");

        assertFileExists(testFile("premove/work/ready"));
        assertFileExists(testFile("premove/work/a.txt"));
    }

    @Test
    public void testFileWrittenDuringExactlyMaxMessagesPerPollFilesIsNotConsumed() throws Exception {
        // y.txt is written while b.txt, the last file, is routed, without a done file
        template.sendBodyAndHeader(fileUri("exactlate"), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri("exactlate"), "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri("exactlate"), "", Exchange.FILE_NAME, "ready");
        MockEndpoint mock = getMockEndpoint("mock:exactlate");
        mock.expectedBodiesReceived("A", "B");

        context.getRouteController().startRoute("exactlate");

        awaitDoneFileDeleted("exactlate/ready");
        mock.assertIsSatisfied();
        assertFileExists(testFile("exactlate/y.txt"));
    }

    @Test
    public void testFileRolledBackWaitsForNextDoneFile() throws Exception {
        // as before: b.txt fails once and waits for the next done file
        writeFilesAndDoneFile("retry");
        MockEndpoint mock = getMockEndpoint("mock:retry");
        mock.expectedBodiesReceivedInAnyOrder("A", "C");

        context.getRouteController().startRoute("retry");

        awaitDoneFileDeleted("retry/ready");
        mock.assertIsSatisfied();
        assertFileExists(testFile("retry/b.txt"));

        mock.reset();
        mock.expectedBodiesReceived("B");
        template.sendBodyAndHeader(fileUri("retry"), "", Exchange.FILE_NAME, "ready");

        mock.assertIsSatisfied();
        awaitDoneFileDeleted("retry/ready");
    }

    @Test
    public void testLastFileRolledBackKeepsDoneFileAsBefore() throws Exception {
        // as before: b.txt, the last file of the batch, fails once and writes y.txt; the done file stays, so the next
        // poll consumes both
        template.sendBodyAndHeader(fileUri("rollback"), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri("rollback"), "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri("rollback"), "", Exchange.FILE_NAME, "ready");
        getMockEndpoint("mock:rollback").expectedBodiesReceived("A", "B", "Y");

        context.getRouteController().startRoute("rollback");

        assertMockEndpointsSatisfied();
        awaitDoneFileDeleted("rollback/ready");
    }

    @Test
    public void testFileThatAlwaysFailsDoesNotHoldDoneFile() throws Exception {
        writeFilesAndDoneFile("poison", "BAD");
        MockEndpoint mock = getMockEndpoint("mock:poison");
        mock.expectedBodiesReceivedInAnyOrder("A", "C");

        context.getRouteController().startRoute("poison");

        awaitDoneFileDeleted("poison/ready");
        mock.assertIsSatisfied();

        // the next batch is consumed with its done file (b.txt is tried again and fails again)
        mock.reset();
        mock.expectedBodiesReceived("D");
        template.sendBodyAndHeader(fileUri("poison"), "D", Exchange.FILE_NAME, "d.txt");
        template.sendBodyAndHeader(fileUri("poison"), "", Exchange.FILE_NAME, "ready");

        mock.assertIsSatisfied();
        awaitDoneFileDeleted("poison/ready");
        assertFileExists(testFile("poison/b.txt"));
    }

    @Test
    public void testFileThatAlwaysFailsWithFilesLeftDoesNotHoldDoneFile() throws Exception {
        // b.txt fails as the last file of the first poll, c.txt is left for the next one, which tries b.txt again (as
        // the next poll does on main when the last file failed) and deletes the done file after c.txt
        writeFilesAndDoneFile("poisonlimit", "BAD");
        MockEndpoint mock = getMockEndpoint("mock:poisonlimit");
        mock.expectedBodiesReceivedInAnyOrder("A", "C");

        context.getRouteController().startRoute("poisonlimit");

        awaitDoneFileDeleted("poisonlimit/ready");
        mock.assertIsSatisfied();
        assertEquals(2, badAttempts.get(), "b.txt is tried again with the file left");

        mock.reset();
        mock.expectedBodiesReceived("D");
        template.sendBodyAndHeader(fileUri("poisonlimit"), "D", Exchange.FILE_NAME, "d.txt");
        template.sendBodyAndHeader(fileUri("poisonlimit"), "", Exchange.FILE_NAME, "ready");

        mock.assertIsSatisfied();
        assertFileExists(testFile("poisonlimit/b.txt"));
    }

    @Test
    public void testDynamicDoneFileNameUnchanged() throws Exception {
        template.sendBodyAndHeader(fileUri("dynamic"), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri("dynamic"), "", Exchange.FILE_NAME, "a.txt.done");
        template.sendBodyAndHeader(fileUri("dynamic"), "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri("dynamic"), "", Exchange.FILE_NAME, "b.txt.done");
        template.sendBodyAndHeader(fileUri("dynamic"), "C", Exchange.FILE_NAME, "c.txt");
        MockEndpoint mock = getMockEndpoint("mock:dynamic");
        // the eager limit takes the first listed file, so the order is not known
        mock.expectedBodiesReceivedInAnyOrder("A", "B");

        context.getRouteController().startRoute("dynamic");

        awaitDoneFileDeleted("dynamic/a.txt.done");
        awaitDoneFileDeleted("dynamic/b.txt.done");
        mock.assertIsSatisfied();
        assertFileExists(testFile("dynamic/c.txt"));
    }

    private void awaitDoneFileDeleted(String doneFile) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertFileNotExists(testFile(doneFile)));
    }

    private void writeFilesAndDoneFile(String dir) {
        writeFilesAndDoneFile(dir, "B");
    }

    private void writeFilesAndDoneFile(String dir, String bodyB) {
        template.sendBodyAndHeader(fileUri(dir), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri(dir), bodyB, Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(fileUri(dir), "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(fileUri(dir), "", Exchange.FILE_NAME, "ready");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("limit?doneFileName=ready&maxMessagesPerPoll=2&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("limit").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:limit");

                from(fileUri("noteager?doneFileName=ready&maxMessagesPerPoll=2&eagerMaxMessagesPerPoll=false"
                             + "&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("noteager").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:noteager");

                from(fileUri("notstarted?doneFileName=ready&exclusiveReadLockStrategy=#lockBOnce&sortBy=file:name"
                             + "&initialDelay=0&delay=10"))
                        .routeId("notstarted").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:notstarted");

                from(fileUri("retry?doneFileName=ready&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("retry").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("B".equals(e.getMessage().getBody()) && failB.getAndSet(false)) {
                                throw new IllegalStateException("B fails once");
                            }
                        })
                        .to("mock:retry");

                from(fileUri("exact?doneFileName=ready&maxMessagesPerPoll=2&initialDelay=0&delay=10"))
                        .routeId("exact").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:exact");

                from(fileUri("gate?doneFileName=ready&maxMessagesPerPoll=1&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("gate").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if (writeLate.compareAndSet(true, false)) {
                                Files.writeString(testFile("gate/x.txt"), "X");
                            }
                        })
                        .to("mock:gate");

                from(fileUri("producer?doneFileName=ready&maxMessagesPerPoll=1&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("producer").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if (produceLate.compareAndSet(true, false)) {
                                // deletes and rewrites the done file after writing x.txt
                                template.sendBodyAndHeader(fileUri("producer?doneFileName=ready"), "X", Exchange.FILE_NAME,
                                        "x.txt");
                            }
                        })
                        .to("mock:producer");

                from("failingfile:" + testDirectory("premove")
                     + "?doneFileName=ready&preMove=work&sortBy=file:name&initialDelay=0&delay=10")
                        .routeId("premove").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:premove");

                from(fileUri("exactlate?doneFileName=ready&maxMessagesPerPoll=2&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("exactlate").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("B".equals(e.getMessage().getBody())) {
                                Files.writeString(testFile("exactlate/y.txt"), "Y");
                            }
                        })
                        .to("mock:exactlate");

                from(fileUri("rollback?doneFileName=ready&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("rollback").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("B".equals(e.getMessage().getBody()) && failLastB.getAndSet(false)) {
                                Files.writeString(testFile("rollback/y.txt"), "Y");
                                throw new IllegalStateException("B fails once");
                            }
                        })
                        .to("mock:rollback");

                from(fileUri("poison?doneFileName=ready&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("poison").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("BAD".equals(e.getMessage().getBody())) {
                                throw new IllegalStateException("B always fails");
                            }
                        })
                        .to("mock:poison");

                from(fileUri("poisonlimit?doneFileName=ready&maxMessagesPerPoll=2&eagerMaxMessagesPerPoll=false"
                             + "&sortBy=file:name&initialDelay=0&delay=10"))
                        .routeId("poisonlimit").autoStartup(false)
                        .convertBodyTo(String.class)
                        .process(e -> {
                            if ("BAD".equals(e.getMessage().getBody())) {
                                badAttempts.incrementAndGet();
                                throw new IllegalStateException("B always fails");
                            }
                        })
                        .to("mock:poisonlimit");

                from(fileUri("dynamic?doneFileName=${file:name}.done&maxMessagesPerPoll=1&sortBy=file:name"
                             + "&initialDelay=0&delay=10"))
                        .routeId("dynamic").autoStartup(false)
                        .convertBodyTo(String.class).to("mock:dynamic");
            }
        };
    }
}
