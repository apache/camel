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
package org.apache.camel.component.aws.s3.client.impl;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import org.apache.camel.component.aws.s3.S3Configuration;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basic testing to ensure that the IAMOptimizedAWSS3ClientImplTest class is
 * returning a standard client that is capable of encryption given certain
 * parameters. This client is new to Camel as of 02-15-2018 and enables IAM
 * temporary credentials to improve security.
 */
public class IAMOptimizedAWSS3ClientImplTest {
    private static final int MAX_CONNECTIONS = 1;

    @Test
    public void iamOptimizedAWSS3ClientImplNoEncryption() {
        S3ClientIAMOptimizedImpl iamOptimizedAWSS3Client = new S3ClientIAMOptimizedImpl(getS3ConfigurationNoEncryption(), MAX_CONNECTIONS);
        AmazonS3 s3Client = iamOptimizedAWSS3Client.getS3Client();
        Assert.assertNotNull(s3Client);
        Assert.assertFalse(s3Client instanceof AmazonS3EncryptionClient);
    }

    @Test
    public void iamOptimizedAWSS3ClientImplUseEncryption() {
        S3ClientIAMOptimizedImpl iamOptimizedAWSS3Client = new S3ClientIAMOptimizedImpl(getS3ConfigurationUseEncryption(), MAX_CONNECTIONS);
        AmazonS3 s3Client = iamOptimizedAWSS3Client.getS3Client();
        Assert.assertNotNull(s3Client);
        Assert.assertTrue(s3Client instanceof AmazonS3EncryptionClient);
    }

    @Test
    public void iamOptimizedAWSS3ClientImplWithProxy() {
        S3ClientIAMOptimizedImpl iamOptimizedAWSS3Client = new S3ClientIAMOptimizedImpl(getS3ConfigurationUseProxy(), MAX_CONNECTIONS);
        AmazonS3 s3Client = iamOptimizedAWSS3Client.getS3Client();
        Assert.assertNotNull(s3Client);
        Assert.assertFalse(s3Client instanceof AmazonS3EncryptionClient);

        ClientConfiguration configuration = ((AmazonS3Client)s3Client).getClientConfiguration();
        Assert.assertEquals(Protocol.HTTP, configuration.getProxyProtocol());
    }

    private S3Configuration getS3ConfigurationNoEncryption() {
        S3Configuration s3Configuration = mock(S3Configuration.class);
        when(s3Configuration.getRegion()).thenReturn("US_EAST_1");
        when(s3Configuration.isUseEncryption()).thenReturn(false);
        return s3Configuration;
    }

    private S3Configuration getS3ConfigurationUseEncryption() {
        S3Configuration s3Configuration = mock(S3Configuration.class);
        when(s3Configuration.getRegion()).thenReturn("US_EAST_1");
        when(s3Configuration.isUseEncryption()).thenReturn(true);
        return s3Configuration;
    }

    private S3Configuration getS3ConfigurationUseProxy() {
        S3Configuration s3Configuration = mock(S3Configuration.class);
        when(s3Configuration.getRegion()).thenReturn("US_EAST_1");

        when(s3Configuration.hasProxyConfiguration()).thenReturn(true);
        when(s3Configuration.getProxyProtocol()).thenReturn(Protocol.HTTP);
        when(s3Configuration.getProxyHost()).thenReturn("PROXY_HOST");
        when(s3Configuration.getProxyPort()).thenReturn(1234);

        return s3Configuration;
    }
}
