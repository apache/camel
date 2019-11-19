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
package org.apache.camel.component.aws.s3;

import org.apache.camel.component.aws.s3.client.S3Client;
import org.apache.camel.component.aws.s3.client.S3ClientFactory;
import org.apache.camel.component.aws.s3.client.impl.S3ClientIAMOptimizedImpl;
import org.apache.camel.component.aws.s3.client.impl.S3ClientStandardImpl;
import org.junit.Assert;
import org.junit.Test;

public class AWSS3ClientFactoryTest {
    private static final int MAX_CONNECTIONS = 1;

    @Test
    public void getStandardS3ClientDefault() {
        S3Configuration s3Configuration = new S3Configuration();
        S3Client awss3Client = S3ClientFactory.getAWSS3Client(s3Configuration, MAX_CONNECTIONS);
        Assert.assertTrue(awss3Client instanceof S3ClientStandardImpl);
    }

    @Test
    public void getStandardS3Client() {
        S3Configuration s3Configuration = new S3Configuration();
        s3Configuration.setUseIAMCredentials(false);
        S3Client awss3Client = S3ClientFactory.getAWSS3Client(s3Configuration, MAX_CONNECTIONS);
        Assert.assertTrue(awss3Client instanceof S3ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedS3Client() {
        S3Configuration s3Configuration = new S3Configuration();
        s3Configuration.setUseIAMCredentials(true);
        S3Client awss3Client = S3ClientFactory.getAWSS3Client(s3Configuration, MAX_CONNECTIONS);
        Assert.assertTrue(awss3Client instanceof S3ClientIAMOptimizedImpl);
    }
}
