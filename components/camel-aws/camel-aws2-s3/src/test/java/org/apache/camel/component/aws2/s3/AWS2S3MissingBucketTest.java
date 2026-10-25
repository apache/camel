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
package org.apache.camel.component.aws2.s3;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A missing bucket is detected from the status code of the exception, also when the exception has no error details.
 */
public class AWS2S3MissingBucketTest {

    private final S3Client s3Client = mock(S3Client.class);
    private DefaultCamelContext context;

    @BeforeEach
    public void setUp() {
        // only the bucket "mybucket" exists; a missing bucket fails with a 404 without error details, as an
        // AwsServiceException can be built
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenAnswer(inv -> {
            HeadBucketRequest request = inv.getArgument(0);
            if ("mybucket".equals(request.bucket())) {
                return HeadBucketResponse.builder().build();
            }
            throw AwsServiceException.builder().statusCode(404).message("Not Found").build();
        });
        when(s3Client.createBucket(any(CreateBucketRequest.class))).thenReturn(CreateBucketResponse.builder().build());
        context = new DefaultCamelContext();
        context.getRegistry().bind("s3", s3Client);
        context.start();
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void endpointCreatesTheMissingBucket() throws Exception {
        Endpoint endpoint = context.getEndpoint("aws2-s3://newbucket?amazonS3Client=#s3&autoCreateBucket=true");

        endpoint.start();

        ArgumentCaptor<CreateBucketRequest> captor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(captor.capture());
        assertEquals("newbucket", captor.getValue().bucket());
        endpoint.stop();
    }

    @Test
    public void consumerCreatesTheMissingDestinationBucket() throws Exception {
        Endpoint endpoint = context.getEndpoint("aws2-s3://mybucket?amazonS3Client=#s3&autoCreateBucket=true"
                                                + "&moveAfterRead=true&destinationBucket=dest&initialDelay=3600000");
        endpoint.start();
        Consumer consumer = endpoint.createConsumer(exchange -> {
        });

        consumer.start();

        ArgumentCaptor<CreateBucketRequest> captor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(captor.capture());
        assertEquals("dest", captor.getValue().bucket());
        consumer.stop();
        endpoint.stop();
    }
}
