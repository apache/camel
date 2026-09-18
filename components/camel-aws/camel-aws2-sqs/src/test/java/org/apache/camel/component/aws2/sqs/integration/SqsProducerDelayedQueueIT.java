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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SqsProducerDelayedQueueIT extends Aws2SQSBaseTest {

    @Test
    void delayedQueue() throws Exception {
        int delay = 20;
        String delayedQueueName = sharedNameGenerator.getName() + "_delayed";
        Instant start = Instant.now();

        // Create the delayed queue via autoCreateQueue
        template.requestBody(
                String.format(
                        "aws2-sqs://%s?autoCreateQueue=true&delayQueue=true&delaySeconds=%d&operation=listQueues",
                        delayedQueueName, delay),
                null, ListQueuesResponse.class);

        // Send a message to the delayed queue
        String msg = "sqs" + UUID.randomUUID().toString().replace("-", "");
        template.requestBody("aws2-sqs://" + delayedQueueName, msg, String.class);

        // Poll until the message is received; use visibilityTimeout (per-request) instead of
        // defaultVisibilityTimeout (queue-level attribute) to avoid overwriting the queue's
        // DELAY_SECONDS attribute via SetQueueAttributes.
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).atMost(120, TimeUnit.SECONDS)
                .until(() -> msg.equals(receiveMessageFromQueue(delayedQueueName)));

        assertThat(Duration.between(start, Instant.now()).getSeconds()).isGreaterThanOrEqualTo(delay);
    }

    private String receiveMessageFromQueue(String queueName) {
        // visibilityTimeout=0 sets the per-request visibility timeout in ReceiveMessage,
        // which does not call SetQueueAttributes and therefore does not disturb DELAY_SECONDS.
        return consumer.receiveBody(
                String.format("aws2-sqs://%s?deleteAfterRead=false&deleteIfFiltered=false&visibilityTimeout=0",
                        queueName),
                10000, String.class);
    }
}
