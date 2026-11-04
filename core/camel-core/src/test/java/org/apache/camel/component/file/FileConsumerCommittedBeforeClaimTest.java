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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic regression test for CAMEL-25778: verifies {@code FileConsumer.isValidFile()} correctly handles the case
 * where a concurrent commit thread moves the file between {@code inProgressRepository.contains()} and {@code add()}.
 * <p/>
 * The race is made deterministic by injecting a custom {@code inProgressRepository} whose {@code add()} deletes the
 * file before delegating to the real repository — exactly what a concurrent commit thread would do (move file + remove
 * in-progress entry). Without the fix, the file would be routed and the eager idempotent key would remain. With the
 * fix, neither happens.
 */
class FileConsumerCommittedBeforeClaimTest extends ContextTestSupport {

    private final MemoryIdempotentRepository idempotent = new MemoryIdempotentRepository();
    private final AtomicBoolean claimed = new AtomicBoolean();

    /** Custom in-progress repository that simulates a concurrent commit deleting the file inside add(). */
    private final MemoryIdempotentRepository inProgress = new MemoryIdempotentRepository() {
        @Override
        public boolean add(String key) {
            // Simulate: a commit on another thread lands between contains() and add() —
            // the file is moved away (here: deleted) before this poll's add() completes.
            try {
                Files.deleteIfExists(Path.of(key));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            claimed.set(true);
            return super.add(key);
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("inProgress", inProgress);
        context.getRegistry().bind("idem", idempotent);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10"
                             + "&inProgressRepository=#inProgress"
                             + "&idempotent=true&idempotentRepository=#idem"))
                        .routeId("myRoute").autoStartup(false)
                        .to("mock:done");
            }
        };
    }

    @Test
    void testFileGoneAfterClaimIsSkipped() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(0);

        Path file = testDirectory().resolve("hello.txt");
        Files.writeString(file, "Hello");
        String key = file.toAbsolutePath().toString();

        context.getRouteController().startRoute("myRoute");

        // Wait until the in-progress add() was triggered and cleanup has completed on the poll thread
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(claimed.get(), "in-progress add() must have been triggered");
            assertFalse(inProgress.contains(key), "in-progress key must be removed after skip");
            assertFalse(idempotent.contains(key), "eager idempotent key must be removed after skip");
        });

        getMockEndpoint("mock:done").assertIsSatisfied(2000);
    }
}
