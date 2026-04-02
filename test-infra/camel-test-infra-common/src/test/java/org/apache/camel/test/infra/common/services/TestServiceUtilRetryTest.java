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
package org.apache.camel.test.infra.common.services;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestServiceUtilRetryTest {

    @Test
    void retriesOnContainerFetchException() {
        AtomicInteger attempts = new AtomicInteger();

        TestService service = new StubTestService(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ContainerFetchException("404: image not found");
            }
        });

        assertDoesNotThrow(() -> TestServiceUtil.tryInitialize(service, null));
        assertEquals(3, attempts.get());
    }

    @Test
    void retriesOnContainerLaunchException() {
        AtomicInteger attempts = new AtomicInteger();

        TestService service = new StubTestService(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new ContainerLaunchException("Container startup failed");
            }
        });

        assertDoesNotThrow(() -> TestServiceUtil.tryInitialize(service, null));
        assertEquals(2, attempts.get());
    }

    @Test
    void failsImmediatelyOnNonContainerException() {
        AtomicInteger attempts = new AtomicInteger();

        TestService service = new StubTestService(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("not a container error");
        });

        assertThrows(IllegalStateException.class, () -> TestServiceUtil.tryInitialize(service, null));
        assertEquals(1, attempts.get());
    }

    @Test
    void failsAfterMaxRetries() {
        AtomicInteger attempts = new AtomicInteger();

        TestService service = new StubTestService(() -> {
            attempts.incrementAndGet();
            throw new ContainerFetchException("always fails");
        });

        assertThrows(ContainerFetchException.class, () -> TestServiceUtil.tryInitialize(service, null));
        assertEquals(3, attempts.get());
    }

    @Test
    void retriesOnWrappedContainerException() {
        AtomicInteger attempts = new AtomicInteger();

        TestService service = new StubTestService(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("wrapper", new ContainerLaunchException("nested container error"));
            }
        });

        assertDoesNotThrow(() -> TestServiceUtil.tryInitialize(service, null));
        assertEquals(2, attempts.get());
    }

    @Test
    void succeedsOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        TestService service = new StubTestService(attempts::incrementAndGet);

        assertDoesNotThrow(() -> TestServiceUtil.tryInitialize(service, null));
        assertEquals(1, attempts.get());
    }

    private static class StubTestService implements TestService {
        private final Runnable action;

        StubTestService(Runnable action) {
            this.action = action;
        }

        @Override
        public void initialize() {
            action.run();
        }

        @Override
        public void registerProperties() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void close() {
        }
    }
}
