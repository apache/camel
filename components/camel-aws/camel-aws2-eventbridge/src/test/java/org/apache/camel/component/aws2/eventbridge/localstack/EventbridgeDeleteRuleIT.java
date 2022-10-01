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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.eventbridge.EventbridgeConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.Target;

import static org.junit.Assert.assertNotNull;

public class EventbridgeDeleteRuleIT extends Aws2EventbridgeBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:evs", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
            }
        });

        template.send("direct:evs-targets", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
                Target target = Target.builder().id("sqs-queue").arn("arn:aws:sqs:eu-west-1:780410022472:camel-connector-test")
                        .build();
                List<Target> targets = new ArrayList<Target>();
                targets.add(target);
                exchange.getIn().setHeader(EventbridgeConstants.TARGETS, targets);
            }
        });

        // https://docs.aws.amazon.com/eventbridge/latest/APIReference/API_DeleteRule.html before deleting the route, all targets must be removed
        template.send("direct:evs-removeTarget", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
                List<String> targets = new ArrayList<String>();
                targets.add("sqs-queue");
                exchange.getIn().setHeader(EventbridgeConstants.TARGETS_IDS, targets);
            }
        });

        Exchange ex = template.send("direct:evs-deleteRule", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        assertNotNull(ex.getIn().getBody(DeleteRuleResponse.class));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint
                        = "aws2-eventbridge://default?operation=putRule&eventPatternFile=file:src/test/resources/eventpattern.json";
                String target = "aws2-eventbridge://default?operation=putTargets";
                String removeTarget = "aws2-eventbridge://default?operation=removeTargets";
                String deleteRule = "aws2-eventbridge://default?operation=deleteRule";
                from("direct:evs").to(awsEndpoint);
                from("direct:evs-targets").to(target);
                from("direct:evs-removeTarget").to(removeTarget);
                from("direct:evs-deleteRule").to(deleteRule).to("mock:result");
            }
        };
    }
}
