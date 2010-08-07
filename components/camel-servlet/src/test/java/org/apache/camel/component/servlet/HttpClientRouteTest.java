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
package org.apache.camel.component.servlet;

import java.io.ByteArrayInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Ignore;
import org.junit.Test;

public class HttpClientRouteTest extends ServletCamelRouterTestSupport {
    private static final String POST_DATA = "<request> hello world </request>";
    private static final String CONTENT_TYPE = "text/xml";

    @Test
    public void testHttpClient() throws Exception {
        WebRequest req = new PostMethodWebRequest(CONTEXT_URL + "/services/hello", new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        
        assertEquals("Get wrong content type", "text/xml", response.getContentType());
        assertTrue("UTF-8".equalsIgnoreCase(response.getCharacterSet()));
        assertEquals("Get a wrong message header", "/hello", response.getHeaderField("PATH"));
        assertEquals("The response message is wrong ", "OK", response.getResponseMessage());
        
        req = new PostMethodWebRequest(CONTEXT_URL + "/services/helloworld", new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        response = client.getResponse(req);
        
        assertEquals("Get wrong content type", "text/xml", response.getContentType());
        assertTrue("UTF-8".equalsIgnoreCase(response.getCharacterSet()));
        assertEquals("Get a wrong message header", "/helloworld", response.getHeaderField("PATH"));
        assertEquals("The response message is wrong ", "OK", response.getResponseMessage());
        client.setExceptionsThrownOnErrorStatus(false);
    }
    
    @Test
    public void testHttpConverter() throws Exception {
        WebRequest req = new PostMethodWebRequest(CONTEXT_URL + "/services/testConverter", new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        ServletUnitClient client = newClient();
        client.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = client.getResponse(req);        
        assertEquals("The response message is wrong ", "OK", response.getResponseMessage());
        assertEquals("The response body is wrong", "Bye World", response.getText());
    }
    
    public static class MyServletRoute extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            errorHandler(noErrorHandler());
            // START SNIPPET: route
            from("servlet:///hello?matchOnUriPrefix=true").process(new Processor() {
                public void process(Exchange exchange) throws Exception {                    
                    String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                    String path = exchange.getIn().getHeader(Exchange.HTTP_PATH, String.class);
                    assertEquals("Get a wrong content type", CONTENT_TYPE, contentType);
                    String charsetEncoding = exchange.getIn().getHeader(Exchange.HTTP_CHARACTER_ENCODING, String.class);
                    assertEquals("Get a wrong charset name", "UTF-8", charsetEncoding);
                    exchange.getOut().setHeader(Exchange.CONTENT_TYPE, contentType + "; charset=UTF-8");                        
                    exchange.getOut().setHeader("PATH", path);
                    exchange.getOut().setBody("<b>Hello World</b>");
                }
            });
            // END SNIPPET: route
            
            from("servlet:///testConverter?matchOnUriPrefix=true")
                .convertBodyTo(String.class)             
                .process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        HttpServletRequest request = exchange.getIn(HttpServletRequest.class);
                        assertNotNull("We should get request object here", request);
                        HttpServletResponse response = exchange.getIn(HttpServletResponse.class);
                        assertNotNull("We should get response object here", response);
                        String s = exchange.getIn().getBody(String.class);
                        assertEquals("<request> hello world </request>", s);
                    }
                }).transform(constant("Bye World"));
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new MyServletRoute();
    }    

}
