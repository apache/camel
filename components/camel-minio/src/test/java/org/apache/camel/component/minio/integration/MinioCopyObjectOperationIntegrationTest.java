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

import io.minio.MinioClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.minio.MinioOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class MinioCopyObjectOperationIntegrationTest extends CamelTestSupport {

    @BindToRegistry("minioClient")
    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("https://play.min.io")
                    .credentials("Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG")
                    .build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
            exchange.getIn().setBody("Test");
        });

        template.send("direct:copyObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
            exchange.getIn().setHeader(MinioConstants.DESTINATION_OBJECT_NAME, "test1.txt");
            exchange.getIn().setHeader(MinioConstants.DESTINATION_BUCKET_NAME, "mycamel1");
            exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.copyObject);
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamel?autoCreateBucket=false";

                from("direct:putObject").to(minioEndpoint);

                from("direct:copyObject").to(minioEndpoint).to("mock:result");

            }
        };
    }
}
