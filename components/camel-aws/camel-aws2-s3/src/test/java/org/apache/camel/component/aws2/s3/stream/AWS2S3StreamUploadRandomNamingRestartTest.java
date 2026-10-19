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
package org.apache.camel.component.aws2.s3.stream;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * With timestamp grouping, a window whose upload is completed (by batchMessageNumber, batchSize or the streaming upload
 * timeout) and then gets more messages is uploaded again. The new object must not replace the first one: a bucket keeps
 * one object per key. Uses a mocked S3 client that keeps the completed objects by key, like a bucket.
 */
public class AWS2S3StreamUploadRandomNamingRestartTest {

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    // the bucket: completed objects by key
    private final Map<String, String> bucket = new ConcurrentHashMap<>();
    private final Map<String, String> keysByUploadId = new ConcurrentHashMap<>();
    private final Map<String, ByteArrayOutputStream> partsByUploadId = new ConcurrentHashMap<>();
    private final AtomicInteger uploadIds = new AtomicInteger();

    private DefaultCamelContext camelContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);

        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.getKeyName()).thenReturn("camel.txt");
        when(configuration.isMultiPartUpload()).thenReturn(false);
        when(configuration.getPartSize()).thenReturn(25L * 1024 * 1024);
        when(configuration.getNamingStrategy()).thenReturn(AWSS3NamingStrategyEnum.random);
        when(configuration.getRestartingPolicy()).thenReturn(AWSS3RestartingPolicyEnum.override);
        // the defaults of the endpoint
        when(configuration.getBatchMessageNumber()).thenReturn(10);
        when(configuration.getBatchSize()).thenReturn(1000000);
        when(configuration.getBufferSize()).thenReturn(1000000);

        SdkHttpResponse httpResponse = SdkHttpResponse.builder().statusCode(200).build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CreateMultipartUploadRequest request = invocation.getArgument(0);
            String uploadId = "upload-" + uploadIds.incrementAndGet();
            keysByUploadId.put(uploadId, request.key());
            partsByUploadId.put(uploadId, new ByteArrayOutputStream());
            return CreateMultipartUploadResponse.builder().uploadId(uploadId).sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            UploadPartRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            partsByUploadId.get(request.uploadId()).write(body.contentStreamProvider().newStream().readAllBytes());
            return UploadPartResponse.builder().eTag("etag").sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CompleteMultipartUploadRequest request = invocation.getArgument(0);
            // like S3: the completed object replaces any object of the same key
            bucket.put(keysByUploadId.get(request.uploadId()),
                    partsByUploadId.get(request.uploadId()).toString(StandardCharsets.UTF_8));
            return CompleteMultipartUploadResponse.builder().eTag("final-etag").sdkHttpResponse(httpResponse).build();
        });
    }

    @Test
    public void aProducerStartedAgainDoesNotReplaceTheObjectsOfTheFirstOne() throws Exception {
        // the first run of the route: 15 messages, one object of 10 messages and 5 messages flushed at stop
        AWS2S3StreamUploadProducer first = new AWS2S3StreamUploadProducer(endpoint);
        first.start();
        send(first, 0, 15);
        first.stop();
        assertEquals(2, bucket.size(), "the first run wrote two objects: " + bucket.keySet());

        // the route is started again: a new producer, same endpoint options
        AWS2S3StreamUploadProducer second = new AWS2S3StreamUploadProducer(endpoint);
        second.start();
        send(second, 15, 15);
        second.stop();

        assertEquals(4, uploadIds.get(), "four uploads were completed");
        assertEquals(4, bucket.size(), "every upload must be its own object, but the bucket has " + bucket.keySet());
        List<String> stored = new ArrayList<>();
        for (String content : bucket.values()) {
            for (String line : content.split("\n")) {
                stored.add(line);
            }
        }
        assertEquals(30, stored.size(), "every message sent must be in the bucket: " + bucket);
    }

    @Test
    public void everyObjectHasTheKeyNameAndARandomPart() throws Exception {
        AWS2S3StreamUploadProducer producer = new AWS2S3StreamUploadProducer(endpoint);
        send(producer, 0, 20);

        assertEquals(2, bucket.size(), bucket.keySet().toString());
        for (String key : bucket.keySet()) {
            assertTrue(key.matches("camel-[0-9a-f-]{36}\\.txt"), key);
        }
    }

    private void send(AWS2S3StreamUploadProducer producer, int from, int count) throws Exception {
        for (int i = from; i < from + count; i++) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody("message-" + i + "\n");
            producer.process(exchange);
        }
    }
}
