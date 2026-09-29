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
package org.apache.camel.processor;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The onCompletion EIP must be able to read a body that stream caching spooled to disk, and the spool file must be
 * deleted once the onCompletion is done.
 */
public class OnCompletionStreamCachingSpoolTest extends ContextTestSupport {

    private static final byte[] DATA = createData(16 * 1024);

    // the parallel onCompletion waits until the original exchange is done
    private final CountDownLatch originalDone = new CountDownLatch(1);
    private final CountDownLatch blockingTaskStarted = new CountDownLatch(1);
    private final CountDownLatch releaseBlockingTask = new CountDownLatch(1);

    private final ExecutorService rejecting = Executors.newSingleThreadExecutor();
    private final ExecutorService discarding = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
    // a thread pool that another thread shuts down (gracefully) right after it has accepted a task
    private final ExecutorService shutdownAfterSubmit = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadPoolExecutor.DiscardPolicy()) {
        @Override
        public Future<?> submit(Runnable task) {
            Future<?> answer = super.submit(task);
            shutdown();
            return answer;
        }
    };

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        releaseBlockingTask.countDown();
        super.tearDown();
        rejecting.shutdownNow();
        discarding.shutdownNow();
        shutdownAfterSubmit.shutdownNow();
    }

    @Test
    public void testOnCompletion() throws Exception {
        sendAndAssertReadByOnCompletion("direct:after");
    }

    @Test
    public void testOnCompletionParallel() throws Exception {
        sendAndAssertReadByOnCompletion("direct:parallel");
    }

    @Test
    public void testOnCompletionBeforeConsumer() throws Exception {
        // runs before the original exchange is done, so this has always worked
        sendAndAssertReadByOnCompletion("direct:before");
    }

    @Test
    public void testOnCompletionOnFailureOnly() throws Exception {
        MockEndpoint done = getMockEndpoint("mock:done");
        done.expectedMessageCount(1);

        assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:failure", stream()));

        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, done.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertNoSpoolFiles();
    }

    @Test
    public void testNoOnCompletion() throws Exception {
        // the spool file is still deleted when the exchange is done
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:plain", stream());

        assertMockEndpointsSatisfied();
        assertNoSpoolFiles();
    }

    @Test
    public void testParallelTaskRejected() throws Exception {
        // the thread pool is shut down and rejects the onCompletion task with an exception
        rejecting.shutdown();
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:done").expectedMessageCount(0);

        template.sendBody("direct:rejected", stream());

        assertMockEndpointsSatisfied();
        assertNoSpoolFiles();
        assertNoPendingTasks("rejected");
    }

    @Test
    public void testParallelTaskDiscarded() throws Exception {
        // the thread pool is shut down and discards the onCompletion task without an exception
        discarding.shutdown();
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:done").expectedMessageCount(0);

        template.sendBody("direct:discarded", stream());

        assertMockEndpointsSatisfied();
        assertNoSpoolFiles();
        assertNoPendingTasks("discarded");
    }

    @Test
    public void testParallelTaskAcceptedBeforeShutdown() throws Exception {
        // the thread pool is shut down after it accepted the onCompletion task, and a graceful shutdown still runs the
        // task, so it must not be discarded
        MockEndpoint done = getMockEndpoint("mock:done");
        done.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:shutdownAfterSubmit", stream());

        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, done.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertTrue(shutdownAfterSubmit.isShutdown());
        assertNoSpoolFiles();
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNoPendingTasks("shutdownAfterSubmit"));
    }

    @Test
    public void testParallelTaskDroppedAtShutdown() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        // the first onCompletion task blocks the only thread of the pool, the second one waits in its queue
        template.sendBody("direct:dropped", stream());
        assertTrue(blockingTaskStarted.await(10, TimeUnit.SECONDS));
        template.sendBody("direct:dropped", stream());
        assertMockEndpointsSatisfied();
        OnCompletionProcessor onCompletion = context.getProcessor("dropped", OnCompletionProcessor.class);
        assertEquals(2, onCompletion.getPendingExchangesSize());

        // the graceful shutdown times out, and the thread pool is shut down with shutdownNow: the queued task never
        // runs
        context.getShutdownStrategy().setTimeout(1);
        context.stop();

        assertNoSpoolFiles();
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, onCompletion.getPendingExchangesSize()));
        assertEquals(0, getMockEndpoint("mock:done").getReceivedCounter(), "The queued onCompletion should not have run");
    }

    private void sendAndAssertReadByOnCompletion(String uri) throws Exception {
        MockEndpoint done = getMockEndpoint("mock:done");
        done.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody(uri, stream());
        originalDone.countDown();

        assertMockEndpointsSatisfied();
        assertArrayEquals(DATA, done.getReceivedExchanges().get(0).getMessage().getBody(byte[].class));
        assertNoSpoolFiles();
    }

    private void assertNoPendingTasks(String id) {
        // the task that never runs is no longer counted as pending, so a graceful shutdown does not wait for it
        assertEquals(0, context.getProcessor(id, OnCompletionProcessor.class).getPendingExchangesSize());
    }

    private void assertNoSpoolFiles() {
        File spoolDir = testDirectory().toFile();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String[] files = spoolDir.list();
            assertNotNull(files);
            assertEquals(0, files.length, "Spool files left behind: " + List.of(files));
        });
    }

    private static InputStream stream() {
        // a stream that is not converted to an in-memory cache, so it is spooled to disk
        return new BufferedInputStream(new ByteArrayInputStream(DATA));
    }

    private static byte[] createData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ('a' + i % 26);
        }
        return data;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(1024);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);
                context.setStreamCaching(true);

                context.getExecutorServiceManager().registerThreadPoolProfile(
                        new ThreadPoolProfileBuilder("singleThread").poolSize(1).maxPoolSize(1).maxQueueSize(10).build());

                from("direct:after")
                        .onCompletion().convertBodyTo(byte[].class).to("mock:done").end()
                        .to("mock:result");

                from("direct:parallel")
                        .onCompletion().parallelProcessing()
                        .process(e -> originalDone.await(20, TimeUnit.SECONDS))
                        .convertBodyTo(byte[].class).to("mock:done").end()
                        .to("mock:result");

                from("direct:before")
                        .onCompletion().modeBeforeConsumer().convertBodyTo(byte[].class).to("mock:done").end()
                        .to("mock:result");

                from("direct:failure")
                        .onCompletion().onFailureOnly().convertBodyTo(byte[].class).to("mock:done").end()
                        .to("mock:result")
                        .throwException(new IllegalArgumentException("Forced"));

                from("direct:plain")
                        .to("mock:result");

                from("direct:rejected")
                        .onCompletion().id("rejected").parallelProcessing().executorService(rejecting).to("mock:done").end()
                        .to("mock:result");

                from("direct:discarded")
                        .onCompletion().id("discarded").parallelProcessing().executorService(discarding).to("mock:done").end()
                        .to("mock:result");

                from("direct:shutdownAfterSubmit")
                        .onCompletion().id("shutdownAfterSubmit").parallelProcessing().executorService(shutdownAfterSubmit)
                        .convertBodyTo(byte[].class).to("mock:done").end()
                        .to("mock:result");

                from("direct:dropped")
                        .onCompletion().id("dropped").parallelProcessing().executorService("singleThread")
                        .process(e -> {
                            blockingTaskStarted.countDown();
                            releaseBlockingTask.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:done").end()
                        .to("mock:result");
            }
        };
    }
}
