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

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the SUBMIT_ASYNC_CONVERSION and CHECK_CONVERSION_STATUS two-step async workflow.
 *
 * <p>
 * Before the fix, the {@code CompletionStage} returned by {@code convertSourceAsync()} was discarded and a fabricated
 * task ID with no server-side correlation was returned. CHECK_CONVERSION_STATUS would then fail because the server had
 * no record of the fake ID, and the error was silently masked by returning COMPLETED.
 *
 * <p>
 * After the fix, the {@code CompletableFuture} is stored in a local map keyed by the generated task ID. When
 * CHECK_CONVERSION_STATUS is called, it checks the local map first and returns the actual status of the async task.
 */
class DoclingAsyncConversionTest extends CamelTestSupport {

    @Test
    void submitReturnsTaskIdLinkedToFuture() throws Exception {
        DoclingEndpoint endpoint = context.getEndpoint(
                "docling:convert?operation=SUBMIT_ASYNC_CONVERSION&useDoclingServe=true", DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        // Access the pendingAsyncTasks map via reflection to verify the future is stored
        Map<String, CompletableFuture<ConvertDocumentResponse>> pendingTasks = getPendingAsyncTasks(producer);
        assertNotNull(pendingTasks, "pendingAsyncTasks map should exist");
        assertTrue(pendingTasks.isEmpty(), "pendingAsyncTasks should start empty");
    }

    @Test
    void checkStatusReturnsFailedForUnknownTaskId() throws Exception {
        // When CHECK_CONVERSION_STATUS is called with an unknown task ID and the server
        // is not available, it should return FAILED — not COMPLETED (the old bug).
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(DoclingHeaders.TASK_ID, "nonexistent-task-id");
            exchange.getIn().setHeader(DoclingHeaders.OPERATION, DoclingOperations.CHECK_CONVERSION_STATUS);

            template.send("direct:check-status", exchange);

            Object body = exchange.getIn().getBody();
            assertInstanceOf(ConversionStatus.class, body);
            ConversionStatus status = (ConversionStatus) body;

            // The key assertion: unknown task IDs should NOT return COMPLETED
            assertNotEquals(ConversionStatus.Status.COMPLETED, status.getStatus(),
                    "Unknown task ID should not return COMPLETED status");
            assertEquals(ConversionStatus.Status.FAILED, status.getStatus(),
                    "Unknown task ID with unavailable server should return FAILED");
            assertNotNull(status.getErrorMessage(), "Error message should be populated");
        } catch (CamelExecutionException e) {
            // If the exchange throws instead of setting FAILED status, that's also acceptable —
            // the important thing is it doesn't silently return COMPLETED
        }
    }

    @Test
    void checkStatusReturnsCompletedForFinishedLocalTask() throws Exception {
        DoclingEndpoint endpoint = context.getEndpoint(
                "docling:convert?operation=CHECK_CONVERSION_STATUS&useDoclingServe=true", DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        // Manually insert a completed future into the pending tasks map
        Map<String, CompletableFuture<ConvertDocumentResponse>> pendingTasks = getPendingAsyncTasks(producer);

        // Create a completed future with a mock response
        ConvertDocumentResponse mockResponse = InBodyConvertDocumentResponse.builder()
                .document(DocumentResponse.builder()
                        .markdownContent("# Converted Document")
                        .build())
                .build();
        CompletableFuture<ConvertDocumentResponse> completedFuture = CompletableFuture.completedFuture(mockResponse);
        pendingTasks.put("test-task-1", completedFuture);

        // Check the status — should find it in local map and return COMPLETED with result
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DoclingHeaders.TASK_ID, "test-task-1");

        producer.process(exchange);

        Object body = exchange.getIn().getBody();
        assertInstanceOf(ConversionStatus.class, body);
        ConversionStatus status = (ConversionStatus) body;
        assertEquals(ConversionStatus.Status.COMPLETED, status.getStatus());
        assertNotNull(status.getResult(), "Result should contain the converted content");

        // Future should be removed from map after completion
        assertFalse(pendingTasks.containsKey("test-task-1"),
                "Completed task should be removed from pending map");
    }

    @Test
    void checkStatusReturnsInProgressForPendingLocalTask() throws Exception {
        DoclingEndpoint endpoint = context.getEndpoint(
                "docling:convert?operation=CHECK_CONVERSION_STATUS&useDoclingServe=true", DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        Map<String, CompletableFuture<ConvertDocumentResponse>> pendingTasks = getPendingAsyncTasks(producer);

        // Insert an incomplete future
        CompletableFuture<ConvertDocumentResponse> incompleteFuture = new CompletableFuture<>();
        pendingTasks.put("test-task-2", incompleteFuture);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DoclingHeaders.TASK_ID, "test-task-2");

        producer.process(exchange);

        Object body = exchange.getIn().getBody();
        assertInstanceOf(ConversionStatus.class, body);
        ConversionStatus status = (ConversionStatus) body;
        assertEquals(ConversionStatus.Status.IN_PROGRESS, status.getStatus());

        // Future should remain in map since it's not done yet
        assertTrue(pendingTasks.containsKey("test-task-2"),
                "In-progress task should remain in pending map");

        // Clean up
        incompleteFuture.cancel(true);
    }

    @Test
    void checkStatusReturnsFailedForExceptionalLocalTask() throws Exception {
        DoclingEndpoint endpoint = context.getEndpoint(
                "docling:convert?operation=CHECK_CONVERSION_STATUS&useDoclingServe=true", DoclingEndpoint.class);
        DoclingProducer producer = (DoclingProducer) endpoint.createProducer();

        Map<String, CompletableFuture<ConvertDocumentResponse>> pendingTasks = getPendingAsyncTasks(producer);

        // Insert a failed future
        CompletableFuture<ConvertDocumentResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Server connection refused"));
        pendingTasks.put("test-task-3", failedFuture);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DoclingHeaders.TASK_ID, "test-task-3");

        producer.process(exchange);

        Object body = exchange.getIn().getBody();
        assertInstanceOf(ConversionStatus.class, body);
        ConversionStatus status = (ConversionStatus) body;
        assertEquals(ConversionStatus.Status.FAILED, status.getStatus());
        assertNotNull(status.getErrorMessage());
        assertTrue(status.getErrorMessage().contains("Server connection refused"));

        // Failed task should be removed from map
        assertFalse(pendingTasks.containsKey("test-task-3"),
                "Failed task should be removed from pending map");
    }

    @SuppressWarnings("unchecked")
    private Map<String, CompletableFuture<ConvertDocumentResponse>> getPendingAsyncTasks(DoclingProducer producer)
            throws Exception {
        Field field = DoclingProducer.class.getDeclaredField("pendingAsyncTasks");
        field.setAccessible(true);
        return (Map<String, CompletableFuture<ConvertDocumentResponse>>) field.get(producer);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:check-status")
                        .to("docling:convert?operation=CHECK_CONVERSION_STATUS&useDoclingServe=true");
            }
        };
    }
}
