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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class S3ComponentCopyObjectTest extends CamelTestSupport {
    
    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;
    
    private AmazonS3ClientMock client;
    
    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);
        
        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(S3Constants.BUCKET_DESTINATION_NAME, "camelDestinationBucket");
                exchange.getIn().setHeader(S3Constants.KEY, "camelKey");
                exchange.getIn().setHeader(S3Constants.DESTINATION_KEY, "camelDestinationKey");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
        
    }
    
    private void assertResultExchange(Exchange resultExchange) {
        assertEquals(resultExchange.getIn().getHeader(S3Constants.VERSION_ID), "11192828ahsh2723");
        assertNull(resultExchange.getIn().getHeader(S3Constants.LAST_MODIFIED));
        assertEquals(resultExchange.getIn().getHeader(S3Constants.E_TAG), "3a5c8b1ad448bca04584ecb55b836264");
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_TYPE));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_ENCODING));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_DISPOSITION));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CONTENT_MD5));
        assertNull(resultExchange.getIn().getHeader(S3Constants.CACHE_CONTROL));
        assertNull(resultExchange.getIn().getHeader(S3Constants.USER_METADATA));
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
                String awsEndpoint = "aws-s3://mycamelbucket?amazonS3Client=#amazonS3Client&region=us-west-1&operation=copyObject";
                
                from("direct:start")
                    .to(awsEndpoint)
                    .to("mock:result");
                
            }
        };
    }
}