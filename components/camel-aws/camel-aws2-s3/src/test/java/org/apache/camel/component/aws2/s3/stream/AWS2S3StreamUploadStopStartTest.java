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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A streaming upload producer that is stopped and started again: the stop completes the open upload and the producer
 * starts a new one afterwards, and a stop that fails on S3 still stops the timeout task. The mocked S3Client keeps the
 * completed objects by key and, like S3, refuses parts for an upload that is completed or aborted.
 */
class AWS2S3StreamUploadStopStartTest {

    private static final String TIMEOUT_CHECKER = "S3_Streaming_Upload_Timeout_Checker";

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
    private final Set<String> openUploads = ConcurrentHashMap.newKeySet();
    private final AtomicInteger uploadIds = new AtomicInteger();
    // the next uploadPart calls that fail
    private final AtomicInteger failingUploadParts = new AtomicInteger();

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
        // the defaults of the endpoint: a small exchange is kept for the next ones
        when(configuration.getBatchMessageNumber()).thenReturn(10);
        when(configuration.getBatchSize()).thenReturn(1000000);
        when(configuration.getBufferSize()).thenReturn(1000000);

        SdkHttpResponse httpResponse = SdkHttpResponse.builder().statusCode(200).build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CreateMultipartUploadRequest request = invocation.getArgument(0);
            String uploadId = "upload-" + uploadIds.incrementAndGet();
            keysByUploadId.put(uploadId, request.key());
            partsByUploadId.put(uploadId, new ByteArrayOutputStream());
            openUploads.add(uploadId);
            return CreateMultipartUploadResponse.builder().uploadId(uploadId).sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            UploadPartRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            if (!openUploads.contains(request.uploadId())) {
                throw noSuchUpload(request.uploadId());
            }
            if (failingUploadParts.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw (S3Exception) S3Exception.builder().statusCode(503).message("Slow Down").build();
            }
            ByteArrayOutputStream parts = partsByUploadId.get(request.uploadId());
            synchronized (parts) {
                parts.write(body.contentStreamProvider().newStream().readAllBytes());
            }
            return UploadPartResponse.builder().eTag("etag").sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CompleteMultipartUploadRequest request = invocation.getArgument(0);
            if (!openUploads.remove(request.uploadId())) {
                throw noSuchUpload(request.uploadId());
            }
            bucket.put(keysByUploadId.get(request.uploadId()),
                    partsByUploadId.get(request.uploadId()).toString(StandardCharsets.UTF_8));
            return CompleteMultipartUploadResponse.builder().eTag("final-etag").sdkHttpResponse(httpResponse).build();
        });

        when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenAnswer(invocation -> {
            AbortMultipartUploadRequest request = invocation.getArgument(0);
            if (!openUploads.remove(request.uploadId())) {
                throw noSuchUpload(request.uploadId());
            }
            return AbortMultipartUploadResponse.builder().build();
        });

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @AfterEach
    void tearDown() {
        producer.stop();
        camelContext.stop();
    }

    @Test
    void anExchangeAfterAStopAndStartIsUploadedToANewObject() throws Exception {
        producer.start();
        send("message-0\n");
        producer.stop();
        assertEquals(Map.of("camel.txt", "message-0\n"), bucket, "the stop completes the upload");

        producer.start();
        send("message-1\n");
        producer.stop();

        assertEquals(Map.of("camel.txt", "message-0\n", "camel-1.txt", "message-1\n"), bucket,
                "the exchange sent after the start is stored in an object of its own");
        assertEquals(Set.of(), openUploads, "every upload is completed");
    }

    @Test
    void aStopThatFailsOnS3StopsTheTimeoutTask() throws Exception {
        // the timeout task does not run during the test: the test checks that it is stopped
        when(configuration.getStreamingUploadTimeout()).thenReturn(60000L);
        Set<Thread> others = timeoutCheckers();
        producer.start();
        assertEquals(1, timeoutCheckersOfTheProducer(others).size(), "the start runs the timeout task");
        send("message-0\n");

        failingUploadParts.set(1);
        assertThrows(S3Exception.class, producer::stop, "the upload of the stop fails");
        await().atMost(5, TimeUnit.SECONDS).until(() -> timeoutCheckersOfTheProducer(others).isEmpty());

        producer.start();
        assertEquals(1, timeoutCheckersOfTheProducer(others).size(), "the start after the failed stop runs one task");
        send("message-1\n");
        producer.stop();
        assertEquals("message-1\n", bucket.get("camel.txt"),
                "the exchange sent after the start is uploaded, not added to the failed upload");
        assertEquals(Set.of(), timeoutCheckersOfTheProducer(others), "the stop ends the timeout task");
    }

    private void send(String body) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(body);
        producer.process(exchange);
    }

    private static Set<Thread> timeoutCheckers() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().contains(TIMEOUT_CHECKER))
                .collect(Collectors.toSet());
    }

    private static Set<Thread> timeoutCheckersOfTheProducer(Set<Thread> others) {
        Set<Thread> checkers = timeoutCheckers();
        checkers.removeAll(others);
        return checkers;
    }

    private static S3Exception noSuchUpload(String uploadId) {
        return (S3Exception) S3Exception.builder().statusCode(404).message("NoSuchUpload " + uploadId).build();
    }
}
