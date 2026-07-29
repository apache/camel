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
import java.util.Collection;
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
import software.amazon.awssdk.services.eventbridge.model.Rule;
import software.amazon.awssdk.services.eventbridge.model.RuleState;
import software.amazon.awssdk.services.eventbridge.model.Target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventbridgeEnableRuleIT extends Aws2EventbridgeBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:evs-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule-EventbridgeEnableRuleIT");
            }
        });

        template.send("direct:evs-targets-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule-EventbridgeEnableRuleIT");
                Target target = Target.builder().id("sqs-queue-EventbridgeEnableRuleIT")
                        .arn("arn:aws:sqs:eu-west-1:780410022472:camel-connector-test")
                        .build();
                List<Target> targets = new ArrayList<Target>();
                targets.add(target);
                exchange.getIn().setHeader(EventbridgeConstants.TARGETS, targets);
            }
        });

        template.send("direct:evs-disableRule-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule-EventbridgeEnableRuleIT");
            }
        });

        template.send("direct:evs-enableRule-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule-EventbridgeEnableRuleIT");
            }
        });

        Exchange ex = template.request("direct:evs-listRules-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
            }
        });

        ListRulesResponse resp = ex.getIn().getBody(ListRulesResponse.class);
        assertEquals(true, resp.hasRules());
        assertThat(resp.rules().stream().map(Rule::name))
                .contains("firstrule-EventbridgeEnableRuleIT");
        Rule enabledRule = resp.rules().stream().filter(rule -> "firstrule-EventbridgeEnableRuleIT".equals(rule.name()))
                .findAny().get();
        assertEquals(RuleState.ENABLED, enabledRule.state());
        MockEndpoint.assertIsSatisfied(context);

        // Clean up eventbridge
        template.send("direct:evs-deleteTargets-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule-EventbridgeEnableRuleIT");
                Collection<String> targets = new ArrayList<>();
                targets.add("sqs-queue-EventbridgeEnableRuleIT");
                exchange.getIn().setHeader(EventbridgeConstants.TARGETS_IDS, targets);
            }
        });

        template.send("direct:evs-deleteRule-EventbridgeEnableRuleIT", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.RULE_NAME, "firstrule-EventbridgeEnableRuleIT");
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String putRule
                        = "aws2-eventbridge://default?operation=putRule&eventPatternFile=file:src/test/resources/eventpattern.json";
                String putTargets = "aws2-eventbridge://default?operation=putTargets";
                String removeTargets = "aws2-eventbridge://default?operation=removeTargets";
                String listRule = "aws2-eventbridge://default?operation=listRules";
                String disableRule = "aws2-eventbridge://default?operation=disableRule";
                String enableRule = "aws2-eventbridge://default?operation=enableRule";
                String deleteRule = "aws2-eventbridge://default?operation=deleteRule";

                from("direct:evs-EventbridgeEnableRuleIT").to(putRule);
                from("direct:evs-targets-EventbridgeEnableRuleIT").to(putTargets);
                from("direct:evs-listRules-EventbridgeEnableRuleIT").to(listRule);
                from("direct:evs-disableRule-EventbridgeEnableRuleIT").to(disableRule);
                from("direct:evs-deleteRule-EventbridgeEnableRuleIT").to(deleteRule);
                from("direct:evs-deleteTargets-EventbridgeEnableRuleIT").to(removeTargets);
                from("direct:evs-enableRule-EventbridgeEnableRuleIT").to(enableRule).log("${body}").to("mock:result");
            }
        };
    }
}
