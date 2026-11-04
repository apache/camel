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
 * Exchanges sent at the same time through one streaming upload producer must not start two uploads. The mocked S3Client
 * keeps the completed objects by key and holds the first createMultipartUpload call until the test releases it.
 */
public class AWS2S3StreamUploadConcurrentExchangesTest {

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
    // the uploads neither completed nor aborted
    private final Map<String, String> openUploads = new ConcurrentHashMap<>();
    private final AtomicInteger uploadIds = new AtomicInteger();
    // a failure of an exchange sent from another thread
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    // the first createMultipartUpload call waits in the S3 client until released
    private final CountDownLatch firstCreateEntered = new CountDownLatch(1);
    private final CountDownLatch releaseFirstCreate = new CountDownLatch(1);

    private DefaultCamelContext camelContext;
    private AWS2S3StreamUploadProducer producer;

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
        when(configuration.getNamingStrategy()).thenReturn(AWSS3NamingStrategyEnum.progressive);
        when(configuration.getRestartingPolicy()).thenReturn(AWSS3RestartingPolicyEnum.override);
        // the defaults of the endpoint
        when(configuration.getBatchMessageNumber()).thenReturn(10);
        when(configuration.getBatchSize()).thenReturn(1000000);
        when(configuration.getBufferSize()).thenReturn(1000000);

        SdkHttpResponse httpResponse = SdkHttpResponse.builder().statusCode(200).build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CreateMultipartUploadRequest request = invocation.getArgument(0);
            int id = uploadIds.incrementAndGet();
            if (id == 1) {
                firstCreateEntered.countDown();
                assertTrue(releaseFirstCreate.await(20, TimeUnit.SECONDS), "the first upload was not released");
            }
            String uploadId = "upload-" + id;
            keysByUploadId.put(uploadId, request.key());
            partsByUploadId.put(uploadId, new ByteArrayOutputStream());
            openUploads.put(uploadId, request.key());
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
            openUploads.remove(request.uploadId());
            // like S3: the completed object replaces any object of the same key
            bucket.put(keysByUploadId.get(request.uploadId()),
                    partsByUploadId.get(request.uploadId()).toString(StandardCharsets.UTF_8));
            return CompleteMultipartUploadResponse.builder().eTag("final-etag").sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenAnswer(invocation -> {
            AbortMultipartUploadRequest request = invocation.getArgument(0);
            openUploads.remove(request.uploadId());
            return AbortMultipartUploadResponse.builder().build();
        });

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @AfterEach
    public void tearDown() {
        releaseFirstCreate.countDown();
        camelContext.stop();
    }

    @Test
    public void anExchangeThatStartedAnUploadWhileAnotherRanDoesNotReplaceAnObject() throws Exception {
        producer.start();

        // exchange 0 waits in createMultipartUpload while 10 other exchanges arrive (batchMessageNumber=10)
        sendWhileTheFirstUploadIsCreated("message-0\n", () -> send(1, 10));
        send(11, 9);
        producer.stop();

        assertNull(failure.get(), () -> "an exchange failed: " + failure.get());
        List<String> stored = storedLines();
        for (int i = 0; i < 20; i++) {
            assertEquals(1, Collections.frequency(stored, "message-" + i),
                    "message-" + i + " must be stored once, but the bucket has " + bucket);
        }
        assertEquals(20, stored.size(), "every message sent must be in the bucket: " + bucket);
        assertEquals(Collections.emptyMap(), openUploads, "every upload is completed or aborted");
    }

    @Test
    public void theFirstPartOfALargeBodyIsNotLostWhenAnotherExchangeOpensTheUpload() throws Exception {
        // multipart upload; the body of exchange A is one part and a half (the reads of the producer are 4096 bytes)
        when(configuration.isMultiPartUpload()).thenReturn(true);
        when(configuration.getPartSize()).thenReturn(8192L);
        String large = "A".repeat(8191) + "\n" + "B".repeat(4095) + "\n";
        producer.start();

        // the large exchange reads its first part and waits in createMultipartUpload while a small exchange arrives
        sendWhileTheFirstUploadIsCreated(large, () -> send(0, 1));
        producer.stop();

        assertNull(failure.get(), () -> "an exchange failed: " + failure.get());
        String all = String.join("", bucket.values());
        assertTrue(all.contains(large), "the large body must be stored whole, but the bucket has "
                                        + bucket.keySet() + " with " + all.length() + " characters");
        assertTrue(all.contains("message-0\n"), "the small body must be stored");
        assertEquals(large.length() + "message-0\n".length(), all.length(), "nothing else is stored");
        assertEquals(Collections.emptyMap(), openUploads, "every upload is completed or aborted");
    }

    private void sendWhileTheFirstUploadIsCreated(String firstBody, Runnable others) throws Exception {
        Thread first = new Thread(() -> {
            try {
                Exchange exchange = new DefaultExchange(camelContext);
                exchange.getIn().setBody(firstBody);
                producer.process(exchange);
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "first");
        first.start();
        assertTrue(firstCreateEntered.await(20, TimeUnit.SECONDS));

        Thread other = new Thread(others, "others");
        other.start();
        // the other exchanges either ran (the first one holds no lock) or wait for the first one
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> !other.isAlive() || other.getState() == Thread.State.WAITING);
        releaseFirstCreate.countDown();
        first.join(20000);
        other.join(20000);
        assertFalse(first.isAlive() || other.isAlive(), "the exchanges did not complete");
    }

    private void send(int from, int count) {
        for (int i = from; i < from + count; i++) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getIn().setBody("message-" + i + "\n");
            try {
                producer.process(exchange);
            } catch (Throwable e) {
                failure.set(e);
            }
        }
    }

    private List<String> storedLines() {
        return bucket.values().stream().flatMap(content -> Arrays.stream(content.split("\n"))).toList();
    }
}
