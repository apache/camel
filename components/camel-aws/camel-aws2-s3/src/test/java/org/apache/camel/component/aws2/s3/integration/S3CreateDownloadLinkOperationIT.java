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
package org.apache.camel.component.aws2.s3.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3CreateDownloadLinkOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @SuppressWarnings("unchecked")
    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:listBucket", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listBuckets);
            }
        });

        template.send("direct:addObject", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "CamelUnitTest2");
                exchange.getIn().setBody("This is my bucket content.");
                exchange.getIn().removeHeader(AWS2S3Constants.S3_OPERATION);
            }
        });

        Exchange ex1 = template.request("direct:createDownloadLink", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "CamelUnitTest2");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, "mycamel2");
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.createDownloadLink);
            }
        });

        Exchange ex3 = template.request("direct:createDownloadLinkWithUriOverride", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "CamelUnitTest2");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, "mycamel2");
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.createDownloadLink);
            }
        });

        String downloadLink = ex1.getMessage().getBody(String.class);
        assertNotNull(downloadLink);
        assertTrue(downloadLink.startsWith("https://mycamel2.s3.eu-west-1.amazonaws.com"));

        String downloadLinkWithUriOverride = ex3.getMessage().getBody(String.class);
        assertNotNull(downloadLinkWithUriOverride);
        assertTrue(downloadLinkWithUriOverride.startsWith("http://mycamel2.localhost:8080"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://mycamel2?autoCreateBucket=true";

                from("direct:listBucket").to(awsEndpoint + "&accessKey=xxx&secretKey=yyy&region=eu-west-1");

                from("direct:addObject").to(awsEndpoint + "&accessKey=xxx&secretKey=yyy&region=eu-west-1");

                from("direct:createDownloadLink").to(awsEndpoint + "&accessKey=xxx&secretKey=yyy&region=eu-west-1")
                        .to("mock:result");

                from("direct:createDownloadLinkWithUriOverride")
                        .to(awsEndpoint
                            + "&accessKey=xxx&secretKey=yyy&region=eu-west-1&uriEndpointOverride=http://localhost:8080");
            }
        };
    }
}
