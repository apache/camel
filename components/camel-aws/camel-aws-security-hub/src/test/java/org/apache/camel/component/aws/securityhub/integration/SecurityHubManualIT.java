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
package org.apache.camel.component.aws.securityhub.integration;

import java.time.Instant;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.securityhub.SecurityHubConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFinding;
import software.amazon.awssdk.services.securityhub.model.AwsSecurityFindingFilters;
import software.amazon.awssdk.services.securityhub.model.Resource;
import software.amazon.awssdk.services.securityhub.model.Severity;
import software.amazon.awssdk.services.securityhub.model.SeverityLabel;
import software.amazon.awssdk.services.securityhub.model.StringFilter;
import software.amazon.awssdk.services.securityhub.model.StringFilterComparison;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class SecurityHubManualIT extends CamelTestSupport {
    private static String accessKey = System.getProperty("aws.manual.access.key");
    private static String secretKey = System.getProperty("aws.manual.secret.key");
    private static String region = System.getProperty("aws.manual.region", "us-east-1");

    @BindToRegistry("securityhub-client")
    SecurityHubClient client
            = SecurityHubClient.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .region(Region.of(region)).build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testDescribeHub() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:describeHub", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No specific headers needed for describeHub
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = result.getExchanges().get(0);
        assertNotNull(exchange.getIn().getBody());
    }

    @Test
    public void testGetFindings() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:getFindings", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(SecurityHubConstants.MAX_RESULTS, 10);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testGetFindingsWithFilters() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:getFindings", new Processor() {
            @Override
            public void process(Exchange exchange) {
                AwsSecurityFindingFilters filters = AwsSecurityFindingFilters.builder()
                        .severityLabel(StringFilter.builder()
                                .comparison(StringFilterComparison.EQUALS)
                                .value("CRITICAL")
                                .build())
                        .build();
                exchange.getIn().setHeader(SecurityHubConstants.FILTERS, filters);
                exchange.getIn().setHeader(SecurityHubConstants.MAX_RESULTS, 5);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testGetCamelFindings() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:getFindings", new Processor() {
            @Override
            public void process(Exchange exchange) {
                AwsSecurityFindingFilters filters = AwsSecurityFindingFilters.builder()
                        .generatorId(StringFilter.builder()
                                .comparison(StringFilterComparison.EQUALS)
                                .value("camel-security-hub-test")
                                .build())
                        .build();
                exchange.getIn().setHeader(SecurityHubConstants.FILTERS, filters);
                exchange.getIn().setHeader(SecurityHubConstants.MAX_RESULTS, 50);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testListEnabledProductsForImport() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:listProducts", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No specific headers needed
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testBatchImportFindings() throws Exception {
        result.expectedMessageCount(1);

        // Note: This test requires a valid product ARN registered with Security Hub
        // You may need to adjust the productArn to match your AWS account setup
        String accountId = System.getProperty("aws.manual.account.id");
        if (accountId == null || accountId.isEmpty()) {
            // Skip if account ID not provided
            return;
        }

        template.send("direct:batchImport", new Processor() {
            @Override
            public void process(Exchange exchange) {
                String timestamp = Instant.now().toString();
                String productArn = String.format("arn:aws:securityhub:%s:%s:product/%s/default",
                        region, accountId, accountId);

                AwsSecurityFinding finding = AwsSecurityFinding.builder()
                        .schemaVersion("2018-10-08")
                        .id("camel-test-finding-" + System.currentTimeMillis())
                        .productArn(productArn)
                        .generatorId("camel-security-hub-test")
                        .awsAccountId(accountId)
                        .types("Software and Configuration Checks/Vulnerabilities/Test")
                        .createdAt(timestamp)
                        .updatedAt(timestamp)
                        .severity(Severity.builder().label(SeverityLabel.INFORMATIONAL).build())
                        .title("Camel Security Hub Integration Test Finding")
                        .description("This is a test finding created by the Camel AWS Security Hub integration test")
                        .resources(Resource.builder()
                                .type("Other")
                                .id("camel-test-resource")
                                .build())
                        .build();

                exchange.getIn().setBody(List.of(finding));
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = result.getExchanges().get(0);
        Integer successCount = exchange.getIn().getHeader(SecurityHubConstants.SUCCESS_COUNT, Integer.class);
        Integer failedCount = exchange.getIn().getHeader(SecurityHubConstants.FAILED_COUNT, Integer.class);
        assertNotNull(successCount);
        assertNotNull(failedCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:describeHub")
                        .to("aws-security-hub://hub?operation=describeHub")
                        .log("DescribeHub result: ${body}")
                        .to("mock:result");

                from("direct:getFindings")
                        .to("aws-security-hub://findings?operation=getFindings")
                        .log("GetFindings result: ${body}")
                        .split(simple("${body.findings}"))
                            .log("Finding ID: ${body.id}")
                            .log("  Title: ${body.title}")
                            .log("  Severity: ${body.severity.label}")
                            .log("  Description: ${body.description}")
                            .log("  Created: ${body.createdAt}")
                            .log("  Product ARN: ${body.productArn}")
                        .end()
                        .to("mock:result");

                from("direct:listProducts")
                        .to("aws-security-hub://products?operation=listEnabledProductsForImport")
                        .log("ListProducts result: ${body}")
                        .to("mock:result");

                from("direct:batchImport")
                        .log("Importing findings: ${body}")
                        .to("aws-security-hub://findings?operation=batchImportFindings")
                        .log("BatchImport result: ${body}")
                        .log("  Success count: ${header.CamelAwsSecurityHubSuccessCount}")
                        .log("  Failed count: ${header.CamelAwsSecurityHubFailedCount}")
                        .log("  Failed findings: ${body.failedFindings}")
                        .to("mock:result");
            }
        };
    }
}
