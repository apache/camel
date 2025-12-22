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
package org.apache.camel.component.file.cluster;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileLockClusterTaskExecutorTest {
    @Test
    void runTaskWithDefaultMaxAttemptsAndTimeout() throws ExecutionException, TimeoutException {
        FileLockClusterService service = new FileLockClusterService();
        service.setCamelContext(new DefaultCamelContext());

        FileLockClusterTaskExecutor executor = new FileLockClusterTaskExecutor(service);

        String message = "Hello World";
        String result = executor.run(new Supplier<String>() {
            @Override
            public String get() {
                return message;
            }
        });

        Assertions.assertEquals(message, result);
    }

    @Test
    void runTaskWithMaxAttemptsExceeded() {
        int maxAttempts = 3;
        int timeoutMs = 100;

        FileLockClusterService service = new FileLockClusterService();
        service.setCamelContext(new DefaultCamelContext());
        service.setClusterDataTaskMaxAttempts(maxAttempts);
        service.setClusterDataTaskTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        FileLockClusterTaskExecutor executor = new FileLockClusterTaskExecutor(service);

        AtomicInteger count = new AtomicInteger();
        String message = "Hello World";

        Assertions.assertThrows(TimeoutException.class, () -> {
            executor.run(new Supplier<String>() {
                @Override
                public String get() {
                    count.incrementAndGet();
                    try {
                        Thread.sleep(timeoutMs + 50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return message;
                }
            });
        });

        Assertions.assertEquals(3, count.get());
    }

    @Test
    void runTaskWithMaxAttemptsNotExceeded() throws ExecutionException, TimeoutException {
        int maxAttempts = 3;
        int timeoutMs = 100;

        FileLockClusterService service = new FileLockClusterService();
        service.setCamelContext(new DefaultCamelContext());
        service.setClusterDataTaskMaxAttempts(maxAttempts);
        service.setClusterDataTaskTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        FileLockClusterTaskExecutor executor = new FileLockClusterTaskExecutor(service);

        AtomicInteger count = new AtomicInteger();
        String message = "Hello World";

        String result = executor.run(new Supplier<String>() {
            @Override
            public String get() {
                if (count.incrementAndGet() < 3) {
                    try {
                        Thread.sleep(timeoutMs + 50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return message;
            }
        });

        Assertions.assertEquals(3, count.get());
        Assertions.assertEquals(message, result);
    }
}
