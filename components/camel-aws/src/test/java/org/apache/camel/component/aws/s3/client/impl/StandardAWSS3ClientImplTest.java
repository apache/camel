/**
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import org.apache.camel.component.aws.s3.S3Configuration;
import org.junit.Assert;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basic testing to ensure that the StandardAWSS3ClientImpl class is returning a standard client that is
 * capable of encryption given certain parameters. These clients have been in existence for a long time, but haven't
 * been properly unit tested.
 */
public class StandardAWSS3ClientImplTest {
    private static final int MAX_CONNECTIONS = 1;
    private EncryptionMaterials encryptionMaterials = mock(EncryptionMaterials.class);

    @Test
    public void standardAWSS3ClientImplNoEncryption() {
        S3ClientStandardImpl standardAWSS3Client = new S3ClientStandardImpl(getS3ConfigurationNoEncryption(), MAX_CONNECTIONS);
        AmazonS3 s3Client = standardAWSS3Client.getS3Client();
        Assert.assertNotNull(s3Client);
        Assert.assertFalse(s3Client instanceof AmazonS3EncryptionClient);
    }

    @Test
    public void standardAWSS3ClientImplUseEncryption() {
        S3ClientStandardImpl standardAWSS3Client = new S3ClientStandardImpl(getS3ConfigurationUseEncryption(), MAX_CONNECTIONS);
        AmazonS3 s3Client = standardAWSS3Client.getS3Client();
        Assert.assertNotNull(s3Client);
        Assert.assertTrue(s3Client instanceof AmazonS3EncryptionClient);
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
}
