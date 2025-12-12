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
package org.apache.camel.component.aws2.bedrock.agent.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.bedrock.agent.BedrockAgentConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BedrockAgentProducerIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testSyncIngestionJob() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:start_ingestion", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentConstants.KNOWLEDGE_BASE_ID, "QOZ68KOXTS");
            exchange.getMessage().setHeader(BedrockAgentConstants.DATASOURCE_ID, "9V85PTUEAH");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testListIngestionJobs() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:list_ingestion_jobs", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentConstants.KNOWLEDGE_BASE_ID, "QOZ68KOXTS");
            exchange.getMessage().setHeader(BedrockAgentConstants.DATASOURCE_ID, "9V85PTUEAH");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start_ingestion")
                        .to("aws-bedrock-agent:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}}&region=us-east-1&operation=startIngestionJob")
                        .to(result);
                from("direct:list_ingestion_jobs")
                        .to("aws-bedrock-agent:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}}&region=us-east-1&operation=listIngestionJobs")
                        .to(result);
            }
        };
    }
}
