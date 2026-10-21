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
import java.lang.reflect.Constructor;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
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
public class AWS2S3StreamUploadTimestampWindowOverwriteTest {

    private static final long WINDOW_START = 1704096000000L; // 2024-01-01 08:00:00 UTC

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

    private AWS2S3StreamUploadProducer producer;
    private DefaultCamelContext camelContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);

        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.getKeyName()).thenReturn("grouped-messages.txt");
        when(configuration.isMultiPartUpload()).thenReturn(false);
        when(configuration.getPartSize()).thenReturn(25L * 1024 * 1024);
        when(configuration.getNamingStrategy()).thenReturn(AWSS3NamingStrategyEnum.progressive);
        when(configuration.getRestartingPolicy()).thenReturn(AWSS3RestartingPolicyEnum.override);
        // the defaults of the endpoint
        when(configuration.getBatchMessageNumber()).thenReturn(10);
        when(configuration.getBatchSize()).thenReturn(1000000);
        when(configuration.getBufferSize()).thenReturn(1000000);
        when(configuration.isTimestampGroupingEnabled()).thenReturn(true);
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(300000L);
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);

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

        // like S3: the keys with the prefix in lexical order, at most two per page
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            List<String> keys = bucket.keySet().stream().filter(key -> key.startsWith(request.prefix())).sorted()
                    .toList();
            int from = request.continuationToken() != null ? Integer.parseInt(request.continuationToken()) : 0;
            int to = Math.min(from + 2, keys.size());
            ListObjectsV2Response.Builder response = ListObjectsV2Response.builder()
                    .contents(keys.subList(from, to).stream().map(key -> S3Object.builder().key(key).build()).toList())
                    .isTruncated(to < keys.size());
            if (to < keys.size()) {
                response.nextContinuationToken(String.valueOf(to));
            }
            return response.build();
        });

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @Test
    public void everyMessageOfAWindowIsStoredWhenTheWindowIsUploadedMoreThanOnce() throws Exception {
        // the example of the documentation: a message every 10 seconds, in 5 minute windows, so 30 messages per
        // window; with batchMessageNumber=10 the window is uploaded after the 10th, the 20th and the 30th message
        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String body = "message-" + i + "\n";
            sent.add(body);
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody(body);
            exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, WINDOW_START + i * 10000L);
            producer.process(exchange);
        }

        assertEquals(3, uploadIds.get(), "the window is uploaded three times");
        assertEquals(3, bucket.size(),
                "each upload of the window must be its own object, but the bucket has " + bucket.keySet());
        StringBuilder stored = new StringBuilder();
        bucket.keySet().stream().sorted().forEach(key -> stored.append(bucket.get(key)));
        List<String> storedMessages = new ArrayList<>();
        for (String line : stored.toString().split("\n")) {
            storedMessages.add(line + "\n");
        }
        assertEquals(sent.size(), storedMessages.size(), "every message sent must be in the bucket");
        assertEquals(sent.stream().sorted().toList(), storedMessages.stream().sorted().toList());
    }

    @Test
    public void everyMessageOfAWindowIsStoredWhenTheTimeoutUploadsTheWindowBeforeItEnds() throws Exception {
        // streamingUploadTimeout: the timer uploads the window, and more messages of the window come after it
        Runnable timeoutTask = timeoutTask();
        for (int i = 0; i < 10; i++) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody("message-" + i + "\n");
            exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, WINDOW_START + i * 10000L);
            producer.process(exchange);
            if (i == 4) {
                timeoutTask.run();
            }
        }
        timeoutTask.run();

        assertEquals(2, uploadIds.get(), "the window is uploaded twice");
        assertEquals(2, bucket.size(),
                "each upload of the window must be its own object, but the bucket has " + bucket.keySet());
        assertEquals("message-0\nmessage-1\nmessage-2\nmessage-3\nmessage-4\n",
                bucket.get("grouped-messages_20240101_0800_0800-0805.txt"));
        assertEquals("message-5\nmessage-6\nmessage-7\nmessage-8\nmessage-9\n",
                bucket.get("grouped-messages_20240101_0800_0800-0805-1.txt"));
    }

    private Runnable timeoutTask() throws Exception {
        Class<?> taskClass = Class.forName(AWS2S3StreamUploadProducer.class.getName() + "$StreamingUploadTimeoutTask");
        Constructor<?> constructor = taskClass.getDeclaredConstructor(AWS2S3StreamUploadProducer.class);
        constructor.setAccessible(true);
        return (Runnable) constructor.newInstance(producer);
    }

    @Test
    public void theFirstUploadOfAWindowKeepsItsName() throws Exception {
        for (int i = 0; i < 10; i++) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody("message-" + i + "\n");
            exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, WINDOW_START + i * 10000L);
            producer.process(exchange);
        }

        assertEquals(List.of("grouped-messages_20240101_0800_0800-0805.txt"), new ArrayList<>(bucket.keySet()));
    }

    @Test
    public void anUploadAfterARestartDoesNotReplaceTheObjectsOfTheWindow() throws Exception {
        // stopping the producer uploads the open window; after a restart (a new producer) the next upload of the
        // window must not replace that object
        producer.start();
        sendMessages(0, 5);
        producer.stop();
        assertEquals(List.of("grouped-messages_20240101_0800_0800-0805.txt"), new ArrayList<>(bucket.keySet()));

        producer = new AWS2S3StreamUploadProducer(endpoint);
        producer.start();
        sendMessages(5, 10);
        producer.stop();

        assertEquals("message-0\nmessage-1\nmessage-2\nmessage-3\nmessage-4\n",
                bucket.get("grouped-messages_20240101_0800_0800-0805.txt"));
        assertEquals("message-5\nmessage-6\nmessage-7\nmessage-8\nmessage-9\n",
                bucket.get("grouped-messages_20240101_0800_0800-0805-1.txt"));
        assertEquals(2, bucket.size(), "the bucket has " + bucket.keySet());
    }

    @Test
    public void theNextUploadAfterARestartFollowsTheHighestUploadInTheBucket() throws Exception {
        // listed in lexical order "-1", "-10", "-2": the highest number wins, not the last key listed
        bucket.put("grouped-messages_20240101_0800_0800-0805.txt", "a");
        bucket.put("grouped-messages_20240101_0800_0800-0805-1.txt", "b");
        bucket.put("grouped-messages_20240101_0800_0800-0805-10.txt", "c");
        bucket.put("grouped-messages_20240101_0800_0800-0805-2.txt", "d");
        // not uploads of the window
        bucket.put("grouped-messages_20240101_0800_0800-0805-99.log", "e");
        bucket.put("grouped-messages_20240101_0800_0800-0805-x.txt", "f");
        bucket.put("grouped-messages_20240101_0800_0800-0805-12-1.txt", "g");

        sendMessages(0, 10);

        assertEquals("message-0\nmessage-1\nmessage-2\nmessage-3\nmessage-4\nmessage-5\nmessage-6\nmessage-7\n"
                     + "message-8\nmessage-9\n",
                bucket.get("grouped-messages_20240101_0800_0800-0805-11.txt"));
        assertEquals(8, bucket.size(), "the bucket has " + bucket.keySet());
    }

    @Test
    public void theUploadsOfAWindowAreNumberedFromTheFirstWhenTheBucketCannotBeListed() throws Exception {
        // without s3:ListBucket the producer still uploads, numbering the uploads of the window from the first one
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());

        sendMessages(0, 20);

        assertEquals(2, bucket.size(), "the bucket has " + bucket.keySet());
        assertTrue(bucket.containsKey("grouped-messages_20240101_0800_0800-0805.txt"));
        assertTrue(bucket.containsKey("grouped-messages_20240101_0800_0800-0805-1.txt"));
    }

    private void sendMessages(int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody("message-" + i + "\n");
            exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, WINDOW_START + i * 10000L);
            producer.process(exchange);
        }
    }
}
