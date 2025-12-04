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

public class S3StreamUploadTimestampGroupingIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testTimestampGroupingBasic() throws Exception {
        result.reset();
        result.expectedMessageCount(20);

        // Send messages with timestamps in two different 5-minute windows
        // Window 1: 2024-01-01 08:00:00 to 08:05:00
        long baseTime1 = 1704096000000L; // 2024-01-01 08:00:00 UTC
        // Window 2: 2024-01-01 08:05:00 to 08:10:00
        long baseTime2 = 1704096300000L; // 2024-01-01 08:05:00 UTC

        // Send 10 messages for first window
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader(
                    "direct:timestampGroupingBasic",
                    "Message from window 1: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    baseTime1 + (i * 10000)); // spread within window
        }

        // Send 10 messages for second window
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader(
                    "direct:timestampGroupingBasic",
                    "Message from window 2: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    baseTime2 + (i * 10000)); // spread within window
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "basicTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(2, resp.size(), "Should create 2 files for 2 different timestamp windows");

        // Verify files have timestamp-based naming
        boolean foundWindow1File = false;
        boolean foundWindow2File = false;

        for (S3Object s3Object : resp) {
            String key = s3Object.key();
            if (key.contains("20240101_0800_0800-0805")) {
                foundWindow1File = true;
            } else if (key.contains("20240101_0805_0805-0810")) {
                foundWindow2File = true;
            }
        }

        assertTrue(foundWindow1File, "Should find file for first timestamp window");
        assertTrue(foundWindow2File, "Should find file for second timestamp window");
    }

    @Test
    public void testTimestampGroupingWithStringTimestamp() throws Exception {
        result.reset();
        result.expectedMessageCount(15);

        // Test with string timestamp headers
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        for (int i = 0; i < 15; i++) {
            template.sendBodyAndHeader(
                    "direct:timestampGrouping",
                    "Message with string timestamp: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    String.valueOf(baseTime + (i * 10000)));
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "timestampTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should create at least 1 file for timestamp window");
    }

    @Test
    public void testTimestampGroupingFallbackToCurrentTime() throws Exception {
        result.reset();
        result.expectedMessageCount(5);

        // Send messages without timestamp headers - should fall back to current time
        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:timestampGrouping", "Message without timestamp: " + i);
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "timestampTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should create at least 1 file even without timestamp header");
    }

    @Test
    public void testTimestampGroupingWithDateTimestamp() throws Exception {
        result.reset();
        result.expectedMessageCount(12);

        // Test with Date timestamp headers
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        for (int i = 0; i < 12; i++) {
            Date messageDate = new Date(baseTime + (i * 30000)); // 30 seconds apart
            template.sendBodyAndHeader(
                    "direct:timestampGrouping",
                    "Message with Date timestamp: " + i,
                    Exchange.MESSAGE_TIMESTAMP,
                    messageDate);
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "timestampTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should create at least 1 file for Date timestamps");
    }

