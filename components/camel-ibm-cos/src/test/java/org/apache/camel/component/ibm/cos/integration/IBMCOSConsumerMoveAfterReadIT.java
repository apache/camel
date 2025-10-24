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

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.cos.IBMCOSConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for IBM COS Consumer moveAfterRead functionality.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.cos.apiKey", matches = ".*",
                                 disabledReason = "IBM COS API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.serviceInstanceId", matches = ".*",
                                 disabledReason = "IBM COS Service Instance ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.endpointUrl", matches = ".*",
                                 disabledReason = "IBM COS Endpoint URL not provided")
})
public class IBMCOSConsumerMoveAfterReadIT extends IBMCOSTestSupport {

    private static String destinationBucketName;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @BeforeAll
    public static void setUpDestinationBucket() {
        // Call parent first to setup client and main bucket
        IBMCOSTestSupport.setUpCosClientAndBucket();

        // Create destination bucket for move operations
        destinationBucketName = bucketName + "-dest";
        try {
            cosClient.createBucket(destinationBucketName);
        } catch (Exception e) {
            throw e;
        }
    }

    @AfterAll
    public static void tearDownDestinationBucket() {
        // Clean up destination bucket first
        if (cosClient != null && destinationBucketName != null) {
            try {
                // Delete all objects in the destination bucket
                cosClient.listObjectsV2(destinationBucketName).getObjectSummaries()
                        .forEach(obj -> cosClient.deleteObject(destinationBucketName, obj.getKey()));

                // Delete the destination bucket
                cosClient.deleteBucket(destinationBucketName);
            } catch (Exception e) {
            }
        }

        // Call parent to clean up main test bucket
        IBMCOSTestSupport.tearDownTestBucket();
    }

    @Test
    public void testMoveAfterRead() throws Exception {
        mockResult.expectedMessageCount(1);

        final String testKey = "move-test.txt";
        final String testContent = "Content to be moved";

        // Upload an object
        byte[] contentBytes = testContent.getBytes();
        com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata metadata
                = new com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata();
        metadata.setContentLength(contentBytes.length);
        cosClient.putObject(bucketName, testKey, new ByteArrayInputStream(contentBytes), metadata);

        // Verify source object exists
        assertTrue(cosClient.doesObjectExist(bucketName, testKey), "Source object should exist before consumption");

        // Wait for consumer to pick it up
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        Exchange exchange = mockResult.getExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(testKey, exchange.getMessage().getHeader(IBMCOSConstants.KEY, String.class));

        // Verify object was moved
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertFalse(cosClient.doesObjectExist(bucketName, testKey),
                            "Source object should not exist after move");
                    // The route is configured with destinationBucketPrefix=moved-
                    String expectedDestinationKey = "moved-" + testKey;
                    assertTrue(cosClient.doesObjectExist(destinationBucketName, expectedDestinationKey),
                            "Destination object should exist after move at: " + expectedDestinationKey);
                });
    }

    @Test
    public void testMoveAfterReadWithPrefix() throws Exception {
        mockResult.expectedMessageCount(1);

        final String testKey = "prefix-move-test.txt";
        final String testContent = "Content with prefix";
        final String prefix = "moved-";

        // Upload an object
        byte[] contentBytes = testContent.getBytes();
        com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata metadata
                = new com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata();
        metadata.setContentLength(contentBytes.length);
        cosClient.putObject(bucketName, testKey, new ByteArrayInputStream(contentBytes), metadata);

        // Wait for consumer to pick it up
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        // Verify object was moved with prefix
        String expectedDestinationKey = prefix + testKey;
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(cosClient.doesObjectExist(destinationBucketName, expectedDestinationKey),
                        "Destination object with prefix should exist: " + expectedDestinationKey));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF(buildEndpointUri()
                      + "&deleteAfterRead=false&moveAfterRead=true&destinationBucket="
                      + destinationBucketName + "&destinationBucketPrefix=moved-&delay=2000&maxMessagesPerPoll=10")
                        .to("mock:result");
            }
        };
    }
}
