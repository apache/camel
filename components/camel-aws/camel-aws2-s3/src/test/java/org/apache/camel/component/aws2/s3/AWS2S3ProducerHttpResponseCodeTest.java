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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests that verify the {@link Exchange#HTTP_RESPONSE_CODE} header is populated on the producer response, allowing
 * routes to react to the HTTP status code returned by S3.
 */
public class AWS2S3ProducerHttpResponseCodeTest {

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    private AWS2S3Producer producer;
    private DefaultCamelContext camelContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);
        when(configuration.getBucketName()).thenReturn("test-bucket");

        producer = new AWS2S3Producer(endpoint);
    }

    private static SdkHttpResponse httpStatus(int statusCode) {
        return SdkHttpResponse.builder().statusCode(statusCode).build();
    }

    @Test
    public void deleteObjectShouldSetHttpResponseCode() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.deleteObject);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn((DeleteObjectResponse) DeleteObjectResponse.builder()
                        .sdkHttpResponse(httpStatus(204))
                        .build());

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "my-key");

        producer.process(exchange);

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void deleteBucketShouldSetHttpResponseCode() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.deleteBucket);
        when(s3Client.deleteBucket(any(DeleteBucketRequest.class)))
                .thenReturn((DeleteBucketResponse) DeleteBucketResponse.builder()
                        .sdkHttpResponse(httpStatus(204))
                        .build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void createBucketShouldSetHttpResponseCode() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.createBucket);
        when(configuration.getRegion()).thenReturn("us-east-1");
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn((CreateBucketResponse) CreateBucketResponse.builder()
                        .sdkHttpResponse(httpStatus(200))
                        .build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void listObjectsShouldSetHttpResponseCode() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.listObjects);
        when(s3Client.listObjects(any(ListObjectsRequest.class)))
                .thenReturn((ListObjectsResponse) ListObjectsResponse.builder()
                        .sdkHttpResponse(httpStatus(200))
                        .build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void listBucketsShouldSetHttpResponseCode() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.listBuckets);
        when(s3Client.listBuckets())
                .thenReturn((ListBucketsResponse) ListBucketsResponse.builder()
                        .sdkHttpResponse(httpStatus(200))
                        .build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void deleteBucketPolicyShouldSetHttpResponseCode() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.deleteBucketPolicy);
        when(s3Client.deleteBucketPolicy(any(DeleteBucketPolicyRequest.class)))
                .thenReturn((DeleteBucketPolicyResponse) DeleteBucketPolicyResponse.builder()
                        .sdkHttpResponse(httpStatus(204))
                        .build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
}
