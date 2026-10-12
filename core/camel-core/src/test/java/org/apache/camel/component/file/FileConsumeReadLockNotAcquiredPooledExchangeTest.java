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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The consumer gives back the exchange of every file whose read lock it could not get, without routing it. With pooled
 * exchanges, the files of the next poll must each get their own exchange.
 */
class FileConsumeReadLockNotAcquiredPooledExchangeTest extends ContextTestSupport {

    private final RefuseTwiceReadLockStrategy readLockStrategy = new RefuseTwiceReadLockStrategy();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getCamelContextExtension().setExchangeFactory(new PooledExchangeFactory());
        return camelContext;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("refuseTwice", readLockStrategy);
        return registry;
    }

    @Test
    void filesWithoutReadLockAreConsumedByTheNextPoll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("AAA", "BBB");
        mock.expectedHeaderValuesReceivedInAnyOrder(Exchange.FILE_NAME_ONLY, "a.txt", "b.txt");

        template.sendBodyAndHeader(fileUri(), "AAA", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri(), "BBB", Exchange.FILE_NAME, "b.txt");

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
        // both files were refused in the first poll and locked in the second one
        assertEquals(4, readLockStrategy.attempts.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&sortBy=file:name&exclusiveReadLockStrategy=#refuseTwice"))
                        .routeId("foo").autoStartup(false)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    /**
     * Does not give the lock for the first two files it is asked for (the two files of the first poll).
     */
    private static final class RefuseTwiceReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {

        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) {
            // noop
        }

        @Override
        public boolean acquireExclusiveReadLock(
                GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) {
            return attempts.incrementAndGet() > 2;
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
}
