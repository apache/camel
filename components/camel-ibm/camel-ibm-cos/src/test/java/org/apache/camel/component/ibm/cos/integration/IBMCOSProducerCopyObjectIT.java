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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.cos.IBMCOSConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for copyObject operation.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.cos.apiKey", matches = ".*",
                                 disabledReason = "IBM COS API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.serviceInstanceId", matches = ".*",
                                 disabledReason = "IBM COS Service Instance ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.endpointUrl", matches = ".*",
                                 disabledReason = "IBM COS Endpoint URL not provided")
})
public class IBMCOSProducerCopyObjectIT extends IBMCOSTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testCopyObject() throws Exception {
        final String sourceKey = "source-file.txt";
        final String destinationKey = "destination-file.txt";
        final String testContent = "Content to be copied";

        // Put source object
        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, sourceKey);
                exchange.getIn().setBody(new ByteArrayInputStream(testContent.getBytes()));
            }
        });

        // Verify source exists
        assertTrue(cosClient.doesObjectExist(bucketName, sourceKey), "Source object should exist");

        // Copy object
        template.send("direct:copyObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, sourceKey);
                exchange.getIn().setHeader(IBMCOSConstants.BUCKET_DESTINATION_NAME, bucketName);
                exchange.getIn().setHeader(IBMCOSConstants.DESTINATION_KEY, destinationKey);
            }
        });

        // Verify destination exists
        assertTrue(cosClient.doesObjectExist(bucketName, destinationKey), "Destination object should exist after copy");

        // Verify both objects still exist
        assertTrue(cosClient.doesObjectExist(bucketName, sourceKey), "Source object should still exist after copy");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putObject")
                        .to(buildEndpointUri("putObject"));

                from("direct:copyObject")
                        .to(buildEndpointUri("copyObject"))
                        .to("mock:result");
            }
        };
    }
}
