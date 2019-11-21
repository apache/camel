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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Map;

import com.amazonaws.services.s3.model.PutObjectRequest;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class S3ComponentContentLengthFileTest extends CamelTestSupport {

    @BindToRegistry("amazonS3Client")
    AmazonS3ClientMock client = new AmazonS3ClientMock();

    @EndpointInject("direct:startKeep")
    ProducerTemplate templateKeep;

    @EndpointInject("direct:startDelete")
    ProducerTemplate templateDelete;

    @EndpointInject("mock:result")
    MockEndpoint result;

    File testFile;

    String getCamelBucket() {
        return "mycamelbucket";
    }

    @Before
    public void setup() throws Exception {
        super.setUp();

        testFile = FileUtil.createTempFile("test", "file", new File("target/tmp"));

        FileWriter writer = new FileWriter(testFile);
        writer.write("This is my bucket content.");
        writer.close();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        FileUtil.deleteFile(testFile);
    }

    @Test
    public void sendFile() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = templateKeep.send("direct:startKeep", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody(new FileInputStream(testFile));
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0), true);

        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals(getCamelBucket(), putObjectRequest.getBucketName());

        assertResponseMessage(exchange.getIn());

        assertFileExists(testFile.getAbsolutePath());
    }

    @Test
    public void sendFileWithContentLength() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = templateKeep.send("direct:startKeep", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setHeader(S3Constants.CONTENT_LENGTH, testFile.length());
                exchange.getIn().setBody(new FileInputStream(testFile));
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0), true);

        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals(getCamelBucket(), putObjectRequest.getBucketName());

        assertResponseMessage(exchange.getIn());

        assertFileExists(testFile.getAbsolutePath());
    }

    void assertResultExchange(Exchange resultExchange, boolean delete) {
        assertIsInstanceOf(InputStream.class, resultExchange.getIn().getBody());

        if (!delete) {
            // assert on the file content only in case the "deleteAfterWrite"
            // option is NOT enabled
            // in which case we would still have the file and thereby could
            // assert on it's content
            assertEquals("This is my bucket content.", resultExchange.getIn().getBody(String.class));
        }

        assertEquals(getCamelBucket(), resultExchange.getIn().getHeader(S3Constants.BUCKET_NAME));
        assertEquals("CamelUnitTest", resultExchange.getIn().getHeader(S3Constants.KEY));
        assertNull(resultExchange.getIn().getHeader(S3Constants.VERSION_ID)); // not
                                                                              // enabled
                                                                              // on
                                                                              // this
                                                                              // bucket
        assertNull(resultExchange.getIn().getHeader(S3Constants.LAST_MODIFIED));
        assertNull(resultExchange.getIn().getHeader(S3Constants.E_TAG));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_TYPE));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_ENCODING));
        assertEquals(0L, resultExchange.getIn().getHeader(S3Constants.CONTENT_LENGTH));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_DISPOSITION));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_MD5));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CACHE_CONTROL));
        assertNotNull(resultExchange.getIn().getHeader(S3Constants.USER_METADATA));
        assertEquals(0, resultExchange.getIn().getHeader(S3Constants.S3_HEADERS, Map.class).size());
    }

    void assertResponseMessage(Message message) {
        assertEquals("3a5c8b1ad448bca04584ecb55b836264", message.getHeader(S3Constants.E_TAG));
        assertNull(message.getHeader(S3Constants.VERSION_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String awsEndpoint = "aws-s3://" + getCamelBucket() + "?amazonS3Client=#amazonS3Client";

                from("direct:startKeep").to(awsEndpoint + "&deleteAfterWrite=false");

                from("direct:startDelete").to(awsEndpoint + "&deleteAfterWrite=true");

                from(awsEndpoint + "&maxMessagesPerPoll=5").to("mock:result");
            }
        };
    }
}
