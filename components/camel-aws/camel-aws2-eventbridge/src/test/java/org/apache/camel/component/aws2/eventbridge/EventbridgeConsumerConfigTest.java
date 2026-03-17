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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.Processor;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EventBridge consumer configuration parsing from endpoint URIs.
 */
public class EventbridgeConsumerConfigTest extends CamelTestSupport {

    @Test
    public void testConsumerConfigFromUri() throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(
                "aws2-eventbridge://default?accessKey=xxx&secretKey=yyy"
                                                                                      + "&ruleName=my-rule"
                                                                                      + "&queueUrl=https://sqs.us-east-1.amazonaws.com/123456789/my-queue"
                                                                                      + "&autoCreateQueue=false"
                                                                                      + "&deleteQueueOnShutdown=false"
                                                                                      + "&maxMessagesPerPoll=5"
                                                                                      + "&waitTimeSeconds=15"
                                                                                      + "&visibilityTimeout=45");

        EventbridgeConfiguration config = endpoint.getConfiguration();
        assertEquals("my-rule", config.getRuleName());
        assertEquals("https://sqs.us-east-1.amazonaws.com/123456789/my-queue", config.getQueueUrl());
        assertFalse(config.isAutoCreateQueue());
        assertFalse(config.isDeleteQueueOnShutdown());
        assertEquals(5, config.getMaxMessagesPerPoll());
        assertEquals(15, config.getWaitTimeSeconds());
        assertEquals(45, config.getVisibilityTimeout());
    }

    @Test
    public void testConsumerConfigDefaults() throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        EventbridgeEndpoint endpoint
                = (EventbridgeEndpoint) component.createEndpoint("aws2-eventbridge://default?accessKey=xxx&secretKey=yyy");

        EventbridgeConfiguration config = endpoint.getConfiguration();
        assertNull(config.getRuleName());
        assertNull(config.getQueueUrl());
        assertTrue(config.isAutoCreateQueue());
        assertTrue(config.isDeleteQueueOnShutdown());
        assertEquals(10, config.getMaxMessagesPerPoll());
        assertEquals(20, config.getWaitTimeSeconds());
        assertEquals(30, config.getVisibilityTimeout());
    }

    @Test
    public void testRuleNameRequiredValidation() throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        EventbridgeEndpoint endpoint
                = (EventbridgeEndpoint) component.createEndpoint("aws2-eventbridge://default?accessKey=xxx&secretKey=yyy");

        Processor noopProcessor = exchange -> {
        };
        IllegalArgumentException exception
                = assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(noopProcessor));
        assertTrue(exception.getMessage().contains("ruleName"));
    }
}
