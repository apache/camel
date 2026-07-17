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
package org.apache.camel.component.snakeyaml;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.snakeyaml.model.TestPojo;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SnakeYAMLConcurrentTest extends CamelTestSupport {

    private static final int THREAD_COUNT = 8;
    private static final int ITERATIONS = 500;

    @Test
    public void testConcurrentMarshalUnmarshal() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    String name = "Thread" + threadId + "-Iter" + i;
                    TestPojo original = new TestPojo(name);

                    String yaml = template.requestBody("direct:marshal", original, String.class);
                    TestPojo result = template.requestBody("direct:unmarshal", yaml, TestPojo.class);

                    assertEquals(name, result.getName(),
                            "Data corruption detected: expected '" + name + "' but got '" + result.getName() + "'");
                }
                return null;
            }));
        }

        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        List<Throwable> errors = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                errors.add(e.getCause() != null ? e.getCause() : e);
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(errors.size()).append(" thread(s) failed:\n");
            for (Throwable err : errors) {
                sb.append("  - ").append(err.getMessage()).append("\n");
            }
            fail(sb.toString());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        SnakeYAMLDataFormat format = new SnakeYAMLDataFormat(TestPojo.class);
        format.setAllowAnyType(true);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal")
                        .marshal(format);
                from("direct:unmarshal")
                        .unmarshal(format)
                        .convertBodyTo(TestPojo.class);
            }
        };
    }
}
