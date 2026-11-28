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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ExecutorServiceManager;
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
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A failed upload must neither drop the data of exchanges that already returned success (the upload of the
 * streamingUploadTimeout task) nor keep the data of the exchange that failed (the upload of an exchange, which is
 * redelivered). The timeout task is not scheduled: the test runs it. The mocked S3Client keeps the parts of each
 * multipart upload by part number, as S3 does (a part uploaded again replaces the one with the same number), stores the
 * parts listed by completeMultipartUpload, and answers NoSuchUpload (404) for a completed or aborted upload.
 */
class AWS2S3StreamUploadFailedFlushTest {

    private static final String TIMEOUT_CHECKER = "S3_Streaming_Upload_Timeout_Checker";
    private static final long WINDOW_TIMESTAMP = 1_700_000_000_000L;

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    // the bucket: completed objects by key
    private final Map<String, String> bucket = new ConcurrentHashMap<>();
    private final Map<String, String> keysByUploadId = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, byte[]>> partsByUploadId = new ConcurrentHashMap<>();
    // open, completed or aborted
    private final Map<String, String> uploadStatus = new ConcurrentHashMap<>();
    private final AtomicInteger uploadIds = new AtomicInteger();
    // the next uploadPart calls that answer 503, after the given number of calls that succeed
    private final AtomicInteger uploadPartsBeforeFailure = new AtomicInteger();
    private final AtomicInteger failingUploadParts = new AtomicInteger();
    private final AtomicInteger failingCompletes = new AtomicInteger();
    // the failures given to the exception handler of the endpoint
    private final List<String> handledFailures = new CopyOnWriteArrayList<>();
    // whether the exception handler of the endpoint throws
    private final AtomicBoolean failingExceptionHandler = new AtomicBoolean();

