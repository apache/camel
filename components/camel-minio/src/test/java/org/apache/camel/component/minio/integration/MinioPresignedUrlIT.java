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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.minio.MinioClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class MinioPresignedUrlIT extends MinioIntegrationTestSupport {
    @BindToRegistry("minioClient")
    MinioClient client = MinioClient.builder()
            .endpoint("http://" + service.host(), service.port(), false)
            .credentials(service.accessKey(), service.secretKey())
            .build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    MinioPresignedUrlIT() throws IOException {
    }

    @Test
    void createDownloadLinkTest() throws Exception {
        result.expectedMessageCount(1);
        result.message(0).body().contains("test.txt");
        result.message(0).body().contains("X-Amz-Expires=60");

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
            exchange.getIn().setBody("Test");
        });
        template.send("direct:createDownloadLink", exchange -> {
            Map<String, Object> header = new HashMap<>();
            header.put(MinioConstants.OBJECT_NAME, "test.txt");
            header.put(MinioConstants.PRESIGNED_URL_EXPIRATION_TIME, 60);
            exchange.getIn().setHeaders(header);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createUploadLinkTest() throws Exception {
        result.expectedMessageCount(1);
        result.message(0).body().contains("test.txt");
        result.message(0).body().contains("X-Amz-Expires=3600");

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "test.txt");
            exchange.getIn().setBody("Test");
        });
        template.send("direct:createUploadLink", exchange -> {
            Map<String, Object> header = new HashMap<>();
            header.put(MinioConstants.OBJECT_NAME, "test.txt");
            header.put(MinioConstants.PRESIGNED_URL_EXPIRATION_TIME, 60 * 60);
            exchange.getIn().setHeaders(header);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamel?autoCreateBucket=true";

                from("direct:createDownloadLink")
                        .to("minio://mycamel?minioClient=#minioClient&operation=createDownloadLink")
                        .to("mock:result");
                from("direct:createUploadLink")
                        .to("minio://mycamel?minioClient=#minioClient&operation=createUploadLink")
                        .to("mock:result");

            }
        };
    }
}
