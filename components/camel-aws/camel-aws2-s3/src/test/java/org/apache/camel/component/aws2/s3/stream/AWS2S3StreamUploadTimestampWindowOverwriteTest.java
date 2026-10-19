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
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(configuration.getStreamingUploadTimeout()).thenReturn(60000L);
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
}
