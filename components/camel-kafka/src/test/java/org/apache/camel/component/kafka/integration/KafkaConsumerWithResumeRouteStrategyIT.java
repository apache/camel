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
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.support.KafkaConsumerResumeAdapter;
import org.apache.camel.component.kafka.consumer.support.KafkaResumable;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.support.resume.Resumables;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaConsumerWithResumeRouteStrategyIT extends BaseEmbeddedKafkaTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerWithResumeRouteStrategyIT.class);
    private static final String TOPIC = "resumable-route-tp";
    private static final int RANDOM_VALUE = ThreadLocalRandom.current().nextInt(1, 1000);

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BindToRegistry("resumeStrategy")
    private TestUpdateStrategy resumeStrategy;
    private CountDownLatch messagesLatch;

    private static class TestUpdateStrategy extends TransientResumeStrategy {
        private final CountDownLatch messagesLatch;
        private boolean startCalled;
        private boolean offsetNull = true;
        private boolean offsetAddressableNull = true;
        private boolean offsetAddressableEmpty = true;
        private boolean offsetValueNull = true;
        private boolean offsetValueEmpty = true;
        private int lastOffset;

        public TestUpdateStrategy(ResumeAdapter resumeAdapter, CountDownLatch messagesLatch) {
            super(resumeAdapter);

            this.messagesLatch = messagesLatch;
        }

        @Override
        public void start() {
            LOG.warn("Start was called");
            startCalled = true;
        }

        @Override
        public void init() {
            LOG.warn("Init was called");
        }

        @Override
        public void updateLastOffset(Resumable offset) {
            try {
                if (offset != null) {
                    offsetNull = false;

                    OffsetKey<?> addressable = offset.getOffsetKey();
                    if (addressable != null) {
                        offsetAddressableNull = false;
                        offsetAddressableEmpty = addressable.getValue() == null;

                    }

                    Offset<?> offsetValue = offset.getLastOffset();
                    if (offsetValue != null) {
                        offsetValueNull = false;

                        if (offsetValue.getValue() != null) {
                            offsetValueEmpty = false;
                            lastOffset = (int) offsetValue.getValue();
                        }
                    }
                }
            } finally {
                messagesLatch.countDown();
            }
        }

        public boolean isOffsetNull() {
            return offsetNull;
        }

        public boolean isOffsetAddressableNull() {
            return offsetAddressableNull;
        }

        public boolean isOffsetValueNull() {
            return offsetValueNull;
        }

        public boolean isOffsetAddressableEmpty() {
            return offsetAddressableEmpty;
        }

        public boolean isOffsetValueEmpty() {
            return offsetValueEmpty;
        }

        public boolean isStartCalled() {
            return startCalled;
        }
    }

    private static class TestKafkaConsumerResumeAdapter implements KafkaConsumerResumeAdapter {
        private boolean resumeCalled;
        private boolean consumerIsNull = true;

        @Override
        public void setConsumer(Consumer<?, ?> consumer) {
            if (consumer != null) {
                consumerIsNull = false;
            }
        }

        @Override
        public void setKafkaResumable(KafkaResumable kafkaResumable) {

        }

        @Override
        public void resume() {
            resumeCalled = true;
        }

        public boolean isResumeCalled() {
            return resumeCalled;
        }

        public boolean isConsumerIsNull() {
            return consumerIsNull;
        }
    }

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        KafkaProducer<Object, Object> producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);

        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>(TOPIC, String.valueOf(i)));
        }
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        messagesLatch = new CountDownLatch(1);
        resumeStrategy = new TestUpdateStrategy(new TestKafkaConsumerResumeAdapter(), messagesLatch);
    }

    @Test
    //    @Timeout(value = 30)
    public void testOffsetIsBeingChecked() throws InterruptedException {
        assertTrue(messagesLatch.await(100, TimeUnit.SECONDS), "The resume was not called");

        final TestKafkaConsumerResumeAdapter adapter = resumeStrategy.getAdapter(TestKafkaConsumerResumeAdapter.class);
        assertNotNull(adapter, "The adapter should not be null");
        assertTrue(adapter.isResumeCalled(),
                "The resume strategy should have been called when the partition was assigned");
        assertFalse(adapter.isConsumerIsNull(),
                "The consumer passed to the strategy should not be null");
        assertTrue(resumeStrategy.isStartCalled(),
                "The resume strategy should have been started");
        assertFalse(resumeStrategy.isOffsetNull(),
                "The offset should not be null");
        assertFalse(resumeStrategy.isOffsetAddressableNull(),
                "The offset addressable should not be null");
        assertFalse(resumeStrategy.isOffsetAddressableEmpty(),
                "The offset addressable should not be empty");
        assertFalse(resumeStrategy.isOffsetValueNull(),
                "The offset value should not be null");
        assertFalse(resumeStrategy.isOffsetValueEmpty(),
                "The offset value should not be empty");
        assertEquals(RANDOM_VALUE, resumeStrategy.lastOffset, "the offsets don't match");
    }

    @AfterEach
    public void after() {
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?groupId=" + TOPIC + "_GROUP&autoCommitIntervalMs=1000"
                     + "&autoOffsetReset=earliest&consumersCount=1")
                             .resumable().resumeStrategy("resumeStrategy", "DEBUG")
                             .routeId("resume-strategy-route")
                             .setHeader(Exchange.OFFSET, constant(Resumables.of("key", RANDOM_VALUE)))
                             // Note: this is for manually testing the ResumableCompletion onFailure exception logging. Uncomment it for testing it
                             // .process(e -> e.setException(new RuntimeCamelException("Mock error in test")))
                             .to("mock:result");
            }
        };
    }
}
