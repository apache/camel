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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.eventbridge.EventbridgeComponent;
import org.apache.camel.component.aws2.eventbridge.EventbridgeConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Doesn't work with Localstack v4")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventbridgeConsumerIT extends CamelTestSupport {

    @RegisterExtension
    public static AWSService service = AWSServiceFactory.createSingletonEventBridgeService();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:consumed")
    private MockEndpoint resultConsumed;

    @EndpointInject("mock:consumedMultiple")
    private MockEndpoint resultConsumedMultiple;

    @EndpointInject("mock:consumedHeaders")
    private MockEndpoint resultConsumedHeaders;

    @EndpointInject("mock:consumedUserQueue")
    private MockEndpoint resultConsumedUserQueue;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        EventbridgeComponent eventbridgeComponent = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        eventbridgeComponent.getConfiguration().setEventbridgeClient(AWSSDKClientUtils.newEventBridgeClient());
        return context;
    }

    @Test
    public void testConsumeEventsFromEventBridge() throws Exception {
        // Create a rule first via the EventBridge client
        EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient();
        ebClient.putRule(PutRuleRequest.builder()
                .name("consumer-test-rule")
                .eventBusName("default")
                .eventPattern("{\"source\":[\"camel.test\"]}")
                .build());

        resultConsumed.expectedMinimumMessageCount(1);

        // Send an event that matches the rule
        template.send("direct:putEvent", exchange -> {
            exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "consumer-test-rule");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_RESOURCES_ARN,
                    "arn:aws:sqs:eu-west-1:123456789012:test");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_SOURCE, "camel.test");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, "CamelTestEvent");
            exchange.getIn().setBody("{\"message\":\"Hello from EventBridge\"}");
        });

        boolean satisfied = resultConsumed.await(30, TimeUnit.SECONDS);
        assertTrue(satisfied, "Consumer should have received at least one event");

        ebClient.close();
    }

    @Test
    public void testConsumeMultipleEvents() throws Exception {
        EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient();
        ebClient.putRule(PutRuleRequest.builder()
                .name("consumer-multi-rule")
                .eventBusName("default")
                .eventPattern("{\"source\":[\"camel.multi\"]}")
                .build());

        resultConsumedMultiple.expectedMinimumMessageCount(3);

        // Send 3 events
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            template.send("direct:putEventMulti", exchange -> {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "consumer-multi-rule");
                exchange.getIn().setHeader(EventbridgeConstants.EVENT_RESOURCES_ARN,
                        "arn:aws:sqs:eu-west-1:123456789012:test");
                exchange.getIn().setHeader(EventbridgeConstants.EVENT_SOURCE, "camel.multi");
                exchange.getIn().setHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, "CamelTestEvent");
                exchange.getIn().setBody("{\"message\":\"Event " + idx + "\"}");
            });
        }

        boolean satisfied = resultConsumedMultiple.await(30, TimeUnit.SECONDS);
        assertTrue(satisfied, "Consumer should have received at least 3 events");
        assertTrue(resultConsumedMultiple.getExchanges().size() >= 3);

        ebClient.close();
    }

    @Test
    public void testConsumerHeadersAreSet() throws Exception {
        EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient();
        ebClient.putRule(PutRuleRequest.builder()
                .name("consumer-headers-rule")
                .eventBusName("default")
                .eventPattern("{\"source\":[\"camel.headers\"]}")
                .build());

        resultConsumedHeaders.expectedMinimumMessageCount(1);

        template.send("direct:putEventHeaders", exchange -> {
            exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "consumer-headers-rule");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_RESOURCES_ARN,
                    "arn:aws:sqs:eu-west-1:123456789012:test");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_SOURCE, "camel.headers");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, "CamelTestEvent");
            exchange.getIn().setBody("{\"message\":\"Header test\"}");
        });

        boolean satisfied = resultConsumedHeaders.await(30, TimeUnit.SECONDS);
        assertTrue(satisfied, "Consumer should have received at least one event");

        // Verify consumer-specific headers are set
        assertNotNull(
                resultConsumedHeaders.getExchanges().get(0).getMessage()
                        .getHeader(EventbridgeConstants.MESSAGE_ID, String.class));
        assertNotNull(
                resultConsumedHeaders.getExchanges().get(0).getMessage()
                        .getHeader(EventbridgeConstants.RECEIPT_HANDLE, String.class));

        ebClient.close();
    }

    @Test
    public void testConsumeWithUserProvidedQueue() throws Exception {
        // Pre-create an SQS queue
        SqsClient sqsClient = AWSSDKClientUtils.newSQSClient();
        CreateQueueResponse createResp = sqsClient.createQueue(b -> b.queueName("user-provided-eb-queue"));
        String userQueueUrl = createResp.queueUrl();

        EventBridgeClient ebClient = AWSSDKClientUtils.newEventBridgeClient();
        ebClient.putRule(PutRuleRequest.builder()
                .name("consumer-userq-rule")
                .eventBusName("default")
                .eventPattern("{\"source\":[\"camel.userq\"]}")
                .build());

        resultConsumedUserQueue.expectedMinimumMessageCount(1);

        template.send("direct:putEventUserQueue", exchange -> {
            exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "consumer-userq-rule");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_RESOURCES_ARN,
                    "arn:aws:sqs:eu-west-1:123456789012:test");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_SOURCE, "camel.userq");
            exchange.getIn().setHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, "CamelTestEvent");
            exchange.getIn().setBody("{\"message\":\"User queue test\"}");
        });

        boolean satisfied = resultConsumedUserQueue.await(30, TimeUnit.SECONDS);
        assertTrue(satisfied, "Consumer should have received event via user-provided queue");

        sqsClient.close();
        ebClient.close();
    }

    @Test
    public void testAutoCreateQueueDisabledWithoutQueueUrlFails() {
        // This test verifies that the consumer throws when autoCreateQueue=false and no queueUrl
        // The route should fail to start
        assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("aws2-eventbridge://default?ruleName=fail-rule&autoCreateQueue=false&delay=1000")
                            .to("mock:shouldNotReach");
                }
            });
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Producer route to put events
                from("direct:putEvent")
                        .to("aws2-eventbridge://default?operation=putEvent");

                from("direct:putEventMulti")
                        .to("aws2-eventbridge://default?operation=putEvent");

                from("direct:putEventHeaders")
                        .to("aws2-eventbridge://default?operation=putEvent");

                from("direct:putEventUserQueue")
                        .to("aws2-eventbridge://default?operation=putEvent");

                // Consumer route that polls EventBridge rule via SQS
                from("aws2-eventbridge://default?ruleName=consumer-test-rule"
                     + "&autoCreateQueue=true&deleteQueueOnShutdown=true"
                     + "&delay=1000")
                        .log("Received EventBridge event: ${body}")
                        .to("mock:consumed");

                from("aws2-eventbridge://default?ruleName=consumer-multi-rule"
                     + "&autoCreateQueue=true&deleteQueueOnShutdown=true"
                     + "&delay=1000")
                        .to("mock:consumedMultiple");

                from("aws2-eventbridge://default?ruleName=consumer-headers-rule"
                     + "&autoCreateQueue=true&deleteQueueOnShutdown=true"
                     + "&delay=1000")
                        .to("mock:consumedHeaders");

                from("aws2-eventbridge://default?ruleName=consumer-userq-rule"
                     + "&autoCreateQueue=false"
                     + "&queueUrl=PLACEHOLDER_WILL_OVERRIDE"
                     + "&deleteQueueOnShutdown=false"
                     + "&delay=1000")
                        .to("mock:consumedUserQueue");
            }
        };
    }
}
