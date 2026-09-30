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
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When retrieving a file fails after the read lock was acquired (e.g. a FTP, SFTP or SMB download fails), the eagerly
 * added idempotent key and the read lock must be released, so the file is retried on a later poll.
 * <p/>
 * The retrieve of the file component itself does not fail, so a {@link FileOperations} whose first retrieve fails is
 * plugged into the real {@link FileConsumer}, which shares {@link GenericFileConsumer} with the remote components.
 */
class FileConsumerRetrieveFailureTest extends ContextTestSupport {

    private final AtomicInteger failuresLeft = new AtomicInteger(1);
    private final AtomicInteger retrieveCalls = new AtomicInteger();
    private final AtomicBoolean ignoreCannotRetrieve = new AtomicBoolean();
    private final AtomicInteger handledExceptions = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testNoopRetriedAfterRetrieveFailure() throws Exception {
        FileEndpoint endpoint = createEndpoint();
        endpoint.setNoop(true);

        assertFileRetried(endpoint);
        assertEquals(1, handledExceptions.get(), "The retrieve failure should be reported");
    }

    @Test
    void testIdempotentPreMoveKeyRemovedAfterRetrieveFailure() throws Exception {
        MemoryIdempotentRepository repo = new MemoryIdempotentRepository();
        FileEndpoint endpoint = createEndpoint();
        endpoint.setIdempotent(true);
        endpoint.setIdempotentRepository(repo);
        endpoint.setPreMove("inprogress");

        context.start();
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpoint).convertBodyTo(String.class).to("mock:result");
            }
        });

        // the first retrieve fails, after the file was pre moved
        Path preMoved = testFile("inprogress/hello.txt");
        await().atMost(5, TimeUnit.SECONDS).until(() -> handledExceptions.get() == 1);
        assertTrue(Files.exists(preMoved), "The file should be left in the pre move directory");
        // the key was added for the original file, and not for the pre moved file bound to the exchange
        assertEquals(0, repo.getCacheSize(), "The eager idempotent key of the original file should be removed");

        // move the file back, such as an operator would do, and the file must be consumed
        Files.move(preMoved, testFile("hello.txt"));

        mock.assertIsSatisfied(5000);
        assertEquals(2, retrieveCalls.get(), "The file should be retrieved again after the failure");
    }

    @Test
    void testIdempotentReadLockRetriedAfterRetrieveFailure() throws Exception {
        MemoryIdempotentRepository repo = new MemoryIdempotentRepository();
        FileEndpoint endpoint = createEndpoint();
        endpoint.setReadLock("idempotent");
        endpoint.setIdempotentRepository(repo);

        assertFileRetried(endpoint);
        assertEquals(1, handledExceptions.get(), "The retrieve failure should be reported");
    }

    @Test
    void testIdempotentReadLockRetriedAfterIgnoredRetrieveFailure() throws Exception {
        ignoreCannotRetrieve.set(true);
        MemoryIdempotentRepository repo = new MemoryIdempotentRepository();
        FileEndpoint endpoint = createEndpoint();
        endpoint.setReadLock("idempotent");
        endpoint.setIdempotentRepository(repo);

        assertFileRetried(endpoint);
        assertEquals(0, handledExceptions.get(), "An ignored retrieve failure should not be reported");
    }

    private void assertFileRetried(FileEndpoint endpoint) throws Exception {
        context.start();
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpoint).convertBodyTo(String.class).to("mock:result");
            }
        });

        // the first retrieve fails, and the next poll must retrieve the file again
        mock.assertIsSatisfied(5000);
        assertEquals(2, retrieveCalls.get(), "The file should be retrieved again after the failure");
    }

    private FileEndpoint createEndpoint() {
        FileEndpoint endpoint = new FlakyFileEndpoint(fileUri(), context.getComponent("file"));
        endpoint.setCamelContext(context);
        endpoint.setFile(testDirectory().toFile());
        endpoint.setInitialDelay(0);
        endpoint.setDelay(10);
        endpoint.setExceptionHandler(new CountingExceptionHandler());
        return endpoint;
    }

    private final class FlakyFileEndpoint extends FileEndpoint {

        FlakyFileEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        protected FileConsumer newFileConsumer(Processor processor, GenericFileOperations<File> operations) {
            FileOperations flaky = new FileOperations(this) {
                @Override
                public boolean retrieveFile(String name, Exchange exchange, long size) {
                    retrieveCalls.incrementAndGet();
                    if (failuresLeft.getAndDecrement() > 0) {
                        if (ignoreCannotRetrieve.get()) {
                            return false;
                        }
                        // such as a FTP download that fails
                        throw new GenericFileOperationFailedException("Simulated connection reset while retrieving " + name);
                    }
                    return super.retrieveFile(name, exchange, size);
                }
            };
            return new FileConsumer(this, processor, flaky, createGenericFileStrategy()) {
                @Override
                protected boolean ignoreCannotRetrieveFile(String name, Exchange exchange, Exception cause) {
                    return ignoreCannotRetrieve.get();
                }
            };
        }
    }

    private final class CountingExceptionHandler implements ExceptionHandler {

        @Override
        public void handleException(Throwable exception) {
            handledExceptions.incrementAndGet();
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handledExceptions.incrementAndGet();
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            handledExceptions.incrementAndGet();
        }
    }
}
