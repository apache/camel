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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.apache.camel.component.minio.MinioOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.minio.MinioTestUtils.countObjectsInBucket;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

class MinioListObjectsOperationIT extends MinioIntegrationTestSupport {

    private static final String BUCKET_NAME = "mycamel2";

    @BindToRegistry("minioClient")
    MinioClient client = MinioClient.builder()
            .endpoint("http://" + service.host(), service.port(), false)
            .credentials(service.accessKey(), service.secretKey())
            .build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    MinioListObjectsOperationIT() {
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendIn() throws Exception {
        client.removeBucket(RemoveBucketArgs.builder().bucket(BUCKET_NAME).build());

        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());

        result.expectedMessageCount(1);

        template.send("direct:listBuckets",
                exchange -> exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.listBuckets));

        template.send("direct:addObject", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "CamelUnitTest2");
            exchange.getIn().setBody("This is my bucket content.");
            exchange.getIn().removeHeader(MinioConstants.MINIO_OPERATION);
        });

        Exchange exchange = template.request("direct:listObjects", exchange13 -> {
            exchange13.getIn().setHeader(MinioConstants.BUCKET_NAME, BUCKET_NAME);
            exchange13.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.listObjects);
        });

        Iterable<Result<Item>> respond = (Iterable<Result<Item>>) exchange.getMessage().getBody();
        Iterator<Result<Item>> respondSize = respond.iterator();
        Iterator<Result<Item>> respondIterator = respond.iterator();

        assertEquals(1, Iterators.size(respondSize));
        assertEquals("CamelUnitTest2", respondIterator.next().get().objectName());

        template.send("direct:deleteObject", ExchangePattern.InOnly, exchange12 -> {
            exchange12.getIn().setHeader(MinioConstants.OBJECT_NAME, "CamelUnitTest2");
            exchange12.getIn().setHeader(MinioConstants.BUCKET_NAME, BUCKET_NAME);
            exchange12.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.deleteObject);
        });

        template.send("direct:deleteBucket", exchange1 -> {
            exchange1.getIn().setHeader(MinioConstants.BUCKET_NAME, BUCKET_NAME);
            exchange1.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.deleteBucket);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteObjectsTest() throws Exception {

        client.removeBucket(RemoveBucketArgs.builder().bucket(BUCKET_NAME).build());

        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());

        final List<DeleteObject> objects = new ArrayList<>(20);

        // set up the environment
        for (int i = 0; i < 20; i++) {
            String currentDeleteObjectName = "CamelUnitTest-" + randomAlphanumeric(5);
            objects.add(new DeleteObject(currentDeleteObjectName));
            template.send("direct:addObject", ExchangePattern.InOnly, new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setHeader(MinioConstants.BUCKET_NAME, BUCKET_NAME);
                    exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, currentDeleteObjectName);
                    exchange.getIn().setBody("This is my bucket content.");
                    exchange.getIn().removeHeader(MinioConstants.MINIO_OPERATION);
                }
            });
        }

        assertEquals(20, countObjectsInBucket(client, BUCKET_NAME));

        // delete all objects of the bucket

        template.send("direct:deleteObjects", exchange -> {
            exchange.getIn().setHeader(MinioConstants.BUCKET_NAME, BUCKET_NAME);
            exchange.getIn().setHeader(MinioConstants.MINIO_OPERATION, MinioOperations.deleteObjects);
            exchange.getIn().setBody(RemoveObjectsArgs.builder().bucket(BUCKET_NAME).objects(objects));
        });

        assertEquals(0, countObjectsInBucket(client, BUCKET_NAME));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String minioEndpoint = "minio://" + BUCKET_NAME + "?autoCreateBucket=true";
                String minioEndpointPojoEnabled = minioEndpoint + "&pojoRequest=true";

                from("direct:listBucket").to(minioEndpoint);

                from("direct:addObject").to(minioEndpoint);

                from("direct:deleteObject").to(minioEndpoint);

                from("direct:deleteObjects").to(minioEndpointPojoEnabled);

                from("direct:listObjects").to(minioEndpoint);

                from("direct:deleteBucket").to(minioEndpoint).to("mock:result");

            }
        };
    }
}
