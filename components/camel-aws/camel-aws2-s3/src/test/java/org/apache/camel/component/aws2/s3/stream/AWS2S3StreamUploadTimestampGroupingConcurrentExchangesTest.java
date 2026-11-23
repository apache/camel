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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * With timestamp grouping, the bytes of an exchange must be stored whole: neither another exchange of the same window
 * nor the timeout flush may write or upload between two reads of its body. The body of the first exchange stops after
 * its first read (4096 bytes) until the test releases it. The mocked S3Client keeps the completed objects by key.
 */
class AWS2S3StreamUploadTimestampGroupingConcurrentExchangesTest {

    private static final long TIMESTAMP = 1_700_000_000_000L;
    private static final int READ = 4096;

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
    // a failure of an exchange sent from another thread
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    // the body of the first exchange waits after its first read until released
    private final CountDownLatch firstReadDone = new CountDownLatch(1);
    private final CountDownLatch releaseBody = new CountDownLatch(1);

    private DefaultCamelContext camelContext;
    private AWS2S3StreamUploadProducer producer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();
        camelContext.start();

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);

        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.getKeyName()).thenReturn("camel.txt");
        when(configuration.isMultiPartUpload()).thenReturn(false);
        when(configuration.getPartSize()).thenReturn(25L * 1024 * 1024);
        when(configuration.getNamingStrategy()).thenReturn(AWSS3NamingStrategyEnum.progressive);
        when(configuration.getRestartingPolicy()).thenReturn(AWSS3RestartingPolicyEnum.override);
        when(configuration.isTimestampGroupingEnabled()).thenReturn(true);
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(300000L);
        when(configuration.getBatchMessageNumber()).thenReturn(10);
        when(configuration.getBatchSize()).thenReturn(1000000);
        // the producer reads a body 4096 bytes at a time
        when(configuration.getBufferSize()).thenReturn(READ);

        SdkHttpResponse httpResponse = SdkHttpResponse.builder().statusCode(200).build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(Collections.emptyList()).isTruncated(false).build());

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
            ByteArrayOutputStream parts = partsByUploadId.get(request.uploadId());
            synchronized (parts) {
                parts.write(body.contentStreamProvider().newStream().readAllBytes());
            }
            return UploadPartResponse.builder().eTag("etag").sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CompleteMultipartUploadRequest request = invocation.getArgument(0);
            bucket.put(keysByUploadId.get(request.uploadId()),
                    partsByUploadId.get(request.uploadId()).toString(StandardCharsets.UTF_8));
            return CompleteMultipartUploadResponse.builder().eTag("final-etag").sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .thenReturn(AbortMultipartUploadResponse.builder().build());

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @AfterEach
    void tearDown() {
        releaseBody.countDown();
        camelContext.stop();
    }

    @Test
    void anotherExchangeOfTheWindowIsNotWrittenBetweenTheReadsOfAnExchange() throws Exception {
        producer.start();

        Thread first = sendSlowBody('A');
        Thread second = new Thread(() -> send("B".repeat(2 * READ)), "second");
        second.start();
        // the second exchange either ran (the first one holds no lock between its reads) or waits for the first one
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> !second.isAlive() || second.getState() == Thread.State.WAITING);
        releaseBody.countDown();
        first.join(20000);
        second.join(20000);
        assertFalse(first.isAlive() || second.isAlive(), "the exchanges did not complete");
        producer.stop();

        assertNull(failure.get(), () -> "an exchange failed: " + failure.get());
        assertEquals(List.of("8192 x A, 8192 x B"), objects(),
                "the window is one object with the bytes of each exchange in one piece");
    }

    @Test
    void theTimeoutFlushDoesNotCutAnExchangeIntoTwoObjects() throws Exception {
        when(configuration.getStreamingUploadTimeout()).thenReturn(50L);
        producer.start();

        Thread first = sendSlowBody('A');
        // the timeout task either uploaded the first read of the exchange or waits for the exchange
        await().atMost(20, TimeUnit.SECONDS).until(() -> !bucket.isEmpty() || timeoutTaskWaits());
        releaseBody.countDown();
        first.join(20000);
        assertFalse(first.isAlive(), "the exchange did not complete");
        producer.stop();

        assertNull(failure.get(), () -> "the exchange failed: " + failure.get());
        assertEquals(List.of("8192 x A"), objects(), "the exchange is stored whole in one object");
    }

    /**
     * Sends an exchange of the window whose body of 8192 times the given character waits after its first 4096 bytes,
     * and returns once the producer read them.
     */
    private Thread sendSlowBody(char c) throws InterruptedException {
        InputStream body = new InputStream() {
            private int pos;

            @Override
            public int read() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (pos >= 2 * READ) {
                    return -1;
                }
                if (pos == READ) {
                    firstReadDone.countDown();
                    try {
                        if (!releaseBody.await(20, TimeUnit.SECONDS)) {
                            throw new IOException("the body was not released");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
                int n = Math.min(len, READ - pos % READ);
                Arrays.fill(b, off, off + n, (byte) c);
                pos += n;
                return n;
            }
        };
        Thread thread = new Thread(() -> send(body), "first");
        thread.start();
        assertTrue(firstReadDone.await(20, TimeUnit.SECONDS), "the producer did not read the body");
        return thread;
    }

    private void send(Object body) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, TIMESTAMP);
        try {
            producer.process(exchange);
        } catch (Throwable e) {
            failure.set(e);
        }
    }

    private static boolean timeoutTaskWaits() {
        return Thread.getAllStackTraces().entrySet().stream()
                .anyMatch(e -> e.getKey().getState() == Thread.State.WAITING
                        && Arrays.stream(e.getValue())
                                .anyMatch(frame -> frame.getClassName().endsWith("$StreamingUploadTimeoutTask")));
    }

    /**
     * The objects of the bucket, each as its runs of one character, such as "4096 x A, 8192 x B".
     */
    private List<String> objects() {
        List<String> objects = new ArrayList<>();
        for (String content : bucket.values()) {
            List<String> runs = new ArrayList<>();
            int start = 0;
            for (int i = 1; i <= content.length(); i++) {
                if (i == content.length() || content.charAt(i) != content.charAt(start)) {
                    runs.add((i - start) + " x " + content.charAt(start));
                    start = i;
                }
            }
            objects.add(String.join(", ", runs));
        }
        return objects;
    }
}
