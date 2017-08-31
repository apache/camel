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

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.s3.model.PutObjectRequest;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class S3ComponentExistingBucketTest extends CamelTestSupport {
    
    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;
    
    private AmazonS3ClientMock client;
    
    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);
        
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody("This is my bucket content.");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
        
        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals("REDUCED_REDUNDANCY", putObjectRequest.getStorageClass());
        assertEquals("mycamelbucket", putObjectRequest.getBucketName());
        
        assertResponseMessage(exchange.getIn());
    }

    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);
        
        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody("This is my bucket content.");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
        
        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals("REDUCED_REDUNDANCY", putObjectRequest.getStorageClass());
        assertEquals("mycamelbucket", putObjectRequest.getBucketName());
        
        assertResponseMessage(exchange.getOut());
    }
    
    @Test
    public void sendCustomHeaderValues() throws Exception {
        result.expectedMessageCount(1);
        final Date now = new Date();
        final Map<String, String> userMetadata = new HashMap<String, String>();
        userMetadata.put("CamelName", "Camel");
        final Map<String, String> s3Headers = new HashMap<String, String>();
        s3Headers.put("x-aws-s3-header", "extra");

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.STORAGE_CLASS, "STANDARD");
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setHeader(S3Constants.CONTENT_LENGTH, 26L);
                exchange.getIn().setHeader(S3Constants.CONTENT_TYPE, "text/html");
                exchange.getIn().setHeader(S3Constants.CACHE_CONTROL, "no-cache");
                exchange.getIn().setHeader(S3Constants.CONTENT_DISPOSITION, "attachment;");
                exchange.getIn().setHeader(S3Constants.CONTENT_ENCODING, "gzip");
                exchange.getIn().setHeader(S3Constants.CONTENT_MD5, "TWF");
                exchange.getIn().setHeader(S3Constants.LAST_MODIFIED, now);
                exchange.getIn().setHeader(S3Constants.USER_METADATA, userMetadata);
                exchange.getIn().setHeader(S3Constants.S3_HEADERS, s3Headers);

                exchange.getIn().setBody("This is my bucket content.");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
        
        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals("STANDARD", putObjectRequest.getStorageClass());
        assertEquals("mycamelbucket", putObjectRequest.getBucketName());
        assertEquals(26L, putObjectRequest.getMetadata().getContentLength());
        assertEquals("text/html", putObjectRequest.getMetadata().getContentType());
        assertEquals("no-cache", putObjectRequest.getMetadata().getCacheControl());
        assertEquals("attachment;", putObjectRequest.getMetadata().getContentDisposition());
        assertEquals("gzip", putObjectRequest.getMetadata().getContentEncoding());
        assertEquals("TWF", putObjectRequest.getMetadata().getContentMD5());
        assertEquals(now, putObjectRequest.getMetadata().getLastModified());
        assertEquals(userMetadata, putObjectRequest.getMetadata().getUserMetadata());
        assertEquals("extra", putObjectRequest.getMetadata().getRawMetadataValue("x-aws-s3-header"));

        assertResponseMessage(exchange.getIn());
    }
    
    private void assertResultExchange(Exchange resultExchange) {
        assertIsInstanceOf(InputStream.class, resultExchange.getIn().getBody());
        assertEquals("This is my bucket content.", resultExchange.getIn().getBody(String.class));
        assertEquals("mycamelbucket", resultExchange.getIn().getHeader(S3Constants.BUCKET_NAME));
        assertEquals("CamelUnitTest", resultExchange.getIn().getHeader(S3Constants.KEY));
        assertNull(resultExchange.getIn().getHeader(S3Constants.VERSION_ID)); // not enabled on this bucket
        assertNull(resultExchange.getIn().getHeader(S3Constants.LAST_MODIFIED));
        assertNull(resultExchange.getIn().getHeader(S3Constants.E_TAG));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_TYPE));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_ENCODING));
        assertEquals(0L, resultExchange.getIn().getHeader(S3Constants.CONTENT_LENGTH));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_DISPOSITION));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_MD5));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CACHE_CONTROL));
        assertNull(resultExchange.getIn().getHeader(S3Constants.USER_METADATA));
        assertEquals(0, resultExchange.getIn().getHeader(S3Constants.S3_HEADERS, Map.class).size());
    }
    
    private void assertResponseMessage(Message message) {
        assertEquals("3a5c8b1ad448bca04584ecb55b836264", message.getHeader(S3Constants.E_TAG));
        assertNull(message.getHeader(S3Constants.VERSION_ID));
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        client = new AmazonS3ClientMock();
        registry.bind("amazonS3Client", client);
        
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String awsEndpoint = "aws-s3://mycamelbucket?amazonS3Client=#amazonS3Client&region=us-west-1";
                
                from("direct:start")
                    .to(awsEndpoint + "&storageClass=REDUCED_REDUNDANCY");
                
                from(awsEndpoint + "&maxMessagesPerPoll=5")
                    .to("mock:result");
            }
        };
    }
}