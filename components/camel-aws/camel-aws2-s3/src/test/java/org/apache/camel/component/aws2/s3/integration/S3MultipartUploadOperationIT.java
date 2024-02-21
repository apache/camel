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

import java.io.File;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3MultipartUploadOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "empty.bin");
                exchange.getIn().setBody(new File("src/test/resources/empty.bin"));
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void sendInWithContentType() {
        result.expectedMessageCount(1);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "camel-content-type.txt");
                exchange.getIn().setBody(new File("src/test/resources/empty.bin"));
                exchange.getIn().setHeader(AWS2S3Constants.CONTENT_TYPE, "application/text");
            }
        });

        S3Client s = AWSSDKClientUtils.newS3Client();
        ResponseInputStream<GetObjectResponse> response
                = s.getObject(GetObjectRequest.builder().bucket("mycamel").key("camel-content-type.txt").build());
        assertEquals("application/text", response.response().contentType());
    }

    @Test
    public void sendZeroLength() throws Exception {
        result.expectedMessageCount(1);

        File zero = new File("target/zero.txt");
        IOHelper.writeText("", zero);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "zero.txt");
                exchange.getIn().setBody(zero);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://mycamel?multiPartUpload=true&autoCreateBucket=true";

                from("direct:putObject").to(awsEndpoint).to("mock:result");

            }
        };
    }
}
