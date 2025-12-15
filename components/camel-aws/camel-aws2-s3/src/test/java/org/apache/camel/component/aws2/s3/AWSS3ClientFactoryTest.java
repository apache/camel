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

import org.apache.camel.component.aws2.s3.client.AWS2S3ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AWSS3ClientFactoryTest {

    @Test
    public void getS3ClientWithDefaultCredentials() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseDefaultCredentialsProvider(true);
        s3Configuration.setRegion("eu-west-1");
        S3Client s3Client = AWS2S3ClientFactory.getS3Client(s3Configuration);
        assertNotNull(s3Client);
        s3Client.close();
    }

    @Test
    public void getS3ClientWithStaticCredentials() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setAccessKey("testAccessKey");
        s3Configuration.setSecretKey("testSecretKey");
        s3Configuration.setRegion("eu-west-1");
        S3Client s3Client = AWS2S3ClientFactory.getS3Client(s3Configuration);
        assertNotNull(s3Client);
        s3Client.close();
    }

    @Test
    public void getS3ClientWithForcePathStyle() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseDefaultCredentialsProvider(true);
        s3Configuration.setRegion("eu-west-1");
        s3Configuration.setForcePathStyle(true);
        S3Client s3Client = AWS2S3ClientFactory.getS3Client(s3Configuration);
        assertNotNull(s3Client);
        s3Client.close();
    }

    @Test
    public void getS3ClientWithEndpointOverride() {
        AWS2S3Configuration s3Configuration = new AWS2S3Configuration();
        s3Configuration.setUseDefaultCredentialsProvider(true);
        s3Configuration.setRegion("eu-west-1");
        s3Configuration.setOverrideEndpoint(true);
        s3Configuration.setUriEndpointOverride("http://localhost:4566");
        S3Client s3Client = AWS2S3ClientFactory.getS3Client(s3Configuration);
        assertNotNull(s3Client);
        s3Client.close();
    }
}
