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
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3StreamUploadTimestampTimeoutIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInWithTimestampAndTimeout() throws Exception {

        for (int i = 1; i <= 2; i++) {
            int count = i * 23;

            result.expectedMessageCount(count);

            long beforeUpload = System.currentTimeMillis();

            for (int j = 0; j < 23; j++) {
                template.sendBody("direct:stream1", "TestData\n");
            }

            long afterUpload = System.currentTimeMillis();

            Awaitility.await()
                    .atMost(11, TimeUnit.SECONDS)
                    .untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

            Awaitility.await().atMost(11, TimeUnit.SECONDS).untilAsserted(() -> {
                Exchange ex = template.request("direct:listObjects", this::process);

                List<S3Object> resp = ex.getMessage().getBody(List.class);
                assertEquals(1, resp.size());

                // Verify the uploaded file uses timestamp naming strategy
                S3Object s3Object = resp.get(0);
                String key = s3Object.key();

                // The file should either be the base name or have a timestamp suffix
                if ("fileTest.txt".equals(key)) {
                    // This is fine - it's the base file
                } else if (key.startsWith("fileTest-") && key.endsWith(".txt")) {
                    // Extract and validate timestamp
                    String timestampStr = key.substring("fileTest-".length(), key.length() - ".txt".length());
                    try {
                        long timestamp = Long.parseLong(timestampStr);
                        assertTrue(
                                timestamp >= beforeUpload
                                        && timestamp <= afterUpload + 11000, // Allow extra time for timeout
                                "Timestamp " + timestamp + " should be within expected range");
                    } catch (NumberFormatException e) {
                        throw new AssertionError("Expected numeric timestamp in filename: " + key, e);
                    }
                } else {
                    throw new AssertionError("Unexpected filename format: " + key);
                }
            });
        }
    }

    private void process(Exchange exchange) {
        exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint1 = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=fileTest.txt&batchMessageNumber=25&namingStrategy=timestamp&streamingUploadTimeout=10000",
                        name.get());

                from("direct:stream1").to(awsEndpoint1).to("mock:result");

                String awsEndpoint = String.format("aws2-s3://%s?autoCreateBucket=true", name.get());

                from("direct:listObjects").to(awsEndpoint);
            }
        };
    }
}
