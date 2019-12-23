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
package org.apache.camel.component.cxf.jaxrs;

import java.io.InputStream;

import javax.ws.rs.core.Response;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit test that verify header propagation functionality for CxfRsProducer
 * that uses WebClient API.
 *  
 */
@ContextConfiguration
public class CxfRsProducerHeaderTest extends AbstractJUnit4SpringContextTests {
    static int port2 = CXFTestSupport.getPort2(); 
    static int port3 = CXFTestSupport.getPort("CxfRsProducerHeaderTest.1");
    
    private static final Object RESPONSE = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Customer><id>123</id><name>John</name></Customer>";
    
    @Autowired
    protected CamelContext camelContext;
    
    @Test
    public void testInvokeThatDoesNotInvolveHeaders() throws Exception {
        Exchange exchange = camelContext.createProducerTemplate().send("direct://http", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
                inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                inMessage.setHeader(Exchange.HTTP_PATH, "/customerservice/customers/123");                
                inMessage.setBody(null);                
            }
            
        });
     
        // verify the out message is a Response object by default
        Response response = (Response)exchange.getOut().getBody();
        assertNotNull("The response should not be null ", response);
        assertEquals(200, response.getStatus());
        
        // test converter (from Response to InputStream)
        assertEquals(RESPONSE, CxfUtils.getStringFromInputStream(exchange.getOut().getBody(InputStream.class)));
    }

    @Test
    public void testHeaderFilteringAndPropagation() throws Exception {
        Exchange exchange = camelContext.createProducerTemplate().send("direct://http2", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message inMessage = exchange.getIn();
                inMessage.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.TRUE);
                inMessage.setHeader(Exchange.HTTP_METHOD, "GET");
                inMessage.setHeader(Exchange.HTTP_PATH, "/CxfRsProducerHeaderTest/customerservice/customers/123");    
                inMessage.setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
                inMessage.setHeader("my-user-defined-header", "my-value");
                inMessage.setBody(null);                
            }
            
        });
     
        // get the response message 
        Response response = (Response)exchange.getOut().getBody();
        
        // check the response code on the Response object as set by the "HttpProcess"
        assertEquals(200, response.getStatus());
        
        // get out message
        Message outMessage = exchange.getOut();
        
        // verify the content-type header sent by the "HttpProcess"
        assertEquals("text/xml", outMessage.getHeader(Exchange.CONTENT_TYPE));
        
        // check the user defined header echoed by the "HttpProcess"
        assertEquals("my-value", outMessage.getHeader("echo-my-user-defined-header"));
        
        // check the Accept header echoed by the "HttpProcess"
        assertEquals("application/json", outMessage.getHeader("echo-accept"));
        
        // make sure the HttpProcess have not seen CxfConstants.CAMEL_CXF_RS_USING_HTTP_API
        assertNull(outMessage.getHeader("failed-header-using-http-api"));
        
        // make sure response code has been set in out header
        assertEquals(Integer.valueOf(200), outMessage.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
    }
    
    public static class HttpProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Message out = exchange.getOut();
            
            if (in.getHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API) != null) {
                // this should have been filtered
                out.setHeader("failed-header-using-http-api", CxfConstants.CAMEL_CXF_RS_USING_HTTP_API);
            }
            
            out.setHeader("echo-accept", in.getHeader("Accept"));
            out.setHeader("echo-my-user-defined-header", in.getHeader("my-user-defined-header"));
            
            exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
            exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "text/xml");
            
            
        }
        
    }
}
