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

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpNotFoundException;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MultiServletConsumerTest extends ServletCamelRouterTestSupport {
    /**
     * @return The web.xml to use for testing.
     */
    protected String getConfiguration() {
        return "/org/apache/camel/component/servlet/multiServletWeb.xml";
    }
    
    protected void loadServlets() throws Exception {
        try {
            sr.newClient().getResponse(CONTEXT_URL + "/services1");
        } catch (HttpNotFoundException e) {
            // ignore, we just want to boot up the servlet
        }
        
        try {
            sr.newClient().getResponse(CONTEXT_URL + "/services2");
        } catch (HttpNotFoundException e) {
            // ignore, we just want to boot up the servlet
        }
    }
    
    @Test
    public void testMultiServletsConsumers() throws Exception {
        String result = getService("/services2/hello");
        assertEquals("Get a wrong response", "/mycontext/services2/hello", result);
        
        result = getService("/services1/echo");
        assertEquals("Get a wrong response", "/mycontext/services1/echo", result);
        
        result = getService("/services2/echo");
        assertEquals("Get a wrong response", "/mycontext/services2/echo", result);
    }
    
    public String getService(String path) throws Exception {
        WebRequest req = new GetMethodWebRequest(CONTEXT_URL + path);
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        
        return response.getText();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            Processor echoRequestURIProcessor = new Processor() {
                public void process(Exchange exchange) throws Exception {
                    String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                    exchange.getOut().setBody(uri);
                }
            };
            
            public void configure() {
                errorHandler(noErrorHandler());
                from("servlet:///hello").process(echoRequestURIProcessor);
                from("servlet:///echo?servletName=CamelServlet1").process(echoRequestURIProcessor);
                from("servlet:///echo?servletName=CamelServlet2").process(echoRequestURIProcessor);
            }
        };
    }

}
