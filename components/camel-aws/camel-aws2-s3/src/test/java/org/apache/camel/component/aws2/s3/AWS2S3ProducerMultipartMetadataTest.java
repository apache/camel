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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With multiPartUpload=true, the user metadata of the {@code CamelAwsS3Metadata} header is stored with the object
 * whether the body is uploaded in parts or, when it is smaller than the part size, with a single put.
 */
class AWS2S3ProducerMultipartMetadataTest {

    private static final Map<String, String> METADATA = Map.of("origin", "camel", "checksum", "abc123");

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    private AWS2S3Producer producer;
    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);
        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.isMultiPartUpload()).thenReturn(true);
        when(configuration.getPartSize()).thenReturn(5L);
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-1").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("part").build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn((CompleteMultipartUploadResponse) CompleteMultipartUploadResponse.builder().eTag("etag")
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build())
                        .build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn((PutObjectResponse) PutObjectResponse.builder().eTag("etag")
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build())
                        .build());
        producer = new AWS2S3Producer(endpoint);
    }

    private Exchange exchange(String body) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "my-key");
        exchange.getIn().setHeader(AWS2S3Constants.METADATA, METADATA);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    void multipartUploadStoresTheUserMetadata() throws Exception {
        producer.process(exchange("twelve bytes"));

        ArgumentCaptor<CreateMultipartUploadRequest> captor = ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
        verify(s3Client).createMultipartUpload(captor.capture());
        verify(s3Client, times(3)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThat(captor.getValue().metadata()).isEqualTo(METADATA);
    }

    @Test
    void bodySmallerThanThePartSizeStoresTheUserMetadata() throws Exception {
        producer.process(exchange("four"));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        assertThat(captor.getValue().metadata()).isEqualTo(METADATA);
    }
}
