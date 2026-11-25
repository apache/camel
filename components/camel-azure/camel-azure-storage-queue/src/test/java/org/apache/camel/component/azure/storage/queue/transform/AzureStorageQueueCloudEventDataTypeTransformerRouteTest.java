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
package org.apache.camel.component.azure.storage.queue.transform;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The CloudEvent source of the messages of the azure-storage-queue consumer, when the transformer runs in another
 * route, as with the output data type of a Kamelet: that route gets a copy of the exchange with the headers of the
 * consumer, and its own from endpoint.
 */
class AzureStorageQueueCloudEventDataTypeTransformerRouteTest extends CamelTestSupport {

    @BindToRegistry("serviceClient")
    private final QueueServiceClient serviceClient = mock(QueueServiceClient.class);

    private final QueueClient queueClient = mock(QueueClient.class);

    @Override
    protected void doPreSetup() throws Exception {
        when(serviceClient.getQueueClient("orders")).thenReturn(queueClient);
        AtomicBoolean received = new AtomicBoolean();
        when(queueClient.receiveMessages(any(), any(), any(), any())).thenAnswer(invocation -> {
            // two messages on the first poll, and none after
            List<QueueMessageItem> items = received.getAndSet(true)
                    ? List.of()
                    : List.of(item("1", "Hello"), item("2", "World"));
            PagedIterable<QueueMessageItem> page = mock();
            when(page.stream()).thenAnswer(i -> items.stream());
            return page;
        });
    }

    private static QueueMessageItem item(String id, String text) {
        return new QueueMessageItem().setMessageId(id).setPopReceipt("receipt-" + id).setBody(BinaryData.fromString(text));
    }

    @Test
    void theSourceIsTheQueueOfTheConsumer() throws Exception {
        // CAMEL-27101: the source was built from the message id, so every event had a source of its own
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello", "World");
        result.allMessages().header(QueueConstants.CONSUMED_QUEUE_NAME).isEqualTo("orders");
        result.allMessages().header(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).isEqualTo("azure.storage.queue.orders");
        // the queue name header of the producer is not set, so a producer in the route is not sent to this queue
        result.allMessages().header(QueueConstants.QUEUE_NAME).isNull();

        MockEndpoint.assertIsSatisfied(context);

        Exchange first = result.getReceivedExchanges().get(0);
        Exchange second = result.getReceivedExchanges().get(1);
        assertEquals("1", first.getMessage().getHeader(QueueConstants.MESSAGE_ID));
        // the id of the events is still of each message
        assertNotEquals(first.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_ID),
                second.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands a copy of the exchange to the next route, as the sink of a Kamelet does
                from("azure-storage-queue://account/orders?serviceClient=#serviceClient&delay=50&initialDelay=0")
                        .to("seda:next");

                from("seda:next")
                        .transformDataType("azure-storage-queue:application-cloudevents")
                        .to("mock:result");
            }
        };
    }
}
