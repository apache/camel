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
package org.apache.camel.component.pulsar.utils.consumers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.component.pulsar.PulsarConfiguration;
import org.apache.camel.component.pulsar.PulsarConsumer;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.ConsumerBuilderImpl;
import org.apache.pulsar.client.impl.conf.ConsumerConfigurationData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dead letter policy that the consumer passes to the Pulsar client. The builder is the real one of the Pulsar
 * client (it does not connect before subscribe): at subscribe, in retry mode, the client replaces only a blank dead
 * letter or retry letter topic of the policy by its default ({@code <topic>-<subscription>-DLQ} / {@code -RETRY}).
 */
class CommonCreationStrategyImplTest {

    private static final String TOPIC = "persistent://public/default/orders";

    private final PulsarEndpoint endpoint = mock(PulsarEndpoint.class);
    private final PulsarConfiguration configuration = new PulsarConfiguration();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender warningsAppender
            = new AbstractAppender("warnings", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };
    private static PulsarClient client;

    @BeforeAll
    static void createClient() throws Exception {
        client = PulsarClient.builder().serviceUrl("pulsar://localhost:6650").build();
    }

    @AfterAll
    static void closeClient() throws Exception {
        client.close();
    }

    @BeforeEach
    void setUp() {
        configuration.setSubscriptionName("subs");
        configuration.setMessageListener(false);
        when(endpoint.getPulsarClient()).thenReturn(client);
        when(endpoint.getPulsarConfiguration()).thenReturn(configuration);
        when(endpoint.getUri()).thenReturn(TOPIC);
        warningsAppender.start();
        logger().addAppender(warningsAppender);
    }

    @AfterEach
    void removeAppender() {
        logger().removeAppender(warningsAppender);
        warningsAppender.stop();
    }

    @Test
    void retryModeUsesTheConfiguredDeadLetterTopic() {
        configuration.setEnableRetry(true);
        configuration.setMaxRedeliverCount(3);
        configuration.setRetryLetterTopic("persistent://public/default/my-retry");
        configuration.setDeadLetterTopic("persistent://public/default/my-dlq");

        ConsumerConfigurationData<byte[]> conf = build();

        assertTrue(conf.isRetryEnable());
        DeadLetterPolicy policy = conf.getDeadLetterPolicy();
        assertEquals(3, policy.getMaxRedeliverCount());
        assertEquals("persistent://public/default/my-retry", policy.getRetryLetterTopic());
        assertEquals("persistent://public/default/my-dlq", policy.getDeadLetterTopic(),
                "the dead letter topic must be passed to the Pulsar client in retry mode");
        assertEquals(List.of(), warnings);
    }

    @Test
    void retryModeWithoutTopicsLeavesTheDefaultsToThePulsarClient() {
        configuration.setEnableRetry(true);
        configuration.setMaxRedeliverCount(3);

        DeadLetterPolicy policy = build().getDeadLetterPolicy();

        assertEquals(3, policy.getMaxRedeliverCount());
        assertNull(policy.getRetryLetterTopic());
        assertNull(policy.getDeadLetterTopic());
    }

    @Test
    void retryModeWithoutMaxRedeliverCountLeavesThePolicyToThePulsarClient() {
        // as the maxRedeliverCount option documents, no dead letter policy is created: at subscribe the Pulsar client
        // then uses its own (16 redeliveries and the default retry letter and dead letter topics)
        configuration.setEnableRetry(true);

        ConsumerConfigurationData<byte[]> conf = build();

        assertTrue(conf.isRetryEnable());
        assertNull(conf.getDeadLetterPolicy());
        assertEquals(List.of(), warnings);
    }

    @Test
    void retryModeWithoutMaxRedeliverCountIgnoresTheTopicsAsWithoutRetryMode() {
        configuration.setEnableRetry(true);
        configuration.setRetryLetterTopic("persistent://public/default/my-retry");
        configuration.setDeadLetterTopic("persistent://public/default/my-dlq");

        ConsumerConfigurationData<byte[]> conf = build();

        assertTrue(conf.isRetryEnable());
        assertNull(conf.getDeadLetterPolicy());
        // before, this configuration failed at start, so the user noticed: now a WARN says the topics are ignored
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(warnings.get(0).startsWith("The retryLetterTopic and deadLetterTopic options of the consumer of "
                                              + TOPIC + " are ignored as maxRedeliverCount is not set."),
                warnings.get(0));
    }

    @Test
    void retryModeWithoutMaxRedeliverCountWarnsAlsoForOneTopic() {
        configuration.setEnableRetry(true);
        configuration.setDeadLetterTopic("persistent://public/default/my-dlq");

        assertNull(build().getDeadLetterPolicy());
        assertEquals(1, warnings.size(), warnings::toString);
    }

    @Test
    void deadLetterTopicModeIsUnchanged() {
        // control: without retry mode the dead letter topic was already passed
        configuration.setMaxRedeliverCount(5);
        configuration.setDeadLetterTopic("persistent://public/default/my-dlq");

        ConsumerConfigurationData<byte[]> conf = build();

        assertFalse(conf.isRetryEnable());
        assertEquals(5, conf.getDeadLetterPolicy().getMaxRedeliverCount());
        assertEquals("persistent://public/default/my-dlq", conf.getDeadLetterPolicy().getDeadLetterTopic());
        assertEquals(List.of(), warnings);
    }

    @Test
    void noMaxRedeliverCountWithoutRetryModeCreatesNoPolicy() {
        // control: as the maxRedeliverCount option documents
        configuration.setDeadLetterTopic("persistent://public/default/my-dlq");

        assertNull(build().getDeadLetterPolicy());
        assertEquals(List.of("The deadLetterTopic option of the consumer of " + TOPIC
                             + " is ignored as maxRedeliverCount is not set. Without a dead letter policy the Pulsar"
                             + " client sends no message to a dead letter topic. Set maxRedeliverCount to use the"
                             + " configured topic."),
                warnings);
    }

    @Test
    void retryLetterTopicWithoutRetryModeIsIgnoredWithAWarning() {
        configuration.setMaxRedeliverCount(5);
        configuration.setRetryLetterTopic("persistent://public/default/my-retry");

        assertEquals(5, build().getDeadLetterPolicy().getMaxRedeliverCount());
        assertEquals(List.of("The retryLetterTopic option of the consumer of " + TOPIC
                             + " is ignored as enableRetry is false."),
                warnings);
    }

    private static Logger logger() {
        return (Logger) LogManager.getLogger(CommonCreationStrategyImpl.class);
    }

    private ConsumerConfigurationData<byte[]> build() {
        ConsumerBuilder<byte[]> builder = CommonCreationStrategyImpl.getBuilder("consumer-1", endpoint,
                mock(PulsarConsumer.class));
        return ((ConsumerBuilderImpl<byte[]>) builder).getConf();
    }
}
