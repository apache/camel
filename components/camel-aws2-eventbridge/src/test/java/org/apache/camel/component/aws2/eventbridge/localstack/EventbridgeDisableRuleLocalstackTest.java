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
import software.amazon.awssdk.services.eventbridge.model.ListRulesResponse;
import software.amazon.awssdk.services.eventbridge.model.RuleState;
import software.amazon.awssdk.services.eventbridge.model.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventbridgeDisableRuleLocalstackTest extends Aws2EventbridgeBaseTest {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:evs", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
            }
        });

        template.send("direct:evs-targets", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
                Target target = Target.builder().id("sqs-queue").arn("arn:aws:sqs:eu-west-1:780410022472:camel-connector-test")
                        .build();
                List<Target> targets = new ArrayList<Target>();
                targets.add(target);
                exchange.getIn().setHeader(EventbridgeConstants.TARGETS, targets);
            }
        });

        template.send("direct:evs-disableRule", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule");
            }
        });

        Exchange ex = template.request("direct:evs-listRules", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });

        ListRulesResponse resp = ex.getIn().getBody(ListRulesResponse.class);
        assertEquals(true, resp.hasRules());
        assertEquals(1, resp.rules().size());
        assertEquals("firstrule", resp.rules().get(0).name());
        assertEquals(RuleState.DISABLED, resp.rules().get(0).state());
        assertMockEndpointsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String putRule
                        = "aws2-eventbridge://default?operation=putRule&eventPatternFile=file:src/test/resources/eventpattern.json";
                String putTargets = "aws2-eventbridge://default?operation=putTargets";
                String listRule = "aws2-eventbridge://default?operation=listRules";
                String disableRule = "aws2-eventbridge://default?operation=disableRule";

                from("direct:evs").to(putRule);
                from("direct:evs-targets").to(putTargets);
                from("direct:evs-listRules").to(listRule);
                from("direct:evs-disableRule").to(disableRule).log("${body}").to("mock:result");
            }
        };
    }
}
