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
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class S3CreateBucketOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void createBucket() throws Exception {
        result.expectedMessageCount(1);

        String testBucketName = name.get() + "-create-test";

        template.send("direct:createBucket", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, testBucketName);
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.createBucket);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        CreateBucketResponse response = resultExchange.getMessage().getBody(CreateBucketResponse.class);
        assertNotNull(response);

        // Clean up - delete the bucket
        template.send("direct:deleteBucket", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, testBucketName);
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.deleteBucket);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://" + name.get();

                from("direct:createBucket")
                        .to(awsEndpoint)
                        .to("mock:result");

                from("direct:deleteBucket")
                        .to(awsEndpoint);
            }
        };
    }
}
