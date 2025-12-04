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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class S3CreateUploadLinkOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void createUploadLink() throws Exception {
        // First upload an object
        template.send("direct:addObject", exchange -> {
            exchange.getIn().setHeader(AWS2S3Constants.KEY, "test-upload-key");
            exchange.getIn().setBody("Test content for upload link");
        });

        Exchange response = template.request("direct:createUploadLink", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test-upload-key");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, name.get());
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.createUploadLink);
                exchange.getIn().setHeader(AWS2S3Constants.UPLOAD_LINK_EXPIRATION_TIME, 3600000L); // 1 hour
            }
        });

        String uploadUrl = response.getMessage().getBody(String.class);
        assertNotNull(uploadUrl);
        assertTrue(uploadUrl.startsWith("http"));
        assertTrue(uploadUrl.contains(name.get()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://" + name.get() + "?autoCreateBucket=true";

                from("direct:addObject").to(awsEndpoint + "&accessKey=xxx&secretKey=yyy&region=eu-west-1");

                from("direct:createUploadLink").to(awsEndpoint + "&accessKey=xxx&secretKey=yyy&region=eu-west-1");
            }
        };
    }
}
