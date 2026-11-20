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
package org.apache.camel.component.file.strategy;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The idempotent read locks release the key they acquired, also when the route changes the headers that the
 * idempotentKey expression reads (here CamelFileName, as a route does before it writes to a file endpoint).
 */
class FileIdempotentReadLockRoutedFileNameTest extends ContextTestSupport {

    private final MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myRepo", myRepo);
        return jndi;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private String consumerUri(String readLock, String options) {
        return fileUri("?initialDelay=0&delay=10&readLockCheckInterval=100&readLock=" + readLock
                       + "&idempotentRepository=#myRepo&idempotentKey=${file:name}" + options);
    }

    @ParameterizedTest
    @ValueSource(strings = { "idempotent", "idempotent-changed", "idempotent-rename" })
    void rolledBackFileIsPickedUpAgain(String readLock) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());

                from(consumerUri(readLock, ""))
                        // the name of the file to write, as a route does before a file producer
                        .setHeader(Exchange.FILE_NAME, simple("out-${file:name}"))
                        .process(e -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("Forced failure");
                            }
                        })
                        .to("mock:done");
            }
        });
        context.start();

        MockEndpoint done = getMockEndpoint("mock:done");
        done.expectedMessageCount(1);
        done.setResultWaitTime(10000);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        // the first attempt fails and is rolled back: the read lock must remove the key it added for hello.txt,
        // so the file is picked up again and the second attempt completes
        done.assertIsSatisfied();
        assertThat(attempts.get()).isEqualTo(2);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(Files.exists(testFile("hello.txt"))).as("file moved out of the inbox").isFalse();
            assertThat(myRepo.contains("hello.txt")).as("key confirmed on commit").isTrue();
            assertThat(myRepo.getCacheSize()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "idempotent", "idempotent-changed", "idempotent-rename" })
    void commitRemovesOnlyItsOwnKey(String readLock) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(consumerUri(readLock, "&readLockRemoveOnCommit=true&include=a.txt"))
                        .setHeader(Exchange.FILE_NAME, constant("b.txt"))
                        .to("mock:a");
            }
        });
        context.start();

        // another node holds the read lock of b.txt (it is processing that file right now)
        myRepo.add("b.txt");

        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        a.setResultWaitTime(10000);

        template.sendBodyAndHeader(fileUri(), "A", Exchange.FILE_NAME, "a.txt");

        a.assertIsSatisfied();
        // the commit must remove the key a.txt (its own) and leave b.txt to the node that holds it
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(Files.exists(testFile("a.txt"))).as("file moved out of the inbox").isFalse();
            assertThat(myRepo.contains("a.txt")).as("own key removed on commit").isFalse();
            assertThat(myRepo.contains("b.txt")).as("key of the other node kept").isTrue();
        });
    }
}
