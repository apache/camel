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
package org.apache.camel.component.kafka.integration;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.support.KafkaConsumerResumeStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaConsumerWithResumeStrategyIT extends BaseEmbeddedKafkaTestSupport {
    private static final String TOPIC = "custom-resume";

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BindToRegistry("resumeStrategy")
    private TestKafkaConsumerResumeStrategy resumeStrategy;
    private CountDownLatch messagesLatch;

    private static class TestKafkaConsumerResumeStrategy implements KafkaConsumerResumeStrategy {
        private final CountDownLatch messagesLatch;
        private boolean resumeCalled;
        private boolean consumerIsNull = true;

        public TestKafkaConsumerResumeStrategy(CountDownLatch messagesLatch) {
            this.messagesLatch = messagesLatch;
        }

        @Override
        public void resume(Consumer<?, ?> consumer) {
            resumeCalled = true;

            if (consumer != null) {
                consumerIsNull = false;
            }

            messagesLatch.countDown();
        }

        public boolean isResumeCalled() {
            return resumeCalled;
        }

        public boolean isConsumerIsNull() {
            return consumerIsNull;
        }
    }

    @Override
    protected void doPreSetup() {
        messagesLatch = new CountDownLatch(1);
        resumeStrategy = new TestKafkaConsumerResumeStrategy(messagesLatch);
    }

    @Test
    @Timeout(value = 30)
    public void offsetGetStateMustHaveBeenCalledTwice() throws InterruptedException {
        assertTrue(messagesLatch.await(4, TimeUnit.SECONDS), "The resume was not called");

        assertTrue(resumeStrategy.isResumeCalled(),
                "The resume strategy should have been called when the partition was assigned");
        assertFalse(resumeStrategy.isConsumerIsNull(),
                "The consumer passed to the strategy should not be null");
    }

    @AfterEach
    public void after() {
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("kafka:" + TOPIC + "?groupId=" + TOPIC + "_GROUP&autoCommitIntervalMs=1000"
                     + "&autoOffsetReset=latest" + "&consumersCount=1"
                     + "&resumeStrategy=#resumeStrategy")
                             .routeId("resume-strategy-route")
                             .to("mock:result");
            }
        };
    }
}