    @Test
    public void testTimestampGroupingLargeTimeSpan() throws Exception {
        result.reset();
        result.expectedMessageCount(30);

        // Test spanning multiple time windows over 30 minutes
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        // Send messages across 6 different 5-minute windows (30 minutes total)
        for (int window = 0; window < 6; window++) {
            for (int msg = 0; msg < 5; msg++) {
                long timestamp = baseTime + (window * 300000) + (msg * 10000); // 5-minute windows
                template.sendBodyAndHeader(
                        "direct:timestampGroupingLargeSpan",
                        String.format("Window %d, Message %d", window, msg),
                        Exchange.MESSAGE_TIMESTAMP,
                        timestamp);
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "largeSpanTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(6, resp.size(), "Should create 6 files for 6 different timestamp windows");

        // Verify all files have timestamp-based naming pattern
        for (S3Object s3Object : resp) {
            String key = s3Object.key();
            assertTrue(
                    key.matches("largeSpanTest_\\d{8}_\\d{4}_\\d{4}-\\d{4}\\.txt"),
                    "File should have timestamp-based name pattern: " + key);
        }
    }

    @Test
    public void testTimestampGroupingWithCustomWindow() throws Exception {
        result.reset();
        result.expectedMessageCount(20);

        // Test with 1-minute windows instead of default 5-minute
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        // Send messages across 4 different 1-minute windows
        for (int window = 0; window < 4; window++) {
            for (int msg = 0; msg < 5; msg++) {
                long timestamp = baseTime + (window * 60000) + (msg * 5000); // 1-minute windows
                template.sendBodyAndHeader(
                        "direct:timestampGroupingCustomWindow",
                        String.format("1min Window %d, Message %d", window, msg),
                        Exchange.MESSAGE_TIMESTAMP,
                        timestamp);
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "customWindowTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(4, resp.size(), "Should create 4 files for 4 different 1-minute windows");
    }

    @Test
    public void testTimestampGroupingWithCustomHeaderName() throws Exception {
        result.reset();
        result.expectedMessageCount(8);

        // Test with custom timestamp header name
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC

        for (int i = 0; i < 8; i++) {
            template.sendBodyAndHeader(
                    "direct:timestampGroupingCustomHeader",
                    "Message with custom header: " + i,
                    "MyCustomTimestamp",
                    baseTime + (i * 30000));
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "customHeaderTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertTrue(resp.size() >= 1, "Should create at least 1 file with custom timestamp header");
    }

    @Test
    public void testTimestampGroupingMultipartUpload() throws Exception {
        result.reset();
        result.expectedMessageCount(6);

        // Test with multipart upload enabled and larger messages
        long baseTime = 1704096000000L; // 2024-01-01 08:00:00 UTC
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is line ").append(i).append(" of a large message for multipart testing.\n");
        }

        // Send large messages in two different windows
        for (int window = 0; window < 2; window++) {
            for (int msg = 0; msg < 3; msg++) {
                long timestamp = baseTime + (window * 300000) + (msg * 10000);
                template.sendBodyAndHeader(
                        "direct:timestampGroupingMultipart",
                        largeContent.toString() + " Window: " + window + ", Message: " + msg,
                        Exchange.MESSAGE_TIMESTAMP,
                        timestamp);
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange ex = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
                exchange.getIn().setHeader(AWS2S3Constants.PREFIX, "multipartTest");
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(2, resp.size(), "Should create 2 files for 2 different timestamp windows with multipart");

        // Verify files are reasonably large (indicating multipart worked)
        for (S3Object s3Object : resp) {
            assertTrue(s3Object.size() > 50000, "Multipart files should be substantial in size");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route for basic timestamp grouping test
                String basicTestEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=basicTest.txt"
                                + "&batchMessageNumber=5&timestampGroupingEnabled=true&timestampWindowSizeMillis=300000"
                                + "&timestampHeaderName=CamelMessageTimestamp",
                        name.get());

                from("direct:timestampGroupingBasic").to(basicTestEndpoint).to("mock:result");

                // Route for large span test
                String largeSpanEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=largeSpanTest.txt"
                                + "&batchMessageNumber=5&timestampGroupingEnabled=true&timestampWindowSizeMillis=300000"
                                + "&timestampHeaderName=CamelMessageTimestamp",
                        name.get());

                from("direct:timestampGroupingLargeSpan").to(largeSpanEndpoint).to("mock:result");

                // Route for timestamp grouping with 5-minute windows (other tests)
                String timestampGroupingEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=timestampTest.txt"
                                + "&batchMessageNumber=5&timestampGroupingEnabled=true&timestampWindowSizeMillis=300000"
                                + "&timestampHeaderName=CamelMessageTimestamp",
                        name.get());

                from("direct:timestampGrouping").to(timestampGroupingEndpoint).to("mock:result");

                // Route for timestamp grouping with custom 1-minute windows
                String customWindowEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=customWindowTest.txt"
                                + "&batchMessageNumber=5&timestampGroupingEnabled=true&timestampWindowSizeMillis=60000"
                                + "&timestampHeaderName=CamelMessageTimestamp",
                        name.get());

                from("direct:timestampGroupingCustomWindow")
                        .to(customWindowEndpoint)
                        .to("mock:result");

                // Route for timestamp grouping with custom header name
                String customHeaderEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=customHeaderTest.txt"
                                + "&batchMessageNumber=4&timestampGroupingEnabled=true&timestampWindowSizeMillis=300000"
                                + "&timestampHeaderName=MyCustomTimestamp",
                        name.get());

                from("direct:timestampGroupingCustomHeader")
                        .to(customHeaderEndpoint)
                        .to("mock:result");

                // Route for timestamp grouping with multipart upload
                String multipartEndpoint = String.format(
                        "aws2-s3://%s?autoCreateBucket=true&streamingUploadMode=true&keyName=multipartTest.txt"
                                + "&batchMessageNumber=3&timestampGroupingEnabled=true&timestampWindowSizeMillis=300000"
                                + "&timestampHeaderName=CamelMessageTimestamp&multiPartUpload=true&partSize=5242880",
                        name.get());

                from("direct:timestampGroupingMultipart").to(multipartEndpoint).to("mock:result");

                // Common route for listing objects
                String listEndpoint = String.format("aws2-s3://%s?autoCreateBucket=true", name.get());
                from("direct:listObjects").to(listEndpoint);
            }
        };
    }
}
