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
package org.apache.camel.component.azure.storage.queue.operations;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.queue.models.UpdateMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class QueueOperationsTest extends CamelTestSupport {

    private QueueConfiguration configuration;

    @Mock
    private QueueClientWrapper client;

    @BeforeEach
    public void setup() {
        configuration = new QueueConfiguration();
        configuration.setAccountName("test");
    }

    @Test
    public void testDeleteMessage() {
        // mocking
        final HttpHeaders httpHeaders = new HttpHeaders().set("x-test-header", "123");
        when(client.deleteMessage(any(), any(), any())).thenReturn(new ResponseBase<>(null, 200, httpHeaders, null, null));

        final QueueOperations operations = new QueueOperations(configuration, client);
        final Exchange exchange = new DefaultExchange(context);

        // test if we have nothing set on exchange
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));

        exchange.getIn().removeHeader(QueueConstants.MESSAGE_ID);
        exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, "12");
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        final QueueOperationResponse response = operations.deleteMessage(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
    }

    @Test
    public void testUpdateMessage() {
        //mocking
        final HttpHeaders httpHeaders = new HttpHeaders().set("x-test-header", "123");
        final UpdateMessageResult result = new UpdateMessageResult("12", OffsetDateTime.now());
        when(client.updateMessage(any(), any(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(null, 200, httpHeaders, result, null));

        final QueueOperations operations = new QueueOperations(configuration, client);
        final Exchange exchange = new DefaultExchange(context);

        // test if we have nothing set on exchange
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().removeHeader(QueueConstants.MESSAGE_ID);
        exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, "12");
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, "1");
        assertThrows(IllegalArgumentException.class, () -> operations.updateMessage(exchange));

        exchange.getIn().setHeader(QueueConstants.VISIBILITY_TIMEOUT, Duration.ofMillis(10));

        final QueueOperationResponse response = operations.updateMessage(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders());
    }
}
