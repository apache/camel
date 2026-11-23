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
package org.apache.camel.component.aws2.eventbridge.localstack;

import java.util.List;
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws.common.AWSConfigs;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With deleteQueueOnShutdown=false, the queue that the consumer creates and its target stay on shutdown, so the queue
 * collects the events while the consumer is stopped, and the next start reuses it instead of creating another queue.
 */
@DisabledOnOs(architectures = { "s390x", "ppc64le" },
              disabledReason = "Needs EventBridge delivering to SQS, as Floci does; these architectures use LocalStack")
public class EventbridgeConsumerKeepQueueIT extends Aws2EventbridgeBase {

    private static final String RULE_NAME = "camel-keep-queue-rule";

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Override
    protected RouteBuilder createRouteBuilder() {
        // the rule must exist before the consumer starts
        try (EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient()) {
            ebClient.putRule(b -> b.name(RULE_NAME)
                    .eventBusName("default")
                    .eventPattern("{\"source\":[\"camel.keep\"]}"));
        }

        Properties props = service.getConnectionProperties();
        String uri = "aws2-eventbridge://default?ruleName=" + RULE_NAME
                     + "&deleteQueueOnShutdown=false"
                     + "&region=" + props.getProperty(AWSConfigs.REGION)
                     + "&accessKey=RAW(" + props.getProperty(AWSConfigs.ACCESS_KEY) + ")"
                     + "&secretKey=RAW(" + props.getProperty(AWSConfigs.SECRET_KEY) + ")"
                     + "&overrideEndpoint=true&uriEndpointOverride=" + props.getProperty(AWSConfigs.PROTOCOL) + "://"
                     + props.getProperty(AWSConfigs.AMAZON_AWS_HOST)
                     + "&waitTimeSeconds=1&delay=500";

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("keep").to("mock:result");
            }
        };
    }

    @Test
    public void restartReusesTheQueueAndGetsTheEventsSentWhileStopped() throws Exception {
        List<String> queuesAtFirstStart = queuesOfTheRule();
        assertEquals(1, queuesAtFirstStart.size(), "queues of the consumer: " + queuesAtFirstStart);

        context.getRouteController().stopRoute("keep");

        result.expectedMessageCount(1);
        result.message(0).body().contains("Sent while stopped");
        putEvent("Sent while stopped");

        context.getRouteController().startRoute("keep");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(queuesAtFirstStart, queuesOfTheRule());
        try (EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient()) {
            assertEquals(1, ebClient.listTargetsByRule(b -> b.rule(RULE_NAME).eventBusName("default")).targets().size());
        }
    }

    private static List<String> queuesOfTheRule() {
        try (SqsClient sqsClient = AWSSDKClientUtils.newSQSClient()) {
            return sqsClient.listQueues(b -> b.queueNamePrefix("camel-eventbridge-" + RULE_NAME)).queueUrls();
        }
    }

    private static void putEvent(String message) {
        try (EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient()) {
            PutEventsResponse response = ebClient.putEvents(b -> b.entries(PutEventsRequestEntry.builder()
                    .eventBusName("default")
                    .source("camel.keep")
                    .detailType("CamelTestEvent")
                    .detail("{\"message\":\"" + message + "\"}")
                    .build()));
            assertEquals(0, response.failedEntryCount());
        }
    }
}
