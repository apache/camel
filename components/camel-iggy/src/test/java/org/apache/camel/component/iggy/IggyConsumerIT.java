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
package org.apache.camel.component.iggy;

import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Iggy 0.6.0+ requires io_uring which is not available on CI environments")
public class IggyConsumerIT extends IggyTestBase {

    @Test
    public void consumeOneMessage() throws InterruptedException {
        String message = "Hello world";
        MockEndpoint mockEndpoint = contextExtension.getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        sendMessage(message);

        mockEndpoint.assertIsSatisfied();

        Assertions.assertEquals(message, mockEndpoint.getExchanges().get(0).getMessage().getBody());
    }

    @Test
    public void consumeMultipleMessages() throws InterruptedException {
        int messages = 200;
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(messages);

        for (int i = 0; i < messages; i++) {
            sendMessage("First batch " + i);
        }

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();

        int nextBatch = 5;
        contextExtension.getMockEndpoint("mock:result").reset();
        contextExtension.getMockEndpoint("mock:result").expectedMessageCount(nextBatch);

        for (int i = 0; i < nextBatch; i++) {
            sendMessage("Next batch " + i);
        }

        contextExtension.getMockEndpoint("mock:result").assertIsSatisfied();
    }

    @Test
    public void checkHeaders() throws Exception {
        String message = "Hello world";
        MockEndpoint mockEndpoint = contextExtension.getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        sendMessage(message);

        mockEndpoint.assertIsSatisfied();

        Map<String, Object> headers = mockEndpoint.getExchanges().get(0).getMessage().getHeaders();
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_ID));
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_OFFSET));
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_ORIGIN_TIMESTAMP));
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_TIMESTAMP));
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_CHECKSUM));
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_LENGTH));
        Assertions.assertNotNull(headers.get(IggyConstants.MESSAGE_SIZE));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("iggy:%s?username=%s&password=%s&streamName=%s&host=%s&port=%d&consumerGroupName=%s",
                        TOPIC,
                        iggyService.username(),
                        iggyService.password(),
                        STREAM,
                        iggyService.host(),
                        iggyService.port(),
                        CONSUMER_GROUP)
                        .to("mock:result");
            }
        };
    }
}
