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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import io.minio.MinioClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MinioObjectRangeOperationIT extends MinioIntegrationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MinioObjectRangeOperationIT.class);

    @BindToRegistry("minioClient")
    MinioClient client = MinioClient.builder()
            .endpoint("http://" + service.host(), service.port(), false)
            .credentials(service.accessKey(), service.secretKey())
            .build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    MinioObjectRangeOperationIT() {
    }

    @Test
    void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "element.txt");
            exchange.getIn()
                    .setBody("MinIO is a cloud storage server compatible with Amazon S3, released under Apache License v2. "
                             + "As an object store, MinIO can store unstructured data such as photos, videos, log files, backups and container images. "
                             + "The maximum size of an object is 5TB.");
        });

        template.send("direct:getPartialObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "element.txt");
            exchange.getIn().setHeader(MinioConstants.OFFSET, 0);
            exchange.getIn().setHeader(MinioConstants.LENGTH, 9);
        });
        MockEndpoint.assertIsSatisfied(context);

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://mycamelbucket?autoCreateBucket=true";
                String minioEndpoint1 = "minio://mycamelbucket?operation=getPartialObject";

                from("direct:putObject").to(minioEndpoint);

                from("direct:getPartialObject").to(minioEndpoint1).process(exchange -> {
                    InputStream minioPartialObject = exchange.getIn().getBody(InputStream.class);
                    LOG.info(readInputStream(minioPartialObject));

                }).to("mock:result");

            }
        };
    }

    private String readInputStream(InputStream minioObject) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader
                = new BufferedReader(new InputStreamReader(minioObject, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }
}
