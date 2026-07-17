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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AWS2S3StreamUploadMultipartTest {

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    @Mock
    private ExecutorServiceManager executorServiceManager;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

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
        when(configuration.getKeyName()).thenReturn("test-file.txt");
        when(configuration.isMultiPartUpload()).thenReturn(true);
        when(configuration.getNamingStrategy()).thenReturn(AWSS3NamingStrategyEnum.random);
        when(configuration.getRestartingPolicy()).thenReturn(AWSS3RestartingPolicyEnum.override);
        when(configuration.getBatchMessageNumber()).thenReturn(10);

        SdkHttpResponse httpResponse = SdkHttpResponse.builder().statusCode(200).build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn((CreateMultipartUploadResponse) CreateMultipartUploadResponse.builder()
                        .uploadId("test-upload-id")
                        .sdkHttpResponse(httpResponse)
                        .build());

        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn((UploadPartResponse) UploadPartResponse.builder()
                        .eTag("test-etag")
                        .checksumCRC32("test-crc32")
                        .sdkHttpResponse(httpResponse)
                        .build());

        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn((CompleteMultipartUploadResponse) CompleteMultipartUploadResponse.builder()
                        .eTag("final-etag")
                        .sdkHttpResponse(httpResponse)
                        .build());

        producer = new AWS2S3StreamUploadProducer(endpoint);
    }

    @Test
    public void testBodyLargerThanPartSizeIsNotTruncated() throws Exception {
        int partSize = 8192;
        int batchSize = 4096;
        int bodySize = 12288;

        when(configuration.getPartSize()).thenReturn((long) partSize);
        when(configuration.getBatchSize()).thenReturn(batchSize);
        when(configuration.getBufferSize()).thenReturn(batchSize);

        byte[] body = new byte[bodySize];
        Arrays.fill(body, (byte) 0xAB);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(new ByteArrayInputStream(body));

        producer.process(exchange);

        ArgumentCaptor<RequestBody> requestBodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, atLeastOnce()).uploadPart(any(UploadPartRequest.class), requestBodyCaptor.capture());

        List<RequestBody> uploadedParts = requestBodyCaptor.getAllValues();
        long totalUploaded = 0;
        ByteArrayOutputStream allBytes = new ByteArrayOutputStream();
        for (RequestBody rb : uploadedParts) {
            byte[] partBytes = rb.contentStreamProvider().newStream().readAllBytes();
            allBytes.write(partBytes);
            totalUploaded += partBytes.length;
        }

        assertEquals(bodySize, totalUploaded, "All bytes should be uploaded without truncation");
        assertArrayEquals(body, allBytes.toByteArray(), "Uploaded content should match original body");
    }

    @Test
    public void testCreateMultipartUploadCalledOncePerUpload() throws Exception {
        int partSize = 4096;
        int batchSize = 20000;
        int bodySize = 12288;

        when(configuration.getPartSize()).thenReturn((long) partSize);
        when(configuration.getBatchSize()).thenReturn(batchSize);
        when(configuration.getBufferSize()).thenReturn(batchSize);

        byte[] body = new byte[bodySize];
        Arrays.fill(body, (byte) 0xCD);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(new ByteArrayInputStream(body));

        producer.process(exchange);

        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    public void testMultiplePartsUploadedCorrectly() throws Exception {
        int partSize = 4096;
        int batchSize = 20000;
        int bodySize = 12288;

        when(configuration.getPartSize()).thenReturn((long) partSize);
        when(configuration.getBatchSize()).thenReturn(batchSize);
        when(configuration.getBufferSize()).thenReturn(batchSize);

        byte[] body = new byte[bodySize];
        for (int i = 0; i < bodySize; i++) {
            body[i] = (byte) (i % 256);
        }

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(new ByteArrayInputStream(body));

        producer.process(exchange);

        ArgumentCaptor<UploadPartRequest> requestCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, atLeastOnce()).uploadPart(requestCaptor.capture(), bodyCaptor.capture());

        List<UploadPartRequest> requests = requestCaptor.getAllValues();
        List<RequestBody> bodies = bodyCaptor.getAllValues();

        ByteArrayOutputStream allBytes = new ByteArrayOutputStream();
        List<Integer> partNumbers = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            partNumbers.add(requests.get(i).partNumber());
            byte[] partBytes = bodies.get(i).contentStreamProvider().newStream().readAllBytes();
            allBytes.write(partBytes);
        }

        assertEquals(bodySize, allBytes.size(), "Total uploaded bytes should match body size");
        assertArrayEquals(body, allBytes.toByteArray(), "Uploaded content should match original body");

        for (int i = 0; i < partNumbers.size(); i++) {
            assertEquals(i + 1, partNumbers.get(i), "Part numbers should be sequential starting from 1");
        }

        assertEquals("test-upload-id", requests.get(0).uploadId());
        for (UploadPartRequest req : requests) {
            assertEquals("test-upload-id", req.uploadId(), "All parts should use the same upload ID");
        }
    }
}
