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
package org.apache.camel.component.docling;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test TTL-based cleanup of pending async conversion tasks.
 */
public class DoclingAsyncTaskTtlTest extends CamelTestSupport {

    @Test
    public void testAsyncTaskTtlEviction() throws Exception {
        // Get the component to access pending tasks map
        DoclingComponent component = context.getComponent("docling", DoclingComponent.class);
        assertNotNull(component);

        // Configure with a short TTL (2 seconds) for testing
        component.getConfiguration().setAsyncTaskTtl(2000);
        component.getConfiguration().setUseDoclingServe(true);
        component.getConfiguration().setDoclingServeUrl("http://localhost:5001");

        // Start the component to trigger cleanup scheduler
        context.start();

        // Simulate adding a task entry directly (since we don't have a real docling-serve)
        String taskId = "test-task-1";
        AsyncTaskEntry entry = new AsyncTaskEntry(taskId, new java.util.concurrent.CompletableFuture<>());
        component.getPendingAsyncTasks().put(taskId, entry);

        // Verify task is present
        assertEquals(1, component.getPendingAsyncTasks().size());

        // Wait for TTL to expire and cleanup to run
        // Cleanup runs every 10% of TTL (minimum 1 minute), but for 2s TTL that's 200ms
        // Add buffer for cleanup execution
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, component.getPendingAsyncTasks().size()));
    }

    @Test
    public void testAsyncTaskNotEvictedBeforeTtl() throws Exception {
        // Get the component
        DoclingComponent component = context.getComponent("docling", DoclingComponent.class);
        assertNotNull(component);

        // Configure with a longer TTL (10 seconds)
        component.getConfiguration().setAsyncTaskTtl(10000);
        component.getConfiguration().setUseDoclingServe(true);
        component.getConfiguration().setDoclingServeUrl("http://localhost:5001");

        context.start();

        // Add a task entry
        String taskId = "test-task-2";
        AsyncTaskEntry entry = new AsyncTaskEntry(taskId, new java.util.concurrent.CompletableFuture<>());
        component.getPendingAsyncTasks().put(taskId, entry);

        // Verify task is present
        assertEquals(1, component.getPendingAsyncTasks().size());

        // Wait a short time (less than TTL)
        Thread.sleep(1000);

        // Task should still be present
        assertEquals(1, component.getPendingAsyncTasks().size());
    }

    @Test
    public void testMultipleTasksEviction() throws Exception {
        // Get the component
        DoclingComponent component = context.getComponent("docling", DoclingComponent.class);
        assertNotNull(component);

        // Configure with a short TTL
        component.getConfiguration().setAsyncTaskTtl(2000);
        component.getConfiguration().setUseDoclingServe(true);
        component.getConfiguration().setDoclingServeUrl("http://localhost:5001");

        context.start();

        // Add multiple task entries
        for (int i = 0; i < 5; i++) {
            String taskId = "test-task-" + i;
            AsyncTaskEntry entry = new AsyncTaskEntry(taskId, new java.util.concurrent.CompletableFuture<>());
            component.getPendingAsyncTasks().put(taskId, entry);
        }

        // Verify all tasks are present
        assertEquals(5, component.getPendingAsyncTasks().size());

        // Wait for TTL to expire and cleanup to run
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, component.getPendingAsyncTasks().size()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Empty route - we're testing component-level functionality
                from("direct:start")
                        .to("mock:result");
            }
        };
    }

}
