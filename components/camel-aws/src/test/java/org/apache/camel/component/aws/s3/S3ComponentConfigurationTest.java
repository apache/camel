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
package org.apache.camel.component.aws.s3;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class S3ComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?accessKey=xxx&secretKey=yyy");
        
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonS3Client());
        assertNull(endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(10, endpoint.getMaxMessagesPerPoll());
    }
    
    @Test
    public void createEndpointWithMinimalConfigurationAndProvidedClient() throws Exception {
        AmazonS3ClientMock mock = new AmazonS3ClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonS3Client", mock);
        
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?amazonS3Client=#amazonS3Client");
        
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertNull(endpoint.getConfiguration().getAccessKey());
        assertNull(endpoint.getConfiguration().getSecretKey());
        assertSame(mock, endpoint.getConfiguration().getAmazonS3Client());
        assertNull(endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(10, endpoint.getMaxMessagesPerPoll());
    }
    
    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?accessKey=xxx&secretKey=yyy&region=us-west-1&deleteAfterRead=false&maxMessagesPerPoll=1");
        
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonS3Client());
        assertEquals("us-west-1", endpoint.getConfiguration().getRegion());
        assertFalse(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(1, endpoint.getMaxMessagesPerPoll());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutBucketName() throws Exception {
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-s3:// ");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-sns://MyTopic?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-sns://MyTopic?accessKey=xxx");
    }
}