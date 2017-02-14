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
package org.apache.camel.component.azure.blob;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class BlobServiceProducerSpringTest extends CamelSpringTestSupport {
    
    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;
    
    @Test
    @Ignore
    public void testUpdateBlockBlob() throws Exception {
        result.expectedMessageCount(1);
        
        template.send("direct:updateBlockBlob", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Block Blob");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
    }
    
    @Test
    @Ignore
    public void testUploadBlobBlocks() throws Exception {
        result.expectedMessageCount(1);
        final BlobBlock st = new BlobBlock(new ByteArrayInputStream("Block Blob List".getBytes()));
        template.send("direct:uploadBlobBlocks", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(st);
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
    }

    @Test
    @Ignore
    public void testGetBlockBlob() throws Exception {
        result.expectedMessageCount(1);
        OutputStream os = new ByteArrayOutputStream();
        template.send("direct:getBlockBlob", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(os);
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
    }
    
    @Test
    @Ignore
    public void testUpdateAppendBlob() throws Exception {
        result.expectedMessageCount(1);
        
        template.send("direct:updateAppendBlob", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Append Blob");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
    }
    
    @Test
    @Ignore
    public void testUpdatePageBlob() throws Exception {
        result.expectedMessageCount(1);
        final byte[] data = new byte[512];
        Arrays.fill(data, (byte)1);
        template.send("direct:updatePageBlob", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new ByteArrayInputStream(data));
            }
        });
        
        assertMockEndpointsSatisfied();
        
        assertResultExchange(result.getExchanges().get(0));
    }
    
    private void assertResultExchange(Exchange resultExchange) {
    }
    
    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/azure/blob/BlobServiceProducerSpringTest-context.xml");
    }
    
}