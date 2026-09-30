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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests that the producer determines the length of a body that is not a {@link java.io.File}, without failing on big
 * payloads and without losing content.
 */
public class AWS2S3ProducerPayloadLengthTest {

    // bigger than the buffer of a BufferedInputStream, which a mark/reset based length probe cannot handle
    private static final int PAYLOAD_SIZE = 1024 * 1024;

    @Mock
    private AWS2S3Endpoint endpoint;

    @Mock
    private AWS2S3Configuration configuration;

    @Mock
    private S3Client s3Client;

    private AWS2S3Producer producer;
    private DefaultCamelContext camelContext;
    private byte[] payload;

    private Long uploadedLength;
    private byte[] uploadedContent;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        camelContext = new DefaultCamelContext();

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        when(endpoint.getS3Client()).thenReturn(s3Client);
        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.getPartSize()).thenReturn(25L * 1024 * 1024);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            uploadedLength = request.contentLength();
            try (InputStream is = body.contentStreamProvider().newStream()) {
                uploadedContent = is.readAllBytes();
            }
            return PutObjectResponse.builder()
                    .sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build())
                    .build();
        });

        producer = new AWS2S3Producer(endpoint);

        payload = new byte[PAYLOAD_SIZE];
        new Random(42).nextBytes(payload);
    }

    @Test
    public void uploadPathBodyWithoutContentLength(@TempDir Path dir) throws Exception {
        Path file = Files.write(dir.resolve("big.bin"), payload);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "big.bin");
        exchange.getIn().setBody(file);

        producer.process(exchange);

        assertEquals(PAYLOAD_SIZE, uploadedLength);
        assertArrayEquals(payload, uploadedContent);
    }

    @Test
    public void multiPartUploadStreamBodyWithUnknownLength() throws Exception {
        when(configuration.isMultiPartUpload()).thenReturn(true);

        // a stream that does not support mark/reset, such as a network stream
        InputStream stream = new FilterInputStream(new ByteArrayInputStream(payload)) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(AWS2S3Constants.KEY, "big.bin");
        exchange.getIn().setBody(stream);

        producer.process(exchange);

        assertEquals(PAYLOAD_SIZE, uploadedLength);
        assertArrayEquals(payload, uploadedContent);
    }
}
