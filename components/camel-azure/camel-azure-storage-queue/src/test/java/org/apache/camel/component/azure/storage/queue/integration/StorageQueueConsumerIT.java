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
package org.apache.camel.component.azure.storage.queue.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class StorageQueueConsumerIT extends StorageQueueBase {

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    @BeforeAll
    public void setup() {
        serviceClient.getQueueClient(queueName).sendMessage("test-message-1");
        serviceClient.getQueueClient(queueName).sendMessage("test-message-2");
        serviceClient.getQueueClient(queueName).sendMessage("test-message-3");
    }

    @Test
    public void testPollingMessages() throws InterruptedException, IOException {
        result.expectedMessageCount(3);
        result.assertIsSatisfied();

        final List<Exchange> exchanges = result.getExchanges();

        result.message(0).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(0);
        result.message(1).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(1);
        result.message(2).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(2);
        result.expectedPropertyReceived(Exchange.BATCH_SIZE, 3);

        assertEquals("test-message-1", bodyToString(exchanges.get(0).getMessage().getBody()));
        assertEquals("test-message-2", bodyToString(exchanges.get(1).getMessage().getBody()));
        assertEquals("test-message-3", bodyToString(exchanges.get(2).getMessage().getBody()));
    }

    private String bodyToString(Object body) throws IOException {
        if (body == null || !(body instanceof InputStream)) {
            fail();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copy((InputStream) body, bos);
        return bos.toString(StandardCharsets.UTF_8);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-queue://cameldev/" + queueName + "?maxMessages=5")
                        .to(resultName);

            }
        };
    }
}
