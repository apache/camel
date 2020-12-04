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

import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.StringInputStream;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test to verify that the polling consumer delivers an empty Exchange when the sendEmptyMessageWhenIdle property is set
 * and a polling event yields no results.
 */
public class S3ConsumerPrefixTest extends CamelTestSupport {

    @BindToRegistry("amazonS3Client")
    DummyAmazonS3Client clientMock = new DummyAmazonS3Client();

    @Test
    public void testConsumePrefixedMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();
        assertEquals("Camel rocks!", mock.getExchanges().get(0).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("aws-s3://mycamelbucket?amazonS3Client=#amazonS3Client&delay=50"
                     + "&maxMessagesPerPoll=5&prefix=confidential").to("mock:result");
            }
        };
    }

    class DummyAmazonS3Client extends AmazonS3Client {

        private AtomicInteger requestCount = new AtomicInteger();

        DummyAmazonS3Client() {
            super(new BasicAWSCredentials("myAccessKey", "mySecretKey"));
        }

        @Override
        public ObjectListing listObjects(ListObjectsRequest request) throws AmazonClientException, AmazonServiceException {
            int currentRequestCount = requestCount.incrementAndGet();

            assertEquals("mycamelbucket", request.getBucketName());
            if (currentRequestCount == 2) {
                assertEquals("confidential", request.getPrefix());
            }

            ObjectListing response = new ObjectListing();
            response.setBucketName(request.getBucketName());
            response.setPrefix(request.getPrefix());

            S3ObjectSummary s3ObjectSummary = new S3ObjectSummary();
            s3ObjectSummary.setBucketName(request.getBucketName());
            s3ObjectSummary.setKey("key");
            response.getObjectSummaries().add(s3ObjectSummary);

            return response;
        }

        @Override
        public S3Object getObject(String bucketName, String key) throws AmazonClientException, AmazonServiceException {
            assertEquals("mycamelbucket", bucketName);
            assertEquals("key", key);

            S3Object s3Object = new S3Object();
            s3Object.setBucketName(bucketName);
            s3Object.setKey(key);
            try {
                s3Object.setObjectContent(new StringInputStream("Camel rocks!"));
            } catch (UnsupportedEncodingException e) {
                // noop
            }

            return s3Object;
        }

        @Override
        public void deleteObject(String bucketName, String key) throws AmazonClientException, AmazonServiceException {
            // noop
        }
    }
}
