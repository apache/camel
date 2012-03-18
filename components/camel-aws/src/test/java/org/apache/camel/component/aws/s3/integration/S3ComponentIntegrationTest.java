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
package org.apache.camel.component.aws.s3.integration;

import java.io.InputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.s3.S3Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class S3ComponentIntegrationTest extends CamelTestSupport {
    
    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;
    
    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(2);
        
        Exchange exchange1 = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest1");
                exchange.getIn().setBody("This is my bucket content.");
            }
        });
        
        Exchange exchange2 = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.KEY, "CamelUnitTest2");
                exchange.getIn().setBody("This is my bucket content.");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
        assertResultExchange(result.getExchanges().get(1));
        
        assertResponseMessage(exchange1.getIn());
        assertResponseMessage(exchange2.getIn());
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
        
        assertResponseMessage(exchange.getOut());
    }
    
    private void assertResultExchange(Exchange resultExchange) {
        assertIsInstanceOf(InputStream.class, resultExchange.getIn().getBody());
        assertEquals("This is my bucket content.", resultExchange.getIn().getBody(String.class));
        assertEquals("mynewcamelbucket", resultExchange.getIn().getHeader(S3Constants.BUCKET_NAME));
        assertTrue(resultExchange.getIn().getHeader(S3Constants.KEY, String.class).startsWith("CamelUnitTest"));
        assertNull(resultExchange.getIn().getHeader(S3Constants.VERSION_ID)); // not enabled on this bucket
        assertNotNull(resultExchange.getIn().getHeader(S3Constants.LAST_MODIFIED));
        assertEquals("3a5c8b1ad448bca04584ecb55b836264", resultExchange.getIn().getHeader(S3Constants.E_TAG));
        assertEquals("application/octet-stream", resultExchange.getIn().getHeader(S3Constants.CONTENT_TYPE));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_ENCODING));
        assertEquals(26L, resultExchange.getIn().getHeader(S3Constants.CONTENT_LENGTH));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_DISPOSITION));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_MD5));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CACHE_CONTROL));
    }
    
    private void assertResponseMessage(Message message) {
        assertEquals("3a5c8b1ad448bca04584ecb55b836264", message.getHeader(S3Constants.E_TAG));
        assertNull(message.getHeader(S3Constants.VERSION_ID));
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String s3EndpointUri = "aws-s3://mynewcamelbucket?accessKey=xxx&secretKey=yyy&region=us-west-1&policy=%7B%22Version%22%3A%222008-10-17%22,%22Id%22%3A%22Policy4324355464%22,"
                    + "%22Statement%22%3A%5B%7B%22Sid%22%3A%22Stmt456464646477%22,%22Action%22%3A%5B%22s3%3AGetObject%22%5D,%22Effect%22%3A%22Allow%22,%22Resource%22%3A%5B%22arn%3A"
                    + "aws%3As3%3A%3A%3Amynewcamelbucket/*%22%5D,%22Principal%22%3A%7B%22AWS%22%3A%5B%22*%22%5D%7D%7D%5D%7D";
                
                from("direct:start")
                    .to(s3EndpointUri);
                
                from(s3EndpointUri)
                    .to("mock:result");
            }
        };
    }
}