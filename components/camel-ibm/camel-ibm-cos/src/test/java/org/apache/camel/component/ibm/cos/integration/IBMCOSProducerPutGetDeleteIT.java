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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

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
 * Integration test for putObject, getObject, and deleteObject operations.
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
public class IBMCOSProducerPutGetDeleteIT extends IBMCOSTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testPutAndGetObject() throws Exception {
        mockResult.expectedMessageCount(2);

        final String testKey = "test-file.txt";
        final String testContent = "Hello from IBM Cloud Object Storage!";

        // Put object
        Exchange putExchange = template.request("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
                exchange.getIn().setBody(new ByteArrayInputStream(testContent.getBytes()));
            }
        });

        assertNotNull(putExchange);
        String etag = putExchange.getMessage().getHeader(IBMCOSConstants.E_TAG, String.class);
        assertNotNull(etag, "ETag should be returned after upload");

        // Get object
        Exchange getExchange = template.request("direct:getObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
            }
        });

        assertNotNull(getExchange);
        InputStream is = getExchange.getMessage().getBody(InputStream.class);
        assertNotNull(is);
        String retrievedContent = IOHelper.loadText(is).trim();
        assertEquals(testContent, retrievedContent);

        // Verify headers
        assertEquals(testKey, getExchange.getMessage().getHeader(IBMCOSConstants.KEY, String.class));
        assertNotNull(getExchange.getMessage().getHeader(IBMCOSConstants.E_TAG));
        assertNotNull(getExchange.getMessage().getHeader(IBMCOSConstants.CONTENT_LENGTH));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPutWithContentType() throws Exception {
        final String testKey = "test-json.json";
        final String testContent = "{\"message\":\"Hello IBM COS\"}";
        final String contentType = "application/json";

        // Put object with content type
        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
                exchange.getIn().setHeader(IBMCOSConstants.CONTENT_TYPE, contentType);
                exchange.getIn().setBody(new ByteArrayInputStream(testContent.getBytes()));
            }
        });

        // Get object and verify content type
        Exchange getExchange = template.request("direct:getObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
            }
        });

        String retrievedContentType = getExchange.getMessage().getHeader(IBMCOSConstants.CONTENT_TYPE, String.class);
        assertEquals(contentType, retrievedContentType);
    }

    @Test
    public void testDeleteObject() throws Exception {
        final String testKey = "test-delete.txt";

        // Put object first
        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
                exchange.getIn().setBody(new ByteArrayInputStream("Test content".getBytes()));
            }
        });

        // Verify object exists
        boolean existsBefore = cosClient.doesObjectExist(bucketName, testKey);
        assertEquals(true, existsBefore, "Object should exist before deletion");

        // Delete object
        template.send("direct:deleteObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, testKey);
            }
        });

        // Verify object is deleted
        boolean existsAfter = cosClient.doesObjectExist(bucketName, testKey);
        assertEquals(false, existsAfter, "Object should not exist after deletion");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putObject").to(buildEndpointUri("putObject")).to("mock:result");

                from("direct:getObject").to(buildEndpointUri("getObject")).to("mock:result");

                from("direct:deleteObject").to(buildEndpointUri("deleteObject")).to("mock:result");
            }
        };
    }
}
