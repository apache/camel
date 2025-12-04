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

import java.util.Date;
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

public class S3StreamUploadTimestampGroupingEdgeCasesIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testTimestampGroupingWithInvalidStringTimestamp() throws Exception {
        result.reset();
        result.expectedMessageCount(5);

        // Test with invalid string timestamp - should fall back to current time
        for (int i = 0; i < 5; i++) {
            template.sendBodyAndHeader(
                    "direct:timestampGrouping",
                    "Message with invalid timestamp: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    "invalid-timestamp-" + i);
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "edgeTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should create at least 1 file even with invalid timestamps");
    }

    @Test
    public void testTimestampGroupingWithMixedTimestampTypes() throws Exception {
        result.reset();
        result.expectedMessageCount(6);

        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        // Mix of Long, Date, String, and invalid timestamps
        template.sendBodyAndHeader(
                "direct:timestampGrouping", "Message 1 - Long", Exchange.MESSAGE_TIMESTAMP, baseTime);
        template.sendBodyAndHeader(
                "direct:timestampGrouping", "Message 2 - Date", Exchange.MESSAGE_TIMESTAMP, new Date(baseTime + 60000));
        template.sendBodyAndHeader(
                "direct:timestampGrouping",
                "Message 3 - String",
                Exchange.MESSAGE_TIMESTAMP,
                String.valueOf(baseTime + 120000));
        template.sendBodyAndHeader(
                "direct:timestampGrouping", "Message 4 - Invalid", Exchange.MESSAGE_TIMESTAMP, "not-a-timestamp");
        template.sendBody("direct:timestampGrouping", "Message 5 - No header");
        template.sendBodyAndHeader("direct:timestampGrouping", "Message 6 - Null", Exchange.MESSAGE_TIMESTAMP, null);

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "edgeTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should handle mixed timestamp types gracefully");
    }

    @Test
    public void testTimestampGroupingWithVeryLargeWindow() throws Exception {
        result.reset();
        result.expectedMessageCount(10);

        // Test with very large window (1 hour = 3600000ms)
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        // Send messages spread over 30 minutes - should all go to same window
        for (int i = 0; i < 10; i++) {
            long timestamp = baseTime + (i * 180000); // 3 minutes apart
            template.sendBodyAndHeader(
                    "direct:timestampGroupingLargeWindow",
                    "Message in large window: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    timestamp);
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "largeWindowTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(1, resp.size(), "Should create only 1 file for large time window");
    }

    @Test
    public void testTimestampGroupingWithVerySmallWindow() throws Exception {
        result.reset();
        result.expectedMessageCount(6);

        // Test with very small window (5 seconds = 5000ms)
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        // Send messages spread across 3 different 5-second windows to ensure clear separation
        // Window 1: 08:00:00 - 08:00:05
        template.sendBodyAndHeader(
                "direct:timestampGroupingSmallWindow", "Message in window 1a", Exchange.MESSAGE_TIMESTAMP, baseTime);
        template.sendBodyAndHeader(
                "direct:timestampGroupingSmallWindow",
                "Message in window 1b",
                Exchange.MESSAGE_TIMESTAMP,
                baseTime + 2000);

        // Window 2: 08:00:10 - 08:00:15 (skip 5 seconds to be in different window)
        template.sendBodyAndHeader(
                "direct:timestampGroupingSmallWindow",
                "Message in window 2a",
                Exchange.MESSAGE_TIMESTAMP,
                baseTime + 10000);
        template.sendBodyAndHeader(
                "direct:timestampGroupingSmallWindow",
                "Message in window 2b",
                Exchange.MESSAGE_TIMESTAMP,
                baseTime + 12000);

        // Window 3: 08:00:20 - 08:00:25
        template.sendBodyAndHeader(
                "direct:timestampGroupingSmallWindow",
                "Message in window 3a",
                Exchange.MESSAGE_TIMESTAMP,
                baseTime + 20000);
        template.sendBodyAndHeader(
                "direct:timestampGroupingSmallWindow",
                "Message in window 3b",
                Exchange.MESSAGE_TIMESTAMP,
                baseTime + 22000);

        MockEndpoint.assertIsSatisfied(context);

        // Add a delay to ensure all uploads complete via timeout
        Thread.sleep(5000);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "smallWindowTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 3, "Should create 3 files for 3 different 5-second windows, got: " + resp.size());
    }

    @Test
    public void testTimestampGroupingWithBoundaryTimestamps() throws Exception {
        result.reset();
        result.expectedMessageCount(6);

        // Test messages exactly at window boundaries
        long windowStart = 1704096000000L; // Exact start of 5-minute window
        long windowSize = 300000L; // 5 minutes

        // Send messages at exact boundaries
        template.sendBodyAndHeader(
                "direct:timestampGrouping", "Message at window start", Exchange.MESSAGE_TIMESTAMP, windowStart);
        template.sendBodyAndHeader(
                "direct:timestampGrouping",
                "Message 1ms before window end",
                Exchange.MESSAGE_TIMESTAMP,
                windowStart + windowSize - 1);
        template.sendBodyAndHeader(
                "direct:timestampGrouping",
                "Message at next window start",
                Exchange.MESSAGE_TIMESTAMP,
                windowStart + windowSize);
        template.sendBodyAndHeader(
                "direct:timestampGrouping",
                "Message in first window middle",
                Exchange.MESSAGE_TIMESTAMP,
                windowStart + windowSize / 2);
        template.sendBodyAndHeader(
                "direct:timestampGrouping",
                "Message in second window middle",
                Exchange.MESSAGE_TIMESTAMP,
                windowStart + windowSize + windowSize / 2);
        template.sendBodyAndHeader(
                "direct:timestampGrouping",
                "Message at exact boundary",
                Exchange.MESSAGE_TIMESTAMP,
                windowStart + windowSize);

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "edgeTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(2, resp.size(), "Should create exactly 2 files for boundary timestamps");
    }

    @Test
    public void testTimestampGroupingWithOldTimestamps() throws Exception {
        result.reset();
        result.expectedMessageCount(8);

        // Test with very old timestamps (year 2000)
        long oldBaseTime = 946684800000L; // 2000-01-01 00:00:00 UTC

        for (int i = 0; i < 8; i++) {
            long timestamp = oldBaseTime + (i * 60000); // 1 minute apart
            template.sendBodyAndHeader(
                    "direct:timestampGrouping",
                    "Message with old timestamp: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    timestamp);
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "edgeTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should handle old timestamps correctly");

        // Verify files have correct naming with old dates
        boolean foundOldDateFile = false;
        for (S3Object s3Object : resp) {
            String key = s3Object.key();
            if (key.contains("20000101")) { // Check for year 2000
                foundOldDateFile = true;
                break;
            }
        }
        assertTrue(foundOldDateFile, "Should create files with old date naming");
    }

    @Test
    public void testTimestampGroupingWithFutureTimestamps() throws Exception {
        result.reset();
        result.expectedMessageCount(6);

        // Test with future timestamps (year 2030)
        long futureBaseTime = 1893456000000L; // 2030-01-01 00:00:00 UTC

        for (int i = 0; i < 6; i++) {
            long timestamp = futureBaseTime + (i * 60000); // 1 minute apart
            template.sendBodyAndHeader(
                    "direct:timestampGrouping",
                    "Message with future timestamp: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    timestamp);
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "edgeTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should handle future timestamps correctly");

        // Verify files have correct naming with future dates
        boolean foundFutureDateFile = false;
        for (S3Object s3Object : resp) {
            String key = s3Object.key();
            if (key.contains("20300101")) { // Check for year 2030
                foundFutureDateFile = true;
                break;
            }
        }
        assertTrue(foundFutureDateFile, "Should create files with future date naming");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Standard timestamp grouping route
                String timestampGroupingEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=edgeTest.txt"
                                + "&batchMessageNumber=3&timestampGroupingEnabled=true&timestampWindowSizeMillis=300000"
                                + "&timestampHeaderName=CamelMessageTimestamp",
                        name.get());

                from("direct:timestampGrouping").to(timestampGroupingEndpoint).to("mock:result");

                // Large window test (1 hour)
                String largeWindowEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=largeWindowTest.txt"
                                + "&batchMessageNumber=5&timestampGroupingEnabled=true&timestampWindowSizeMillis=3600000"
                                + "&timestampHeaderName=CamelMessageTimestamp",
                        name.get());

                from("direct:timestampGroupingLargeWindow")
                        .to(largeWindowEndpoint)
                        .to("mock:result");

                // Small window test (5 seconds)
                String smallWindowEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=smallWindowTest.txt"
                                + "&batchMessageNumber=3&timestampGroupingEnabled=true&timestampWindowSizeMillis=5000"
                                + "&timestampHeaderName=CamelMessageTimestamp&streamingUploadTimeout=2000",
                        name.get());

                from("direct:timestampGroupingSmallWindow")
                        .to(smallWindowEndpoint)
                        .to("mock:result");

                // Common route for listing objects
                String listEndpoint = String.format("aws2-s3://%s?autoCreateBucket=true", name.get());
                from("direct:listObjects").to(listEndpoint);
            }
        };
    }
}
