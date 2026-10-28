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

import java.net.URI;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the presigners that the producer creates for download and upload links. The producer closes such a presigner
 * after each use, and closing the presigner closes its credentials provider, so each presigner gets its own default
 * credentials provider (a provider shared with other clients would break them), and the presigner is closed also when
 * presigning fails (its credentials provider would leak otherwise).
 */
class AWS2S3ProducerPresignerCredentialsTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();
    private final AWS2S3Configuration configuration = mock(AWS2S3Configuration.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3Presigner.Builder presignerBuilder = mock(S3Presigner.Builder.class, RETURNS_SELF);
    private final SdkClientException failure = SdkClientException.create("Unable to load credentials");

    @BeforeEach
    void setUp() {
        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.getKeyName()).thenReturn("a.txt");
        when(configuration.getRegion()).thenReturn("eu-west-1");
        when(configuration.isUseDefaultCredentialsProvider()).thenReturn(true);
        when(presignerBuilder.build()).thenReturn(presigner);
    }

    @Test
    void eachDownloadLinkUsesItsOwnDefaultCredentialsProvider() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://test-bucket.s3.eu-west-1.amazonaws.com/a.txt").toURL());
        when(presignedRequest.isBrowserExecutable()).thenReturn(true);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        AWS2S3Producer producer = createProducer(AWS2S3Operations.createDownloadLink);
        try (MockedStatic<S3Presigner> presigners = mockStatic(S3Presigner.class)) {
            presigners.when(S3Presigner::builder).thenReturn(presignerBuilder);

            producer.process(new DefaultExchange(camelContext));
            producer.process(new DefaultExchange(camelContext));
        }

        ArgumentCaptor<AwsCredentialsProvider> credentialsProviders = ArgumentCaptor.forClass(AwsCredentialsProvider.class);
        verify(presignerBuilder, times(2)).credentialsProvider(credentialsProviders.capture());
        List<AwsCredentialsProvider> providers = credentialsProviders.getAllValues();
        assertInstanceOf(DefaultCredentialsProvider.class, providers.get(0));
        assertNotSame(providers.get(0), providers.get(1));
        // each presigner is closed after use, and its credentials provider with it
        verify(presigner, times(2)).close();
    }

    @Test
    void presignerIsClosedWhenPresigningTheDownloadLinkFails() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(failure);

        assertSame(failure, processAndExpectFailure(AWS2S3Operations.createDownloadLink));
        verify(presigner).close();
    }

    @Test
    void presignerIsClosedWhenPresigningTheUploadLinkFails() {
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenThrow(failure);

        assertSame(failure, processAndExpectFailure(AWS2S3Operations.createUploadLink));
        verify(presigner).close();
    }

    @Test
    void presignerOfTheConfigurationIsNotClosedWhenPresigningFails() {
        when(configuration.getAmazonS3Presigner()).thenReturn(presigner);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(failure);

        assertSame(failure, processAndExpectFailure(AWS2S3Operations.createDownloadLink));
        // the presigner belongs to the user, who closes it
        verify(presigner, never()).close();
    }

    private AWS2S3Producer createProducer(AWS2S3Operations operation) {
        when(configuration.getOperation()).thenReturn(operation);
        AWS2S3Endpoint endpoint = mock(AWS2S3Endpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);
        return new AWS2S3Producer(endpoint);
    }

    private SdkClientException processAndExpectFailure(AWS2S3Operations operation) {
        AWS2S3Producer producer = createProducer(operation);
        Exchange exchange = new DefaultExchange(camelContext);
        try (MockedStatic<S3Presigner> presigners = mockStatic(S3Presigner.class)) {
            presigners.when(S3Presigner::builder).thenReturn(presignerBuilder);

            return assertThrows(SdkClientException.class, () -> producer.process(exchange));
        }
    }
}
