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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3UploadWithConditionalWritesIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInWithConditionalWrites() {
        result.expectedMessageCount(1);

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(AWS2S3Constants.KEY, "camel-content-type.txt");
            exchange.getIn().setBody("Camel rocks!");
        });

        S3Client s = AWSSDKClientUtils.newS3Client();
        ResponseInputStream<GetObjectResponse> response
                = s.getObject(GetObjectRequest.builder().bucket(name.get()).key("camel-content-type.txt").build());
        assertTrue(response.response().hasMetadata());

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(AWS2S3Constants.KEY, "testtest");
            exchange.getIn().setHeader(AWS2S3Constants.IF_NONE_MATCH_WRITE_CONDITION, "camel-content-*");
            exchange.getIn().setBody("Camel rocks!");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = String.format("aws2-s3://%s?autoCreateBucket=true", name.get());

                from("direct:putObject").to(awsEndpoint);

            }
        };
    }
}
