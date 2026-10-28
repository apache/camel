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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The targets of the EventBridge rule when consumers use a user-provided queue. EventBridge puts a copy of every
 * matching event on the queue of every target of the rule, so the rule must hold one target for the queue whatever the
 * number of consumers that share it, and whether a consumer stopped cleanly or not. The rule is a map of target id to
 * target ARN kept by the mocked EventBridge client, with the semantics of PutTargets (a target with the same id is
 * replaced) and RemoveTargets.
 */
class EventbridgeConsumerUserQueueTargetTest extends CamelTestSupport {

    private static final String USER_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789/user-queue";
    private static final String USER_QUEUE_ARN = "arn:aws:sqs:us-east-1:123456789:user-queue";
    private static final String AUTO_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789/camel-eventbridge-auto";
    private static final String AUTO_QUEUE_ARN = "arn:aws:sqs:us-east-1:123456789:camel-eventbridge-auto";

    private final Map<String, String> ruleTargets = new LinkedHashMap<>();
    private SqsClient sqsClient;
    private EventBridgeClient eventBridgeClient;

    @BeforeEach
    void setupClients() {
        sqsClient = mock(SqsClient.class);
        eventBridgeClient = mock(EventBridgeClient.class);
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenAnswer(inv -> {
            GetQueueAttributesRequest req = inv.getArgument(0);
            String arn = USER_QUEUE_URL.equals(req.queueUrl()) ? USER_QUEUE_ARN : AUTO_QUEUE_ARN;
            return GetQueueAttributesResponse.builder().attributes(Map.of(QueueAttributeName.QUEUE_ARN, arn)).build();
        });
        when(sqsClient.createQueue(any(CreateQueueRequest.class)))
                .thenReturn(CreateQueueResponse.builder().queueUrl(AUTO_QUEUE_URL).build());
        when(sqsClient.setQueueAttributes(any(SetQueueAttributesRequest.class)))
                .thenReturn(SetQueueAttributesResponse.builder().build());
        when(eventBridgeClient.putTargets(any(PutTargetsRequest.class))).thenAnswer(inv -> {
            PutTargetsRequest req = inv.getArgument(0);
            for (Target target : req.targets()) {
                ruleTargets.put(target.id(), target.arn());
            }
            return PutTargetsResponse.builder().failedEntryCount(0).build();
        });
        when(eventBridgeClient.removeTargets(any(RemoveTargetsRequest.class))).thenAnswer(inv -> {
            RemoveTargetsRequest req = inv.getArgument(0);
            req.ids().forEach(ruleTargets::remove);
            return RemoveTargetsResponse.builder().build();
        });
    }

    @Test
    void twoConsumersSharingAQueueAddOneTarget() throws Exception {
        EventbridgeConsumer first = createConsumer(USER_QUEUE_URL);
        EventbridgeConsumer second = createConsumer(USER_QUEUE_URL);
        first.start();
        second.start();

        // two targets for the queue would put every event on the queue twice
        assertEquals(1, targetsFor(USER_QUEUE_ARN), "targets of the rule: " + ruleTargets);

        second.stop();
        // the consumer that still runs keeps receiving the events of the rule
        assertEquals(1, targetsFor(USER_QUEUE_ARN), "targets of the rule: " + ruleTargets);
        first.stop();
    }

    @Test
    void restartAfterACrashAddsNoSecondTarget() throws Exception {
        // the first consumer is never stopped, as when the JVM is killed: its target is left on the rule
        EventbridgeConsumer crashed = createConsumer(USER_QUEUE_URL);
        crashed.start();
        EventbridgeConsumer restarted = createConsumer(USER_QUEUE_URL);
        restarted.start();

        assertEquals(1, targetsFor(USER_QUEUE_ARN), "targets of the rule: " + ruleTargets);
        restarted.stop();
    }

    @Test
    void userQueueKeepsCollectingEventsWhileTheConsumerIsStopped() throws Exception {
        EventbridgeConsumer consumer = createConsumer(USER_QUEUE_URL);
        consumer.start();
        consumer.stop();

        assertEquals(1, targetsFor(USER_QUEUE_ARN), "targets of the rule: " + ruleTargets);
    }

    @Test
    void autoCreatedQueueTargetIsStillRemovedOnStop() throws Exception {
        EventbridgeConsumer consumer = createConsumer(null);
        consumer.start();
        assertEquals(1, targetsFor(AUTO_QUEUE_ARN), "targets of the rule: " + ruleTargets);
        consumer.stop();

        assertTrue(ruleTargets.isEmpty(), "targets of the rule: " + ruleTargets);
    }

    private long targetsFor(String arn) {
        return ruleTargets.values().stream().filter(arn::equals).count();
    }

    private EventbridgeConsumer createConsumer(String queueUrl) throws Exception {
        EventbridgeComponent component = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        String uri = "aws2-eventbridge://default?accessKey=test&secretKey=test&ruleName=test-rule";
        if (queueUrl != null) {
            uri += "&queueUrl=" + queueUrl;
        }
        EventbridgeEndpoint endpoint = (EventbridgeEndpoint) component.createEndpoint(uri);
        endpoint.getConfiguration().setEventbridgeClient(eventBridgeClient);
        EventbridgeEndpoint spyEndpoint = Mockito.spy(endpoint);
        doReturn(sqsClient).when(spyEndpoint).getSqsClient();
        doReturn(eventBridgeClient).when(spyEndpoint).getEventbridgeClient();

        EventbridgeConsumer consumer = new EventbridgeConsumer(spyEndpoint, exchange -> {
        });
        consumer.setStartScheduler(false);
        return consumer;
    }
}
