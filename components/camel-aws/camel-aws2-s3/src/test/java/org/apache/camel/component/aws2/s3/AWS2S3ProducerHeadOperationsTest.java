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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that headBucket and headObject take the bucket and the key from the endpoint configuration, or from the
 * headers, as the other operations do.
 */
public class AWS2S3ProducerHeadOperationsTest {

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

    @Test
    public void headBucketUsesTheBucketOfTheEndpoint() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.headBucket);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        ArgumentCaptor<HeadBucketRequest> captor = ArgumentCaptor.forClass(HeadBucketRequest.class);
        verify(s3Client).headBucket(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals(Boolean.TRUE, exchange.getMessage().getHeader(AWS2S3Constants.BUCKET_EXISTS));
    }

    @Test
    public void headBucketUsesTheOverrideBucketNameHeader() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.headBucket);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.OVERRIDE_BUCKET_NAME, "other-bucket");

        producer.process(exchange);

        ArgumentCaptor<HeadBucketRequest> captor = ArgumentCaptor.forClass(HeadBucketRequest.class);
        verify(s3Client).headBucket(captor.capture());
        assertEquals("other-bucket", captor.getValue().bucket());
    }

    @Test
    public void headObjectUsesTheKeyNameOption() throws Exception {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.headObject);
        when(configuration.getKeyName()).thenReturn("a.txt");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        Exchange exchange = new DefaultExchange(camelContext);

        producer.process(exchange);

        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("a.txt", captor.getValue().key());
    }

    @Test
    public void headObjectWithoutKeyNamesTheKey() {
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.headObject);

        Exchange exchange = new DefaultExchange(camelContext);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
        assertTrue(e.getMessage().contains("Key"), e.getMessage());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }
}
