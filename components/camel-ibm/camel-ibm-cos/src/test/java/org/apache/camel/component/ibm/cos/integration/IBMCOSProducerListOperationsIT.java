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
import java.util.List;

import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for listObjects and listBuckets operations.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.cos.apiKey", matches = ".*",
                                 disabledReason = "IBM COS API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.serviceInstanceId", matches = ".*",
                                 disabledReason = "IBM COS Service Instance ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.cos.endpointUrl", matches = ".*",
                                 disabledReason = "IBM COS Endpoint URL not provided")
})
public class IBMCOSProducerListOperationsIT extends IBMCOSTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testListObjects() throws Exception {
        mockResult.expectedMessageCount(1);

        // Create some test objects
        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, "file1.txt");
                exchange.getIn().setBody(new ByteArrayInputStream("Content 1".getBytes()));
            }
        });

        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, "file2.txt");
                exchange.getIn().setBody(new ByteArrayInputStream("Content 2".getBytes()));
            }
        });

        // List objects
        Exchange listExchange = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No additional headers needed
            }
        });

        assertNotNull(listExchange);
        List<S3ObjectSummary> objects = listExchange.getMessage().getBody(List.class);
        assertNotNull(objects);
        assertTrue(objects.size() >= 2, "Should have at least 2 objects");

        // Verify objects
        boolean foundFile1 = objects.stream().anyMatch(obj -> "file1.txt".equals(obj.getKey()));
        boolean foundFile2 = objects.stream().anyMatch(obj -> "file2.txt".equals(obj.getKey()));
        assertTrue(foundFile1, "Should find file1.txt");
        assertTrue(foundFile2, "Should find file2.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testListObjectsWithPrefix() throws Exception {
        // Create test objects with different prefixes
        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, "prefix1/file1.txt");
                exchange.getIn().setBody(new ByteArrayInputStream("Content 1".getBytes()));
            }
        });

        template.send("direct:putObject", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.KEY, "prefix2/file2.txt");
                exchange.getIn().setBody(new ByteArrayInputStream("Content 2".getBytes()));
            }
        });

        // List objects with prefix
        Exchange listExchange = template.request("direct:listObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IBMCOSConstants.PREFIX, "prefix1/");
            }
        });

        List<S3ObjectSummary> objects = listExchange.getMessage().getBody(List.class);
        assertNotNull(objects);

        // Should only find objects with prefix1
        for (S3ObjectSummary obj : objects) {
            assertTrue(obj.getKey().startsWith("prefix1/"), "All objects should start with prefix1/");
        }
    }

    @Test
    public void testListBuckets() throws Exception {
        // List all buckets
        Exchange listExchange = template.request("direct:listBuckets", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No headers needed
            }
        });

        assertNotNull(listExchange);
        assertNotNull(listExchange.getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putObject")
                        .to(buildEndpointUri("putObject"));

                from("direct:listObjects")
                        .to(buildEndpointUri("listObjects"))
                        .to("mock:result");

                from("direct:listBuckets")
                        .to(buildEndpointUri("listBuckets"))
                        .to("mock:result");
            }
        };
    }
}
