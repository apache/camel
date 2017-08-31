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

import java.io.File;

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

public class S3ComponentStringTest extends CamelTestSupport {

    @EndpointInject(uri = "direct:sendString")
    ProducerTemplate templateSendString;

    @EndpointInject(uri = "mock:result")
    MockEndpoint result;

    AmazonS3ClientMock client;

    File testFile;

    String getCamelBucket() {
        return "mycamelbucket";
    }

    @Test
    public void sendString() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = templateSendString.send("direct:sendString", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest");
                exchange.getIn().setBody("Peppe");
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0), true);

        PutObjectRequest putObjectRequest = client.putObjectRequests.get(0);
        assertEquals(getCamelBucket(), putObjectRequest.getBucketName());

        assertResponseMessage(exchange.getIn());
    }

    void assertResultExchange(Exchange resultExchange, boolean delete) {
        assertIsInstanceOf(String.class, resultExchange.getIn().getBody());
    }

    void assertResponseMessage(Message message) {
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
                String awsEndpoint = "aws-s3://" + getCamelBucket() + "?amazonS3Client=#amazonS3Client&region=us-west-1";

                from("direct:sendString")
                        .to(awsEndpoint + "&deleteAfterWrite=false").to("mock:result");
            }
        };
    }
}
