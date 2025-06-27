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
package org.apache.camel.component.aws2.sqs.integration;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SqsConsumerLocalstackIT extends Aws2SQSBaseTest {
    private static final int MAX_MESSAGES_PER_POLL = 50;
    private static final MessageSystemAttributeName SORT_ATTRIBUTE = MessageSystemAttributeName.SENT_TIMESTAMP;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private String queueUrl;

    @BeforeEach
    void setup() {
        queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(sharedNameGenerator.getName()).build())
                .queueUrl();
    }

    @Test
    void shouldPollMessagesFromAmazonSQS() {
        // given
        var messages = IntStream.range(0, MAX_MESSAGES_PER_POLL)
                .mapToObj(i -> SendMessageBatchRequestEntry.builder()
                        .id(Integer.toString(i))
                        .messageBody("MSG%s".formatted(i))
                        .build())
                .toList();

        // when
        sendAll(messages);

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(result.getReceivedExchanges()).hasSize(MAX_MESSAGES_PER_POLL);
            assertThat(result.getReceivedExchanges().stream().map(it -> it.getIn().getBody())).containsExactly(
                    messages.stream().map(SendMessageBatchRequestEntry::messageBody).toArray());
        });
    }

    private void sendAll(List<SendMessageBatchRequestEntry> messages) {
        int requestCount = (int) Math.ceil(messages.size() / 10D);
        IntStream.range(0, requestCount)
                .mapToObj(i -> messages.subList(i * 10, Math.min((i + 1) * 10, messages.size())))
                .forEach(entries -> {
                    sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
                            .queueUrl(queueUrl)
                            .entries(entries)
                            .build());
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final var sqsEndpointUri
                = "aws2-sqs://%s?autoCreateQueue=true&maxMessagesPerPoll=%s&attributeNames=All&sortAttributeName=%s"
                        .formatted(sharedNameGenerator.getName(), MAX_MESSAGES_PER_POLL, SORT_ATTRIBUTE);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(sqsEndpointUri).to("mock:result");
            }
        };
    }
}
