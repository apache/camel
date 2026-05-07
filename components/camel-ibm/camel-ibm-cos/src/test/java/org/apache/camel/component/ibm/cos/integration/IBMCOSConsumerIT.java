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
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.cos.IBMCOSConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for IBM COS Consumer.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.cos.apiKey", matches = ".*",
                                 disabledReason = "IBM COS API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.serviceInstanceId", matches = ".*",
                                 disabledReason = "IBM COS Service Instance ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.endpointUrl", matches = ".*",
                                 disabledReason = "IBM COS Endpoint URL not provided")
})
public class IBMCOSConsumerIT extends IBMCOSTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testConsumeObject() throws Exception {
        mockResult.expectedMessageCount(1);

        final String testKey = "consumer-test.txt";
        final String testContent = "Hello from Consumer Test!";

        // Upload an object
        byte[] contentBytes = testContent.getBytes();
        com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata metadata
                = new com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata();
        metadata.setContentLength(contentBytes.length);
        cosClient.putObject(bucketName, testKey, new ByteArrayInputStream(contentBytes), metadata);

        // Wait for consumer to pick it up
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);

        Exchange exchange = mockResult.getExchanges().get(0);
        assertNotNull(exchange);

        // Verify headers
        assertEquals(testKey, exchange.getMessage().getHeader(IBMCOSConstants.KEY, String.class));
        assertEquals(bucketName, exchange.getMessage().getHeader(IBMCOSConstants.BUCKET_NAME, String.class));
        assertNotNull(exchange.getMessage().getHeader(IBMCOSConstants.E_TAG));
        assertNotNull(exchange.getMessage().getHeader(IBMCOSConstants.LAST_MODIFIED));

        // Verify body
        InputStream is = exchange.getMessage().getBody(InputStream.class);
        assertNotNull(is);
        String retrievedContent = IOHelper.loadText(is).trim();
        assertEquals(testContent, retrievedContent);

        // Object should be deleted after consumption (default deleteAfterRead=true)
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(cosClient.doesObjectExist(bucketName, testKey),
                        "Object should be deleted after read"));
    }

    @Test
    public void testConsumeMultipleObjects() throws Exception {
        mockResult.expectedMinimumMessageCount(3);

        // Upload multiple objects
        for (int i = 1; i <= 3; i++) {
            final String key = "multi-test-" + i + ".txt";
            final String content = "Content " + i;
            byte[] bytes = content.getBytes();
            com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata meta
                    = new com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata();
            meta.setContentLength(bytes.length);
            cosClient.putObject(bucketName, key, new ByteArrayInputStream(bytes), meta);
        }

        // Wait for consumer to pick them up
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        assertTrue(mockResult.getExchanges().size() >= 3, "Should have consumed at least 3 messages");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF(buildEndpointUri() + "&deleteAfterRead=true&maxMessagesPerPoll=10&delay=2000")
                        .to("mock:result");
            }
        };
    }
}
