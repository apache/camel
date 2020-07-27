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

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class S3ComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalS3ClientConfiguration() throws Exception {

        AmazonS3ClientMock clientMock = new AmazonS3ClientMock();
        context.getRegistry().bind("amazonS3Client", clientMock);
        S3Component component = context.getComponent("aws-s3", S3Component.class);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertNotNull(endpoint.getConfiguration().getAmazonS3Client());
        assertNull(endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(10, endpoint.getMaxMessagesPerPoll());
        assertNull(endpoint.getConfiguration().getPolicy());
        assertNull(endpoint.getConfiguration().getPrefix());
        assertTrue(endpoint.getConfiguration().isIncludeBody());
    }

    @Test
    public void createEndpointWithMinimalS3ClientMisconfiguration() throws Exception {

        S3Component component = context.getComponent("aws-s3", S3Component.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-s3://MyBucket"));
    }

    @Test
    public void createEndpointWithCredentialsAndClientExistInRegistry() throws Exception {
        AmazonS3ClientMock clientMock = new AmazonS3ClientMock();
        context.getRegistry().bind("amazonS3Client", clientMock);
        S3Component component = context.getComponent("aws-s3", S3Component.class);
        S3Endpoint endpoint = (S3Endpoint) component
                .createEndpoint("aws-s3://MyBucket?accessKey=RAW(XXX)&secretKey=RAW(XXX)&autoDiscoverClient=false");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertNotSame(clientMock, endpoint.getConfiguration().getAmazonS3Client());
    }

    @Test
    public void createEndpointWithCredentialsAndClientExistInRegistryWithAutodiscover() throws Exception {
        AmazonS3ClientMock clientMock = new AmazonS3ClientMock();
        context.getRegistry().bind("amazonS3Client", clientMock);
        S3Component component = context.getComponent("aws-s3", S3Component.class);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?accessKey=RAW(XXX)&secretKey=RAW(XXX)");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertSame(clientMock, endpoint.getConfiguration().getAmazonS3Client());
    }
}
