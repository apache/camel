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
package org.apache.camel.component.aws2.sqs.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class SqsConsumerMessageIntegrationTest extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("ignore");
            }
        });

        exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("test1");
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String sqsEndpointUri = String.format("aws2-sqs://camel-1?accessKey=RAW(xxxx)&secretKey=RAW(xxxx)&region=eu-west-1");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").startupOrder(2).to(sqsEndpointUri);

                from("aws2-sqs://camel-1?accessKey=RAW(xxxx)&secretKey=RAW(xxxx)&region=eu-west-1&deleteAfterRead=false&deleteIfFiltered=true").startupOrder(1)
                    .filter(simple("${body} != 'ignore'")).log("${body}").log("${header.CamelAwsSqsReceiptHandle}").to("mock:result");
            }
        };
    }
}
