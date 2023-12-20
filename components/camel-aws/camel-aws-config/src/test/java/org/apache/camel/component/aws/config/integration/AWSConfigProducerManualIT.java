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
package org.apache.camel.component.aws.config.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.config.AWSConfigConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.config.model.DeleteConfigRuleResponse;
import software.amazon.awssdk.services.config.model.DescribeComplianceByConfigRuleResponse;
import software.amazon.awssdk.services.config.model.PutConfigRuleResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class AWSConfigProducerManualIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void PutAndRemoveRule() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:putConfigRule", new Processor() {
            @Override
            public void process(Exchange exchange) {

                exchange.getMessage().setHeader(AWSConfigConstants.SOURCE, "AWS");
                exchange.getMessage().setHeader(AWSConfigConstants.RULE_SOURCE_IDENTIFIER, "S3_LIFECYCLE_POLICY_CHECK");
                exchange.getMessage().setHeader(AWSConfigConstants.RULE_NAME, "Test");
            }
        });

        PutConfigRuleResponse resultGet = (PutConfigRuleResponse) exchange.getIn().getBody();
        assertTrue(resultGet.sdkHttpResponse().isSuccessful());

        exchange = template.request("direct:removeConfigRule", new Processor() {
            @Override
            public void process(Exchange exchange) {

                exchange.getMessage().setHeader(AWSConfigConstants.RULE_NAME, "Test");
            }
        });

        DeleteConfigRuleResponse deleteResponse = (DeleteConfigRuleResponse) exchange.getIn().getBody();
        assertTrue(deleteResponse.sdkHttpResponse().isSuccessful());
    }

    @Test
    public void PutRuleCheckCompliance() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:putConfigRule", new Processor() {
            @Override
            public void process(Exchange exchange) {

                exchange.getMessage().setHeader(AWSConfigConstants.SOURCE, "AWS");
                exchange.getMessage().setHeader(AWSConfigConstants.RULE_SOURCE_IDENTIFIER, "S3_VERSION_LIFECYCLE_POLICY_CHECK");
                exchange.getMessage().setHeader(AWSConfigConstants.RULE_NAME, "Lifecycle-policy-check");
            }
        });

        PutConfigRuleResponse resultGet = (PutConfigRuleResponse) exchange.getIn().getBody();
        assertTrue(resultGet.sdkHttpResponse().isSuccessful());

        exchange = template.request("direct:checkCompliance", new Processor() {
            @Override
            public void process(Exchange exchange) {

                exchange.getMessage().setHeader(AWSConfigConstants.RULE_NAME, "Lifecycle-policy-check");
            }
        });

        DescribeComplianceByConfigRuleResponse compliancy = (DescribeComplianceByConfigRuleResponse) exchange.getIn().getBody();
        assertTrue(compliancy.sdkHttpResponse().isSuccessful());

        exchange = template.request("direct:removeConfigRule", new Processor() {
            @Override
            public void process(Exchange exchange) {

                exchange.getMessage().setHeader(AWSConfigConstants.RULE_NAME, "Lifecycle-policy-check");
            }
        });

        DeleteConfigRuleResponse deleteResponse = (DeleteConfigRuleResponse) exchange.getIn().getBody();
        assertTrue(deleteResponse.sdkHttpResponse().isSuccessful());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putConfigRule")
                        .to("aws-config://test?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=eu-west-1&operation=putConfigRule")
                        .to("mock:result");

                from("direct:removeConfigRule")
                        .to("aws-config://test?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=eu-west-1&operation=removeConfigRule")
                        .to("mock:result");

                from("direct:checkCompliance")
                        .to("aws-config://test?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=eu-west-1&operation=describeRuleCompliance")
                        .to("mock:result");
            }
        };
    }
}
