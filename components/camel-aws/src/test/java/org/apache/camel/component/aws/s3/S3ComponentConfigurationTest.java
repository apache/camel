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

import com.amazonaws.regions.Regions;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class S3ComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithMinimalS3ClientConfiguration() throws Exception {
        AmazonS3ClientMock mock = new AmazonS3ClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonS3Client", mock);
        
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?amazonS3Client=#amazonS3Client&accessKey=xxx&secretKey=yyy");
        
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonS3Client());
        assertNull(endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(10, endpoint.getMaxMessagesPerPoll());
        assertNull(endpoint.getConfiguration().getAmazonS3Endpoint());
        assertNull(endpoint.getConfiguration().getPolicy());
        assertNull(endpoint.getConfiguration().getPrefix());
        assertTrue(endpoint.getConfiguration().isIncludeBody());
    }

    @Test
    public void createEndpointWithMinimalCredentialsConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?accessKey=xxx&secretKey=yyy");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonS3Client());
        assertNull(endpoint.getConfiguration().getRegion());
        assertTrue(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(10, endpoint.getMaxMessagesPerPoll());
        assertNull(endpoint.getConfiguration().getAmazonS3Endpoint());
        assertNull(endpoint.getConfiguration().getPolicy());
        assertNull(endpoint.getConfiguration().getPrefix());
        assertTrue(endpoint.getConfiguration().isIncludeBody());
    }

    @Test
    public void createEndpointWithMinimalArnConfiguration() throws Exception {
        AmazonS3ClientMock mock = new AmazonS3ClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonS3Client", mock);
        
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://arn:aws:s3:::MyBucket?amazonS3Client=#amazonS3Client&accessKey=xxx&secretKey=yyy");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
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
        assertNull(endpoint.getConfiguration().getAmazonS3Endpoint());
        assertNull(endpoint.getConfiguration().getPolicy());
        assertNull(endpoint.getConfiguration().getPrefix());
        assertTrue(endpoint.getConfiguration().isIncludeBody());
    }

    @Test
    public void createEndpointWithMaximalConfiguration() throws Exception {
        AmazonS3ClientMock mock = new AmazonS3ClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonS3Client", mock);
    
        S3Component component = new S3Component(context);
        S3Endpoint endpoint = (S3Endpoint) component.createEndpoint("aws-s3://MyBucket?amazonS3Client=#amazonS3Client&amazonS3Endpoint=sns.eu-west-1.amazonaws.com"
                + "&accessKey=xxx&secretKey=yyy&region=us-west-1&deleteAfterRead=false&maxMessagesPerPoll=1&policy=%7B%22Version%22%3A%222008-10-17%22,%22Id%22%3A%22Policy4324355464%22,"
                + "%22Statement%22%3A%5B%7B%22Sid%22%3A%22Stmt456464646477%22,%22Action%22%3A%5B%22s3%3AGetObject%22%5D,%22Effect%22%3A%22Allow%22,"
                + "%22Resource%22%3A%5B%22arn%3Aaws%3As3%3A%3A%3Amybucket/some/path/*%22%5D,%22Principal%22%3A%7B%22AWS%22%3A%5B%22*%22%5D%7D%7D%5D%7D&storageClass=REDUCED_REDUNDANCY"
                + "&prefix=confidential&includeBody=false");

        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonS3Client());
        assertEquals("us-west-1", endpoint.getConfiguration().getRegion());
        assertFalse(endpoint.getConfiguration().isDeleteAfterRead());
        assertEquals(1, endpoint.getMaxMessagesPerPoll());
        assertEquals("sns.eu-west-1.amazonaws.com", endpoint.getConfiguration().getAmazonS3Endpoint());
        assertEquals("{\"Version\":\"2008-10-17\",\"Id\":\"Policy4324355464\",\"Statement\":[{\"Sid\":\"Stmt456464646477\",\"Action\":[\"s3:GetObject\"],\"Effect\":\"Allow\",\"Resource\":"
                + "[\"arn:aws:s3:::mybucket/some/path/*\"],\"Principal\":{\"AWS\":[\"*\"]}}]}", endpoint.getConfiguration().getPolicy());
        assertEquals("REDUCED_REDUNDANCY", endpoint.getConfiguration().getStorageClass());
        assertEquals("confidential", endpoint.getConfiguration().getPrefix());
        assertFalse(endpoint.getConfiguration().isIncludeBody());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutBucketName() throws Exception {
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-s3:// ");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-s3://MyTopic?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-s3://MyTopic?accessKey=xxx");
    }
    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        S3Component component = new S3Component(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        S3Endpoint endpoint = (S3Endpoint)component.createEndpoint("aws-s3://MyBucket");
        
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        S3Component component = new S3Component(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        component.setRegion(Regions.US_WEST_1.toString());
        S3Endpoint endpoint = (S3Endpoint)component.createEndpoint("aws-s3://MyBucket?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("MyBucket", endpoint.getConfiguration().getBucketName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }
    
    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        AmazonS3ClientMock mock = new AmazonS3ClientMock();
        
        ((JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry()).bind("amazonS3Client", mock);
        
        S3Component component = new S3Component(context);
        component.createEndpoint("aws-s3://MyTopic?amazonS3Client=#amazonS3Client");
    }
}
