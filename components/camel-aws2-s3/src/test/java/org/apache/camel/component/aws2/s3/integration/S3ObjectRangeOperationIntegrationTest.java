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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class S3ObjectRangeOperationIntegrationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(S3ObjectRangeOperationIntegrationTest.class);

    @BindToRegistry("amazonS3Client")
    S3Client client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("xxx", "yyy"))).region(Region.US_WEST_1).build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:getObjectRange", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "element.txt");
                exchange.getIn().setHeader(AWS2S3Constants.RANGE_START, 0);
                exchange.getIn().setHeader(AWS2S3Constants.RANGE_END, 9);
            }
        });
        assertMockEndpointsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String awsEndpoint = "aws2-s3://mycamelbucket?operation=getObjectRange&autoCreateBucket=false";

                from("direct:getObjectRange").to(awsEndpoint).process(new Processor() {

                    @SuppressWarnings("unchecked")
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        ResponseInputStream<GetObjectResponse> s3 = exchange.getIn().getBody(ResponseInputStream.class);
                        LOG.info(readInputStream(s3));

                    }
                }).to("mock:result");

            }
        };
    }

    private String readInputStream(ResponseInputStream<GetObjectResponse> s3Object) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(s3Object, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char)c);
            }
        }
        return textBuilder.toString();
    }
}
