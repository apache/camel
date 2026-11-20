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
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAMEL-27264: with bridgeErrorHandler, a poll of pollEnrich whose read lock fails delivers a bridged error exchange.
 * The file must then not be polled again by the same receive call, as that exchange cannot be returned and its file
 * would stay in the in-progress repository, skipped by every later poll. A poll is not retried once it delivered an
 * exchange to the receive call, as a retried poll could only find exchanges it cannot return.
 */
public class FilePollEnrichBridgeErrorHandlerInProgressTest extends ContextTestSupport {

    private final MemoryIdempotentRepository inProgress = new MemoryIdempotentRepository();
    private final FailOnceReadLockStrategy readLock = new FailOnceReadLockStrategy();
    private final MemoryIdempotentRepository skipRetryInProgress = new MemoryIdempotentRepository();
    private final FailOnceReadLockStrategy skipRetryReadLock = new FailOnceReadLockStrategy();

    @Test
    public void testFileIsReleasedWhenReadLockFailsOnce() {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        // the first read lock attempt fails, and pollEnrich returns the bridged error
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:start", "Start", String.class));
        IOException cause = assertInstanceOf(IOException.class, e.getCause());
        assertEquals("Forced to simulate a lost connection", cause.getMessage());

        // the receive call returned the error, so it must not have taken the file as well: an exchange it cannot return
        // would keep the file in the in-progress repository, and every later poll would skip the file
        assertEquals(0, inProgress.getCacheSize(), "the file must not be left in the in-progress repository");
        assertEquals(1, readLock.attempts.get(), "the file should not be polled again after the error was delivered");

        // the next pollEnrich gets the file, which is then committed (moved to .camel)
        String out = template.requestBody("direct:start", "Start", String.class);
        assertEquals("Hello World", out);
        assertFileExists(testFile(".camel/hello.txt"));
        assertEquals(0, inProgress.getCacheSize());
    }

    @Test
    public void testPollIsNotRetriedWhenErrorWasDelivered() {
        template.sendBodyAndHeader(fileUri("skipretry"), "Hello World", Exchange.FILE_NAME, "hello.txt");

        // the commit of the poll fails after the bridged error was delivered, and the poll strategy asks to retry,
        // but a retried poll could only find exchanges the receive call cannot return
        assertThrows(CamelExecutionException.class, () -> template.requestBody("direct:skipRetry", "Start", String.class));
        assertEquals(1, skipRetryReadLock.attempts.get(), "the poll should not be retried");
        assertEquals(0, skipRetryInProgress.getCacheSize(), "the file must not be left in the in-progress repository");
        assertFileExists(testFile("skipretry/hello.txt"));
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("failOnceReadLock", readLock);
        registry.bind("inProgress", inProgress);
        registry.bind("skipRetryReadLock", skipRetryReadLock);
        registry.bind("skipRetryInProgress", skipRetryInProgress);
        registry.bind("retryOncePollStrategy", new RetryOncePollStrategy());
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich(fileUri("?exclusiveReadLockStrategy=#failOnceReadLock&bridgeErrorHandler=true"
                                            + "&inProgressRepository=#inProgress&initialDelay=0&delay=10"),
                                2000)
                        .convertBodyTo(String.class);

                from("direct:skipRetry")
                        .pollEnrich(fileUri("skipretry?exclusiveReadLockStrategy=#skipRetryReadLock"
                                            + "&bridgeErrorHandler=true&inProgressRepository=#skipRetryInProgress"
                                            + "&pollStrategy=#retryOncePollStrategy&initialDelay=0&delay=10"),
                                2000)
                        .convertBodyTo(String.class);
            }
        };
    }

    // simulates a read lock that fails on the first attempt, such as when the connection is lost (Pipe closed)
    private static class FailOnceReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {

        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) {
            // noop
        }

        @Override
        public boolean acquireExclusiveReadLock(
                GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
                throws Exception {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("Forced to simulate a lost connection");
            }
            return true;
        }

        @Override
        public void releaseExclusiveReadLockOnAbort(
                GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) {
            // noop
        }

        @Override
        public void releaseExclusiveReadLockOnRollback(
                GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) {
            // noop
        }

        @Override
        public void releaseExclusiveReadLockOnCommit(
                GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) {
            // noop
        }

        @Override
        public void setTimeout(long timeout) {
            // noop
        }

        @Override
        public void setCheckInterval(long checkInterval) {
            // noop
        }

        @Override
        public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
            // noop
        }

        @Override
        public void setMarkerFiler(boolean markerFile) {
            // noop
        }

        @Override
        public void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles) {
            // noop
        }
    }

    // fails the commit of the first poll, and retries it, as a poll strategy can do after a failed poll
    private static class RetryOncePollStrategy implements PollingConsumerPollStrategy {

        private final AtomicInteger commits = new AtomicInteger();

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            if (commits.incrementAndGet() == 1) {
                throw new IllegalStateException("Forced to retry the poll");
            }
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
            return retryCounter < 1;
        }
    }
}
