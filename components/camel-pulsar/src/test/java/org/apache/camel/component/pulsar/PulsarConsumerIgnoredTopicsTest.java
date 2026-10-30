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
package org.apache.camel.component.pulsar;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A dead letter or retry letter topic that the consumer cannot use (no maxRedeliverCount, or a retry letter topic
 * without retry mode) is ignored: the consumer logs a WARN when it starts. In retry mode such a configuration failed at
 * start before (a NullPointerException), so the user noticed it.
 */
class PulsarConsumerIgnoredTopicsTest extends CamelTestSupport {

    private static final String TOPIC = "persistent://public/default/orders";
    private static final String URI = "pulsar:" + TOPIC + "?subscriptionName=subs&messageListener=true";

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

    @BeforeEach
    void addAppender() {
        warningsAppender.start();
        logger().addAppender(warningsAppender);
    }

    @AfterEach
    void removeAppender() {
        logger().removeAppender(warningsAppender);
        warningsAppender.stop();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ConsumerBuilder<byte[]> builder = mock(ConsumerBuilder.class, RETURNS_SELF);
        when(builder.subscribe()).thenReturn(mock(Consumer.class));
        PulsarClient pulsarClient = mock(PulsarClient.class);
        when(pulsarClient.newConsumer()).thenReturn(builder);
        PulsarComponent component = new PulsarComponent(context);
        component.setPulsarClient(pulsarClient);
        context.addComponent("pulsar", component);
        return context;
    }

    @Test
    void retryModeWithTopicsButNoMaxRedeliverCount() throws Exception {
        start("&enableRetry=true&retryLetterTopic=my-retry&deadLetterTopic=my-dlq");

        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(warnings.get(0).startsWith("The retryLetterTopic and deadLetterTopic options of the consumer of "
                                              + TOPIC + " are ignored as maxRedeliverCount is not set."),
                warnings.get(0));
    }

    @Test
    void retryModeWithOneTopicButNoMaxRedeliverCount() throws Exception {
        start("&enableRetry=true&deadLetterTopic=my-dlq");

        assertEquals(1, warnings.size(), warnings::toString);
    }

    @Test
    void deadLetterTopicWithoutMaxRedeliverCount() throws Exception {
        start("&deadLetterTopic=my-dlq");

        assertEquals(List.of("The deadLetterTopic option of the consumer of " + TOPIC
                             + " is ignored as maxRedeliverCount is not set. Without a dead letter policy the Pulsar"
                             + " client sends no message to a dead letter topic. Set maxRedeliverCount to use the"
                             + " configured topic."),
                warnings);
    }

    @Test
    void retryLetterTopicWithoutRetryMode() throws Exception {
        start("&maxRedeliverCount=5&retryLetterTopic=my-retry");

        assertEquals(List.of("The retryLetterTopic option of the consumer of " + TOPIC
                             + " is ignored as enableRetry is false."),
                warnings);
    }

    @Test
    void topicsWithMaxRedeliverCountAreUsed() throws Exception {
        // control
        start("&enableRetry=true&maxRedeliverCount=3&retryLetterTopic=my-retry&deadLetterTopic=my-dlq");
        start("&maxRedeliverCount=5&deadLetterTopic=my-dlq");

        assertEquals(List.of(), warnings);
    }

    @Test
    void noTopics() throws Exception {
        // control
        start("&enableRetry=true");
        start("");

        assertEquals(List.of(), warnings);
    }

    private void start(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(URI + options).to("mock:result");
            }
        });
    }

    private static Logger logger() {
        return (Logger) LogManager.getLogger(PulsarConsumer.class);
    }
}
