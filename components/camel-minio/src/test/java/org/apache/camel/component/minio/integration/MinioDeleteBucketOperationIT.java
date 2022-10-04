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
import org.junit.jupiter.api.Test;

class MinioDeleteBucketOperationIT extends MinioIntegrationTestSupport {

    @BindToRegistry("minioClient")
    MinioClient client = MinioClient.builder()
            .endpoint("http://" + service.host(), service.port(), false)
            .credentials(service.accessKey(), service.secretKey())
            .build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:listBuckets",
                exchange -> exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.listBuckets));

        template.send("direct:deleteBucket", exchange -> {
            exchange.getIn().setHeader(MinioConstants.BUCKET_NAME, "mycamel2");
            exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.deleteBucket);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamel2?autoCreateBucket=true";

                from("direct:listBuckets").to(minioEndpoint);

                from("direct:deleteBucket").to(minioEndpoint).to("mock:result");

            }
        };
    }
}
