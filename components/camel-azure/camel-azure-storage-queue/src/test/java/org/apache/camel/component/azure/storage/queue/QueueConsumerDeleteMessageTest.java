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
package org.apache.camel.component.azure.storage.queue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.BinaryData;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueErrorCode;
import com.azure.storage.queue.models.QueueMessageItem;
import com.azure.storage.queue.models.QueueStorageException;
import com.azure.storage.queue.models.SendMessageResult;
import com.azure.storage.queue.models.UpdateMessageResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer deletes the message it received once the exchange is done, whatever the route did with the message id
 * and pop receipt headers in the meantime.
 */
class QueueConsumerDeleteMessageTest extends CamelTestSupport {

    private static final String SOURCE_URI
            = "azure-storage-queue://account/source?serviceClient=#serviceClient&delay=50&initialDelay=0";

    @BindToRegistry("serviceClient")
    private final QueueServiceClient serviceClient = mock(QueueServiceClient.class);

    private final FakeQueue source = new FakeQueue("source", "a");
    private final FakeQueue target = new FakeQueue("target", "b");

    @Override
    protected void doPreSetup() throws Exception {
        when(serviceClient.getQueueClient("source")).thenReturn(source.client);
        when(serviceClient.getQueueClient("target")).thenReturn(target.client);
        source.add("hello");
    }

    @Test
    void forwardedMessageIsDeletedFromTheSourceQueue() throws Exception {
        // the producer sets the message id and pop receipt headers to the ones of the message it sent to the target
        context.addRoutes(route(from -> from.to("azure-storage-queue://account/target?serviceClient=#serviceClient")));

        verify(source.client, timeout(5000)).deleteMessageWithResponse(eq("a-1"), eq("a-1-receipt-1"), any(), any());
        // and it is the only delete
        verify(source.client).deleteMessageWithResponse(any(), any(), any(), any());
        assertEquals(List.of("b-1"), List.copyOf(target.messages.keySet()));
    }

    @Test
    void messageIsDeletedWhenTheRouteRemovesTheCamelHeaders() throws Exception {
        context.addRoutes(route(from -> from.removeHeaders("Camel*")));

        verify(source.client, timeout(5000)).deleteMessageWithResponse(eq("a-1"), eq("a-1-receipt-1"), any(), any());
        // and it is the only delete
        verify(source.client).deleteMessageWithResponse(any(), any(), any(), any());
    }

    @Test
    void updatedMessageIsDeletedWithItsNewPopReceipt() throws Exception {
        // control, which passes without the fix too: updating the message gives it a new pop receipt, which the
        // producer sets in the header, and only the new receipt deletes the message
        context.addRoutes(route(from -> from.to(
                "azure-storage-queue://account/source?serviceClient=#serviceClient&operation=updateMessage"
                                                + "&visibilityTimeout=60s")));

        verify(source.client, timeout(5000)).deleteMessageWithResponse(eq("a-1"), eq("a-1-receipt-2"), any(), any());
        // and it is the only delete
        verify(source.client).deleteMessageWithResponse(any(), any(), any(), any());
    }

    private RouteBuilder route(Consumer<RouteDefinition> steps) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                steps.accept(from(SOURCE_URI));
            }
        };
    }

    /**
     * A queue that answers like the Azure Queue Storage service (REST API reference of Put Message, Get Messages,
     * Update Message and Delete Message):
     * <ul>
     * <li>Get Messages hides the message for the visibility timeout and gives it a new pop receipt;</li>
     * <li>Put Message returns the id and the pop receipt of the message it added;</li>
     * <li>Update Message returns a new pop receipt, and the one the message had before no longer works;</li>
     * <li>Delete Message fails with 404 MessageNotFound when the queue has no message with that id, and with 400
     * PopReceiptMismatch when the pop receipt is not the current one of the message. The SDK throws both as a
     * {@link QueueStorageException} ({@code QueueClient.deleteMessageWithResponse}).</li>
     * </ul>
     */
    private static final class FakeQueue {

        private final QueueClient client = mock(QueueClient.class);
        private final Map<String, String> messages = new ConcurrentHashMap<>();
        private final Map<String, String> receipts = new ConcurrentHashMap<>();
        private final String name;
        private final String idPrefix;
        private int sent;

        private FakeQueue(String name, String idPrefix) {
            this.name = name;
            this.idPrefix = idPrefix;
            when(client.receiveMessages(any(), any(), any(), any())).thenAnswer(invocation -> receive());
            when(client.sendMessageWithResponse(anyString(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        String id = add(invocation.getArgument(0));
                        SendMessageResult result = new SendMessageResult().setMessageId(id)
                                .setPopReceipt(receipts.get(id));
                        return new SimpleResponse<>(null, 201, new HttpHeaders(), result);
                    });
            when(client.updateMessageWithResponse(anyString(), anyString(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        String id = invocation.getArgument(0);
                        checkReceipt(id, invocation.getArgument(1));
                        String receipt = nextReceipt(id);
                        UpdateMessageResult result = new UpdateMessageResult(receipt, OffsetDateTime.now());
                        return new SimpleResponse<>(null, 204, new HttpHeaders(), result);
                    });
            when(client.deleteMessageWithResponse(any(), any(), any(), any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                checkReceipt(id, invocation.getArgument(1));
                messages.remove(id);
                receipts.remove(id);
                return new SimpleResponse<>(null, 204, new HttpHeaders(), null);
            });
        }

        private synchronized String add(String text) {
            String id = idPrefix + "-" + ++sent;
            messages.put(id, text);
            receipts.put(id, id + "-receipt-0");
            return id;
        }

        private synchronized PagedIterable<QueueMessageItem> receive() {
            PagedIterable<QueueMessageItem> page = mock();
            // the message stays invisible until the test ends, so it is received once
            List<QueueMessageItem> items = messages.entrySet().stream()
                    .filter(e -> receipts.get(e.getKey()).endsWith("-receipt-0"))
                    .map(e -> new QueueMessageItem().setMessageId(e.getKey()).setPopReceipt(nextReceipt(e.getKey()))
                            .setBody(BinaryData.fromString(e.getValue())))
                    .toList();
            when(page.stream()).thenAnswer(invocation -> items.stream());
            return page;
        }

        private String nextReceipt(String id) {
            String current = receipts.get(id);
            int number = Integer.parseInt(current.substring(current.lastIndexOf('-') + 1));
            String next = id + "-receipt-" + (number + 1);
            receipts.put(id, next);
            return next;
        }

        private void checkReceipt(String id, String popReceipt) {
            if (!messages.containsKey(id)) {
                throw error(404, QueueErrorCode.MESSAGE_NOT_FOUND, "The specified message does not exist in " + name);
            }
            if (!receipts.get(id).equals(popReceipt)) {
                throw error(400, QueueErrorCode.POP_RECEIPT_MISMATCH,
                        "The specified pop receipt did not match the pop receipt for a dequeued message");
            }
        }

        private static QueueStorageException error(int status, QueueErrorCode code, String message) {
            HttpResponse response = mock(HttpResponse.class);
            when(response.getStatusCode()).thenReturn(status);
            when(response.getHeaders())
                    .thenReturn(new HttpHeaders().set(HttpHeaderName.fromString("x-ms-error-code"), code.toString()));
            return new QueueStorageException(message, response, null);
        }
    }
}
