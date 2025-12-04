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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3StreamUploadTimestampNamingStrategyIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInWithTimestampNamingStrategy() throws Exception {
        result.expectedMessageCount(1000);

        long beforeUpload = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            template.sendBody("direct:stream1", "TestData\n");
        }

        long afterUpload = System.currentTimeMillis();

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(40, resp.size());

        // Verify that uploaded files use timestamp naming strategy
        // Files should have names like: fileTest.txt, fileTest-<timestamp>.txt
        boolean foundBaseFile = false;
        boolean foundTimestampFile = false;

        for (S3Object s3Object : resp) {
            String key = s3Object.key();

            if ("fileTest.txt".equals(key)) {
                foundBaseFile = true;
            } else if (key.startsWith("fileTest-") && key.endsWith(".txt")) {
                foundTimestampFile = true;

                // Extract timestamp from filename and verify it's within expected range
                String timestampStr = key.substring("fileTest-".length(), key.length() - ".txt".length());
                try {
                    long timestamp = Long.parseLong(timestampStr);
                    assertTrue(
                            timestamp >= beforeUpload && timestamp <= afterUpload,
                            "Timestamp " + timestamp + " should be between " + beforeUpload + " and " + afterUpload);
                } catch (NumberFormatException e) {
                    // This shouldn't happen with timestamp naming strategy
                    throw new AssertionError("Expected numeric timestamp in filename: " + key, e);
                }
            }
        }

        assertTrue(foundBaseFile, "Should find base file (fileTest.txt)");
        assertTrue(foundTimestampFile, "Should find timestamp-named files (fileTest-<timestamp>.txt)");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint1 = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=fileTest.txt&batchMessageNumber=25&namingStrategy=timestamp",
                        name.get());

                from("direct:stream1").to(awsEndpoint1).to("mock:result");

                String awsEndpoint = String.format("aws2-s3://%s?autoCreateBucket=true", name.get());

                from("direct:listObjects").to(awsEndpoint);
            }
        };
    }
}
