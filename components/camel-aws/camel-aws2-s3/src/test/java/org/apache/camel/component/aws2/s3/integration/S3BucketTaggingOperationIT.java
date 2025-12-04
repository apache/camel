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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.Tag;

public class S3BucketTaggingOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void putAndGetBucketTagging() throws Exception {
        // Put tags on the bucket
        Exchange putResult = template.request("direct:putBucketTagging", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Map<String, String> tags = new HashMap<>();
                tags.put("Team", "Integration");
                tags.put("Cost-Center", "Engineering");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_TAGS, tags);
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.putBucketTagging);
            }
        });
        assertNotNull(putResult);

        // Get tags from the bucket
        Exchange getResult = template.request("direct:getBucketTagging", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getBucketTagging);
            }
        });

        List<Tag> tags = getResult.getMessage().getBody(List.class);
        assertNotNull(tags);
        assertEquals(2, tags.size());
    }

    @Test
    public void deleteBucketTagging() throws Exception {
        // Put tags on the bucket
        template.send("direct:putBucketTagging2", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Map<String, String> tags = new HashMap<>();
                tags.put("Temp", "Value");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_TAGS, tags);
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.putBucketTagging);
            }
        });

        // Delete tags
        Exchange deleteResult = template.request("direct:deleteBucketTagging", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.deleteBucketTagging);
            }
        });

        assertNotNull(deleteResult);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://" + name.get() + "?autoCreateBucket=true";

                from("direct:putBucketTagging").to(awsEndpoint);

                from("direct:putBucketTagging2").to(awsEndpoint);

                from("direct:getBucketTagging").to(awsEndpoint);

                from("direct:deleteBucketTagging").to(awsEndpoint);
            }
        };
    }
}
