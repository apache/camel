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

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that the presigner of each download link gets its own default credentials provider. The producer closes the
 * presigner after each use, and closing the presigner closes its credentials provider, so a provider shared with other
 * clients would break them.
 */
class AWS2S3ProducerPresignerCredentialsTest {

    @Test
    void eachDownloadLinkUsesItsOwnDefaultCredentialsProvider() throws Exception {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        AWS2S3Configuration configuration = mock(AWS2S3Configuration.class);
        when(configuration.getOperation()).thenReturn(AWS2S3Operations.createDownloadLink);
        when(configuration.getBucketName()).thenReturn("test-bucket");
        when(configuration.getKeyName()).thenReturn("a.txt");
        when(configuration.getRegion()).thenReturn("eu-west-1");
        when(configuration.isUseDefaultCredentialsProvider()).thenReturn(true);
        AWS2S3Endpoint endpoint = mock(AWS2S3Endpoint.class);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getCamelContext()).thenReturn(camelContext);

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://test-bucket.s3.eu-west-1.amazonaws.com/a.txt").toURL());
        when(presignedRequest.isBrowserExecutable()).thenReturn(true);
        S3Presigner presigner = mock(S3Presigner.class);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        S3Presigner.Builder presignerBuilder = mock(S3Presigner.Builder.class, RETURNS_SELF);
        when(presignerBuilder.build()).thenReturn(presigner);

        AWS2S3Producer producer = new AWS2S3Producer(endpoint);
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
}
