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

package org.apache.camel.component.ibm.cos.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.cos.IBMCOSConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Integration test for additional IBM COS operations: deleteObjects, getObjectRange, headBucket, createBucket,
 * deleteBucket.
 */
@EnabledIfSystemProperties({
    @EnabledIfSystemProperty(
            named = "camel.ibm.cos.apiKey",
            matches = ".*",
            disabledReason = "IBM COS API Key not provided"),
    @EnabledIfSystemProperty(
            named = "camel.ibm.cos.serviceInstanceId",
            matches = ".*",
            disabledReason = "IBM COS Service Instance ID not provided"),
    @EnabledIfSystemProperty(
            named = "camel.ibm.cos.endpointUrl",
            matches = ".*",
            disabledReason = "IBM COS Endpoint URL not provided")
})
public class IBMCOSProducerAdditionalOperationsIT extends IBMCOSTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testDeleteObjects() throws Exception {
        // Create multiple test objects
        final String key1 = "batch-delete-1.txt";
        final String key2 = "batch-delete-2.txt";
        final String key3 = "batch-delete-3.txt";

        // Upload test objects
        for (String key : new String[] {key1, key2, key3}) {
            template.send("direct:putObject", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setHeader(IBMCOSConstants.KEY, key);
                    exchange.getIn().setBody(new ByteArrayInputStream("Test content".getBytes()));
                }
            });
        }

        // Verify objects exist
        assertTrue(cosClient.doesObjectExist(bucketName, key1), "Object 1 should exist");
        assertTrue(cosClient.doesObjectExist(bucketName, key2), "Object 2 should exist");
        assertTrue(cosClient.doesObjectExist(bucketName, key3), "Object 3 should exist");

        // Delete multiple objects
        List<String> keysToDelete = new ArrayList<>();
        keysToDelete.add(key1);
        keysToDelete.add(key2);
        keysToDelete.add(key3);

        template.send("direct:deleteObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEYS_TO_DELETE, keysToDelete);
            }
        });

        // Verify objects are deleted
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(cosClient.doesObjectExist(bucketName, key1), "Object 1 should be deleted");
            assertFalse(cosClient.doesObjectExist(bucketName, key2), "Object 2 should be deleted");
            assertFalse(cosClient.doesObjectExist(bucketName, key3), "Object 3 should be deleted");
        });
    }

    @Test
    public void testGetObjectRange() throws Exception {
        final String testKey = "range-test.txt";
        final String testContent = "0123456789ABCDEFGHIJ"; // 20 characters

        // Upload test object
        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
                exchange.getIn().setBody(new ByteArrayInputStream(testContent.getBytes()));
            }
        });

        // Get partial object (bytes 5-9, should be "56789")
        Exchange getExchange = template.request("direct:getObjectRange", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
                exchange.getIn().setHeader(IBMCOSConstants.RANGE_START, 5L);
                exchange.getIn().setHeader(IBMCOSConstants.RANGE_END, 9L);
            }
        });

        assertNotNull(getExchange);
        InputStream is = getExchange.getMessage().getBody(InputStream.class);
        assertNotNull(is);
        String retrievedContent = IOHelper.loadText(is).trim();
        assertEquals("56789", retrievedContent, "Should retrieve only bytes 5-9");
    }

    @Test
    public void testHeadBucket() throws Exception {
        // Test bucket exists (our test bucket should exist)
        Exchange headExchange = template.request("direct:headBucket", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No headers needed, uses bucket from endpoint
            }
        });

        assertNotNull(headExchange);
        Boolean bucketExists = headExchange.getMessage().getBody(Boolean.class);
        assertTrue(bucketExists, "Test bucket should exist");
    }

    @Test
    public void testCreateAndDeleteBucket() throws Exception {
        final String testBucketName = "camel-test-create-" + System.currentTimeMillis();

        try {
            // Create bucket
            Exchange createExchange = template.request("direct:createBucket", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setHeader(IBMCOSConstants.BUCKET_NAME, testBucketName);
                }
            });

            assertNotNull(createExchange);

            // Verify bucket exists
            assertTrue(cosClient.doesBucketExistV2(testBucketName), "Created bucket should exist");

            // Delete bucket
            template.send("direct:deleteBucket", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setHeader(IBMCOSConstants.BUCKET_NAME, testBucketName);
                }
            });

            // Verify bucket is deleted
            assertFalse(cosClient.doesBucketExistV2(testBucketName), "Bucket should be deleted");

        } finally {
            // Cleanup in case test fails
            try {
                if (cosClient.doesBucketExistV2(testBucketName)) {
                    cosClient.deleteBucket(testBucketName);
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putObject").to(buildEndpointUri("putObject")).to("mock:result");

                from("direct:deleteObjects")
                        .to(buildEndpointUri("deleteObjects"))
                        .to("mock:result");

                from("direct:getObjectRange")
                        .to(buildEndpointUri("getObjectRange"))
                        .to("mock:result");

                from("direct:headBucket").to(buildEndpointUri("headBucket")).to("mock:result");

                from("direct:createBucket").to(buildEndpointUri("createBucket")).to("mock:result");

                from("direct:deleteBucket").to(buildEndpointUri("deleteBucket")).to("mock:result");
            }
        };
    }
}