    private DefaultCamelContext camelContext;
    private Runnable timeoutTask;
    private AWS2S3StreamUploadProducer producer;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();
        // the producer gets this scheduler for its timeout task, which the test runs itself
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> {
                    timeoutTask = invocation.getArgument(0);
                    return mock(ScheduledFuture.class);
                });
        // a scheduler without threads: shut down and terminated, whichever way the producer shuts it down
        when(scheduler.isShutdown()).thenReturn(true);
        when(scheduler.isTerminated()).thenReturn(true);
        when(scheduler.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
        ExecutorServiceManager manager = spy(camelContext.getExecutorServiceManager());
        doReturn(scheduler).when(manager).newSingleThreadScheduledExecutor(any(), eq(TIMEOUT_CHECKER));
        camelContext.setExecutorServiceManager(manager);
        camelContext.start();

        ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);
        doAnswer(invocation -> {
            handledFailures.add(invocation.getArgument(0));
            if (failingExceptionHandler.get()) {
                throw new IllegalStateException("the exception handler failed");
            }
            return null;
        }).when(exceptionHandler).handleException(any(String.class), any(Throwable.class));

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);
        when(endpoint.getExceptionHandler()).thenReturn(exceptionHandler);

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
        when(configuration.getTimestampHeaderName()).thenReturn(Exchange.MESSAGE_TIMESTAMP);
        when(configuration.getTimestampWindowSizeMillis()).thenReturn(300000L);

        SdkHttpResponse ok = SdkHttpResponse.builder().statusCode(200).build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(Collections.emptyList()).isTruncated(false).build());

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CreateMultipartUploadRequest request = invocation.getArgument(0);
            String uploadId = "upload-" + uploadIds.incrementAndGet();
            keysByUploadId.put(uploadId, request.key());
            partsByUploadId.put(uploadId, new ConcurrentHashMap<>());
            uploadStatus.put(uploadId, "open");
            return CreateMultipartUploadResponse.builder().uploadId(uploadId).sdkHttpResponse(ok).build();
        });

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            UploadPartRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            if (!"open".equals(uploadStatus.get(request.uploadId()))) {
                throw noSuchUpload(request.uploadId());
            }
            if (uploadPartsBeforeFailure.getAndUpdate(n -> Math.max(0, n - 1)) == 0
                    && failingUploadParts.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw S3Exception.builder().statusCode(503).message("SlowDown").build();
            }
            partsByUploadId.get(request.uploadId()).put(request.partNumber(),
                    body.contentStreamProvider().newStream().readAllBytes());
            return UploadPartResponse.builder().eTag("etag-" + request.partNumber()).sdkHttpResponse(ok).build();
        });

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenAnswer(invocation -> {
            CompleteMultipartUploadRequest request = invocation.getArgument(0);
            String uploadId = request.uploadId();
            if (!"open".equals(uploadStatus.get(uploadId))) {
                throw noSuchUpload(uploadId);
            }
            if (failingCompletes.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw S3Exception.builder().statusCode(500).message("InternalError").build();
            }
            // the object is made of the listed parts; S3 discards the other parts of the upload
            Map<Integer, byte[]> uploaded = partsByUploadId.get(uploadId);
            Map<Integer, byte[]> listed = new TreeMap<>();
            for (CompletedPart part : request.multipartUpload().parts()) {
                listed.put(part.partNumber(), uploaded.get(part.partNumber()));
            }
            ByteArrayOutputStream object = new ByteArrayOutputStream();
            listed.values().forEach(object::writeBytes);
            uploadStatus.put(uploadId, "completed");
            bucket.put(keysByUploadId.get(uploadId), object.toString(StandardCharsets.UTF_8));
            return CompleteMultipartUploadResponse.builder().eTag("final-etag").sdkHttpResponse(ok).build();
        });

        when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenAnswer(invocation -> {
            AbortMultipartUploadRequest request = invocation.getArgument(0);
            if (!"open".equals(uploadStatus.get(request.uploadId()))) {
                throw noSuchUpload(request.uploadId());
            }
            uploadStatus.put(request.uploadId(), "aborted");
            return AbortMultipartUploadResponse.builder().build();
        });

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    private static S3Exception noSuchUpload(String uploadId) {
        return (S3Exception) S3Exception.builder().statusCode(404)
                .message("NoSuchUpload: the upload " + uploadId + " was completed or aborted").build();
    }

    private void send(Object body, Long timestamp) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(body);
        if (timestamp != null) {
            exchange.getIn().setHeader(Exchange.MESSAGE_TIMESTAMP, timestamp);
        }
        producer.process(exchange);
    }

    // a body read 10 bytes at a time, as a stream from the network gives it
    private static InputStream readInSmallChunks(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 10));
            }
        };
    }

    private void startWithTimeout() {
        when(configuration.getStreamingUploadTimeout()).thenReturn(1000L);
        producer.start();
        assertNotNull(timeoutTask, "the producer scheduled its timeout task");
    }

    @Test
    void timeoutUploadKeepsTheDataWhenUploadingItFails() throws Exception {
        startWithTimeout();
        send("message-0\n", null);

        failingUploadParts.set(1);
        timeoutTask.run();
        assertEquals("open", uploadStatus.get("upload-1"));
        assertTrue(bucket.isEmpty());

        timeoutTask.run();
        assertEquals("completed", uploadStatus.get("upload-1"));
        assertEquals(Map.of("camel.txt", "message-0\n"), bucket);
        assertEquals(List.of(), handledFailures);
        producer.stop();
    }

    @Test
    void nextExchangeUploadsTheDataKeptByAFailedTimeoutUpload() throws Exception {
        when(configuration.getBatchMessageNumber()).thenReturn(2);
        startWithTimeout();
        send("message-0\n", null);

        failingUploadParts.set(1);
        timeoutTask.run();

        // the second exchange of the upload completes it
        send("message-1\n", null);
        assertEquals(Map.of("camel.txt", "message-0\nmessage-1\n"), bucket);
        assertEquals(1, uploadIds.get());
        assertEquals(List.of(), handledFailures);
        producer.stop();
    }

    @Test
    void timeoutUploadIsAbortedAndReportedAfterThreeFailedRuns() throws Exception {
        startWithTimeout();
        send("message-0\n", null);

        failingUploadParts.set(3);
        timeoutTask.run();
        timeoutTask.run();
        assertEquals("open", uploadStatus.get("upload-1"));
        assertEquals(List.of(), handledFailures);

        timeoutTask.run();
        assertEquals("aborted", uploadStatus.get("upload-1"));
        assertEquals(1, handledFailures.size());
        String failure = handledFailures.get(0);
        assertTrue(failure.contains("1 exchange(s)") && failure.contains("camel.txt") && failure.contains("upload-1"),
                failure);

        // the next exchange starts a new upload
        send("message-1\n", null);
        timeoutTask.run();
        assertEquals("completed", uploadStatus.get("upload-2"));
        assertEquals(Map.of("camel.txt", "message-1\n"), bucket);
        producer.stop();
    }

    @Test
    void timeoutUploadWhoseCompletionFailsIsReported() throws Exception {
        startWithTimeout();
        send("message-0\n", null);

        failingCompletes.set(1);
        timeoutTask.run();
        // completeUpload aborts the upload when completing it fails
        assertEquals("aborted", uploadStatus.get("upload-1"));
        assertEquals(1, handledFailures.size());
        assertTrue(handledFailures.get(0).contains("camel.txt"), handledFailures.get(0));
        producer.stop();
    }

    @Test
    void timeoutTaskKeepsRunningWhenTheExceptionHandlerFails() throws Exception {
        failingExceptionHandler.set(true);
        startWithTimeout();
        send("message-0\n", null);

        failingCompletes.set(1);
        // an exception out of the task would stop scheduleAtFixedRate from running it again
        assertDoesNotThrow(timeoutTask::run);
        assertEquals("aborted", uploadStatus.get("upload-1"));
        assertEquals(1, handledFailures.size());

        // the aborted upload is dropped: the next exchange starts a new upload, which the next run stores
        send("message-1\n", null);
        assertDoesNotThrow(timeoutTask::run);
        assertEquals("completed", uploadStatus.get("upload-2"));
        assertEquals(Map.of("camel.txt", "message-1\n"), bucket);
        producer.stop();
    }

    @Test
    void uploadedPartStartsTheCountOfFailedTimeoutRunsAgain() throws Exception {
        when(configuration.isMultiPartUpload()).thenReturn(true);
        when(configuration.getPartSize()).thenReturn(20L);
        startWithTimeout();
        send("message-0\n", null);

        failingUploadParts.set(2);
        timeoutTask.run();
        timeoutTask.run();

        // S3 answers again: the next exchange fills part 1 with the kept data and uploads it
        String body = "C".repeat(15);
        send(readInSmallChunks(body), null);

        // one more failed run is the first one for the data buffered now
        failingUploadParts.set(1);
        timeoutTask.run();
        assertEquals("open", uploadStatus.get("upload-1"));
        assertEquals(List.of(), handledFailures);

        timeoutTask.run();
        assertEquals(Map.of("camel.txt", "message-0\n" + body), bucket);
        producer.stop();
    }

    @Test
    void timeoutUploadOfATimestampWindowKeepsTheDataWhenUploadingItFails() throws Exception {
        when(configuration.isTimestampGroupingEnabled()).thenReturn(true);
        startWithTimeout();
        send("message-0\n", WINDOW_TIMESTAMP);

        failingUploadParts.set(1);
        timeoutTask.run();
        assertEquals("open", uploadStatus.get("upload-1"));
        assertTrue(bucket.isEmpty());

        timeoutTask.run();
        assertEquals("completed", uploadStatus.get("upload-1"));
        assertEquals(List.of("message-0\n"), List.copyOf(bucket.values()));
        assertEquals(List.of(), handledFailures);
        producer.stop();
    }

    @Test
    void failedExchangeIsNotStoredAgainWhenItIsRedelivered() throws Exception {
        when(configuration.getBatchMessageNumber()).thenReturn(2);
        producer.start();
        send("message-0\n", null);

        // the second exchange completes the upload, and uploading the part fails
        failingUploadParts.set(1);
        assertThrows(S3Exception.class, () -> send("message-1\n", null));

        // the redelivery of the exchange
        send("message-1\n", null);
        producer.stop();
        assertEquals(Map.of("camel.txt", "message-0\nmessage-1\n"), bucket);
    }

    @Test
    void failedExchangeOfAMultipartUploadIsNotStoredAgainWhenItIsRedelivered() throws Exception {
        when(configuration.isMultiPartUpload()).thenReturn(true);
        when(configuration.getPartSize()).thenReturn(20L);
        producer.start();
        send("message-0\n", null);

        // the exchange fills part 1 (with the 10 bytes of message-0) and part 2; uploading part 2 fails
        String body = "B".repeat(30);
        uploadPartsBeforeFailure.set(1);
        failingUploadParts.set(1);
        assertThrows(S3Exception.class, () -> send(readInSmallChunks(body), null));

        // the redelivery of the exchange
        send(readInSmallChunks(body), null);
        producer.stop();
        assertEquals(Map.of("camel.txt", "message-0\n" + body), bucket);
    }
}
