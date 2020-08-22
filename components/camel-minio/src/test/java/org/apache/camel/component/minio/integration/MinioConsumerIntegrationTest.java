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
package org.apache.camel.component.minio.integration;

import java.util.Properties;

import io.minio.MinioClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class MinioConsumerIntegrationTest extends CamelTestSupport {
    final Properties properties = MinioTestUtils.loadMinioPropertiesFile();

    @BindToRegistry("minioClient")
    MinioClient client = MinioClient.builder()
            .endpoint(properties.getProperty("endpoint"))
            .credentials(properties.getProperty("accessKey"), properties.getProperty("secretKey"))
            .region(properties.getProperty("region"))
            .build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    public MinioConsumerIntegrationTest() throws Exception {
    }

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(3);

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test1.txt");
            exchange.getIn().setBody("Test1");
        });

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test2.txt");
            exchange.getIn().setBody("Test2");
        });

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test3.txt");
            exchange.getIn().setBody("Test3");
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamel?autoCreateBucket=false";

                from("direct:putObject").startupOrder(1).to(minioEndpoint);
                from("minio://mycamel?moveAfterRead=true&destinationBucketName=camel-kafka-connector&autoCreateBucket=false")
                        .startupOrder(2).to("mock:result");

            }
        };
    }
}
