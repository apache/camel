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

import org.apache.camel.component.aws2.s3.client.AWS2CamelS3InternalClient;
import org.apache.camel.component.aws2.s3.client.AWS2S3ClientFactory;
import org.apache.camel.component.aws2.s3.client.impl.AWS2S3ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.s3.client.impl.AWS2S3ClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.s3.client.impl.AWS2S3ClientSessionTokenImpl;
import org.apache.camel.component.aws2.s3.client.impl.AWS2S3ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AWSS3ClientFactoryTest {

    @Test
    public void getStandardS3ClientDefault() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        AWS2CamelS3InternalClient awss3Client = AWS2S3ClientFactory.getAWSS3Client(s3Configuration);
        assertTrue(awss3Client instanceof AWS2S3ClientStandardImpl);
    }

    @Test
    public void getStandardS3Client() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseDefaultCredentialsProvider(false);
        AWS2CamelS3InternalClient awss3Client = AWS2S3ClientFactory.getAWSS3Client(s3Configuration);
        assertTrue(awss3Client instanceof AWS2S3ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedS3Client() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseDefaultCredentialsProvider(true);
        AWS2CamelS3InternalClient awss3Client = AWS2S3ClientFactory.getAWSS3Client(s3Configuration);
        assertTrue(awss3Client instanceof AWS2S3ClientIAMOptimizedImpl);
    }

    @Test
    public void getIAMProfileOptimizedS3Client() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseProfileCredentialsProvider(true);
        AWS2CamelS3InternalClient awss3Client = AWS2S3ClientFactory.getAWSS3Client(s3Configuration);
        assertTrue(awss3Client instanceof AWS2S3ClientIAMProfileOptimizedImpl);
    }

    @Test
    public void getSessionTokenS3Client() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseSessionCredentials(true);
        AWS2CamelS3InternalClient awss3Client = AWS2S3ClientFactory.getAWSS3Client(s3Configuration);
        assertTrue(awss3Client instanceof AWS2S3ClientSessionTokenImpl);
    }
}
